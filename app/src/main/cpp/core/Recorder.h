#pragma once

// Lock-free capture tap: the audio thread pushes interleaved stereo frames into
// a ring, a writer thread streams 24-bit WAV to disk. No allocation, locking or
// file I/O ever happens on the audio thread.

#include <array>
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <string>
#include <thread>
#include <vector>

namespace rep {

class Recorder {
public:
    Recorder() = default;
    ~Recorder();

    bool start(const std::string& path, int sampleRate, int channels = 2,
               bool blocking = false);
    void stop();
    void push(const float* interleaved, int frames) noexcept;
    bool active() const noexcept { return active_.load(std::memory_order_relaxed); }
    int64_t frames() const noexcept { return framesPushed_.load(std::memory_order_relaxed); }
    int64_t dropped() const noexcept { return dropped_.load(std::memory_order_relaxed); }
    bool failed() const noexcept { return failed_.load(std::memory_order_relaxed); }

private:
    struct Frame {
        float l;
        float r;
    };
    static constexpr uint32_t kRing = 1u << 15;

    void writerLoop();
    void writeHeader(uint32_t dataBytes);
    void patchHeader();

    std::array<Frame, kRing> ring_{};
    std::atomic<uint32_t> head_{0};
    std::atomic<uint32_t> tail_{0};
    std::atomic<bool> active_{false};
    std::atomic<bool> stopRequested_{false};
    std::atomic<bool> failed_{false};
    std::atomic<int64_t> framesPushed_{0};
    std::atomic<int64_t> framesWritten_{0};
    std::atomic<int64_t> dropped_{0};
    std::atomic<bool> blocking_{false};

    std::thread writer_;
    std::FILE* file_ = nullptr;
    int sampleRate_ = 48000;
    int channels_ = 2;
    uint32_t dataBytes_ = 0;
    std::vector<uint8_t> scratch_;
};

} // namespace rep
