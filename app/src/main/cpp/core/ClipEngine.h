#pragma once

// ReplicantSynth clip engine: an 8x8 Ableton-style clip launcher with per-pad
// instruments (Replicant303 / ReplicantWave / Replicant909 / samples), a
// ReplicantFX insert per pad, quantized launching, scene launching and capture.

#include "Acid303.h"
#include "DrumKit909.h"
#include "Effects.h"
#include "Recorder.h"
#include "Wavetable.h"

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace rep {

enum class ClipKind : int {
    Empty = 0,
    Acid = 1,
    Wave = 2,
    Drums = 3,
    Sample = 4,
};

enum class ClipState : int {
    Stopped = 0,
    QueuedStart = 1,
    Playing = 2,
    QueuedStop = 3,
};

struct SampleData {
    std::vector<float> stereo;
    int channels = 2;
    int sampleRate = 48000;
    int frames() const { return channels > 0 ? static_cast<int>(stereo.size()) / channels : 0; }
};

class ClipEngine {
public:
    static constexpr int kPads = 64;
    static constexpr int kRows = 8;
    static constexpr int kCols = 8;
    static constexpr int kSteps = 16;
    static constexpr int kDrumVoices = 11;
    static constexpr int kParams = 32;
    static constexpr int kMaxMidiNotes = 96;

    // Parameter layout inside the 32-float clip block.
    // Acid: 0 wave, 1 cutoff, 2 resonance, 3 envMod, 4 decay, 5 accent,
    //       6 dist type, 7 dist drive, 8 dist mix, 9 dist tone, 10 volume.
    // Wave: 0 bank, 1 position, 2 unison, 3 detune, 4 spread, 5 cutoff,
    //       6 resonance, 7 drive, 8 attack, 9 decay, 10 sustain, 11 release,
    //       12 sub, 13 noise, 14 level, 15 filter mode, 16 chord.
    // Drums: 0 tune, 1 decay, 2 snappy, 3 tone, 4 accent, 5 level,
    //        6 cutoff, 7 resonance.
    // FX params always live at 24..29; 30 = enabled.
    static constexpr int kFxParamBase = 24;
    static constexpr int kFxEnabledIndex = 30;

    ClipEngine();
    ~ClipEngine();

    void prepare(double sampleRate, int maxBlockFrames);
    void process(float* interleavedStereo, int frames);
    void renderSilent(int frames);

    // Transport.
    void setPlaying(bool playing) noexcept;
    bool isPlaying() const noexcept { return playing_.load(std::memory_order_relaxed); }
    void setTempo(float bpm) noexcept;
    float tempo() const noexcept { return tempo_.load(std::memory_order_relaxed); }
    void setQuantize(int steps) noexcept;
    int quantize() const noexcept { return quantize_.load(std::memory_order_relaxed); }
    void setSwing(float swing) noexcept;
    float swing() const noexcept { return swing_.load(std::memory_order_relaxed); }
    void setMasterGain(float gain) noexcept;
    float masterGain() const noexcept { return masterGain_.load(std::memory_order_relaxed); }

    // Clip configuration (safe from UI/MIDI threads).
    void configureClip(int pad, int kind, int root, int length, float gain, float pan,
                       int fxType, bool fxEnabled, bool sampleLoop, bool mute, bool solo,
                       const float* params, int paramCount);
    void updateParams(int pad, const float* params, int paramCount, float gain, float pan,
                      bool mute, bool solo, bool fxEnabled);
    void setStep(int pad, int step, bool gate, int note, int velocity, bool accent, bool slide);
    void setDrumStep(int pad, int voice, int step, bool on, int velocity);
    // Free MIDI notes for the wavetable engine: start/length are 16th-note steps.
    void setMidiNote(int pad, bool on, int note, int start, int length, int velocity);
    void clearMidiNotes(int pad);
    // Audition a note without touching the clip pattern (APC64 key input).
    void previewNote(int pad, bool on, int note, int velocity);
    void ensureFx(int pad);
    void clearClip(int pad);

    // Performance.
    void launchPad(int pad);
    void stopPad(int pad);
    void launchScene(int row);
    void stopScene(int row);
    void stopAll();
    void panic();

    // Samples (UI thread publishes whole buffers; audio only reads pointers).
    void loadSample(int pad, const float* interleaved, int frames, int channels, int sampleRate);
    void clearSample(int pad);

    // Telemetry.
    void getPadStates(int* out, int count) const;
    void getPadMeters(float* out, int count) const;
    float masterMeter() const noexcept { return masterMeter_.load(std::memory_order_relaxed); }
    void getScope(float* out, int count) const;
    int sampleRate() const noexcept { return static_cast<int>(sampleRate_); }
    int maxBlockFrames() const noexcept { return maxBlockFrames_; }

    // Capture.
    void setOfflineRendering(bool offline) noexcept { offlineMode_ = offline; }
    bool startMasterRecording(const std::string& path);
    bool startPadRecording(const std::string& path, int pad);
    void stopRecording();
    bool recording() const noexcept { return masterRec_.active() || padRec_.active(); }
    int64_t masterRecordedFrames() const noexcept { return masterRec_.frames(); }
    int64_t padRecordedFrames() const noexcept { return padRec_.frames(); }
    int64_t droppedRecordFrames() const noexcept {
        return masterRec_.dropped() + padRec_.dropped();
    }

private:
    struct ClipStep {
        bool gate = false;
        uint8_t note = 60;
        uint8_t velocity = 100;
        bool accent = false;
        bool slide = false;
    };

    struct MidiNote {
        bool active = false;
        uint8_t note = 60;
        uint8_t velocity = 100;
        uint8_t start = 0;
        uint8_t length = 1;
    };

    struct Clip {
        int kind = static_cast<int>(ClipKind::Empty);
        int root = 60;
        int length = kSteps;
        float gain = 0.8f;
        float pan = 0.0f;
        bool mute = false;
        bool solo = false;
        int fxType = 0;
        bool fxEnabled = false;
        bool sampleLoop = true;
        float params[kParams] = {};

        ClipStep steps[kSteps] = {};
        MidiNote midi[kMaxMidiNotes] = {};
        uint16_t drumMask[kDrumVoices] = {};
        uint8_t drumVel[kDrumVoices][kSteps] = {};

        // Audio-thread state.
        int state = static_cast<int>(ClipState::Stopped);
        int phase = 0;
        int64_t pendingStart = -1;
        int64_t pendingStop = -1;
        int lastNote = -1;
        double samplePos = 0.0;
        double tailSamples = 0.0;
        float lastSamplePeak = 0.0f;

        std::unique_ptr<Acid303> acid;
        std::unique_ptr<WaveSynth> wave;
        std::unique_ptr<DrumKit909> drums;
    };

    enum CommandType : uint8_t {
        kCmdConfigure = 0,
        kCmdParams = 1,
        kCmdStep = 2,
        kCmdDrumStep = 3,
        kCmdLaunch = 4,
        kCmdStop = 5,
        kCmdLaunchScene = 6,
        kCmdStopScene = 7,
        kCmdStopAll = 8,
        kCmdClear = 9,
        kCmdPanic = 10,
        kCmdMidiNote = 11,
        kCmdClearMidi = 12,
        kCmdPreview = 13,
    };

    struct Command {
        uint8_t type = 0;
        uint8_t pad = 0;
        uint8_t kind = 0;
        uint8_t flags = 0;
        int16_t root = 60;
        int16_t length = kSteps;
        int16_t step = 0;
        int16_t voice = 0;
        int16_t note = 60;
        int16_t velocity = 100;
        int16_t fxType = 0;
        uint8_t fxEnabled = 0;
        float gain = 0.8f;
        float pan = 0.0f;
        float params[kParams] = {};
    };

    class CommandQueue {
    public:
        bool push(const Command& command);
        bool pop(Command& command);
    private:
        static constexpr uint32_t kCapacity = 16384;
        std::array<Command, kCapacity> items_{};
        std::atomic<uint32_t> head_{0};
        std::atomic<uint32_t> tail_{0};
        std::mutex pushMutex_;
    };

    void push(const Command& command);
    void drainCommands();
    void applyConfigure(Clip& clip, int pad, const Command& command);
    void applyParams(Clip& clip, int pad, const Command& command);
    void applyMidiNote(Clip& clip, const Command& command);
    void applyVoiceParams(Clip& clip);
    void updatePadState(int pad);

    int64_t nextBoundary(int quantSteps) const;
    void startTransport();
    void stopTransport();
    void onStepBoundary();
    void triggerStep(Clip& clip, int step);
    void triggerMidiStep(Clip& clip, int step);
    void startClip(Clip& clip);
    void stopClip(Clip& clip, bool hard);
    void renderChunk(float* out, int frames, float* padOut);
    void captureBlock(const float* out, int frames);

    double stepDurationFor(int64_t step) const;

    double sampleRate_ = 48000.0;
    int maxBlockFrames_ = 512;
    bool prepared_ = false;

    std::atomic<bool> playing_{false};
    std::atomic<float> tempo_{120.0f};
    std::atomic<int> quantize_{16};
    std::atomic<float> swing_{0.0f};
    std::atomic<float> masterGain_{0.85f};

    bool transportRunning_ = false;
    int64_t absoluteStep_ = 0;
    double samplesIntoStep_ = 0.0;
    double samplesPerStep_ = 6000.0;
    double stepDuration_ = 6000.0;

    std::array<Clip, kPads> clips_{};
    CommandQueue commands_;

    std::array<std::atomic<Effect*>, kPads> fx_{};
    std::vector<std::unique_ptr<Effect>> fxPool_;
    std::mutex fxMutex_;

    std::array<std::atomic<SampleData*>, kPads> samples_{};
    std::vector<std::shared_ptr<SampleData>> sampleHold_;
    std::mutex sampleMutex_;

    std::array<std::atomic<int>, kPads> padState_{};
    std::array<std::atomic<float>, kPads> padMeter_{};
    std::array<float, kPads> padPeakBlock_{};
    std::atomic<float> masterMeter_{0.0f};

    std::array<float, 512> scope_{};
    std::atomic<uint32_t> scopeWrite_{0};
    uint32_t scopeCounter_ = 0;

    std::vector<float> silentBuffer_;
    std::vector<float> padCapture_;
    std::vector<float> masterCapture_;

    Recorder masterRec_;
    Recorder padRec_;
    int padRecIndex_ = -1;
    bool offlineMode_ = false;
};

} // namespace rep
