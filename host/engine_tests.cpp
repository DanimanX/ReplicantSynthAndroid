// Host-side regression suite for the ReplicantSynth DSP core.
// Build:  cmake -S host -B build-host && cmake --build build-host -j
// Run:    ./build-host/engine_tests

#include "ClipEngine.h"
#include "Effects.h"
#include "Wavetable.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <memory>
#include <string>
#include <vector>

namespace {

int gChecks = 0;
int gFailures = 0;

void check(bool condition, const char* what) {
    ++gChecks;
    if (!condition) {
        ++gFailures;
        std::printf("FAIL  %s\n", what);
    }
}

struct Peaks {
    float peak = 0.0f;
    float energy = 0.0f;
    bool finite = true;
};

Peaks render(rep::ClipEngine& engine, double seconds, int block = 256) {
    const int frames = static_cast<int>(engine.sampleRate() * seconds);
    std::vector<float> buffer(static_cast<size_t>(block) * 2, 0.0f);
    Peaks peaks;
    int done = 0;
    while (done < frames) {
        const int n = std::min(block, frames - done);
        engine.process(buffer.data(), n);
        for (int i = 0; i < n; ++i) {
            const float l = buffer[static_cast<size_t>(i) * 2];
            const float r = buffer[static_cast<size_t>(i) * 2 + 1];
            if (!std::isfinite(l) || !std::isfinite(r)) peaks.finite = false;
            peaks.peak = std::max(peaks.peak, std::max(std::fabs(l), std::fabs(r)));
            peaks.energy += l * l + r * r;
        }
        done += n;
    }
    return peaks;
}

void acidParams(float* p) {
    std::memset(p, 0, sizeof(float) * 32);
    p[0] = 0.0f;      // saw
    p[1] = 420.0f;    // cutoff
    p[2] = 0.72f;     // resonance
    p[3] = 0.68f;     // env mod
    p[4] = 0.55f;     // decay
    p[5] = 0.82f;     // accent
    p[6] = 0.0f;      // poco rat
    p[7] = 0.55f;     // drive
    p[8] = 0.55f;     // mix
    p[9] = 0.62f;     // tone
    p[10] = 0.9f;     // volume
}

void waveParams(float* p) {
    std::memset(p, 0, sizeof(float) * 32);
    p[0] = 4.0f;      // Cyber Acid bank
    p[1] = 0.25f;     // position
    p[2] = 3.0f;      // unison
    p[3] = 14.0f;     // detune cents
    p[4] = 0.5f;      // spread
    p[5] = 1800.0f;   // cutoff
    p[6] = 0.7f;      // resonance
    p[7] = 1.2f;      // drive
    p[8] = 0.005f;    // attack
    p[9] = 0.4f;      // decay
    p[10] = 0.6f;     // sustain
    p[11] = 0.5f;     // release
    p[12] = 0.2f;     // sub
    p[13] = 0.0f;     // noise
    p[14] = 0.8f;     // level
    p[15] = 0.0f;     // LP24
    p[16] = 2.0f;     // minor chord
}

void drumParams(float* p) {
    std::memset(p, 0, sizeof(float) * 32);
    p[0] = 0.5f;      // tune
    p[1] = 0.5f;      // decay
    p[2] = 0.5f;      // snappy
    p[3] = 0.5f;      // tone
    p[4] = 0.7f;      // accent
    p[5] = 0.9f;      // level
    p[6] = 18000.0f;  // cutoff
    p[7] = 0.6f;      // resonance
}

void configureAcid(rep::ClipEngine& engine, int pad, uint32_t seed) {
    float p[32];
    acidParams(p);
    engine.configureClip(pad, 1, 36, 16, 0.8f, 0.0f, 0, false, true, false, false, p, 32);
    for (int i = 0; i < 16; ++i) {
        const bool gate = (i % 4 == 0) || (i == 6) || (i == 11);
        const int note = 36 + ((seed + static_cast<uint32_t>(i)) % 12);
        engine.setStep(pad, i, gate, note, (i % 8 == 0) ? 118 : 92, i % 8 == 0, i == 6);
    }
}

void configureWave(rep::ClipEngine& engine, int pad) {
    float p[32];
    waveParams(p);
    engine.configureClip(pad, 2, 48, 16, 0.7f, 0.0f, 0, false, true, false, false, p, 32);
    for (int i = 0; i < 16; ++i) {
        engine.setStep(pad, i, i % 4 == 0, 48 + (i % 3) * 3, 100, false, false);
    }
}

void configureDrums(rep::ClipEngine& engine, int pad) {
    float p[32];
    drumParams(p);
    engine.configureClip(pad, 3, 36, 16, 0.85f, 0.0f, 0, false, true, false, false, p, 32);
    for (int i = 0; i < 16; ++i) {
        engine.setDrumStep(pad, 0, i, i % 4 == 0, 120);
        engine.setDrumStep(pad, 1, i, i == 4 || i == 12, 110);
        engine.setDrumStep(pad, 7, i, i % 2 == 0, 80);
        engine.setDrumStep(pad, 8, i, i == 14, 85);
    }
}

void testBanks() {
    const auto& banks = rep::sharedWavetableBanks();
    check(banks.ready(), "wavetable banks built");
    float peak = 0.0f;
    for (int bank = 0; bank < rep::WavetableBanks::kCount; ++bank) {
        for (int f = 0; f < rep::WavetableBanks::kFrames; ++f) {
            const float pos = static_cast<float>(f) /
                              static_cast<float>(rep::WavetableBanks::kFrames - 1);
            for (int i = 0; i < 256; ++i) {
                const float s = banks.sample(bank, pos, 2, static_cast<float>(i) / 256.0f);
                check(std::isfinite(s), "wavetable sample finite");
                peak = std::max(peak, std::fabs(s));
            }
        }
    }
    check(peak > 0.4f && peak < 1.2f, "wavetable peak in range");
}

void testAcid() {
    rep::ClipEngine engine;
    configureAcid(engine, 0, 0);
    engine.setTempo(126.0f);
    engine.setQuantize(0);
    engine.launchPad(0);
    const Peaks peaks = render(engine, 3.0);
    check(peaks.finite, "acid output finite");
    check(peaks.peak > 0.02f, "acid output audible");
    check(peaks.peak <= 1.01f, "acid master bounded");
    check(peaks.energy > 0.5f, "acid energy");
    int states[64];
    engine.getPadStates(states, 64);
    check(((states[0] >> 4) & 0xF) == static_cast<int>(rep::ClipState::Playing),
          "acid pad playing");
    float meters[64];
    engine.getPadMeters(meters, 64);
    check(meters[0] >= 0.0f, "acid meter valid");
    engine.panic();
    render(engine, 0.1);
    engine.getPadStates(states, 64);
    check(((states[0] >> 4) & 0xF) == static_cast<int>(rep::ClipState::Stopped),
          "panic stops acid");
}

void testDrumHeadroom() {
    // Regression for the 909 bus: the kit used to re-tanh the summed voices,
    // which squashed every transient (crest factor ~1.7) and sounded saturated.
    rep::ClipEngine engine;
    configureDrums(engine, 0);
    engine.setQuantize(0);
    engine.launchPad(0);
    const double seconds = 4.0;
    const Peaks peaks = render(engine, seconds);
    const double frames = engine.sampleRate() * seconds;
    const double rms = std::sqrt(peaks.energy / (2.0 * frames));
    check(peaks.finite, "909 headroom finite");
    check(peaks.peak < 0.95f, "909 headroom below limiter");
    check(rms > 0.04, "909 headroom audible");
    check(peaks.peak / std::max(1.0e-6, rms) > 2.6, "909 keeps transients (crest factor)");

    rep::DrumKit909 kit;
    kit.prepare(48000.0);
    kit.setParams(0.5f, 0.5f, 0.5f, 0.5f, 1.0f, 1.2f, 18000.0f, 0.6f);
    for (int v = 0; v < rep::DrumKit909::kVoices; ++v) kit.trigger(v, 1.0f, 1.0f);
    float peak = 0.0f;
    for (int i = 0; i < 48000; ++i) {
        float l = 0.0f;
        float r = 0.0f;
        kit.render(l, r);
        peak = std::max(peak, std::max(std::fabs(l), std::fabs(r)));
    }
    check(peak < 0.99f, "all eleven 909 voices stay under full scale");
}

void testMidiNotes() {
    // A long note must sustain across steps instead of being cut every step.
    rep::ClipEngine engine;
    float p[32];
    waveParams(p);
    p[8] = 0.002f;   // attack
    p[10] = 1.0f;    // sustain
    p[11] = 0.005f;  // release
    engine.configureClip(0, 2, 60, 16, 0.8f, 0.0f, 0, false, true, false, false, p, 32);
    for (int i = 0; i < 16; ++i) engine.setStep(0, i, false, 60, 100, false, false);
    engine.setMidiNote(0, true, 60, 0, 16, 110);
    engine.setMidiNote(0, true, 64, 0, 16, 100);
    engine.setTempo(120.0f);
    engine.setQuantize(0);
    engine.launchPad(0);
    render(engine, 0.5); // step 4 of 16

    float meters[64];
    engine.getPadMeters(meters, 64);
    check(meters[0] > 0.01f, "MIDI note sustains across steps");

    // A note shorter than the clip releases on its end step. ClipEngine owns a
    // 16k-command queue, so keep extra instances on the heap instead of
    // stacking several multi-megabyte engines in one test frame.
    auto shortNote = std::make_unique<rep::ClipEngine>();
    shortNote->configureClip(1, 2, 60, 16, 0.8f, 0.0f, 0, false, true, false, false, p, 32);
    for (int i = 0; i < 16; ++i) shortNote->setStep(1, i, false, 60, 100, false, false);
    shortNote->setMidiNote(1, true, 60, 0, 2, 110);
    shortNote->setTempo(120.0f);
    shortNote->setQuantize(0);
    shortNote->launchPad(1);
    render(*shortNote, 0.75); // well past the 2-step gate
    shortNote->getPadMeters(meters, 64);
    check(meters[1] < 0.01f, "MIDI note releases at its end step");

    // Per-key release: holding E while releasing C must keep E sounding.
    rep::WaveSynth synth;
    rep::WaveParams wp;
    wp.attack = 0.001f;
    wp.sustain = 1.0f;
    wp.release = 0.01f;
    wp.level = 0.8f;
    synth.prepare(48000.0);
    synth.setParams(wp);
    synth.noteOn(60, 100);
    synth.noteOn(64, 100);
    float l = 0.0f;
    float r = 0.0f;
    for (int i = 0; i < 512; ++i) synth.render(l, r);
    synth.noteOff(60);
    float late = 0.0f;
    for (int i = 0; i < 4096; ++i) {
        synth.render(l, r);
        if (i > 3800) late = std::max(late, std::max(std::fabs(l), std::fabs(r)));
    }
    check(late > 0.001f, "noteOff only releases its own key");
    synth.noteOff(64);

    // Auditioning a stopped clip must reach the output.
    auto preview = std::make_unique<rep::ClipEngine>();
    preview->configureClip(2, 2, 60, 16, 0.8f, 0.0f, 0, false, true, false, false, p, 32);
    preview->previewNote(2, true, 60, 110);
    render(*preview, 0.1);
    preview->getPadMeters(meters, 64);
    check(meters[2] > 0.001f, "stopped clip audition is audible");
    preview->previewNote(2, false, 60, 0);
    render(*preview, 0.1);
}

void testDrums() {
    rep::ClipEngine engine;
    configureDrums(engine, 1);
    engine.setQuantize(0);
    engine.launchPad(1);
    const Peaks peaks = render(engine, 2.0);
    check(peaks.finite, "909 output finite");
    check(peaks.peak > 0.05f, "909 output audible");
    check(peaks.peak <= 1.01f, "909 master bounded");
    float meters[64];
    engine.getPadMeters(meters, 64);
    check(meters[1] > 0.01f, "909 meter shows activity");

    // All eleven voices should render without producing non-finite samples.
    rep::DrumKit909 kit;
    kit.prepare(48000.0);
    for (int v = 0; v < rep::DrumKit909::kVoices; ++v) {
        kit.trigger(v, 1.0f, 1.0f);
        float l = 0.0f, r = 0.0f;
        for (int i = 0; i < 256; ++i) {
            kit.render(l, r);
            check(std::isfinite(l) && std::isfinite(r), "909 voice finite");
        }
    }
}

void testWave() {
    rep::ClipEngine engine;
    configureWave(engine, 2);
    engine.setQuantize(0);
    engine.launchPad(2);
    const Peaks peaks = render(engine, 2.0);
    check(peaks.finite, "wave output finite");
    check(peaks.peak > 0.01f, "wave output audible");
    check(peaks.peak <= 1.01f, "wave master bounded");
    float meters[64];
    engine.getPadMeters(meters, 64);
    check(meters[2] > 0.001f, "wave meter shows activity");
}

void testSample() {
    rep::ClipEngine engine;
    const int sampleRate = 48000;
    const int frames = sampleRate; // 1 second
    std::vector<float> sine(static_cast<size_t>(frames) * 2);
    for (int i = 0; i < frames; ++i) {
        const float s = std::sin(2.0f * 3.14159265358979f * 220.0f *
                                 static_cast<float>(i) / static_cast<float>(sampleRate)) *
                        0.6f;
        sine[static_cast<size_t>(i) * 2] = s;
        sine[static_cast<size_t>(i) * 2 + 1] = s;
    }
    engine.loadSample(3, sine.data(), frames, 2, sampleRate);
    float p[32];
    std::memset(p, 0, sizeof(p));
    p[0] = 0.9f;
    p[1] = 0.0f;
    engine.configureClip(3, 4, 60, 16, 0.9f, 0.0f, 0, false, true, false, false, p, 32);
    engine.setQuantize(0);
    engine.launchPad(3);
    const Peaks peaks = render(engine, 0.75);
    check(peaks.finite, "sample output finite");
    check(peaks.peak > 0.05f, "sample output audible");
    float meters[64];
    engine.getPadMeters(meters, 64);
    check(meters[3] > 0.01f, "sample meter shows activity");
}

void testFxCatalog() {
    for (int id = 0; id < rep::kFxCount; ++id) {
        const rep::FxInfo& info = rep::fxInfo(id);
        check(info.name != nullptr && info.name[0] != '\0', "fx name present");
        check(info.code != nullptr && info.code[0] != '\0', "fx code present");
        for (int i = 0; i < 6; ++i) {
            check(info.labels[i] != nullptr && info.labels[i][0] != '\0',
                  "fx label present");
        }
    }

    rep::ClipEngine engine;
    engine.ensureFx(4);
    configureWave(engine, 4);
    engine.setQuantize(0);

    for (int id = 0; id < rep::kFxCount; ++id) {
        float p[32];
        waveParams(p);
        const rep::FxInfo& info = rep::fxInfo(id);
        for (int i = 0; i < 6; ++i) p[24 + i] = info.defaults[i];
        p[30] = 1.0f;
        engine.configureClip(4, 2, 48, 16, 0.55f, 0.0f, id, true, true, false, false, p, 32);
        engine.launchPad(4);
        const Peaks peaks = render(engine, 0.12);
        check(peaks.finite, "fx output finite");
        check(peaks.peak <= 1.01f, "fx master bounded");
        engine.stopPad(4);
        render(engine, 0.02);
    }
}

void testQuantize() {
    rep::ClipEngine engine;
    configureAcid(engine, 0, 1);
    configureAcid(engine, 8, 2);
    engine.setTempo(120.0f);
    engine.setQuantize(16);
    engine.setPlaying(true);
    engine.launchPad(0);
    render(engine, 0.1);
    int states[64];
    engine.getPadStates(states, 64);
    check(((states[0] >> 4) & 0xF) == static_cast<int>(rep::ClipState::Playing),
          "quantized pad playing");
    engine.launchPad(8);
    render(engine, 0.05);
    engine.getPadStates(states, 64);
    check(((states[8] >> 4) & 0xF) == static_cast<int>(rep::ClipState::QueuedStart),
          "second pad queued");
    render(engine, 4.2); // one bar at 120 BPM
    engine.getPadStates(states, 64);
    check(((states[8] >> 4) & 0xF) == static_cast<int>(rep::ClipState::Playing),
          "queued pad starts on the bar");
}

void testScene() {
    rep::ClipEngine engine;
    for (int i = 0; i < 3; ++i) configureAcid(engine, i, static_cast<uint32_t>(i));
    for (int i = 8; i < 11; ++i) configureWave(engine, i);
    engine.setTempo(120.0f);
    engine.setQuantize(16);
    engine.setPlaying(true);
    engine.launchScene(0);
    render(engine, 0.1);
    int states[64];
    engine.getPadStates(states, 64);
    check(((states[0] >> 4) & 0xF) == static_cast<int>(rep::ClipState::Playing),
          "scene 0 launches row 0");
    check(((states[8] >> 4) & 0xF) == static_cast<int>(rep::ClipState::Stopped),
          "scene 0 leaves row 1 stopped");
    engine.launchScene(1);
    render(engine, 4.3);
    engine.getPadStates(states, 64);
    check(((states[8] >> 4) & 0xF) == static_cast<int>(rep::ClipState::Playing),
          "scene 1 launches row 1");
    check(((states[0] >> 4) & 0xF) == static_cast<int>(rep::ClipState::Stopped),
          "scene 1 stops row 0");
}

void testRecording() {
    rep::ClipEngine engine;
    configureDrums(engine, 5);
    engine.setQuantize(0);
    const std::string path = "/tmp/repsynth_recording_test.wav";
    std::remove(path.c_str());
    check(engine.startMasterRecording(path), "master recording starts");
    engine.launchPad(5);
    render(engine, 1.0);
    engine.stopRecording();
    check(engine.masterRecordedFrames() > 40000, "master recording has frames");

    std::ifstream file(path, std::ios::binary);
    check(file.good(), "recording file exists");
    char header[44] = {};
    file.read(header, 44);
    check(std::memcmp(header, "RIFF", 4) == 0, "wav RIFF header");
    check(std::memcmp(header + 8, "WAVE", 4) == 0, "wav WAVE header");
    uint32_t dataSize = 0;
    std::memcpy(&dataSize, header + 40, 4);
    check(dataSize > 100000, "wav data size");
    const uint16_t bits = static_cast<uint16_t>(header[34]) |
                          (static_cast<uint16_t>(header[35]) << 8);
    check(bits == 24, "wav is 24-bit");
}

void testStopAndPrepare() {
    rep::ClipEngine engine;
    configureAcid(engine, 0, 3);
    engine.setQuantize(0);
    engine.launchPad(0);
    render(engine, 0.5);
    engine.setPlaying(false);
    const Peaks stopped = render(engine, 0.1);
    check(stopped.peak < 1.0e-6f, "transport stop silences the master");
    engine.prepare(44100.0, 256);
    configureWave(engine, 1);
    engine.launchPad(1);
    const Peaks resampled = render(engine, 0.5);
    check(resampled.finite, "44.1 kHz render finite");
    check(resampled.peak > 0.001f, "44.1 kHz render audible");
}

void testPeakGuard() {
    rep::ClipEngine engine;
    for (int i = 0; i < 8; ++i) configureDrums(engine, i);
    engine.setQuantize(0);
    for (int i = 0; i < 8; ++i) engine.launchPad(i);
    const Peaks peaks = render(engine, 2.0);
    check(peaks.finite, "eight drum clips finite");
    check(peaks.peak <= 1.01f, "eight drum clips bounded");
}

} // namespace

int main() {
    std::printf("ReplicantSynth host suite\n");
    testBanks();
    testAcid();
    testDrums();
    testDrumHeadroom();
    testMidiNotes();
    testWave();
    testSample();
    testFxCatalog();
    testQuantize();
    testScene();
    testRecording();
    testStopAndPrepare();
    testPeakGuard();
    std::printf("%d checks, %d failures\n", gChecks, gFailures);
    return gFailures == 0 ? 0 : 1;
}
