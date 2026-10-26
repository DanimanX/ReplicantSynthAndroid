#include "Effects.h"

#include <algorithm>
#include <cmath>

namespace rep {

namespace {

const FxInfo kFxTable[kFxCount] = {
#include "FxCatalog.inc"
};

constexpr float kChordIntervals[9][6] = {
    {0.0f, 0.0f, 12.0f, 12.0f, 24.0f, 24.0f},   // Root
    {0.0f, 7.0f, 12.0f, 19.0f, 24.0f, 31.0f},   // P5
    {0.0f, -12.0f, 12.0f, 24.0f, -24.0f, 0.0f}, // Oct
    {0.0f, -12.0f, 7.0f, 12.0f, 19.0f, 24.0f},  // UpDn
    {0.0f, 3.0f, 7.0f, 12.0f, 15.0f, 19.0f},    // m3
    {0.0f, 3.0f, 7.0f, 10.0f, 12.0f, 15.0f},    // m7
    {0.0f, 4.0f, 7.0f, 12.0f, 16.0f, 19.0f},    // M3
    {0.0f, 4.0f, 7.0f, 11.0f, 12.0f, 16.0f},    // M7
    {0.0f, 4.0f, 7.0f, 11.0f, 14.0f, 19.0f},    // M9
};

inline float bandGain(float v, float kill) {
    if (kill >= 0.5f || v < 0.02f) return 0.0f;
    if (v <= 0.5f) {
        const float x = v * 2.0f;
        return x * x;
    }
    return 1.0f + (v - 0.5f) * 6.0f;
}

inline float wrap(float x, float limit) {
    if (limit <= 0.0f) return 0.0f;
    float v = std::fmod(x, limit);
    if (v < 0.0f) v += limit;
    return v;
}

} // namespace

const FxInfo& fxInfo(int id) {
    return kFxTable[clampi(id, 0, kFxCount - 1)];
}

Effect::Effect() {
    prepare(48000.0f);
}

void Effect::prepare(float sampleRate) {
    sampleRate_ = std::max(8000.0f, sampleRate);
    for (int i = 0; i < 3; ++i) {
        svfL_[i].prepare(sampleRate_);
        svfR_[i].prepare(sampleRate_);
    }
    delayL_.prepare(kMaxDelay);
    delayR_.prepare(kMaxDelay);
    reset();
}

void Effect::reset() {
    for (int i = 0; i < 3; ++i) {
        svfL_[i].reset();
        svfR_[i].reset();
    }
    lowL_ = lowR_ = highL_ = highR_ = postL_ = postR_ = 0.0f;
    delayL_.reset();
    delayR_.reset();
    looperPlayhead_ = 0.0f;
    looperActive_ = false;
    comb_.fill(0.0f);
    combPos_.fill(0);
    combLp_.fill(0.0f);
    for (int i = 0; i < 12; ++i) allpassL_[i] = allpassR_[i] = 0.0f;
    phaserFbL_ = phaserFbR_ = 0.0f;
    for (int i = 0; i < 4; ++i) revCombL_[i] = revCombR_[i] = 0.0f;
    lfoPhase_ = lfoPhase2_ = 0.0f;
    envFollower_ = compEnv_ = 0.0f;
    srhCounter_ = 0.0f;
    srhL_ = srhR_ = 0.0f;
    subSignL_ = 1.0f;
    subPrevL_ = subLp_ = 0.0f;
    stopperSpeed_ = 1.0f;
    stopperPhase_ = 0.0f;
}

void Effect::setType(int id) noexcept {
    const int next = clampi(id, 0, kFxCount - 1);
    if (next == fxId_) return;
    fxId_ = next;
    looperActive_ = false;
    stopperSpeed_ = 1.0f;
    stopperPhase_ = 0.0f;
    for (int i = 0; i < 6; ++i) smoothed_[i] = params_[i];
}

void Effect::setParam(int index, float value) noexcept {
    if (index < 0 || index >= 6) return;
    params_[index] = clampf(value, 0.0f, 1.0f);
}

void Effect::setParams(const float* values, int count) noexcept {
    if (values == nullptr) return;
    for (int i = 0; i < count && i < 6; ++i) setParam(i, values[i]);
}

float Effect::param(int index) const noexcept {
    return (index >= 0 && index < 6) ? params_[index] : 0.0f;
}

float Effect::svfL(int stage, float in, float cutoff, float k, int mode) noexcept {
    return svfL_[clampi(stage, 0, 2)].process(in, cutoff, 1.0f / std::max(0.2f, k), mode, 1.0f);
}

float Effect::svfR(int stage, float in, float cutoff, float k, int mode) noexcept {
    return svfR_[clampi(stage, 0, 2)].process(in, cutoff, 1.0f / std::max(0.2f, k), mode, 1.0f);
}

void Effect::process(float& outL, float& outR, float bpm) noexcept {
    if (!enabled_) return;

    for (int i = 0; i < 6; ++i) {
        smoothed_[i] += (params_[i] - smoothed_[i]) * 0.003f;
    }
    const float* p = smoothed_;
    const float sr = sampleRate_;
    const float safeBpm = clampf(bpm, 40.0f, 240.0f);
    const float inL = outL;
    const float inR = outR;
    float l = inL;
    float r = inR;
    float writeL = inL;
    float writeR = inR;

    const float absIn = 0.5f * (std::fabs(inL) + std::fabs(inR));
    const float envCoeff = absIn > envFollower_ ? 0.015f : 0.0008f;
    envFollower_ += (absIn - envFollower_) * envCoeff;

    switch (fxId_) {
        case 0: { // Bypass / trim
            const float gain = 0.25f + p[0] * 1.5f;
            const float pan = p[1];
            const float phase = p[3] >= 0.5f ? -1.0f : 1.0f;
            l = inL * gain * phase * std::min(1.0f, 1.0f - pan * 0.5f);
            r = inR * gain * phase * std::min(1.0f, 0.5f + pan * 0.5f);
            break;
        }
        case 1: { // Filter+Drive
            const float cutoff = 25.0f * std::pow(800.0f, p[0]);
            const float reso = clampf(p[1], 0.0f, 0.96f);
            const float drive = 1.0f + p[2] * 9.0f;
            const int mode = static_cast<int>(p[3] * 2.99f);
            const float lowBoost = (p[5] - 0.5f) * 3.0f;
            const float k = 2.0f - 1.92f * reso;

            const float fltL = svfL(0, inL, cutoff, k, mode);
            const float fltR = svfR(0, inR, cutoff, k, mode);
            const float lowG = std::min(0.4f, kPi * (40.0f + p[4] * 250.0f) / sr);
            lowL_ += lowG * (fltL - lowL_);
            lowR_ += lowG * (fltR - lowR_);

            const float preL = (fltL + lowL_ * lowBoost) * drive;
            const float preR = (fltR + lowR_ * lowBoost) * drive;
            l = std::tanh(preL + 0.12f * preL * preL) / std::sqrt(drive);
            r = std::tanh(preR + 0.12f * preR * preR) / std::sqrt(drive);
            break;
        }
        case 3: { // Isolator
            const float gLow = std::min(0.45f, kPi * 240.0f / sr);
            const float gHigh = std::min(0.85f, kPi * 2800.0f / sr);
            lowL_ += gLow * (inL - lowL_);
            lowR_ += gLow * (inR - lowR_);
            highL_ += gHigh * (inL - highL_);
            highR_ += gHigh * (inR - highR_);
            const float midL = highL_ - lowL_;
            const float midR = highR_ - lowR_;
            const float topL = inL - highL_;
            const float topR = inR - highR_;
            const float gl = bandGain(p[0], p[3]);
            const float gm = bandGain(p[1], p[4]);
            const float gh = bandGain(p[2], p[5]);
            l = std::tanh(lowL_ * gl + midL * gm + topL * gh);
            r = std::tanh(lowR_ * gl + midR * gm + topR * gh);
            break;
        }
        case 4: { // Crusher
            const float cutoff = 80.0f * std::pow(200.0f, p[0]);
            const float decim = 1.0f + p[1] * p[1] * 48.0f;
            const float wet = p[2];
            const float bits = 16.0f - p[3] * 13.0f;
            const float steps = std::pow(2.0f, bits);
            const float drive = 1.0f + p[5] * 4.0f;

            srhCounter_ += 1.0f;
            if (srhCounter_ >= decim) {
                srhCounter_ -= decim;
                srhL_ = std::tanh(std::round(inL * drive * steps) / steps);
                srhR_ = std::tanh(std::round(inR * drive * steps) / steps);
            }
            const float k = 2.0f - 1.8f * clampf(p[4], 0.0f, 0.9f);
            const float fltL = svfL(0, srhL_, cutoff, k, 0);
            const float fltR = svfR(0, srhR_, cutoff, k, 0);
            l = inL * (1.0f - wet) + fltL * wet;
            r = inR * (1.0f - wet) + fltR * wet;
            break;
        }
        case 5: { // Phaser
            const float rate = 0.08f + p[0] * p[0] * 8.0f;
            lfoPhase_ = wrap(lfoPhase_ + rate / sr, 1.0f);
            const float lfoL = std::sin(lfoPhase_ * kTwoPi);
            const float lfoR = std::sin((lfoPhase_ + 0.25f) * kTwoPi);
            const int stages = (p[4] < 0.25f) ? 4 : (p[4] < 0.5f ? 8 : 12);
            const float baseF = 120.0f * std::pow(25.0f, p[3]);
            const float fL = clampf(baseF * std::pow(2.0f, lfoL * p[1] * 2.2f), 60.0f, sr * 0.42f);
            const float fR = clampf(baseF * std::pow(2.0f, lfoR * p[1] * 2.2f), 60.0f, sr * 0.42f);
            const float gL = (std::tan(kPi * fL / sr) - 1.0f) / (std::tan(kPi * fL / sr) + 1.0f);
            const float gR = (std::tan(kPi * fR / sr) - 1.0f) / (std::tan(kPi * fR / sr) + 1.0f);
            const float fb = p[2] * 0.82f;
            float xl = std::tanh(inL + phaserFbL_ * fb);
            float xr = std::tanh(inR + phaserFbR_ * fb);
            for (int s = 0; s < stages; ++s) {
                const float yl = gL * xl + allpassL_[s];
                allpassL_[s] = xl - gL * yl;
                xl = yl;
                const float yr = gR * xr + allpassR_[s];
                allpassR_[s] = xr - gR * yr;
                xr = yr;
            }
            phaserFbL_ = xl;
            phaserFbR_ = xr;
            const float wet = p[5];
            l = inL * (1.0f - wet * 0.5f) + xl * wet * 0.7f;
            r = inR * (1.0f - wet * 0.5f) + xr * wet * 0.7f;
            break;
        }
        case 6:
        case 14:
        case 15: { // Flanger / Chorus / JUNO Chorus
            float rate, depthMs, baseMs, fb, noiseAmt, wet;
            if (fxId_ == 15) {
                const int mode = static_cast<int>(p[0] * 4.99f);
                const float modeRate = (mode == 0) ? 0.52f : (mode == 1 ? 0.86f
                                          : mode == 2 ? 0.68f : (mode == 3 ? 1.35f : 2.10f));
                rate = modeRate * (0.5f + p[3]);
                depthMs = 1.8f + p[4] * 3.2f;
                baseMs = 3.4f;
                fb = 0.15f;
                noiseAmt = p[1] * 0.008f;
                wet = p[2];
            } else if (fxId_ == 14) {
                rate = 0.15f + p[0] * 4.0f;
                depthMs = 1.5f + p[1] * 4.5f;
                baseMs = 7.0f;
                fb = 0.1f;
                noiseAmt = 0.0f;
                wet = p[2];
            } else {
                rate = 0.08f + p[0] * 6.0f;
                depthMs = 0.4f + p[1] * 3.5f;
                baseMs = 0.8f + p[3] * 4.0f;
                fb = p[2] * 0.84f;
                noiseAmt = 0.0f;
                wet = p[5];
            }
            lfoPhase_ = wrap(lfoPhase_ + rate / sr, 1.0f);
            const float lfoL = std::sin(lfoPhase_ * kTwoPi);
            const float lfoR = -lfoL;
            const float dL = (baseMs + (0.5f + 0.5f * lfoL) * depthMs) * 0.001f * sr;
            const float dR = (baseMs + (0.5f + 0.5f * lfoR) * depthMs) * 0.001f * sr;
            const float tapL = delayL_.read(dL);
            const float tapR = delayR_.read(dR);
            writeL = inL + tapL * fb;
            writeR = inR + tapR * fb;
            const float bbd = noiseAmt > 0.0001f ? noise() * noiseAmt : 0.0f;
            l = inL * (1.0f - wet * 0.45f) + (tapL + bbd) * wet;
            r = inR * (1.0f - wet * 0.45f) + (tapR - bbd) * wet;
            break;
        }
        case 7:
        case 8:
        case 11:
        case 23: { // Overdrive / Distortion / Saturator / Amp sim
            float drive, tone, wet, level;
            if (fxId_ == 7) {
                drive = 1.0f + p[0] * 14.0f; tone = p[1]; wet = p[2]; level = 0.4f + p[3] * 1.1f;
            } else if (fxId_ == 8) {
                drive = 1.5f + p[0] * 28.0f; tone = p[1]; wet = p[2]; level = 0.35f + p[3] * 1.1f;
            } else if (fxId_ == 11) {
                drive = 1.0f + p[0] * 7.5f; tone = p[2]; wet = p[5]; level = 0.85f;
            } else {
                drive = 1.2f + p[1] * 18.0f; tone = p[5]; wet = 1.0f; level = 0.4f + p[2] * 1.1f;
            }
            const float xl = inL * drive;
            const float xr = inR * drive;
            float satL, satR;
            if (fxId_ == 8) {
                satL = std::tanh(clampf(xl, -1.4f, 1.4f) + 0.25f * std::sin(xl * 1.8f));
                satR = std::tanh(clampf(xr, -1.4f, 1.4f) + 0.25f * std::sin(xr * 1.8f));
            } else {
                satL = std::tanh(xl + 0.18f * xl * xl);
                satR = std::tanh(xr + 0.18f * xr * xr);
            }
            const float cutoff = 250.0f * std::pow(32.0f, tone);
            const float g = std::min(0.85f, kPi * cutoff / sr);
            postL_ += g * (satL - postL_);
            postR_ += g * (satR - postR_);
            const float outL = (postL_ * 0.75f + satL * 0.25f) * level / std::pow(drive, 0.32f);
            const float outR = (postR_ * 0.75f + satR * 0.25f) * level / std::pow(drive, 0.32f);
            l = inL * (1.0f - wet) + outL * wet;
            r = inR * (1.0f - wet) + outR * wet;
            break;
        }
        case 2:
        case 9:
        case 18:
        case 33:
        case 36:
        case 41:
        case 47: { // Delays
            const float beatSec = 60.0f / safeBpm;
            const float divMult = 0.0625f * std::pow(32.0f, p[0]);
            const float baseDelay = clampf(beatSec * divMult, 0.012f, 1.85f);
            float wfDepth;
            if (fxId_ == 9) wfDepth = p[5] * 0.0025f;
            else if (fxId_ == 47) wfDepth = 0.004f;
            else wfDepth = p[5] * 0.0008f;
            lfoPhase_ = wrap(lfoPhase_ + 2.4f / sr, 1.0f);
            const float mod = std::sin(lfoPhase_ * kTwoPi) * wfDepth;
            const float dSamples = clampf((baseDelay + mod) * sr, 16.0f, kMaxDelay - 8.0f);
            const float dSamplesR = (fxId_ == 33 || fxId_ == 18)
                                        ? clampf(dSamples * 0.75f, 16.0f, kMaxDelay - 8.0f)
                                        : dSamples;
            const float tapL = delayL_.read(dSamples);
            const float tapR = delayR_.read(dSamplesR);
            const float hc = (fxId_ == 9) ? 0.48f : clampf(p[3], 0.15f, 0.95f);
            const float gLp = std::min(0.85f, kPi * (400.0f * std::pow(25.0f, hc)) / sr);
            postL_ += gLp * (tapL - postL_);
            postR_ += gLp * (tapR - postR_);
            const float fb = clampf(p[1], 0.0f, 0.92f);
            writeL = std::tanh(inL + postR_ * fb);
            writeR = std::tanh(inR + postL_ * fb);
            const float wet = clampf(p[2], 0.0f, 1.0f);
            l = inL + postL_ * wet;
            r = inR + postR_ * wet;
            break;
        }
        case 10:
        case 37:
        case 44: { // Reverb
            const float decay = 0.70f + p[0] * 0.275f;
            const float wet = clampf(p[1], 0.0f, 1.0f);
            const float damp = 0.25f + (1.0f - clampf(p[5], 0.1f, 0.95f)) * 0.60f;
            static constexpr float dLens[4] = {1553.0f, 1913.0f, 2297.0f, 2689.0f};
            float sumL = 0.0f;
            float sumR = 0.0f;
            const float scale = 0.6f + p[3] * 1.1f;
            for (int i = 0; i < 4; ++i) {
                const float tapL = delayL_.read(dLens[i] * scale);
                const float tapR = delayR_.read((dLens[i] + 173.0f) * scale);
                revCombL_[i] = tapL * (1.0f - damp) + revCombL_[i] * damp;
                revCombR_[i] = tapR * (1.0f - damp) + revCombR_[i] * damp;
                sumL += revCombL_[i];
                sumR += revCombR_[i];
            }
            sumL *= 0.25f;
            sumR *= 0.25f;
            writeL = std::tanh(inL + sumR * decay);
            writeR = std::tanh(inR + sumL * decay);
            l = inL * (1.0f - wet * 0.35f) + sumL * wet;
            r = inR * (1.0f - wet * 0.35f) + sumR * wet;
            break;
        }
        case 12:
        case 35: { // Resonator / Hyper-Reso
            const int rootSemi = static_cast<int>(p[0] * 35.99f);
            const float baseMidi = 36.0f + static_cast<float>(rootSemi) + envFollower_ * p[4] * 12.0f;
            const int chordIdx = clampi(static_cast<int>(p[3] * 8.99f), 0, 8);
            const float bright = 0.20f + p[1] * 0.75f;
            const float fb = 0.65f + p[2] * 0.325f;
            float resL = 0.0f;
            float resR = 0.0f;
            const float monoIn = 0.5f * (inL + inR);
            for (int v = 0; v < kCombVoices; ++v) {
                const float note = baseMidi + kChordIntervals[chordIdx][v];
                const float freq = clampf(440.0f * std::pow(2.0f, (note - 69.0f) / 12.0f),
                                          35.0f, 4000.0f);
                int period = static_cast<int>(std::round(sr / freq));
                period = clampi(period, 8, kCombLen - 2);
                const int base = v * kCombLen;
                const int pos = combPos_[v] % period;
                const float delayed = comb_[static_cast<size_t>(base + pos)];
                combLp_[v] += bright * (delayed - combLp_[v]);
                const float next = std::tanh(monoIn * 0.25f + combLp_[v] * fb);
                comb_[static_cast<size_t>(base + pos)] = next;
                combPos_[v] = (pos + 1) % period;
                if (v % 2 == 0) resL += delayed;
                else resR += delayed;
            }
            const float spread = p[5];
            const float wetL = resL * (0.5f + 0.5f * spread) + resR * (0.5f - 0.5f * spread);
            const float wetR = resR * (0.5f + 0.5f * spread) + resL * (0.5f - 0.5f * spread);
            l = inL * 0.6f + wetL * 0.45f;
            r = inR * 0.6f + wetR * 0.45f;
            break;
        }
        case 13:
        case 24:
        case 30:
        case 46: { // DJFX Looper / Stopper / Scatter / Back Spin
            writeL = inL;
            writeR = inR;
            const float beatSamples = (60.0f / safeBpm) * sr;
            const float loopDiv = 0.03125f * std::pow(16.0f, 1.0f - p[0]);
            const float loopLen = clampf(beatSamples * loopDiv, 128.0f, kMaxDelay / 2.0f);
            bool active;
            if (fxId_ == 13) active = p[2] >= 0.5f || p[0] > 0.05f;
            else if (fxId_ == 24 || fxId_ == 46) active = p[1] >= 0.5f || p[2] > 0.5f;
            else active = true;

            if (active) {
                if (!looperActive_) {
                    looperActive_ = true;
                    looperPlayhead_ = 0.0f;
                }
                float speed;
                if (fxId_ == 24) {
                    stopperSpeed_ = std::max(0.02f, stopperSpeed_ - 0.00015f * (0.2f + p[0]));
                    speed = stopperSpeed_;
                } else if (fxId_ == 46 || p[3] >= 0.5f) {
                    speed = -(0.5f + p[1] * 1.5f);
                } else {
                    speed = 0.4f + p[1] * 1.2f;
                }
                looperPlayhead_ = wrap(looperPlayhead_ + speed, loopLen);
                const float d = loopLen - looperPlayhead_;
                const float ll = delayL_.read(d);
                const float rr = delayR_.read(d);
                const float mix = clampf(p[5], 0.2f, 1.0f);
                l = inL * (1.0f - mix) + ll * mix;
                r = inR * (1.0f - mix) + rr * mix;
            } else {
                looperActive_ = false;
                stopperSpeed_ = 1.0f;
            }
            break;
        }
        case 16:
        case 26: { // Tremolo/Pan / Slicer
            const float rate = 0.5f + p[0] * 14.0f;
            lfoPhase_ = wrap(lfoPhase_ + rate / sr, 1.0f);
            const float depth = p[1];
            float wave;
            if (fxId_ == 26 || p[3] > 0.3f) {
                wave = (wrap(lfoPhase_ * 4.0f, 1.0f) < (0.35f + p[4] * 0.4f)) ? 1.0f : 0.0f;
            } else {
                wave = 0.5f + 0.5f * std::sin(lfoPhase_ * kTwoPi);
            }
            const float gainL = 1.0f - depth * (1.0f - wave);
            const float gainR = (fxId_ == 16 && p[2] >= 0.5f) ? 1.0f - depth * wave : gainL;
            l = inL * gainL;
            r = inR * gainR;
            break;
        }
        case 17:
        case 34:
        case 38:
        case 40: { // Wah / Super Filter / To-Gu-Ro / SBF
            const float rate = 0.15f + p[4] * 9.0f;
            lfoPhase_ = wrap(lfoPhase_ + rate / sr, 1.0f);
            const float lfo = std::sin(lfoPhase_ * kTwoPi);
            const float baseCut = 40.0f * std::pow(350.0f, p[0]);
            const float modOct = lfo * p[5] * 2.5f + envFollower_ * 3.5f;
            const float cutoff = clampf(baseCut * std::pow(2.0f, modOct), 30.0f, sr * 0.45f);
            const float reso = clampf(p[1], 0.0f, 0.96f);
            const int mode = std::min(2, static_cast<int>(p[2] * 2.99f));
            const int stages = (fxId_ == 34) ? 1 + std::min(2, static_cast<int>(p[3] * 2.99f)) : 2;
            const float k = 2.0f - 1.92f * reso;
            for (int s = 0; s < stages; ++s) {
                l = svfL(s, l, cutoff, k, mode);
                r = svfR(s, r, cutoff, k, mode);
            }
            l = std::tanh(l);
            r = std::tanh(r);
            break;
        }
        case 19: { // 3-band parametric EQ
            const float lowFreq = 40.0f + p[3] * 300.0f;
            const float midFreq = 250.0f * std::pow(16.0f, p[4]);
            const float gLow = std::min(0.45f, kPi * lowFreq / sr);
            const float gHigh = std::min(0.85f, kPi * 3500.0f / sr);
            lowL_ += gLow * (inL - lowL_);
            lowR_ += gLow * (inR - lowR_);
            highL_ += gHigh * (inL - highL_);
            highR_ += gHigh * (inR - highR_);
            const float kMid = 2.0f - 1.6f * clampf(p[5], 0.0f, 0.9f);
            const float midL = svfL(0, inL, midFreq, kMid, 1);
            const float midR = svfR(0, inR, midFreq, kMid, 1);
            const float lg = (p[0] - 0.5f) * 3.2f;
            const float mg = (p[1] - 0.5f) * 3.2f;
            const float hg = (p[2] - 0.5f) * 3.2f;
            l = std::tanh(inL + lowL_ * lg + midL * mg + (inL - highL_) * hg);
            r = std::tanh(inR + lowR_ * lg + midR * mg + (inR - highR_) * hg);
            break;
        }
        case 20:
        case 31:
        case 32:
        case 39: { // Lo-fi / VinylSim / Cassette
            const float wfAmt = p[2] * 38.0f;
            lfoPhase_ = wrap(lfoPhase_ + 1.85f / sr, 1.0f);
            lfoPhase2_ = wrap(lfoPhase2_ + 6.7f / sr, 1.0f);
            const float wf = (std::sin(lfoPhase_ * kTwoPi) * 0.75f +
                              std::sin(lfoPhase2_ * kTwoPi) * 0.25f) * wfAmt;
            writeL = inL;
            writeR = inR;
            const float d = 48.0f + std::max(-44.0f, wf);
            const float wl = delayL_.read(d);
            const float wr = delayR_.read(d + 3.0f);
            const float compAmt = (fxId_ == 32) ? 1.0f + p[0] * 3.5f : 1.0f + p[3] * 2.5f;
            const float rms = 0.5f * (std::fabs(wl) + std::fabs(wr));
            compEnv_ += (rms - compEnv_) * 0.004f;
            const float gr = 1.0f / (1.0f + compEnv_ * compAmt * 2.2f);
            const float raw = noise();
            const float crackle = raw > 0.9965f ? raw * 0.35f : 0.0f;
            const float hiss = raw * 0.015f;
            const float noiseMix = (crackle + hiss) * p[1];
            const float toneCtrl = (fxId_ == 31) ? p[0] : std::max(0.25f, p[4]);
            const float cutoff = 450.0f * std::pow(25.0f, toneCtrl);
            const float fltL = svfL(0, std::tanh(wl * compAmt * gr) + noiseMix, cutoff, 1.2f, 0);
            const float fltR = svfR(0, std::tanh(wr * compAmt * gr) + noiseMix, cutoff, 1.2f, 0);
            const float wet = clampf(p[5], 0.1f, 1.0f);
            l = inL * (1.0f - wet) + fltL * wet;
            r = inR * (1.0f - wet) + fltR * wet;
            break;
        }
        case 21:
        case 22:
        case 25:
        case 27:
        case 42:
        case 43:
        case 45: { // Ring mod / pitch / sub / formant family
            if ((inL >= 0.0f && subPrevL_ < 0.0f) || (inL < 0.0f && subPrevL_ >= 0.0f)) {
                subSignL_ = -subSignL_;
            }
            subPrevL_ = inL;
            const float freq = 45.0f * std::pow(40.0f, p[0]);
            lfoPhase_ = wrap(lfoPhase_ + freq / sr, 1.0f);
            const float carrier = std::sin(lfoPhase_ * kTwoPi);
            float modL, modR;
            if (fxId_ == 21) {
                modL = inL * carrier;
                modR = inR * carrier;
            } else if (fxId_ == 45) {
                const float sub = subSignL_ * envFollower_ * 1.8f;
                const float g = std::min(0.25f, kPi * 140.0f / sr);
                subLp_ += g * (sub - subLp_);
                modL = inL + subLp_ * p[1] * 2.0f;
                modR = inR + subLp_ * p[1] * 2.0f;
            } else {
                writeL = inL;
                writeR = inR;
                const float ratio = std::pow(2.0f, (p[0] - 0.5f) * 2.0f);
                const float rate = (1.0f - ratio) / 1024.0f;
                stopperPhase_ = wrap(stopperPhase_ + rate, 1.0f);
                const float ph1 = stopperPhase_;
                const float ph2 = wrap(ph1 + 0.5f, 1.0f);
                const float w1 = std::sin(ph1 * kPi);
                const float w2 = std::sin(ph2 * kPi);
                const float g1 = w1 * w1;
                const float g2 = w2 * w2;
                modL = delayL_.read(24.0f + ph1 * 1024.0f) * g1 +
                       delayL_.read(24.0f + ph2 * 1024.0f) * g2;
                modR = delayR_.read(24.0f + ph1 * 1024.0f) * g1 +
                       delayR_.read(24.0f + ph2 * 1024.0f) * g2;
            }
            const float wet = clampf(p[2], 0.1f, 1.0f);
            l = inL * (1.0f - wet) + std::tanh(modL) * wet;
            r = inR * (1.0f - wet) + std::tanh(modR) * wet;
            break;
        }
        default: { // Compressor / 303 BusComp
            const float sustain = 1.0f + p[0] * 6.5f;
            const float att = 0.001f + (1.0f - p[1]) * 0.05f;
            const float lvl = 0.5f + p[2] * 1.5f;
            const float peak = std::max(std::fabs(inL), std::fabs(inR));
            const float coeff = peak > compEnv_ ? att : 0.0008f;
            compEnv_ += (peak - compEnv_) * coeff;
            const float gain = (sustain / (1.0f + compEnv_ * sustain * 2.4f)) * lvl;
            l = std::tanh(inL * gain);
            r = std::tanh(inR * gain);
            break;
        }
    }

    delayL_.write(writeL);
    delayR_.write(writeR);
    outL = sanitize(l);
    outR = sanitize(r);
}

} // namespace rep
