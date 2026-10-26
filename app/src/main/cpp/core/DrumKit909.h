#pragma once

// Replicant 909 drum voices (MIT) wrapped as a self-contained kit.
// The voice models are the same circuit-level DSP used by Replicant909:
// bridged-T resonators, analog cymbal ROM playback, hand-clap burst generator.

#include "Dsp.h"
#include "drums909/BassDrumVoice.h"
#include "drums909/CymbalVoice.h"
#include "drums909/HandClapVoice.h"
#include "drums909/RimShotVoice.h"
#include "drums909/SnareDrumVoice.h"
#include "drums909/TomVoice.h"

#include <array>
#include <memory>

namespace rep {

class DrumKit909 {
public:
    static constexpr int kVoices = 11;

    DrumKit909();
    void prepare(double sampleRate);
    void reset();

    // tune/decay/snappy/tone/accent/level in 0..1, cutoff in Hz, res 0..1.
    void setParams(float tune, float decay, float snappy, float tone, float accent,
                   float level, float cutoff, float resonance);
    void trigger(int voice, float velocity, float accent);
    void triggerAll(float velocity);
    void chokeOpenHat();
    void render(float& outL, float& outR);
    bool anyActive() const;

    static const char* voiceName(int voice);

private:
    std::array<std::unique_ptr<Replicant909::VoiceBase>, kVoices> voices_{};
    // Headroom trim for the summed voice bus (see render()).
    static constexpr float kKitTrim = 0.75f;

    std::array<float, kVoices> baseLevel_{};
    std::array<float, kVoices> pan_{};
    ZdfSvf filterL_;
    ZdfSvf filterR_;
    float cutoff_ = 18000.0f;
    float resonance_ = 0.6f;
    float level_ = 0.9f;
    float accentScale_ = 0.7f;
};

} // namespace rep
