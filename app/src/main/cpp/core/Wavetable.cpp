#include "Wavetable.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace rep {

namespace {

constexpr float kBankFreqBase = 30.0f;  // mip 0 covers fundamentals below 30 Hz
constexpr float kTargetPeak = 0.90f;

inline float gaussian(float x, float centre, float width) {
    const float d = (x - centre) / width;
    return std::exp(-0.5f * d * d);
}

inline double besselJ(int n, double x) {
    if (n < 0) return 0.0;
    double term = std::pow(x * 0.5, n) / std::tgamma(n + 1.0);
    double sum = term;
    for (int k = 1; k < 32; ++k) {
        term *= -(x * x * 0.25) / (static_cast<double>(k) * (n + k));
        sum += term;
        if (std::fabs(term) < 1.0e-10) break;
    }
    return sum;
}

inline int mipHarmonics(int mip, float sampleRate) {
    const float fundamental = kBankFreqBase * std::pow(2.0f, static_cast<float>(mip));
    const int limit = static_cast<int>((sampleRate * 0.46f) / fundamental);
    return clampi(limit, 1, WavetableBanks::kMaxHarmonics);
}

// Fills the harmonic amplitude/phase tables for one bank frame.
// `t` is 0..1 across the frame axis.
void bankHarmonics(int bank, float t, float* amp, float* phase, int count) {
    for (int n = 0; n < count; ++n) {
        amp[n] = 0.0f;
        phase[n] = 0.0f;
    }

    switch (bank) {
        case 0: {  // Analog Morph: saw -> triangle -> sine -> square -> pulse
            const float pos = clampf(t, 0.0f, 1.0f) * 4.0f;
            const int segment = clampi(static_cast<int>(pos), 0, 3);
            const float blend = pos - static_cast<float>(segment);
            const float pulseWidth = 0.5f - 0.36f * t;
            for (int n = 1; n < count; ++n) {
                const float fn = static_cast<float>(n);
                const float saw = 1.0f / fn;
                const float tri = (n % 2 == 1)
                                      ? (((n - 1) / 2) % 2 == 0 ? 1.0f : -1.0f) /
                                            (fn * fn)
                                      : 0.0f;
                const float sine = (n == 1) ? 1.0f : 0.0f;
                const float square = (n % 2 == 1) ? 1.0f / fn : 0.0f;
                const float pulse = (2.0f / (fn * kPi)) * std::sin(kPi * fn * pulseWidth);
                float a = 0.0f;
                float b = 0.0f;
                switch (segment) {
                    case 0: a = saw; b = tri; break;
                    case 1: a = tri; b = sine; break;
                    case 2: a = sine; b = square; break;
                    default: a = square; b = pulse; break;
                }
                amp[n] = lerpf(a, b, blend);
                if (segment == 3) phase[n] = 0.0f;
            }
            break;
        }
        case 1: {  // Cyber Neuro: formant-swept folded saw
            const float peak = 3.0f + 22.0f * t;
            const float width = 2.0f + 7.0f * t;
            for (int n = 1; n < count; ++n) {
                const float fn = static_cast<float>(n);
                const float base = 1.0f / fn;
                const float growl = gaussian(fn, peak, width) * (2.2f + 4.0f * t);
                amp[n] = base * (0.22f + growl);
                phase[n] = (n % 3 == 0) ? kPi * 0.5f : 0.0f;
            }
            break;
        }
        case 2: {  // Digital FM: 2-op carrier/modulator with a 2:1 ratio
            const float index = 8.0f * t;
            for (int n = 1; n < count; ++n) {
                // FM with modulator ratio 2 puts energy on odd sidebands.
                const double j = (n % 2 == 1)
                                     ? besselJ((n - 1) / 2, index)
                                     : 0.0;
                amp[n] = std::fabs(j) * (0.35f + 0.65f * (1.0f / (1.0f + 0.02f * n)));
                phase[n] = j < 0.0f ? kPi : 0.0f;
            }
            if (t < 0.05f) amp[1] = 1.0f;
            break;
        }
        case 3: {  // Vocaloid: A-E-I-O-U formant morph
            static constexpr float formants[5][3] = {
                {800.0f, 1150.0f, 2900.0f},  // A
                {400.0f, 1600.0f, 2700.0f},  // E
                {350.0f, 1700.0f, 2700.0f},  // I
                {450.0f, 800.0f, 2830.0f},   // O
                {325.0f, 700.0f, 2530.0f},   // U
            };
            const float pos = clampf(t, 0.0f, 1.0f) * 4.0f;
            const int v0 = clampi(static_cast<int>(pos), 0, 4);
            const int v1 = clampi(v0 + 1, 0, 4);
            const float blend = pos - static_cast<float>(v0);
            const float f0 = 110.0f;
            for (int n = 1; n < count; ++n) {
                const float freq = static_cast<float>(n) * f0;
                float a = 0.0f;
                for (int k = 0; k < 3; ++k) {
                    const float c0 = formants[v0][k] / f0;
                    const float c1 = formants[v1][k] / f0;
                    const float c = lerpf(c0, c1, blend);
                    a += gaussian(static_cast<float>(n), c, 1.6f + 1.2f * k);
                }
                amp[n] = (a + 0.08f) / std::pow(static_cast<float>(n), 0.9f);
            }
            break;
        }
        case 4: {  // Cyber Acid: resonant harmonic squelch
            const float peak = 3.0f + 16.0f * t;
            for (int n = 1; n < count; ++n) {
                const float fn = static_cast<float>(n);
                const float resonance = gaussian(fn, peak, 1.1f) * (6.0f + 8.0f * t);
                amp[n] = (1.0f / fn) * (0.35f + resonance);
                phase[n] = (n % 2 == 0) ? kPi : 0.0f;
            }
            break;
        }
        case 5: {  // Dystopian Steel: phase-randomised metallic partials
            Rng rng(0x5EED0000u + static_cast<uint32_t>(t * 1000.0f));
            for (int n = 1; n < count; ++n) {
                const float fn = static_cast<float>(n);
                const bool metallic = (n % 7 == 0) || (n % 11 == 0) || n < 9;
                const float weight = metallic ? 1.0f : 0.22f;
                amp[n] = weight / std::pow(fn, 0.72f);
                phase[n] = rng.uniform() * kTwoPi;
            }
            break;
        }
        case 6: {  // PWM Hyper: 50% -> 4% pulse width
            const float width = 0.5f - 0.46f * t;
            for (int n = 1; n < count; ++n) {
                const float fn = static_cast<float>(n);
                amp[n] = (2.0f / (fn * kPi)) * std::sin(kPi * fn * width);
            }
            break;
        }
        default: {  // Sub Zero: saturated sub with wavefolded odd harmonics
            for (int n = 1; n < count; ++n) {
                const float fn = static_cast<float>(n);
                if (n == 1) {
                    amp[n] = 1.0f;
                } else if (n % 2 == 0) {
                    amp[n] = 0.28f * t / fn;
                } else {
                    amp[n] = (0.55f * t + 0.12f) / std::pow(fn, 0.8f);
                }
            }
            break;
        }
    }
}

} // namespace

float WavetableBanks::sine(float phase) noexcept {
    static constexpr int kSineSize = 4096;
    static std::array<float, kSineSize + 1> table;
    static bool initialised = false;
    if (!initialised) {
        for (int i = 0; i <= kSineSize; ++i) {
            table[static_cast<size_t>(i)] =
                std::sin(kTwoPi * static_cast<float>(i) / static_cast<float>(kSineSize));
        }
        initialised = true;
    }
    const float p = wrap01(phase) * static_cast<float>(kSineSize);
    const int i0 = static_cast<int>(p) % kSineSize;
    const float frac = p - std::floor(p);
    return table[static_cast<size_t>(i0)] * (1.0f - frac) +
           table[static_cast<size_t>(i0 + 1)] * frac;
}

const WavetableBanks& sharedWavetableBanks() {
    static WavetableBanks instance;
    return instance;
}

WavetableBanks::WavetableBanks() { build(); }

void WavetableBanks::build() {
    const size_t total = static_cast<size_t>(kCount) * kFrames * kMips * kSize;
    data_.assign(total, 0.0f);

    std::vector<float> amp(static_cast<size_t>(kMaxHarmonics), 0.0f);
    std::vector<float> phase(static_cast<size_t>(kMaxHarmonics), 0.0f);
    std::vector<float> frame(kMips * kSize, 0.0f);
    std::vector<float> peaks(static_cast<size_t>(kMips), 0.0f);

    for (int bank = 0; bank < kCount; ++bank) {
        for (int f = 0; f < kFrames; ++f) {
            const float t = static_cast<float>(f) / static_cast<float>(kFrames - 1);
            bankHarmonics(bank, t, amp.data(), phase.data(), kMaxHarmonics);

            for (int mip = 0; mip < kMips; ++mip) {
                const int harmonics = mipHarmonics(mip, 48000.0f);
                float* dst = frame.data() + static_cast<size_t>(mip) * kSize;
                float peak = 0.0f;
                for (int i = 0; i < kSize; ++i) {
                    const float p = static_cast<float>(i) / static_cast<float>(kSize);
                    float sample = 0.0f;
                    for (int n = 1; n <= harmonics; ++n) {
                        const float a = amp[static_cast<size_t>(n)];
                        if (std::fabs(a) < 1.0e-6f) continue;
                        sample += a * sine(p * static_cast<float>(n) +
                                           phase[static_cast<size_t>(n)] / kTwoPi);
                    }
                    dst[i] = sample;
                    peak = std::max(peak, std::fabs(sample));
                }
                peaks[static_cast<size_t>(mip)] = peak;
            }

            float framePeak = 0.0f;
            for (float p : peaks) framePeak = std::max(framePeak, p);
            if (framePeak < 1.0e-6f) framePeak = 1.0f;
            const float scale = kTargetPeak / framePeak;

            for (int mip = 0; mip < kMips; ++mip) {
                float* dst = frame.data() + static_cast<size_t>(mip) * kSize;
                float* out = data_.data() +
                             ((static_cast<size_t>(bank) * kFrames + f) * kMips + mip) *
                                 kSize;
                for (int i = 0; i < kSize; ++i) out[i] = dst[i] * scale;
            }
        }
    }
}

float WavetableBanks::sample(int bank, float framePosition, int mip, float phase) const noexcept {
    if (data_.empty()) return 0.0f;
    const int b = clampi(bank, 0, kCount - 1);
    const int m = clampi(mip, 0, kMips - 1);
    const float fp = clampf(framePosition, 0.0f, 1.0f) *
                     static_cast<float>(kFrames - 1);
    const int f0 = clampi(static_cast<int>(fp), 0, kFrames - 1);
    const int f1 = std::min(f0 + 1, kFrames - 1);
    const float fFrac = fp - static_cast<float>(f0);

    const float p = wrap01(phase) * static_cast<float>(kSize);
    const int i0 = static_cast<int>(p) % kSize;
    const float frac = p - std::floor(p);

    const auto read = [&](int frameIndex) {
        const float* t = data_.data() +
                         ((static_cast<size_t>(b) * kFrames + frameIndex) * kMips + m) *
                             kSize;
        return t[i0] * (1.0f - frac) + t[(i0 + 1) % kSize] * frac;
    };

    return lerpf(read(f0), read(f1), fFrac);
}

const char* WavetableBanks::bankName(int bank) noexcept {
    static const char* names[kCount] = {
        "ANALOG MORPH", "CYBER NEURO", "DIGITAL FM", "VOCALOID",
        "CYBER ACID",   "DYSTOPIAN STEEL", "PWM HYPER", "SUB ZERO"};
    return names[clampi(bank, 0, kCount - 1)];
}

// ---------------------------------------------------------------------------
// WaveSynth
// ---------------------------------------------------------------------------

WaveSynth::WaveSynth() {
    // The banks are built here, on the construction thread, never in the audio
    // callback. Construction happens once when the engine is created.
}

void WaveSynth::prepare(double sampleRate) {
    sampleRate_ = std::max(8000.0, sampleRate);
    sampleRateF_ = static_cast<float>(sampleRate_);
    for (auto& v : voices_) {
        v.env.prepare(sampleRateF_);
        v.filterL.prepare(sampleRateF_);
        v.filterR.prepare(sampleRateF_);
        v.filter2L.prepare(sampleRateF_);
        v.filter2R.prepare(sampleRateF_);
    }
    reset();
}

void WaveSynth::reset() {
    for (auto& v : voices_) {
        v = Voice{};
        v.env.prepare(sampleRateF_);
        v.filterL.prepare(sampleRateF_);
        v.filterR.prepare(sampleRateF_);
        v.filter2L.prepare(sampleRateF_);
        v.filter2R.prepare(sampleRateF_);
    }
    ageCounter_ = 0;
    smoothedCutoff_ = params_.cutoff;
    smoothedResonance_ = params_.resonance;
    smoothedDrive_ = params_.drive;
}

void WaveSynth::setParams(const WaveParams& params) {
    params_ = params;
    params_.bank = clampi(params_.bank, 0, WavetableBanks::kCount - 1);
    params_.unison = clampi(params_.unison, 1, kUnison);
    params_.position = clampf(params_.position, 0.0f, 1.0f);
    params_.detune = clampf(params_.detune, 0.0f, 80.0f);
    params_.spread = clampf(params_.spread, 0.0f, 1.0f);
    params_.cutoff = clampf(params_.cutoff, 40.0f, 19000.0f);
    params_.resonance = clampf(params_.resonance, 0.5f, 18.0f);
    params_.drive = clampf(params_.drive, 1.0f, 6.0f);
    params_.attack = clampf(params_.attack, 0.001f, 6.0f);
    params_.decay = clampf(params_.decay, 0.005f, 8.0f);
    params_.sustain = clampf(params_.sustain, 0.0f, 1.0f);
    params_.release = clampf(params_.release, 0.005f, 8.0f);
    params_.sub = clampf(params_.sub, 0.0f, 1.0f);
    params_.noise = clampf(params_.noise, 0.0f, 1.0f);
    params_.level = clampf(params_.level, 0.0f, 1.2f);
    params_.filterMode = clampi(params_.filterMode, 0, 3);
    params_.chord = clampi(params_.chord, 0, 4);

    for (auto& v : voices_) {
        v.env.setAttack(params_.attack);
        v.env.setDecay(params_.decay);
        v.env.setSustain(params_.sustain);
        v.env.setRelease(params_.release);
        if (v.active) updateVoiceTuning(v);
    }
}

void WaveSynth::updateVoiceTuning(Voice& voice) {
    const int unison = clampi(params_.unison, 1, kUnison);
    const float baseFreq = midiToFreq(static_cast<float>(voice.note));
    const float detuneRange = params_.detune;
    const float norm = unison > 1 ? 1.0f / static_cast<float>(unison - 1) : 0.0f;
    const float gainNorm = 0.75f / std::sqrt(static_cast<float>(unison));
    for (int u = 0; u < kUnison; ++u) {
        const float spreadPos = (static_cast<float>(u) * norm) * 2.0f - 1.0f;
        const float cents = (unison == 1) ? 0.0f : spreadPos * detuneRange * 0.5f;
        const float pan = (unison == 1) ? 0.0f : spreadPos * params_.spread;
        const float freq = baseFreq * std::pow(2.0f, cents / 1200.0f);
        voice.inc[u] = (u < unison) ? freq / sampleRateF_ : 0.0f;
        voice.pan[u] = pan;
        const float angle = (pan + 1.0f) * 0.25f * kPi;
        voice.gain[u] = gainNorm * (u < unison ? 1.0f : 0.0f);
        // Keep the pan gains separate; stored below via cos/sin at render time.
        (void)angle;
    }
    voice.subInc = (baseFreq * 0.5f) / sampleRateF_;
}

int WaveSynth::chordInterval(int chord, int index) const {
    static constexpr int intervals[5][3] = {
        {0, 0, 0},     // single
        {0, 7, 12},    // fifth
        {0, 3, 7},     // minor
        {0, 4, 7},     // major
        {0, 12, 19},   // octave stack
    };
    const int c = clampi(chord, 0, 4);
    const int i = clampi(index, 0, 2);
    return intervals[c][i];
}

void WaveSynth::startVoice(int note, int velocity) {
    int slot = -1;
    for (int i = 0; i < kVoices; ++i) {
        if (!voices_[static_cast<size_t>(i)].active) {
            slot = i;
            break;
        }
    }
    if (slot < 0) {
        // Steal the oldest voice.
        uint32_t oldest = UINT32_MAX;
        for (int i = 0; i < kVoices; ++i) {
            if (voices_[static_cast<size_t>(i)].age < oldest) {
                oldest = voices_[static_cast<size_t>(i)].age;
                slot = i;
            }
        }
    }
    Voice& v = voices_[static_cast<size_t>(slot)];
    v = Voice{};
    v.active = true;
    v.held = true;
    v.note = clampi(note, 0, 127);
    v.velocity = clampf(static_cast<float>(velocity) / 127.0f, 0.05f, 1.0f);
    v.age = ++ageCounter_;
    v.env.prepare(sampleRateF_);
    v.env.setAttack(params_.attack);
    v.env.setDecay(params_.decay);
    v.env.setSustain(params_.sustain);
    v.env.setRelease(params_.release);
    v.env.noteOn();
    for (int u = 0; u < kUnison; ++u) {
        v.phase[u] = rng_.uniform();
    }
    v.subPhase = 0.0f;
    v.filterL.prepare(sampleRateF_);
    v.filterR.prepare(sampleRateF_);
    v.filter2L.prepare(sampleRateF_);
    v.filter2R.prepare(sampleRateF_);
    updateVoiceTuning(v);
}

void WaveSynth::noteOn(int note, int velocity) {
    // Chord mode: trigger up to three notes with the same velocity.
    const int notes = (params_.chord == 0) ? 1 : 3;
    for (int i = 0; i < notes; ++i) {
        startVoice(note + chordInterval(params_.chord, i), velocity);
    }
}

void WaveSynth::noteOff(int note) {
    // A clip is a monophonic phrase: releasing any note releases the chord.
    (void)note;
    allNotesOff();
}

void WaveSynth::allNotesOff() {
    for (auto& v : voices_) {
        if (v.active) {
            v.held = false;
            v.env.noteOff();
        }
    }
}

void WaveSynth::render(float& outL, float& outR) {
    float l = 0.0f;
    float r = 0.0f;

    // Smooth global filter controls once per sample (cheap and click-free).
    smoothedCutoff_ += (params_.cutoff - smoothedCutoff_) * 0.002f;
    smoothedResonance_ += (params_.resonance - smoothedResonance_) * 0.002f;
    smoothedDrive_ += (params_.drive - smoothedDrive_) * 0.002f;

    for (auto& v : voices_) {
        if (!v.active) continue;
        const float env = v.env.process();
        if (!v.env.active() && env < 0.0002f) {
            v.active = false;
            continue;
        }

        const float noteCutoff = smoothedCutoff_ *
                                 std::pow(2.0f, (static_cast<float>(v.note) - 60.0f) / 48.0f);
        const float cutoff = clampf(noteCutoff, 40.0f, 19000.0f);
        const float freq = midiToFreq(static_cast<float>(v.note));
        const int mip = clampi(static_cast<int>(std::log2(std::max(20.0f, freq) / 30.0f)),
                               0, WavetableBanks::kMips - 1);

        float voiceL = 0.0f;
        float voiceR = 0.0f;
        const int unison = clampi(params_.unison, 1, kUnison);
        for (int u = 0; u < unison; ++u) {
            v.phase[u] = wrap01(v.phase[u] + v.inc[u]);
            const float s = sharedWavetableBanks().sample(params_.bank, params_.position, mip, v.phase[u]);
            const float angle = (v.pan[u] + 1.0f) * 0.25f * kPi;
            voiceL += s * v.gain[u] * std::cos(angle);
            voiceR += s * v.gain[u] * std::sin(angle);
        }

        if (params_.sub > 0.0001f) {
            v.subPhase = wrap01(v.subPhase + v.subInc);
            const float sub = std::sin(kTwoPi * v.subPhase) * params_.sub * 0.45f;
            voiceL += sub;
            voiceR += sub;
        }
        if (params_.noise > 0.0001f) {
            const float n = rng_.noise() * params_.noise * 0.12f;
            voiceL += n;
            voiceR -= n;
        }

        voiceL = v.filterL.process(voiceL, cutoff, smoothedResonance_, 0, smoothedDrive_);
        voiceR = v.filterR.process(voiceR, cutoff, smoothedResonance_, 0, smoothedDrive_);
        if (params_.filterMode == 0) {
            // LP24: second cascaded stage.
            voiceL = v.filter2L.process(voiceL, cutoff, smoothedResonance_, 0, 1.0f);
            voiceR = v.filter2R.process(voiceR, cutoff, smoothedResonance_, 0, 1.0f);
        } else if (params_.filterMode == 2) {
            voiceL = v.filter2L.process(voiceL, cutoff, smoothedResonance_, 1, 1.0f);
            voiceR = v.filter2R.process(voiceR, cutoff, smoothedResonance_, 1, 1.0f);
        } else if (params_.filterMode == 3) {
            voiceL = v.filter2L.process(voiceL, cutoff, smoothedResonance_, 2, 1.0f);
            voiceR = v.filter2R.process(voiceR, cutoff, smoothedResonance_, 2, 1.0f);
        }

        const float amp = env * v.velocity * params_.level;
        l += fastTanh(voiceL * amp);
        r += fastTanh(voiceR * amp);
    }

    outL = sanitize(l);
    outR = sanitize(r);
}

} // namespace rep
