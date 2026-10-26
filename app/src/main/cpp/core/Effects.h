#pragma once

// ReplicantFX: the 48-slot SP-404MKII MFX engine ported from the Rust plug-in.
// Each clip owns one insert effect; the parameter layout matches the hardware
// CTRL 1..3 / SUB 4..6 table used by the original.

#include "Dsp.h"

#include <array>
#include <vector>

namespace rep {

constexpr int kFxCount = 48;

struct FxInfo {
    const char* name;
    const char* code;
    const char* labels[6];
    float defaults[6];
};

const FxInfo& fxInfo(int id);

class Effect {
public:
    Effect();
    void prepare(float sampleRate);
    void reset();
    void setType(int id) noexcept;
    int type() const noexcept { return fxId_; }
    void setEnabled(bool on) noexcept { enabled_ = on; }
    bool enabled() const noexcept { return enabled_; }
    void setParam(int index, float value) noexcept;
    void setParams(const float* values, int count) noexcept;
    float param(int index) const noexcept;
    void process(float& outL, float& outR, float bpm) noexcept;

    static constexpr int kMaxDelay = 72000; // 1.5 s at 48 kHz
    static constexpr int kCombVoices = 6;
    static constexpr int kCombLen = 2048;

private:
    float svfL(int stage, float in, float cutoff, float k, int mode) noexcept;
    float svfR(int stage, float in, float cutoff, float k, int mode) noexcept;
    float noise() noexcept { return rng_.noise(); }

    int fxId_ = 0;
    bool enabled_ = true;
    float sampleRate_ = 48000.0f;
    float params_[6] = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
    float smoothed_[6] = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};

    ZdfSvf svfL_[3];
    ZdfSvf svfR_[3];
    float lowL_ = 0.0f, lowR_ = 0.0f;
    float highL_ = 0.0f, highR_ = 0.0f;
    float postL_ = 0.0f, postR_ = 0.0f;

    DelayLine delayL_;
    DelayLine delayR_;
    float looperPlayhead_ = 0.0f;
    bool looperActive_ = false;

    std::array<float, kCombVoices * kCombLen> comb_{};
    std::array<int, kCombVoices> combPos_{};
    std::array<float, kCombVoices> combLp_{};

    float allpassL_[12] = {};
    float allpassR_[12] = {};
    float phaserFbL_ = 0.0f, phaserFbR_ = 0.0f;
    float revCombL_[4] = {};
    float revCombR_[4] = {};

    float lfoPhase_ = 0.0f, lfoPhase2_ = 0.0f;
    float envFollower_ = 0.0f, compEnv_ = 0.0f;
    float srhCounter_ = 0.0f, srhL_ = 0.0f, srhR_ = 0.0f;
    float subSignL_ = 1.0f, subPrevL_ = 0.0f, subLp_ = 0.0f;
    float stopperSpeed_ = 1.0f, stopperPhase_ = 0.0f;
    Rng rng_{0x4040303u};
};

} // namespace rep
