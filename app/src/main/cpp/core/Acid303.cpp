#include "Acid303.h"

#include <algorithm>
#include <cmath>

namespace rep {

void Acid303::prepare(double sampleRate) noexcept {
    sampleRate_ = std::max(1000.0, sampleRate);
    osc_.prepare(sampleRate_);
    filter_.prepare(sampleRate_);
    env_.prepare(sampleRate_);
    distortion_.prepare(sampleRate_);
    noteStack_.reserve(128);
    updateSlideCoefficient();
    reset();
}

void Acid303::reset() noexcept {
    osc_.reset();
    filter_.reset();
    env_.reset();
    distortion_.reset();
    noteStack_.clear();
    targetPitchHz_ = 110.0f;
    currentPitchHz_ = 110.0f;
    slideLedActive_ = false;
    accentLedActive_ = false;
}

void Acid303::updateSlideCoefficient() noexcept {
    const float dt = 1.0f / static_cast<float>(sampleRate_);
    pitchSlideCoeff_ = 1.0f - std::exp(-dt / 0.060f);
}

void Acid303::setWaveform(int wave) noexcept {
    osc_.setWaveform(wave == 1 ? r303::Waveform::Square : r303::Waveform::Sawtooth);
}
void Acid303::setTuning(float semitones) noexcept { osc_.setTuningSemi(semitones); }
void Acid303::setCutoff(float hz) noexcept { filter_.setCutoff(hz); }
void Acid303::setResonance(float resonance01) noexcept { filter_.setResonance(resonance01); }
void Acid303::setEnvMod(float envMod01) noexcept { envMod_ = clampf(envMod01, 0.0f, 1.0f); }
void Acid303::setDecay(float seconds) noexcept { env_.setDecayTime(seconds); }
void Acid303::setAccent(float accent01) noexcept { accentAmount_ = clampf(accent01, 0.0f, 1.0f); }
void Acid303::setVolume(float linear) noexcept { masterGain_ = clampf(linear, 0.0f, 1.5f); }

void Acid303::setDistortion(bool active, int type, float drive, float tone,
                            float mix) noexcept {
    distortion_.setActive(active);
    distortion_.setType(static_cast<r303::DistortionType>(clampi(type, 0, 4)));
    distortion_.setDrive(drive);
    distortion_.setTone(tone);
    distortion_.setMix(mix);
}

void Acid303::noteOn(int noteNumber, int velocity) noexcept {
    lastNoteNumber_ = noteNumber;
    const bool isAccented = velocity >= 100;
    const bool isLegato = !noteStack_.empty();

    targetPitchHz_ = midiToFreq(static_cast<float>(noteNumber));
    if (!isLegato) {
        currentPitchHz_ = targetPitchHz_;
        slideLedActive_ = false;
    } else {
        slideLedActive_ = true;
    }
    accentLedActive_ = isAccented;
    env_.noteOn(isAccented, isLegato);

    noteStack_.erase(std::remove_if(noteStack_.begin(), noteStack_.end(),
                                    [noteNumber](const ActiveNote& n) {
                                        return n.note == noteNumber;
                                    }),
                     noteStack_.end());
    if (noteStack_.size() >= 128) noteStack_.erase(noteStack_.begin());
    noteStack_.push_back(ActiveNote{noteNumber, velocity});
}

void Acid303::noteOff(int noteNumber) noexcept {
    const auto it = std::find_if(noteStack_.begin(), noteStack_.end(),
                                 [noteNumber](const ActiveNote& n) {
                                     return n.note == noteNumber;
                                 });
    if (it != noteStack_.end()) noteStack_.erase(it);
    if (noteStack_.empty()) {
        env_.noteOff();
        slideLedActive_ = false;
        accentLedActive_ = false;
    } else {
        targetPitchHz_ = midiToFreq(static_cast<float>(noteStack_.back().note));
        slideLedActive_ = true;
    }
}

void Acid303::allNotesOff() noexcept {
    noteStack_.clear();
    env_.noteOff();
    slideLedActive_ = false;
    accentLedActive_ = false;
}

float Acid303::process() noexcept {
    currentPitchHz_ += (targetPitchHz_ - currentPitchHz_) * pitchSlideCoeff_;

    osc_.setFrequency(currentPitchHz_);
    const float rawOsc = osc_.process();

    float vcfEnv = 0.0f;
    float vcaEnv = 0.0f;
    float accentEnv = 0.0f;
    env_.process(vcfEnv, vcaEnv, accentEnv);

    const float filtered = filter_.process(rawOsc, envMod_, vcfEnv, accentEnv * accentAmount_);
    const float vcaBoost = 1.0f + accentEnv * accentAmount_ * 0.45f;
    const float amplified = filtered * vcaEnv * vcaBoost;
    const float distorted = distortion_.processSample(amplified);
    return sanitize(distorted * masterGain_);
}

} // namespace rep
