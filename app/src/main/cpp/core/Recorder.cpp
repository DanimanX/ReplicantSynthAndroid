#include "Recorder.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

namespace rep {

namespace {
void putU32(uint8_t* p, uint32_t v) {
    p[0] = static_cast<uint8_t>(v & 0xFF);
    p[1] = static_cast<uint8_t>((v >> 8) & 0xFF);
    p[2] = static_cast<uint8_t>((v >> 16) & 0xFF);
    p[3] = static_cast<uint8_t>((v >> 24) & 0xFF);
}
void putU16(uint8_t* p, uint16_t v) {
    p[0] = static_cast<uint8_t>(v & 0xFF);
    p[1] = static_cast<uint8_t>((v >> 8) & 0xFF);
}
} // namespace

Recorder::~Recorder() { stop(); }

bool Recorder::start(const std::string& path, int sampleRate, int channels,
                     bool blocking) {
    stop();
    sampleRate_ = std::max(8000, sampleRate);
    channels_ = std::clamp(channels, 1, 2);
    blocking_.store(blocking, std::memory_order_relaxed);
    file_ = std::fopen(path.c_str(), "wb");
    if (file_ == nullptr) {
        failed_.store(true, std::memory_order_relaxed);
        return false;
    }
    failed_.store(false, std::memory_order_relaxed);
    dataBytes_ = 0;
    writeHeader(0);
    if (std::ferror(file_) != 0) {
        std::fclose(file_);
        file_ = nullptr;
        failed_.store(true, std::memory_order_relaxed);
        return false;
    }
    head_.store(0, std::memory_order_relaxed);
    tail_.store(0, std::memory_order_relaxed);
    framesPushed_.store(0, std::memory_order_relaxed);
    framesWritten_.store(0, std::memory_order_relaxed);
    dropped_.store(0, std::memory_order_relaxed);
    stopRequested_.store(false, std::memory_order_relaxed);
    active_.store(true, std::memory_order_release);
    scratch_.assign(8192, 0);
    writer_ = std::thread([this] { writerLoop(); });
    return true;
}

void Recorder::stop() {
    active_.store(false, std::memory_order_release);
    if (writer_.joinable()) {
        stopRequested_.store(true, std::memory_order_release);
        writer_.join();
    }
    if (file_ != nullptr) {
        std::fflush(file_);
        patchHeader();
        std::fclose(file_);
        file_ = nullptr;
    }
    stopRequested_.store(false, std::memory_order_relaxed);
}

void Recorder::push(const float* interleaved, int frames) noexcept {
    if (!active_.load(std::memory_order_acquire) || interleaved == nullptr || frames <= 0) {
        return;
    }
    uint32_t head = head_.load(std::memory_order_relaxed);
    const bool blocking = blocking_.load(std::memory_order_relaxed);
    for (int i = 0; i < frames; ++i) {
        uint32_t next = (head + 1) % kRing;
        while (next == tail_.load(std::memory_order_acquire)) {
            if (!blocking || !active_.load(std::memory_order_acquire)) {
                dropped_.fetch_add(static_cast<int64_t>(frames - i),
                                   std::memory_order_relaxed);
                head_.store(head, std::memory_order_release);
                framesPushed_.fetch_add(static_cast<int64_t>(i),
                                        std::memory_order_relaxed);
                return;
            }
            std::this_thread::yield();
            next = (head + 1) % kRing;
        }
        ring_[head].l = interleaved[i * 2];
        ring_[head].r = interleaved[i * 2 + 1];
        head = next;
    }
    head_.store(head, std::memory_order_release);
    framesPushed_.fetch_add(static_cast<int64_t>(frames), std::memory_order_relaxed);
}

void Recorder::writerLoop() {
    std::vector<Frame> batch(1024);
    auto lastData = std::chrono::steady_clock::now();

    for (;;) {
        int count = 0;
        uint32_t tail = tail_.load(std::memory_order_relaxed);
        while (count < static_cast<int>(batch.size())) {
            const uint32_t head = head_.load(std::memory_order_acquire);
            if (tail == head) break;
            batch[static_cast<size_t>(count++)] = ring_[tail];
            tail = (tail + 1) % kRing;
        }
        tail_.store(tail, std::memory_order_release);

        if (count > 0) {
            lastData = std::chrono::steady_clock::now();
            size_t o = 0;
            for (int i = 0; i < count; ++i) {
                const float l = std::clamp(batch[static_cast<size_t>(i)].l, -1.0f, 1.0f);
                const float r = std::clamp(batch[static_cast<size_t>(i)].r, -1.0f, 1.0f);
                const int32_t li = static_cast<int32_t>(std::lround(l * 8388607.0f));
                const int32_t ri = static_cast<int32_t>(std::lround(r * 8388607.0f));
                scratch_[o++] = static_cast<uint8_t>(li & 0xFF);
                scratch_[o++] = static_cast<uint8_t>((li >> 8) & 0xFF);
                scratch_[o++] = static_cast<uint8_t>((li >> 16) & 0xFF);
                if (channels_ == 2) {
                    scratch_[o++] = static_cast<uint8_t>(ri & 0xFF);
                    scratch_[o++] = static_cast<uint8_t>((ri >> 8) & 0xFF);
                    scratch_[o++] = static_cast<uint8_t>((ri >> 16) & 0xFF);
                } else {
                    scratch_[o++] = static_cast<uint8_t>(li & 0xFF);
                    scratch_[o++] = static_cast<uint8_t>((li >> 8) & 0xFF);
                }
            }
            if (file_ != nullptr) {
                const size_t wrote = std::fwrite(scratch_.data(), 1, o, file_);
                if (wrote != o) failed_.store(true, std::memory_order_relaxed);
                dataBytes_ += static_cast<uint32_t>(wrote);
                framesWritten_.fetch_add(count, std::memory_order_relaxed);
            }
            continue;
        }

        if (stopRequested_.load(std::memory_order_acquire)) {
            // Give a final push from the audio thread a moment to land.
            const auto quiet = std::chrono::steady_clock::now() - lastData;
            if (quiet > std::chrono::milliseconds(25)) break;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
}

void Recorder::writeHeader(uint32_t dataBytes) {
    uint8_t h[44] = {};
    const uint32_t byteRate = static_cast<uint32_t>(sampleRate_ * channels_ * 3);
    const uint16_t blockAlign = static_cast<uint16_t>(channels_ * 3);
    std::memcpy(h + 0, "RIFF", 4);
    putU32(h + 4, 36 + dataBytes);
    std::memcpy(h + 8, "WAVEfmt ", 8);
    putU32(h + 16, 16);
    putU16(h + 20, 1); // PCM
    putU16(h + 22, static_cast<uint16_t>(channels_));
    putU32(h + 24, static_cast<uint32_t>(sampleRate_));
    putU32(h + 28, byteRate);
    putU16(h + 32, blockAlign);
    putU16(h + 34, 24);
    std::memcpy(h + 36, "data", 4);
    putU32(h + 40, dataBytes);
    if (file_ != nullptr) {
        std::fseek(file_, 0, SEEK_SET);
        std::fwrite(h, 1, sizeof(h), file_);
    }
}

void Recorder::patchHeader() {
    if (file_ == nullptr) return;
    const long data = std::ftell(file_);
    if (data < 44) return;
    writeHeader(static_cast<uint32_t>(data - 44));
}

} // namespace rep
