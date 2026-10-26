#include "ClipEngine.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace rep {

namespace {
constexpr int kFxIdBypass = 0;

inline float balanceL(float pan) { return pan <= 0.0f ? 1.0f : 1.0f - pan; }
inline float balanceR(float pan) { return pan >= 0.0f ? 1.0f : 1.0f + pan; }
constexpr int kDefaultAcidNotes[6] = {0, 0, 12, 0, 7, 12};
constexpr int kDefaultWaveNotes[4] = {0, 3, 7, 12};
constexpr int kDefaultAcidSteps[6] = {0, 3, 6, 8, 11, 14};
constexpr int kDefaultWaveSteps[4] = {0, 4, 8, 12};
} // namespace

ClipEngine::ClipEngine() {
    // Build the shared wavetable banks once, on this construction thread.
    sharedWavetableBanks();

    for (int i = 0; i < kPads; ++i) {
        fx_[static_cast<size_t>(i)].store(nullptr, std::memory_order_relaxed);
        samples_[static_cast<size_t>(i)].store(nullptr, std::memory_order_relaxed);
        padState_[static_cast<size_t>(i)].store(0, std::memory_order_relaxed);
        padMeter_[static_cast<size_t>(i)].store(0.0f, std::memory_order_relaxed);
        clips_[static_cast<size_t>(i)].acid = std::make_unique<Acid303>();
        clips_[static_cast<size_t>(i)].wave = std::make_unique<WaveSynth>();
        clips_[static_cast<size_t>(i)].drums = std::make_unique<DrumKit909>();
    }
    prepare(48000.0, 512);
}

ClipEngine::~ClipEngine() {
    stopRecording();
}

void ClipEngine::prepare(double sampleRate, int maxBlockFrames) {
    sampleRate_ = std::max(8000.0, sampleRate);
    maxBlockFrames_ = std::max(64, maxBlockFrames);
    samplesPerStep_ = sampleRate_ * 60.0 / tempo_.load(std::memory_order_relaxed) / 4.0;
    stepDuration_ = samplesPerStep_;

    stopRecording();

    for (auto& clip : clips_) {
        clip.acid->prepare(sampleRate_);
        clip.wave->prepare(sampleRate_);
        clip.drums->prepare(sampleRate_);
        clip.state = static_cast<int>(ClipState::Stopped);
        clip.pendingStart = -1;
        clip.pendingStop = -1;
        clip.phase = 0;
        clip.tailSamples = 0.0;
        clip.samplePos = 0.0;
        clip.lastNote = -1;
    }

    for (int i = 0; i < kPads; ++i) {
        Effect* fx = fx_[static_cast<size_t>(i)].load(std::memory_order_acquire);
        if (fx != nullptr) fx->prepare(static_cast<float>(sampleRate_));
    }

    silentBuffer_.assign(static_cast<size_t>(maxBlockFrames_ * 2), 0.0f);
    padCapture_.assign(static_cast<size_t>(maxBlockFrames_ * 2), 0.0f);
    masterCapture_.assign(static_cast<size_t>(maxBlockFrames_ * 2), 0.0f);

    transportRunning_ = false;
    absoluteStep_ = 0;
    samplesIntoStep_ = 0.0;
    padPeakBlock_.fill(0.0f);
    prepared_ = true;

    for (int i = 0; i < kPads; ++i) updatePadState(i);
}

bool ClipEngine::CommandQueue::push(const Command& command) {
    std::lock_guard<std::mutex> lock(pushMutex_);
    const uint32_t head = head_.load(std::memory_order_relaxed);
    const uint32_t next = (head + 1) % kCapacity;
    if (next == tail_.load(std::memory_order_acquire)) return false;
    items_[head] = command;
    head_.store(next, std::memory_order_release);
    return true;
}

bool ClipEngine::CommandQueue::pop(Command& command) {
    const uint32_t tail = tail_.load(std::memory_order_relaxed);
    if (tail == head_.load(std::memory_order_acquire)) return false;
    command = items_[tail];
    tail_.store((tail + 1) % kCapacity, std::memory_order_release);
    return true;
}

void ClipEngine::push(const Command& command) {
    commands_.push(command);
}

void ClipEngine::setPlaying(bool playing) noexcept {
    playing_.store(playing, std::memory_order_release);
}

void ClipEngine::setTempo(float bpm) noexcept {
    tempo_.store(clampf(bpm, 30.0f, 300.0f), std::memory_order_relaxed);
}

void ClipEngine::setQuantize(int steps) noexcept {
    static constexpr int allowed[] = {0, 1, 4, 16, 32, 64};
    int best = 16;
    int bestDist = 1 << 30;
    for (int value : allowed) {
        const int d = std::abs(value - steps);
        if (d < bestDist) {
            bestDist = d;
            best = value;
        }
    }
    quantize_.store(best, std::memory_order_relaxed);
}

void ClipEngine::setSwing(float swing) noexcept {
    swing_.store(clampf(swing, 0.0f, 0.66f), std::memory_order_relaxed);
}

void ClipEngine::setMasterGain(float gain) noexcept {
    masterGain_.store(clampf(gain, 0.0f, 1.5f), std::memory_order_relaxed);
}

void ClipEngine::ensureFx(int pad) {
    if (pad < 0 || pad >= kPads) return;
    if (fx_[static_cast<size_t>(pad)].load(std::memory_order_acquire) != nullptr) return;
    std::lock_guard<std::mutex> lock(fxMutex_);
    if (fx_[static_cast<size_t>(pad)].load(std::memory_order_acquire) != nullptr) return;
    auto effect = std::make_unique<Effect>();
    effect->prepare(static_cast<float>(sampleRate_));
    Effect* raw = effect.get();
    fxPool_.push_back(std::move(effect));
    fx_[static_cast<size_t>(pad)].store(raw, std::memory_order_release);
}

void ClipEngine::configureClip(int pad, int kind, int root, int length, float gain,
                               float pan, int fxType, bool fxEnabled, bool sampleLoop,
                               bool mute, bool solo, const float* params, int paramCount) {
    if (pad < 0 || pad >= kPads) return;
    Command command;
    command.type = kCmdConfigure;
    command.pad = static_cast<uint8_t>(pad);
    command.kind = static_cast<uint8_t>(clampi(kind, 0, 4));
    command.flags = static_cast<uint8_t>((sampleLoop ? 0x08 : 0) | (mute ? 0x01 : 0) |
                                         (solo ? 0x02 : 0));
    command.root = static_cast<int16_t>(clampi(root, 0, 127));
    command.length = static_cast<int16_t>(clampi(length, 1, kSteps));
    command.fxType = static_cast<int16_t>(clampi(fxType, 0, kFxCount - 1));
    command.fxEnabled = fxEnabled ? 1 : 0;
    command.gain = clampf(gain, 0.0f, 1.5f);
    command.pan = clampf(pan, -1.0f, 1.0f);
    if (params != nullptr) {
        for (int i = 0; i < paramCount && i < kParams; ++i) command.params[i] = params[i];
    }
    push(command);
}

void ClipEngine::updateParams(int pad, const float* params, int paramCount, float gain,
                              float pan, bool mute, bool solo, bool fxEnabled) {
    if (pad < 0 || pad >= kPads) return;
    Command command;
    command.type = kCmdParams;
    command.pad = static_cast<uint8_t>(pad);
    command.flags = static_cast<uint8_t>((mute ? 0x01 : 0) | (solo ? 0x02 : 0));
    command.fxEnabled = fxEnabled ? 1 : 0;
    command.gain = clampf(gain, 0.0f, 1.5f);
    command.pan = clampf(pan, -1.0f, 1.0f);
    if (params != nullptr) {
        for (int i = 0; i < paramCount && i < kParams; ++i) command.params[i] = params[i];
    }
    push(command);
}

void ClipEngine::setStep(int pad, int step, bool gate, int note, int velocity,
                         bool accent, bool slide) {
    if (pad < 0 || pad >= kPads || step < 0 || step >= kSteps) return;
    Command command;
    command.type = kCmdStep;
    command.pad = static_cast<uint8_t>(pad);
    command.step = static_cast<int16_t>(step);
    command.flags = static_cast<uint8_t>((gate ? 1 : 0) | (accent ? 2 : 0) | (slide ? 4 : 0));
    command.note = static_cast<int16_t>(clampi(note, 0, 127));
    command.velocity = static_cast<int16_t>(clampi(velocity, 1, 127));
    push(command);
}

void ClipEngine::setDrumStep(int pad, int voice, int step, bool on, int velocity) {
    if (pad < 0 || pad >= kPads || voice < 0 || voice >= kDrumVoices || step < 0 ||
        step >= kSteps) {
        return;
    }
    Command command;
    command.type = kCmdDrumStep;
    command.pad = static_cast<uint8_t>(pad);
    command.voice = static_cast<int16_t>(voice);
    command.step = static_cast<int16_t>(step);
    command.flags = on ? 1 : 0;
    command.velocity = static_cast<int16_t>(clampi(velocity, 1, 127));
    push(command);
}

void ClipEngine::clearClip(int pad) {
    if (pad < 0 || pad >= kPads) return;
    Command command;
    command.type = kCmdClear;
    command.pad = static_cast<uint8_t>(pad);
    push(command);
}

void ClipEngine::launchPad(int pad) {
    if (pad < 0 || pad >= kPads) return;
    Command command;
    command.type = kCmdLaunch;
    command.pad = static_cast<uint8_t>(pad);
    push(command);
}

void ClipEngine::stopPad(int pad) {
    if (pad < 0 || pad >= kPads) return;
    Command command;
    command.type = kCmdStop;
    command.pad = static_cast<uint8_t>(pad);
    push(command);
}

void ClipEngine::launchScene(int row) {
    Command command;
    command.type = kCmdLaunchScene;
    command.pad = static_cast<uint8_t>(clampi(row, 0, kRows - 1));
    push(command);
}

void ClipEngine::stopScene(int row) {
    Command command;
    command.type = kCmdStopScene;
    command.pad = static_cast<uint8_t>(clampi(row, 0, kRows - 1));
    push(command);
}

void ClipEngine::stopAll() {
    Command command;
    command.type = kCmdStopAll;
    push(command);
}

void ClipEngine::panic() {
    Command command;
    command.type = kCmdPanic;
    push(command);
}

void ClipEngine::loadSample(int pad, const float* interleaved, int frames, int channels,
                            int sampleRate) {
    if (pad < 0 || pad >= kPads || interleaved == nullptr || frames <= 0) return;
    const int ch = clampi(channels, 1, 2);
    const int sr = clampi(sampleRate, 8000, 192000);
    const int64_t maxFrames = static_cast<int64_t>(sr) * 300; // 5 minutes
    if (frames > maxFrames) return;

    auto data = std::make_shared<SampleData>();
    data->channels = ch;
    data->sampleRate = sr;
    data->stereo.assign(static_cast<size_t>(frames) * static_cast<size_t>(ch), 0.0f);
    std::memcpy(data->stereo.data(), interleaved,
                sizeof(float) * static_cast<size_t>(frames) * static_cast<size_t>(ch));

    {
        std::lock_guard<std::mutex> lock(sampleMutex_);
        sampleHold_.push_back(data);
    }
    samples_[static_cast<size_t>(pad)].store(data.get(), std::memory_order_release);
}

void ClipEngine::clearSample(int pad) {
    if (pad < 0 || pad >= kPads) return;
    samples_[static_cast<size_t>(pad)].store(nullptr, std::memory_order_release);
}

bool ClipEngine::startMasterRecording(const std::string& path) {
    return masterRec_.start(path, static_cast<int>(sampleRate_), 2, offlineMode_);
}

bool ClipEngine::startPadRecording(const std::string& path, int pad) {
    if (pad < 0 || pad >= kPads) return false;
    padRec_.stop();
    padRecIndex_ = pad;
    if (!padRec_.start(path, static_cast<int>(sampleRate_), 2, offlineMode_)) {
        padRecIndex_ = -1;
        return false;
    }
    return true;
}

void ClipEngine::stopRecording() {
    masterRec_.stop();
    padRec_.stop();
    padRecIndex_ = -1;
}

void ClipEngine::getPadStates(int* out, int count) const {
    if (out == nullptr) return;
    for (int i = 0; i < count && i < kPads; ++i) {
        out[i] = padState_[static_cast<size_t>(i)].load(std::memory_order_acquire);
    }
}

void ClipEngine::getPadMeters(float* out, int count) const {
    if (out == nullptr) return;
    for (int i = 0; i < count && i < kPads; ++i) {
        out[i] = padMeter_[static_cast<size_t>(i)].load(std::memory_order_relaxed);
    }
}

void ClipEngine::getScope(float* out, int count) const {
    if (out == nullptr) return;
    const uint32_t write = scopeWrite_.load(std::memory_order_relaxed);
    for (int i = 0; i < count && i < static_cast<int>(scope_.size()); ++i) {
        const uint32_t idx = (write + scope_.size() - static_cast<uint32_t>(count) + i) %
                             scope_.size();
        out[i] = scope_[idx];
    }
}

void ClipEngine::drainCommands() {
    Command command;
    while (commands_.pop(command)) {
        if (command.pad >= kPads) continue;
        Clip& clip = clips_[command.pad];
        const int pad = command.pad;

        switch (command.type) {
            case kCmdConfigure:
                applyConfigure(clip, pad, command);
                break;
            case kCmdParams:
                applyParams(clip, pad, command);
                break;
            case kCmdStep: {
                auto& step = clip.steps[clampi(command.step, 0, kSteps - 1)];
                step.gate = (command.flags & 1) != 0;
                step.accent = (command.flags & 2) != 0;
                step.slide = (command.flags & 4) != 0;
                step.note = static_cast<uint8_t>(command.note);
                step.velocity = static_cast<uint8_t>(command.velocity);
                break;
            }
            case kCmdDrumStep: {
                const int voice = clampi(command.voice, 0, kDrumVoices - 1);
                const int step = clampi(command.step, 0, kSteps - 1);
                const uint16_t bit = static_cast<uint16_t>(1u << step);
                if (command.flags & 1) {
                    clip.drumMask[voice] |= bit;
                    clip.drumVel[voice][step] = static_cast<uint8_t>(command.velocity);
                } else {
                    clip.drumMask[voice] &= static_cast<uint16_t>(~bit);
                }
                break;
            }
            case kCmdLaunch: {
                if (clip.kind == static_cast<int>(ClipKind::Empty)) break;
                if (clip.kind == static_cast<int>(ClipKind::Sample) &&
                    samples_[static_cast<size_t>(pad)].load(std::memory_order_acquire) ==
                        nullptr) {
                    break;
                }
                if (!transportRunning_) {
                    playing_.store(true, std::memory_order_release);
                    clip.pendingStart = 0;
                    clip.pendingStop = -1;
                    clip.state = static_cast<int>(ClipState::QueuedStart);
                } else if (quantize_.load(std::memory_order_relaxed) == 0) {
                    if (clip.state == static_cast<int>(ClipState::Playing)) {
                        stopClip(clip, false);
                    } else {
                        startClip(clip);
                        triggerStep(clip, 0);
                        clip.phase = (clip.length > 0) ? 1 % clip.length : 0;
                    }
                } else if (clip.state == static_cast<int>(ClipState::Playing) ||
                           clip.pendingStop >= 0) {
                    clip.pendingStop = nextBoundary(quantize_.load(std::memory_order_relaxed));
                    clip.pendingStart = -1;
                    clip.state = static_cast<int>(ClipState::QueuedStop);
                } else if (clip.pendingStart >= 0) {
                    clip.pendingStart = -1;
                    clip.pendingStop = -1;
                    clip.state = static_cast<int>(ClipState::Stopped);
                } else {
                    clip.pendingStart = nextBoundary(quantize_.load(std::memory_order_relaxed));
                    clip.pendingStop = -1;
                    clip.state = static_cast<int>(ClipState::QueuedStart);
                }
                updatePadState(pad);
                break;
            }
            case kCmdStop: {
                if (clip.state == static_cast<int>(ClipState::Stopped)) break;
                const int quant = quantize_.load(std::memory_order_relaxed);
                if (!transportRunning_ || quant == 0) {
                    stopClip(clip, false);
                } else {
                    clip.pendingStop = nextBoundary(quant);
                    clip.pendingStart = -1;
                    clip.state = static_cast<int>(ClipState::QueuedStop);
                }
                updatePadState(pad);
                break;
            }
            case kCmdLaunchScene:
            case kCmdStopScene: {
                const int row = clampi(command.pad, 0, kRows - 1);
                const bool launch = command.type == kCmdLaunchScene;
                if (launch && !transportRunning_) {
                    playing_.store(true, std::memory_order_release);
                }
                const int quant = quantize_.load(std::memory_order_relaxed);
                const bool immediate = launch ? (transportRunning_ && quant == 0)
                                              : (!transportRunning_ || quant == 0);
                const int64_t target = (transportRunning_ && !immediate) ? nextBoundary(quant) : 0;
                for (int p = 0; p < kPads; ++p) {
                    Clip& c = clips_[static_cast<size_t>(p)];
                    const bool inRow = (p / kCols) == row;
                    if (launch && inRow) {
                        if (c.kind == static_cast<int>(ClipKind::Empty)) continue;
                        if (c.kind == static_cast<int>(ClipKind::Sample) &&
                            samples_[static_cast<size_t>(p)].load(std::memory_order_acquire) ==
                                nullptr) {
                            continue;
                        }
                        if (immediate) {
                            startClip(c);
                            triggerStep(c, 0);
                            c.phase = (c.length > 0) ? 1 % c.length : 0;
                        } else {
                            c.pendingStart = target;
                            c.pendingStop = -1;
                            c.state = static_cast<int>(ClipState::QueuedStart);
                        }
                    } else if (launch || (inRow && !launch)) {
                        if (c.state == static_cast<int>(ClipState::Playing) ||
                            c.pendingStart >= 0 || c.pendingStop >= 0) {
                            if (immediate) {
                                stopClip(c, false);
                            } else {
                                c.pendingStop = target;
                                c.pendingStart = -1;
                                c.state = static_cast<int>(ClipState::QueuedStop);
                            }
                        }
                    }
                    updatePadState(p);
                }
                break;
            }
            case kCmdStopAll: {
                const int quant = quantize_.load(std::memory_order_relaxed);
                const bool immediate = !transportRunning_ || quant == 0;
                const int64_t target = transportRunning_ ? nextBoundary(quant) : 0;
                for (int p = 0; p < kPads; ++p) {
                    Clip& c = clips_[static_cast<size_t>(p)];
                    if (c.state == static_cast<int>(ClipState::Stopped)) continue;
                    if (immediate) {
                        stopClip(c, false);
                    } else {
                        c.pendingStop = target;
                        c.pendingStart = -1;
                        c.state = static_cast<int>(ClipState::QueuedStop);
                    }
                    updatePadState(p);
                }
                break;
            }
            case kCmdClear: {
                stopClip(clip, true);
                clip.kind = static_cast<int>(ClipKind::Empty);
                clip.fxEnabled = false;
                clip.fxType = 0;
                clip.sampleLoop = true;
                for (auto& s : clip.steps) s = ClipStep{};
                for (auto& mask : clip.drumMask) mask = 0;
                for (auto& row : clip.drumVel) {
                    for (auto& v : row) v = 0;
                }
                samples_[static_cast<size_t>(pad)].store(nullptr,
                                                          std::memory_order_release);
                updatePadState(pad);
                break;
            }
            case kCmdPanic: {
                stopTransport();
                break;
            }
            default:
                break;
        }
    }
}

void ClipEngine::applyConfigure(Clip& clip, int pad, const Command& command) {
    const int kind = clampi(command.kind, 0, 4);
    if (kind != clip.kind) {
        stopClip(clip, true);
        clip.kind = kind;
        clip.phase = 0;
        clip.pendingStart = clip.pendingStop = -1;
        clip.lastNote = -1;
        for (auto& step : clip.steps) step = ClipStep{};
        for (auto& mask : clip.drumMask) mask = 0;
        for (auto& row : clip.drumVel) {
            for (auto& v : row) v = 0;
        }

        const int root = clampi(command.root, 0, 127);
        if (kind == static_cast<int>(ClipKind::Acid)) {
            for (int i = 0; i < 6; ++i) {
                auto& step = clip.steps[kDefaultAcidSteps[i]];
                step.gate = true;
                step.note = static_cast<uint8_t>(clampi(root + kDefaultAcidNotes[i], 0, 127));
                step.velocity = (i == 0 || i == 3) ? 118 : 92;
                step.accent = (i == 0 || i == 3);
                step.slide = (i == 5);
            }
        } else if (kind == static_cast<int>(ClipKind::Wave)) {
            for (int i = 0; i < 4; ++i) {
                auto& step = clip.steps[kDefaultWaveSteps[i]];
                step.gate = true;
                step.note = static_cast<uint8_t>(clampi(root + kDefaultWaveNotes[i], 0, 127));
                step.velocity = 100;
            }
        } else if (kind == static_cast<int>(ClipKind::Drums)) {
            const int bd[4] = {0, 4, 8, 12};
            for (int s : bd) {
                clip.drumMask[0] |= static_cast<uint16_t>(1u << s);
                clip.drumVel[0][s] = 120;
            }
            clip.drumMask[1] |= static_cast<uint16_t>((1u << 4) | (1u << 12));
            clip.drumVel[1][4] = clip.drumVel[1][12] = 112;
            for (int s = 0; s < 16; s += 2) {
                clip.drumMask[7] |= static_cast<uint16_t>(1u << s);
                clip.drumVel[7][s] = (s % 4 == 0) ? 92 : 70;
            }
            clip.drumMask[8] |= static_cast<uint16_t>(1u << 14);
            clip.drumVel[8][14] = 84;
            clip.drumMask[6] |= static_cast<uint16_t>(1u << 11);
            clip.drumVel[6][11] = 96;
        }
    }

    clip.root = clampi(command.root, 0, 127);
    clip.length = clampi(command.length, 1, kSteps);
    clip.gain = clampf(command.gain, 0.0f, 1.5f);
    clip.pan = clampf(command.pan, -1.0f, 1.0f);
    clip.mute = (command.flags & 0x01) != 0;
    clip.solo = (command.flags & 0x02) != 0;
    clip.sampleLoop = (command.flags & 0x08) != 0;
    clip.fxType = clampi(command.fxType, 0, kFxCount - 1);
    clip.fxEnabled = command.fxEnabled != 0;
    for (int i = 0; i < kParams; ++i) clip.params[i] = command.params[i];

    applyVoiceParams(clip);
    Effect* fx = fx_[static_cast<size_t>(pad)].load(std::memory_order_acquire);
    if (fx != nullptr) {
        fx->setType(clip.fxType);
        fx->setParams(&clip.params[kFxParamBase], 6);
        fx->setEnabled(clip.fxEnabled && clip.fxType != kFxIdBypass);
    }
    updatePadState(pad);
}

void ClipEngine::applyParams(Clip& clip, int pad, const Command& command) {
    clip.gain = clampf(command.gain, 0.0f, 1.5f);
    clip.pan = clampf(command.pan, -1.0f, 1.0f);
    clip.mute = (command.flags & 0x01) != 0;
    clip.solo = (command.flags & 0x02) != 0;
    clip.fxEnabled = command.fxEnabled != 0;
    for (int i = 0; i < kParams; ++i) clip.params[i] = command.params[i];
    applyVoiceParams(clip);
    Effect* fx = fx_[static_cast<size_t>(pad)].load(std::memory_order_acquire);
    if (fx != nullptr) {
        fx->setParams(&clip.params[kFxParamBase], 6);
        fx->setEnabled(clip.fxEnabled && clip.fxType != kFxIdBypass);
    }
    updatePadState(pad);
}

void ClipEngine::applyVoiceParams(Clip& clip) {
    if (clip.kind == static_cast<int>(ClipKind::Acid)) {
        const float* p = clip.params;
        const float cutoff = p[1] < 20.0f ? 350.0f : p[1];
        const float decay = p[4] < 0.05f ? 0.7f : p[4];
        const float volume = p[10] <= 0.0f ? 0.8f : p[10];
        clip.acid->setWaveform(static_cast<int>(p[0]));
        clip.acid->setCutoff(cutoff);
        clip.acid->setResonance(clampf(p[2], 0.0f, 1.0f));
        clip.acid->setEnvMod(clampf(p[3], 0.0f, 1.0f));
        clip.acid->setDecay(decay);
        clip.acid->setAccent(clampf(p[5], 0.0f, 1.0f));
        clip.acid->setVolume(volume);
        clip.acid->setDistortion(p[8] > 0.001f, static_cast<int>(p[6]),
                                 clampf(p[7], 0.0f, 1.0f), clampf(p[9], 0.0f, 1.0f),
                                 clampf(p[8], 0.0f, 1.0f));
    } else if (clip.kind == static_cast<int>(ClipKind::Wave)) {
        WaveParams wp;
        const float* p = clip.params;
        wp.bank = static_cast<int>(p[0]);
        wp.position = clampf(p[1], 0.0f, 1.0f);
        wp.unison = p[2] < 1.0f ? 1 : static_cast<int>(p[2]);
        wp.detune = p[3];
        wp.spread = p[4];
        wp.cutoff = p[5] < 40.0f ? 2400.0f : p[5];
        wp.resonance = p[6] < 0.5f ? 0.5f : p[6];
        wp.drive = p[7] < 1.0f ? 1.0f : p[7];
        wp.attack = p[8] <= 0.0f ? 0.005f : p[8];
        wp.decay = p[9] <= 0.0f ? 0.35f : p[9];
        wp.sustain = p[10];
        wp.release = p[11] <= 0.0f ? 0.45f : p[11];
        wp.sub = p[12];
        wp.noise = p[13];
        wp.level = p[14] <= 0.0f ? 0.8f : p[14];
        wp.filterMode = static_cast<int>(p[15]);
        wp.chord = static_cast<int>(p[16]);
        clip.wave->setParams(wp);
    } else if (clip.kind == static_cast<int>(ClipKind::Drums)) {
        const float* p = clip.params;
        clip.drums->setParams(p[0], p[1], p[2], p[3], p[4],
                              p[5] <= 0.0f ? 0.85f : p[5],
                              p[6] <= 0.0f ? 18000.0f : p[6],
                              p[7] <= 0.0f ? 0.6f : p[7]);
    }
}

void ClipEngine::updatePadState(int pad) {
    if (pad < 0 || pad >= kPads) return;
    const Clip& clip = clips_[static_cast<size_t>(pad)];
    const int packed = (clip.kind & 0x0F) |
                       ((clip.state & 0x0F) << 4) |
                       ((clip.phase & 0xFF) << 8) |
                       ((clip.fxType & 0xFF) << 16) |
                       ((clip.mute ? 1 : 0) << 24) |
                       ((clip.solo ? 1 : 0) << 25);
    padState_[static_cast<size_t>(pad)].store(packed, std::memory_order_release);
}

int64_t ClipEngine::nextBoundary(int quantSteps) const {
    const int64_t base = absoluteStep_ + (samplesIntoStep_ > 1.0e-9 ? 1 : 0);
    if (quantSteps <= 0) return base;
    return ((base + quantSteps - 1) / quantSteps) * quantSteps;
}

double ClipEngine::stepDurationFor(int64_t step) const {
    const double sw = static_cast<double>(swing_.load(std::memory_order_relaxed));
    const double mult = (step % 2 == 0) ? (1.0 - sw * 0.5) : (1.0 + sw * 0.5);
    return samplesPerStep_ * std::max(0.25, mult);
}

void ClipEngine::startTransport() {
    transportRunning_ = true;
    absoluteStep_ = 0;
    samplesIntoStep_ = 0.0;
    stepDuration_ = stepDurationFor(0);
}

void ClipEngine::stopTransport() {
    transportRunning_ = false;
    playing_.store(false, std::memory_order_release);
    for (int i = 0; i < kPads; ++i) {
        Clip& clip = clips_[static_cast<size_t>(i)];
        stopClip(clip, true);
        updatePadState(i);
    }
    absoluteStep_ = 0;
    samplesIntoStep_ = 0.0;
}

void ClipEngine::onStepBoundary() {
    samplesIntoStep_ = 0.0;
    for (int pad = 0; pad < kPads; ++pad) {
        Clip& clip = clips_[static_cast<size_t>(pad)];
        if (clip.pendingStop >= 0 && clip.pendingStop == absoluteStep_) {
            stopClip(clip, false);
            updatePadState(pad);
        }
    }
    for (int pad = 0; pad < kPads; ++pad) {
        Clip& clip = clips_[static_cast<size_t>(pad)];
        if (clip.pendingStart >= 0 && clip.pendingStart == absoluteStep_) {
            startClip(clip);
            updatePadState(pad);
        }
    }
    for (int pad = 0; pad < kPads; ++pad) {
        Clip& clip = clips_[static_cast<size_t>(pad)];
        if (clip.state != static_cast<int>(ClipState::Playing)) continue;
        triggerStep(clip, clip.phase);
        clip.phase = (clip.length > 0) ? (clip.phase + 1) % clip.length : 0;
        updatePadState(pad);
    }

    stepDuration_ = stepDurationFor(absoluteStep_);
    samplesIntoStep_ = stepDuration_;
    absoluteStep_ += 1;
}

void ClipEngine::startClip(Clip& clip) {
    clip.state = static_cast<int>(ClipState::Playing);
    clip.pendingStart = -1;
    clip.pendingStop = -1;
    clip.phase = 0;
    clip.tailSamples = 0.0;
    clip.lastNote = -1;
    clip.samplePos = 0.0;
    clip.acid->allNotesOff();
    clip.wave->allNotesOff();
}

void ClipEngine::stopClip(Clip& clip, bool hard) {
    const bool wasPlaying = clip.state == static_cast<int>(ClipState::Playing);
    clip.state = static_cast<int>(ClipState::Stopped);
    clip.pendingStart = -1;
    clip.pendingStop = -1;
    clip.lastNote = -1;
    clip.acid->allNotesOff();
    clip.wave->allNotesOff();
    if (hard) {
        clip.tailSamples = 0.0;
        clip.samplePos = 0.0;
        clip.drums->reset();
    } else {
        clip.tailSamples = (clip.kind == static_cast<int>(ClipKind::Sample) || !wasPlaying)
                               ? 0.0
                               : sampleRate_ * 0.75;
    }
    if (clip.kind == static_cast<int>(ClipKind::Sample)) clip.samplePos = 0.0;
}

void ClipEngine::triggerStep(Clip& clip, int step) {
    const int s = clampi(step, 0, kSteps - 1);
    if (clip.kind == static_cast<int>(ClipKind::Acid)) {
        const ClipStep& cs = clip.steps[s];
        if (cs.gate) {
            if (!cs.slide) clip.acid->allNotesOff();
            clip.acid->noteOn(cs.note, cs.velocity);
            clip.lastNote = cs.note;
        } else {
            clip.acid->allNotesOff();
            clip.lastNote = -1;
        }
    } else if (clip.kind == static_cast<int>(ClipKind::Wave)) {
        const ClipStep& cs = clip.steps[s];
        clip.wave->allNotesOff();
        if (cs.gate) clip.wave->noteOn(cs.note, cs.velocity);
    } else if (clip.kind == static_cast<int>(ClipKind::Drums)) {
        for (int v = 0; v < kDrumVoices; ++v) {
            if ((clip.drumMask[v] & (1u << s)) == 0) continue;
            const int velocity = clip.drumVel[v][s];
            const float accent = velocity >= 100 ? 0.85f : 0.0f;
            clip.drums->trigger(v, static_cast<float>(velocity) / 127.0f, accent);
        }
    }
}

void ClipEngine::process(float* interleavedStereo, int frames) {
    if (interleavedStereo == nullptr || frames <= 0) return;
    if (!prepared_) prepare(48000.0, std::max(512, frames));
    if (frames > maxBlockFrames_) frames = maxBlockFrames_;

    std::fill(interleavedStereo, interleavedStereo + frames * 2, 0.0f);
    drainCommands();

    const bool wantPlaying = playing_.load(std::memory_order_acquire);
    if (wantPlaying && !transportRunning_) {
        startTransport();
    } else if (!wantPlaying && transportRunning_) {
        stopTransport();
    }

    samplesPerStep_ = sampleRate_ * 60.0 /
                      static_cast<double>(tempo_.load(std::memory_order_relaxed)) / 4.0;

    padPeakBlock_.fill(0.0f);
    if (padRec_.active()) {
        std::fill(padCapture_.begin(), padCapture_.end(), 0.0f);
    }

    int offset = 0;
    while (offset < frames) {
        int chunk;
        if (transportRunning_) {
            if (samplesIntoStep_ <= 1.0e-9) onStepBoundary();
            const double remaining = samplesIntoStep_;
            chunk = std::min(frames - offset,
                             std::max(1, static_cast<int>(std::ceil(remaining))));
        } else {
            chunk = frames - offset;
        }
        renderChunk(interleavedStereo + offset * 2, chunk,
                    padRec_.active() ? padCapture_.data() + offset * 2 : nullptr);
        if (transportRunning_) samplesIntoStep_ -= static_cast<double>(chunk);
        offset += chunk;
    }

    float masterPeak = 0.0f;
    for (int i = 0; i < frames; ++i) {
        masterPeak = std::max(masterPeak, std::fabs(interleavedStereo[i * 2]));
        masterPeak = std::max(masterPeak, std::fabs(interleavedStereo[i * 2 + 1]));
    }
    masterMeter_.store(masterPeak, std::memory_order_relaxed);
    for (int i = 0; i < kPads; ++i) {
        padMeter_[static_cast<size_t>(i)].store(padPeakBlock_[static_cast<size_t>(i)],
                                                std::memory_order_relaxed);
        const int packed = padState_[static_cast<size_t>(i)].load(std::memory_order_relaxed);
        if (((packed >> 4) & 0x0F) != clips_[static_cast<size_t>(i)].state) {
            updatePadState(i);
        }
    }

    for (int i = 0; i < frames; i += 32) {
        scope_[scopeWrite_.load(std::memory_order_relaxed) % scope_.size()] =
            interleavedStereo[i * 2];
        const uint32_t next = (scopeWrite_.load(std::memory_order_relaxed) + 1) %
                              static_cast<uint32_t>(scope_.size());
        scopeWrite_.store(next, std::memory_order_relaxed);
    }

    if (masterRec_.active()) masterRec_.push(interleavedStereo, frames);
    if (padRec_.active()) padRec_.push(padCapture_.data(), frames);
}

void ClipEngine::renderChunk(float* out, int frames, float* padOut) {
    const double masterGain = static_cast<double>(masterGain_.load(std::memory_order_relaxed));
    const float bpm = tempo_.load(std::memory_order_relaxed);
    bool anySolo = false;
    for (const auto& clip : clips_) {
        if (clip.solo) {
            anySolo = true;
            break;
        }
    }

    for (int i = 0; i < frames; ++i) {
        double mixL = 0.0;
        double mixR = 0.0;
        float capturedL = 0.0f;
        float capturedR = 0.0f;

        for (int pad = 0; pad < kPads; ++pad) {
            Clip& clip = clips_[static_cast<size_t>(pad)];
            if (clip.kind == static_cast<int>(ClipKind::Empty)) continue;
            const bool playing = clip.state == static_cast<int>(ClipState::Playing);
            if (!playing && clip.tailSamples <= 0.0) continue;

            float l = 0.0f;
            float r = 0.0f;
            if (clip.kind == static_cast<int>(ClipKind::Acid)) {
                const float s = clip.acid->process();
                l = s;
                r = s;
            } else if (clip.kind == static_cast<int>(ClipKind::Wave)) {
                clip.wave->render(l, r);
            } else if (clip.kind == static_cast<int>(ClipKind::Drums)) {
                clip.drums->render(l, r);
            } else if (clip.kind == static_cast<int>(ClipKind::Sample)) {
                SampleData* sample =
                    samples_[static_cast<size_t>(pad)].load(std::memory_order_acquire);
                if (sample == nullptr || sample->frames() < 2) {
                    clip.state = static_cast<int>(ClipState::Stopped);
                    continue;
                }
                const int sampleFrames = sample->frames();
                const double ratio = (static_cast<double>(sample->sampleRate) / sampleRate_) *
                                     std::pow(2.0, static_cast<double>(clip.params[1]) / 12.0);
                int idx = static_cast<int>(clip.samplePos);
                if (idx >= sampleFrames - 1) {
                    if (clip.sampleLoop) {
                        clip.samplePos = std::fmod(clip.samplePos,
                                                   static_cast<double>(sampleFrames - 1));
                        idx = static_cast<int>(clip.samplePos);
                    } else {
                        clip.state = static_cast<int>(ClipState::Stopped);
                        clip.samplePos = 0.0;
                        continue;
                    }
                }
                const float frac = static_cast<float>(clip.samplePos - idx);
                if (sample->channels == 2) {
                    const float l0 = sample->stereo[static_cast<size_t>(idx) * 2];
                    const float r0 = sample->stereo[static_cast<size_t>(idx) * 2 + 1];
                    const float l1 = sample->stereo[static_cast<size_t>(idx + 1) * 2];
                    const float r1 = sample->stereo[static_cast<size_t>(idx + 1) * 2 + 1];
                    l = l0 + (l1 - l0) * frac;
                    r = r0 + (r1 - r0) * frac;
                } else {
                    const float s0 = sample->stereo[static_cast<size_t>(idx)];
                    const float s1 = sample->stereo[static_cast<size_t>(idx + 1)];
                    l = r = s0 + (s1 - s0) * frac;
                }
                const float sampleGain = clip.params[0] <= 0.0f ? 1.0f : clip.params[0];
                l *= sampleGain;
                r *= sampleGain;
                clip.samplePos += ratio;
                if (clip.samplePos >= sampleFrames - 1) {
                    if (clip.sampleLoop) {
                        clip.samplePos = std::fmod(clip.samplePos,
                                                   static_cast<double>(sampleFrames - 1));
                    } else {
                        clip.state = static_cast<int>(ClipState::Stopped);
                        clip.samplePos = 0.0;
                    }
                }
            }

            Effect* fx = fx_[static_cast<size_t>(pad)].load(std::memory_order_acquire);
            if (fx != nullptr && clip.fxEnabled && clip.fxType != kFxIdBypass) {
                fx->process(l, r, bpm);
            }

            const float gain = clip.gain;
            l *= gain * balanceL(clip.pan);
            r *= gain * balanceR(clip.pan);

            if (pad == padRecIndex_) {
                capturedL = l;
                capturedR = r;
            }

            float tailGain = 1.0f;
            if (clip.tailSamples > 0.0) {
                const double fade = sampleRate_ * 0.25;
                tailGain = static_cast<float>(std::min(1.0, clip.tailSamples / fade));
                clip.tailSamples -= 1.0;
                if (clip.tailSamples <= 0.0) {
                    clip.state = static_cast<int>(ClipState::Stopped);
                    clip.tailSamples = 0.0;
                }
            }

            const float peak = std::max(std::fabs(l), std::fabs(r));
            float& stored = padPeakBlock_[static_cast<size_t>(pad)];
            if (peak > stored) stored = peak;

            if (!clip.mute && (!anySolo || clip.solo)) {
                mixL += static_cast<double>(l * tailGain);
                mixR += static_cast<double>(r * tailGain);
            }
        }

        mixL = std::tanh(mixL * masterGain);
        mixR = std::tanh(mixR * masterGain);
        out[i * 2] = sanitize(static_cast<float>(mixL));
        out[i * 2 + 1] = sanitize(static_cast<float>(mixR));
        if (padOut != nullptr) {
            padOut[i * 2] = capturedL;
            padOut[i * 2 + 1] = capturedR;
        }
    }
}

void ClipEngine::renderSilent(int frames) {
    if (frames <= 0) return;
    if (static_cast<int>(silentBuffer_.size()) < frames * 2) {
        silentBuffer_.assign(static_cast<size_t>(frames * 2), 0.0f);
        maxBlockFrames_ = std::max(maxBlockFrames_, frames);
    }
    process(silentBuffer_.data(), frames);
}

} // namespace rep
