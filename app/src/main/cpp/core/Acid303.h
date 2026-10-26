#pragma once

// Replicant 303 voice adapted from the Replicant303 DSP core (MIT).
// Monophonic acid voice: polyBLEP oscillator, diode ladder filter, capacitor
// slide, accent envelope and the five-model distortion unit.

#include "Dsp.h"
#include "acid/distortion.h"
#include "acid/envelope.h"
#include "acid/filter.h"
#include "acid/oscillator.h"

#include <vector>

namespace rep {

class Acid303 {
public:
    void prepare(double sampleRate) noexcept;
    void reset() noexcept;

    void setWaveform(int wave) noexcept;
    void setTuning(float semitones) noexcept;
    void setCutoff(float hz) noexcept;
    void setResonance(float resonance01) noexcept;
    void setEnvMod(float envMod01) noexcept;
    void setDecay(float seconds) noexcept;
    void setAccent(float accent01) noexcept;
    void setVolume(float linear) noexcept;
    void setDistortion(bool active, int type, float drive, float tone, float mix) noexcept;

    void noteOn(int noteNumber, int velocity) noexcept;
    void noteOff(int noteNumber) noexcept;
    void allNotesOff() noexcept;

    float process() noexcept;
    bool isActive() const noexcept { return env_.isActive(); }
    int heldNotes() const noexcept { return static_cast<int>(noteStack_.size()); }
    bool sliding() const noexcept { return slideLedActive_; }

private:
    struct ActiveNote {
        int note = -1;
        int velocity = 0;
    };

    void updateSlideCoefficient() noexcept;

    double sampleRate_ = 48000.0;

    r303::Oscillator303 osc_;
    r303::DiodeLadderFilter filter_;
    r303::Envelope303 env_;
    r303::DistortionUnit distortion_;

    float envMod_ = 0.5f;
    float accentAmount_ = 0.7f;
    float masterGain_ = 0.8f;

    std::vector<ActiveNote> noteStack_;
    float targetPitchHz_ = 110.0f;
    float currentPitchHz_ = 110.0f;
    float pitchSlideCoeff_ = 0.01f;

    bool slideLedActive_ = false;
    bool accentLedActive_ = false;
    int lastNoteNumber_ = 36;
};

} // namespace rep
