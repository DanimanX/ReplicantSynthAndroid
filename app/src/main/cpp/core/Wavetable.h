#pragma once

// ReplicantWave engine ported to the Android C++ core.
// The Rust original builds 8 wavetable banks with continuous frame morphing,
// up to 8 unison voices, a Cytomic-style ZDF filter and a 64-step sequencer.
// This port keeps the sound architecture (band-limited banks, morphing,
// unison, sub/noise, ZDF filter) and is driven by the clip sequencer.

#include "Dsp.h"

#include <array>
#include <vector>

namespace rep {

class WavetableBanks {
public:
    static constexpr int kCount = 8;
    static constexpr int kFrames = 16;
    static constexpr int kMips = 7;
    static constexpr int kSize = 1024;
    static constexpr int kMaxHarmonics = 192;

    WavetableBanks();
    void build();
    bool ready() const noexcept { return !data_.empty(); }

    // Linear frame morph and linear phase interpolation.
    float sample(int bank, float framePosition, int mip, float phase) const noexcept;
    static const char* bankName(int bank) noexcept;

private:
    std::vector<float> data_;
    static float sine(float phase) noexcept;
};

// One shared bank set for every clip: built once, read on the audio thread.
const WavetableBanks& sharedWavetableBanks();

struct WaveParams {
    int bank = 0;
    float position = 0.25f;
    int unison = 1;
    float detune = 12.0f;
    float spread = 0.4f;
    float cutoff = 2400.0f;
    float resonance = 0.35f;
    float drive = 1.0f;
    float attack = 0.005f;
    float decay = 0.35f;
    float sustain = 0.55f;
    float release = 0.45f;
    float sub = 0.15f;
    float noise = 0.0f;
    float level = 0.8f;
    int filterMode = 0;
    int chord = 0;
};

class WaveSynth {
public:
    static constexpr int kVoices = 6;
    static constexpr int kUnison = 8;

    WaveSynth();
    void prepare(double sampleRate);
    void reset();
    void setParams(const WaveParams& params);
    void noteOn(int note, int velocity);
    void noteOff(int note);
    void allNotesOff();
    void render(float& outL, float& outR);

private:
    struct Voice {
        bool active = false;
        bool held = false;
        int note = 60;
        float velocity = 0.8f;
        uint32_t age = 0;
        float phase[kUnison] = {};
        float inc[kUnison] = {};
        float pan[kUnison] = {};
        float gain[kUnison] = {};
        float subPhase = 0.0f;
        float subInc = 0.0f;
        Adsr env;
        ZdfSvf filterL;
        ZdfSvf filterR;
        ZdfSvf filter2L;
        ZdfSvf filter2R;
    };

    void updateVoiceTuning(Voice& voice);
    void startVoice(int note, int velocity);
    int chordInterval(int chord, int index) const;

    double sampleRate_ = 48000.0;
    float sampleRateF_ = 48000.0f;
    WaveParams params_{};
    std::array<Voice, kVoices> voices_{};
    uint32_t ageCounter_ = 0;
    Rng rng_{0x51A7E001u};
    float smoothedCutoff_ = 2400.0f;
    float smoothedResonance_ = 0.35f;
    float smoothedDrive_ = 1.0f;
};

} // namespace rep
