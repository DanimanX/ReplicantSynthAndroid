// Renders the ReplicantSynth demo track with the real clip engine and writes a
// 24-bit WAV through the same capture path the app uses.
//
//   ./build-host/render_demo assets/replicantsynth_demo.wav
//
// The arrangement uses every engine (303, Wave, 909) plus an imported-style
// sample, scene launching and a ReplicantFX insert per pad.

#include "ClipEngine.h"
#include "Effects.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace {

constexpr float kPi = 3.14159265358979323846f;

void acidParams(float* p) {
    std::memset(p, 0, sizeof(float) * 32);
    p[0] = 0.0f;
    p[1] = 420.0f;
    p[2] = 0.72f;
    p[3] = 0.68f;
    p[4] = 0.55f;
    p[5] = 0.82f;
    p[6] = 0.0f;
    p[7] = 0.55f;
    p[8] = 0.55f;
    p[9] = 0.62f;
    p[10] = 0.9f;
}

void waveParams(float* p, int bank, int chord, float cutoff) {
    std::memset(p, 0, sizeof(float) * 32);
    p[0] = static_cast<float>(bank);
    p[1] = 0.3f;
    p[2] = 4.0f;
    p[3] = 16.0f;
    p[4] = 0.55f;
    p[5] = cutoff;
    p[6] = 0.7f;
    p[7] = 1.3f;
    p[8] = 0.02f;
    p[9] = 0.5f;
    p[10] = 0.65f;
    p[11] = 0.55f;
    p[12] = 0.2f;
    p[13] = 0.02f;
    p[14] = 0.72f;
    p[15] = 0.0f;
    p[16] = static_cast<float>(chord);
}

void drumParams(float* p) {
    std::memset(p, 0, sizeof(float) * 32);
    p[0] = 0.52f;
    p[1] = 0.5f;
    p[2] = 0.52f;
    p[3] = 0.55f;
    p[4] = 0.7f;
    p[5] = 0.9f;
    p[6] = 18000.0f;
    p[7] = 0.6f;
}

void addFx(float* p, int fxType, int variant) {
    const rep::FxInfo& info = rep::fxInfo(fxType);
    for (int i = 0; i < 6; ++i) p[24 + i] = info.defaults[i];
    p[24] = std::min(1.0f, p[24] + 0.05f * static_cast<float>(variant));
    p[30] = 1.0f;
}

void clearSteps(rep::ClipEngine& engine, int pad) {
    for (int i = 0; i < 16; ++i) engine.setStep(pad, i, false, 48, 1, false, false);
}

void acidClip(rep::ClipEngine& engine, int pad, int root, int fxType, int variant) {
    float p[32];
    acidParams(p);
    addFx(p, fxType, variant);
    engine.ensureFx(pad);
    engine.configureClip(pad, 1, root, 16, 0.78f, 0.0f, fxType, true, true, false, false, p, 32);
    clearSteps(engine, pad);
    static const int notes[8] = {0, 0, 12, 3, 7, 10, 12, 7};
    static const int gates[8] = {0, 3, 6, 8, 10, 11, 14, 15};
    for (int i = 0; i < 8; ++i) {
        const int step = gates[i];
        const bool accent = (i == 0 || i == 3 || i == 5);
        engine.setStep(pad, step, true, root + notes[i], accent ? 118 : 94, accent, i == 6);
    }
}

void drumClip(rep::ClipEngine& engine, int pad, int fxType, bool busy) {
    float p[32];
    drumParams(p);
    addFx(p, fxType, 0);
    engine.ensureFx(pad);
    engine.configureClip(pad, 3, 36, 16, 0.85f, 0.0f, fxType, true, true, false, false, p, 32);
    for (int v = 0; v < 11; ++v) {
        for (int s = 0; s < 16; ++s) engine.setDrumStep(pad, v, s, false, 1);
    }
    for (int i = 0; i < 16; ++i) {
        engine.setDrumStep(pad, 0, i, i % 4 == 0, 122);
        engine.setDrumStep(pad, 1, i, i == 4 || i == 12, 112);
        engine.setDrumStep(pad, 7, i, i % 2 == 0, i % 4 == 0 ? 92 : 70);
        if (busy) {
            engine.setDrumStep(pad, 8, i, i == 6 || i == 14, 88);
            engine.setDrumStep(pad, 6, i, i == 11, 96);
            engine.setDrumStep(pad, 9, i, i == 0, 90);
        }
    }
}

void waveClip(rep::ClipEngine& engine, int pad, int root, int bank, int chord,
              int fxType, float cutoff, float pan) {
    float p[32];
    waveParams(p, bank, chord, cutoff);
    addFx(p, fxType, 0);
    engine.ensureFx(pad);
    engine.configureClip(pad, 2, root, 16, 0.62f, pan, fxType, true, true, false, false, p, 32);
    clearSteps(engine, pad);
    static const int steps[5] = {0, 3, 6, 10, 14};
    for (int i = 0; i < 5; ++i) {
        engine.setStep(pad, steps[i], true, root + (i % 2) * 7, 100, false, false);
    }
}

void sampleClip(rep::ClipEngine& engine, int pad, int fxType, float pan) {
    float p[32];
    std::memset(p, 0, sizeof(p));
    p[0] = 0.75f;
    p[1] = 0.0f;
    addFx(p, fxType, 0);
    engine.ensureFx(pad);
    engine.configureClip(pad, 4, 60, 16, 0.75f, pan, fxType, true, true, false, false, p, 32);
}

std::vector<float> makeVocalStab(int sampleRate) {
    const int frames = sampleRate * 3;
    std::vector<float> data(static_cast<size_t>(frames) * 2, 0.0f);
    const float notes[4] = {174.61f, 220.0f, 261.63f, 349.23f};
    for (int i = 0; i < frames; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(sampleRate);
        float s = 0.0f;
        for (int n = 0; n < 4; ++n) {
            const float phase = 2.0f * kPi * notes[n] * t +
                                0.35f * std::sin(2.0f * kPi * 5.2f * t);
            s += std::sin(phase) * (0.32f / static_cast<float>(n + 1));
            s += std::sin(phase * 2.0f) * 0.07f;
        }
        const float env = std::exp(-t * 1.9f) * std::min(1.0f, t * 90.0f);
        const float air = std::sin(2.0f * kPi * 5200.0f * t) * 0.02f * std::exp(-t * 8.0f);
        const float left = (s + air) * env * 0.75f;
        const float right = (s - air) * env * 0.75f;
        data[static_cast<size_t>(i) * 2] = std::tanh(left);
        data[static_cast<size_t>(i) * 2 + 1] = std::tanh(right);
    }
    return data;
}

} // namespace

int main(int argc, char** argv) {
    const std::string path = argc > 1 ? argv[1] : "assets/replicantsynth_demo.wav";
    const double bpm = 126.0;
    const double sampleRate = 48000.0;

    rep::ClipEngine engine;
    engine.prepare(sampleRate, 512);
    engine.setTempo(static_cast<float>(bpm));
    engine.setQuantize(16);
    engine.setMasterGain(0.85f);

    // Scene 0: the opening acid / drums / pad trio.
    acidClip(engine, 0, 36, 1, 0);    // Filter+Drive
    drumClip(engine, 1, 3, false);    // Isolator
    waveClip(engine, 2, 48, 0, 0, 15, 2100.0f, 0.0f); // JUNO Chorus
    // Scene 1: a busier variation.
    acidClip(engine, 8, 41, 5, 1);    // Phaser
    drumClip(engine, 9, 12, true);    // Resonator
    waveClip(engine, 10, 53, 4, 2, 18, 2600.0f, -0.2f); // Sync Delay
    // Scene 2: sample + drums.
    sampleClip(engine, 16, 10, -0.15f); // Reverb
    acidClip(engine, 17, 36, 34, 2);    // Super Filter
    drumClip(engine, 18, 31, true);     // 404 VinylSim
    // Scene 3: wavetable lead.
    waveClip(engine, 24, 55, 2, 3, 6, 3200.0f, 0.2f); // Flanger
    acidClip(engine, 25, 43, 8, 3);                   // Distortion
    drumClip(engine, 26, 2, true);                    // TimeCtrlDly
    // Scene 4: breakdown.
    waveClip(engine, 32, 48, 3, 4, 44, 1700.0f, -0.25f); // SX Reverb
    acidClip(engine, 33, 36, 33, 4);                     // Zan-Zou
    // Scene 5: the full stack.
    acidClip(engine, 40, 48, 13, 5);  // DJFX Looper
    drumClip(engine, 41, 8, true);    // Distortion
    waveClip(engine, 42, 60, 7, 3, 14, 3600.0f, 0.25f); // Chorus
    sampleClip(engine, 43, 47, 0.15f);                  // Cloud Delay

    const std::vector<float> vocal = makeVocalStab(static_cast<int>(sampleRate));
    engine.loadSample(16, vocal.data(), static_cast<int>(vocal.size() / 2), 2,
                      static_cast<int>(sampleRate));
    engine.loadSample(43, vocal.data(), static_cast<int>(vocal.size() / 2), 2,
                      static_cast<int>(sampleRate));

    engine.setOfflineRendering(true);
    if (!engine.startMasterRecording(path)) {
        std::printf("cannot open %s\n", path.c_str());
        return 1;
    }

    engine.setPlaying(true);
    engine.launchScene(0);

    const double samplesPerBar = sampleRate * 60.0 / bpm * 4.0;
    const int barsPerScene = 4;
    const int scenes = 6;
    const int totalBars = (scenes - 1) * barsPerScene + 1;
    const int64_t totalSamples = static_cast<int64_t>(samplesPerBar * totalBars);
    std::vector<float> block(512 * 2, 0.0f);

    int currentScene = 0;
    int64_t rendered = 0;
    int64_t nextSceneAt = static_cast<int64_t>(samplesPerBar * barsPerScene);

    while (rendered < totalSamples) {
        if (currentScene + 1 < scenes && rendered >= nextSceneAt) {
            ++currentScene;
            engine.launchScene(currentScene);
            nextSceneAt += static_cast<int64_t>(samplesPerBar * barsPerScene);
        }
        const int n = static_cast<int>(
            std::min<int64_t>(512, totalSamples - rendered));
        engine.process(block.data(), n);
        rendered += n;
    }

    engine.setQuantize(0);
    engine.stopAll();
    for (int i = 0; i < 200; ++i) engine.process(block.data(), 256);
    engine.stopRecording();

    std::printf("wrote %s (%lld frames, %lld dropped)\n", path.c_str(),
                static_cast<long long>(engine.masterRecordedFrames()),
                static_cast<long long>(engine.droppedRecordFrames()));
    return engine.masterRecordedFrames() > 0 ? 0 : 1;
}
