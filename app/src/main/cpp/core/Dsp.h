#pragma once

// Shared, dependency-free DSP primitives for the ReplicantSynth engine.
// The same code builds for Android (NDK) and for the host test renderer.

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

namespace rep {

constexpr float kPi = 3.14159265358979323846f;
constexpr float kTwoPi = 6.28318530717958647692f;

inline float clampf(float v, float lo, float hi) noexcept { return std::clamp(v, lo, hi); }
inline int clampi(int v, int lo, int hi) noexcept { return std::clamp(v, lo, hi); }
inline float lerpf(float a, float b, float t) noexcept { return a + (b - a) * t; }
inline float midiToFreq(float note) noexcept {
    return 440.0f * std::pow(2.0f, (note - 69.0f) / 12.0f);
}
inline float freqToMidi(float hz) noexcept {
    return 69.0f + 12.0f * std::log2(std::max(1.0f, hz) / 440.0f);
}
inline float fastTanh(float x) noexcept {
    if (x < -3.0f) return -1.0f;
    if (x > 3.0f) return 1.0f;
    const float x2 = x * x;
    return x * (27.0f + x2) / (27.0f + 9.0f * x2);
}
inline float softClip(float x) noexcept {
    if (x <= -1.5f) return -1.0f;
    if (x >= 1.5f) return 1.0f;
    return x - (4.0f / 27.0f) * (x * x * x);
}
inline float sanitize(float x) noexcept {
    if (!std::isfinite(x)) return 0.0f;
    return std::fabs(x) < 1.0e-20f ? 0.0f : x;
}
inline float fractf(float x) noexcept { return x - std::floor(x); }
inline float wrap01(float x) noexcept {
    x = std::fmod(x, 1.0f);
    return x < 0.0f ? x + 1.0f : x;
}

// Xorshift32: allocation-free and deterministic.
class Rng {
public:
    explicit Rng(uint32_t seed = 0x9E3779B9u) : state_(seed ? seed : 0x9E3779B9u) {}
    void seed(uint32_t s) noexcept { state_ = s ? s : 0x9E3779B9u; }
    uint32_t nextU32() noexcept {
        uint32_t x = state_;
        x ^= x << 13;
        x ^= x >> 17;
        x ^= x << 5;
        state_ = x;
        return x;
    }
    // -1 .. +1
    float noise() noexcept {
        return static_cast<float>(static_cast<int32_t>(nextU32())) * (1.0f / 2147483648.0f);
    }
    float uniform() noexcept {
        return static_cast<float>(nextU32()) * (1.0f / 4294967296.0f);
    }
private:
    uint32_t state_;
};

// One-pole low pass / parameter smoother. `coeff` is the per-sample step.
class OnePole {
public:
    void reset(float value = 0.0f) noexcept { z_ = value; }
    float process(float x, float coeff) noexcept {
        z_ += (x - z_) * clampf(coeff, 0.0f, 1.0f);
        return z_;
    }
    float value() const noexcept { return z_; }
private:
    float z_ = 0.0f;
};

// Andrew Simper's zero-delay-feedback state variable filter.
class ZdfSvf {
public:
    void reset() noexcept { ic1_ = ic2_ = 0.0f; }
    // mode: 0 LP, 1 BP, 2 HP, 3 notch. drive >= 1 adds pre-filter saturation.
    float process(float input, float cutoffHz, float q, int mode, float drive = 1.0f) noexcept {
        const float fc = clampf(cutoffHz, 20.0f, 20000.0f);
        const float g = std::tan(kPi * std::min(fc, 0.45f * sampleRate_) / sampleRate_);
        const float k = 1.0f / std::max(0.35f, q);
        const float driveGain = std::max(0.1f, drive);
        const float x = fastTanh(input * driveGain) / std::sqrt(driveGain);

        const float a1 = 1.0f / (1.0f + g * (g + k));
        const float a2 = g * a1;
        const float a3 = g * a2;
        const float v3 = x - ic2_;
        const float v1 = a1 * ic1_ + a2 * v3;
        const float v2 = ic2_ + a2 * ic1_ + a3 * v3;
        ic1_ = 2.0f * v1 - ic1_;
        ic2_ = 2.0f * v2 - ic2_;
        switch (mode) {
            case 1: return v1;
            case 2: return x - k * v1 - v2;
            case 3: return x - k * v1;
            default: return v2;
        }
    }
    void prepare(float sampleRate) noexcept { sampleRate_ = std::max(8000.0f, sampleRate); }
private:
    float sampleRate_ = 48000.0f;
    float ic1_ = 0.0f;
    float ic2_ = 0.0f;
};

// Simple stereo delay line with linear interpolation.
class DelayLine {
public:
    void prepare(int maxSamples) {
        buf_.assign(static_cast<size_t>(std::max(8, maxSamples)), 0.0f);
        write_ = 0;
    }
    void reset() { std::fill(buf_.begin(), buf_.end(), 0.0f); write_ = 0; }
    void write(float x) noexcept {
        if (buf_.empty()) return;
        buf_[static_cast<size_t>(write_)] = sanitize(x);
        write_ = (write_ + 1) % static_cast<int>(buf_.size());
    }
    // Delay in samples, measured back from the current write position.
    float read(float delaySamples) const noexcept {
        if (buf_.empty()) return 0.0f;
        const int size = static_cast<int>(buf_.size());
        const float d = clampf(delaySamples, 1.0f, static_cast<float>(size - 4));
        const int di = static_cast<int>(d);
        const float frac = d - static_cast<float>(di);
        const int i0 = (write_ - di + size * 2) % size;
        const int i1 = (i0 - 1 + size) % size;
        return buf_[static_cast<size_t>(i0)] * (1.0f - frac) +
               buf_[static_cast<size_t>(i1)] * frac;
    }
    int size() const noexcept { return static_cast<int>(buf_.size()); }
private:
    std::vector<float> buf_;
    int write_ = 0;
};

// ADSR with a linear attack and exponential decay/release. Cheap and stable.
class Adsr {
public:
    enum class Stage { Idle, Attack, Decay, Sustain, Release };
    void prepare(float sampleRate) noexcept {
        sampleRate_ = std::max(1000.0f, sampleRate);
        update();
    }
    void setAttack(float s) noexcept { attack_ = clampf(s, 0.001f, 8.0f); update(); }
    void setDecay(float s) noexcept { decay_ = clampf(s, 0.001f, 8.0f); update(); }
    void setSustain(float v) noexcept { sustain_ = clampf(v, 0.0f, 1.0f); }
    void setRelease(float s) noexcept { release_ = clampf(s, 0.001f, 8.0f); update(); }
    void reset() noexcept { stage_ = Stage::Idle; level_ = 0.0f; }
    void noteOn() noexcept { stage_ = Stage::Attack; }
    void noteOff() noexcept { if (stage_ != Stage::Idle) stage_ = Stage::Release; }
    bool active() const noexcept { return stage_ != Stage::Idle; }
    float process() noexcept {
        switch (stage_) {
            case Stage::Attack:
                level_ += attackStep_;
                if (level_ >= 1.0f) { level_ = 1.0f; stage_ = Stage::Decay; }
                break;
            case Stage::Decay:
                level_ += (sustain_ - level_) * decayCoeff_;
                if (level_ - sustain_ < 0.0005f) { level_ = sustain_; stage_ = Stage::Sustain; }
                break;
            case Stage::Sustain:
                level_ = sustain_;
                break;
            case Stage::Release:
                level_ += (0.0f - level_) * releaseCoeff_;
                if (level_ < 0.0002f) { level_ = 0.0f; stage_ = Stage::Idle; }
                break;
            default:
                level_ = 0.0f;
                break;
        }
        return level_;
    }
    float level() const noexcept { return level_; }
private:
    void update() noexcept {
        attackStep_ = 1.0f / (attack_ * sampleRate_);
        decayCoeff_ = 1.0f - std::exp(-1.0f / (decay_ * sampleRate_ * 0.35f));
        releaseCoeff_ = 1.0f - std::exp(-1.0f / (release_ * sampleRate_ * 0.35f));
    }
    float sampleRate_ = 48000.0f;
    float attack_ = 0.005f, decay_ = 0.25f, sustain_ = 0.7f, release_ = 0.3f;
    float attackStep_ = 0.001f, decayCoeff_ = 0.001f, releaseCoeff_ = 0.001f;
    float level_ = 0.0f;
    Stage stage_ = Stage::Idle;
};

// Parameter smoothing for UI knobs: 5 ms one-pole.
struct SmoothedValue {
    float current = 0.0f;
    float target = 0.0f;
    float coeff = 0.004f;
    void setSampleRate(float sr) noexcept { coeff = 1.0f - std::exp(-1.0f / (0.005f * sr)); }
    void set(float v) noexcept { target = v; }
    void snap(float v) noexcept { target = current = v; }
    float next() noexcept {
        current += (target - current) * coeff;
        return current;
    }
};

} // namespace rep
