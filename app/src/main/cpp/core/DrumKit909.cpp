#include "DrumKit909.h"

#include <algorithm>

namespace rep {

namespace {
using namespace Replicant909;

// TR-909 voice order used by the editor and the pad sequencer.
constexpr int kBD = 0, kSD = 1, kLT = 2, kMT = 3, kHT = 4, kRS = 5, kCP = 6,
              kCH = 7, kOH = 8, kCC = 9, kRC = 10;
} // namespace

DrumKit909::DrumKit909() {
    voices_[kBD] = std::make_unique<BassDrumVoice>();
    voices_[kSD] = std::make_unique<SnareDrumVoice>();
    voices_[kLT] = std::make_unique<TomVoice>(TOM_LOW);
    voices_[kMT] = std::make_unique<TomVoice>(TOM_MID);
    voices_[kHT] = std::make_unique<TomVoice>(TOM_HI);
    voices_[kRS] = std::make_unique<RimShotVoice>();
    voices_[kCP] = std::make_unique<HandClapVoice>();
    voices_[kCH] = std::make_unique<CymbalVoice>(CYMBAL_CH);
    voices_[kOH] = std::make_unique<CymbalVoice>(CYMBAL_OH);
    voices_[kCC] = std::make_unique<CymbalVoice>(CYMBAL_CRASH);
    voices_[kRC] = std::make_unique<CymbalVoice>(CYMBAL_RIDE);

    baseLevel_ = {1.00f, 0.82f, 0.72f, 0.70f, 0.68f, 0.62f, 0.72f, 0.58f, 0.52f, 0.60f, 0.55f};
    pan_ = {0.0f, 0.02f, -0.28f, 0.0f, 0.28f, -0.45f, 0.42f, 0.34f, -0.34f, -0.55f, 0.55f};
}

void DrumKit909::prepare(double sampleRate) {
    for (auto& v : voices_) v->prepare(sampleRate);
    filterL_.prepare(static_cast<float>(sampleRate));
    filterR_.prepare(static_cast<float>(sampleRate));
    reset();
}

void DrumKit909::reset() {
    for (auto& v : voices_) v->reset();
    filterL_.reset();
    filterR_.reset();
}

void DrumKit909::setParams(float tune, float decay, float snappy, float tone,
                           float accent, float level, float cutoff, float resonance) {
    const float t = clampf(tune, 0.0f, 1.0f);
    const float d = clampf(decay, 0.0f, 1.0f);
    const float s = clampf(snappy, 0.0f, 1.0f);
    const float tn = clampf(tone, 0.0f, 1.0f);
    accentScale_ = clampf(accent, 0.0f, 1.0f);
    level_ = clampf(level, 0.0f, 1.5f);
    cutoff_ = clampf(cutoff, 200.0f, 20000.0f);
    resonance_ = clampf(resonance, 0.5f, 12.0f);

    for (int i = 0; i < kVoices; ++i) {
        auto& v = *voices_[static_cast<size_t>(i)];
        v.setTune(t);
        v.setDecay(d);
        v.setSnappy(s);
        v.setTone(tn);
        v.setLevel(baseLevel_[static_cast<size_t>(i)]);
        v.setPan(0.5f + pan_[static_cast<size_t>(i)] * 0.5f);
    }
}

void DrumKit909::trigger(int voice, float velocity, float accent) {
    if (voice < 0 || voice >= kVoices) return;
    if (voice == kCH) {
        // Hardware choke: a closed hat discharges the open-hat envelope.
        voices_[kOH]->choke();
    }
    voices_[static_cast<size_t>(voice)]->trigger(velocity, accent * accentScale_);
    voices_[static_cast<size_t>(voice)]->decayPeak();
}

void DrumKit909::triggerAll(float velocity) {
    for (int i = 0; i < kVoices; ++i) trigger(i, velocity, 0.0f);
}

void DrumKit909::chokeOpenHat() {
    voices_[kOH]->choke();
}

void DrumKit909::render(float& outL, float& outR) {
    float l = 0.0f;
    float r = 0.0f;
    for (int i = 0; i < kVoices; ++i) {
        auto& v = *voices_[static_cast<size_t>(i)];
        if (!v.isActive()) continue;
        const float s = v.renderSample();
        const float pan = clampf(pan_[static_cast<size_t>(i)], -1.0f, 1.0f);
        const float angle = (pan + 1.0f) * 0.25f * kPi;
        l += s * std::cos(angle);
        r += s * std::sin(angle);
    }

    if (cutoff_ < 19000.0f) {
        l = filterL_.process(l, cutoff_, resonance_, 0);
        r = filterR_.process(r, cutoff_, resonance_, 0);
    }

    // Each voice already carries its own VCA saturation. Summing eleven of
    // them through another tanh squashed every transient into the same block
    // (crest factor ~1.7) and that is what made the kit sound saturated. Keep
    // the bus linear with fixed headroom and only catch overload peaks.
    const float bus = level_ * kKitTrim;
    outL = softLimit(l * bus);
    outR = softLimit(r * bus);
}

bool DrumKit909::anyActive() const {
    for (const auto& v : voices_) {
        if (v->isActive()) return true;
    }
    return false;
}

const char* DrumKit909::voiceName(int voice) {
    static const char* names[kVoices] = {"BD", "SD", "LT", "MT", "HT", "RS",
                                         "CP", "CH", "OH", "CC", "RC"};
    return (voice >= 0 && voice < kVoices) ? names[voice] : "--";
}

} // namespace rep
