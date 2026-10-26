#include "audio/AudioDriver.h"
#include "core/ClipEngine.h"

#include <android/log.h>
#include <jni.h>

#include <cmath>
#include <cstring>
#include <memory>
#include <string>

namespace {

constexpr const char* kTag = "ReplicantSynthJNI";
constexpr const char* kVersion = "ReplicantSynth 0.0.2";

struct EngineHandle {
    rep::ClipEngine engine;
    rep::AudioDriver driver;

    EngineHandle() : driver(&engine) {}
};

EngineHandle* handleOf(jlong handle) {
    return reinterpret_cast<EngineHandle*>(static_cast<intptr_t>(handle));
}

void copyFloats(JNIEnv* env, jfloatArray array, const float* values, int count) {
    if (array == nullptr || values == nullptr) return;
    const jsize size = env->GetArrayLength(array);
    const jsize n = std::min<jsize>(size, count);
    env->SetFloatArrayRegion(array, 0, n, values);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeCreate(JNIEnv*, jobject) {
    auto* handle = new EngineHandle();
    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle));
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeDestroy(JNIEnv*, jobject,
                                                                  jlong handle) {
    delete handleOf(handle);
}

JNIEXPORT jstring JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(kVersion);
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeStartAudio(JNIEnv*, jobject,
                                                                     jlong handle) {
    EngineHandle* h = handleOf(handle);
    return h != nullptr && h->driver.start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeStopAudio(JNIEnv*, jobject,
                                                                    jlong handle) {
    if (EngineHandle* h = handleOf(handle)) h->driver.stop();
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeIsAudioRunning(JNIEnv*, jobject,
                                                                        jlong handle) {
    EngineHandle* h = handleOf(handle);
    return h != nullptr && h->driver.isRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeRenderSilent(JNIEnv*, jobject,
                                                                       jlong handle,
                                                                       jint frames) {
    if (EngineHandle* h = handleOf(handle)) h->driver.renderSilentIfIdle(frames);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeGetAudioStats(
    JNIEnv* env, jobject, jlong handle, jfloatArray floats, jintArray ints) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr) return;
    const rep::AudioDriver::Stats stats = h->driver.stats();
    const float f[4] = {static_cast<float>(stats.latencyMs), stats.cpuLoad, 0.0f, 0.0f};
    const int i[6] = {stats.xRuns, stats.sampleRate, stats.framesPerBurst,
                      stats.bufferFrames, stats.api, stats.sharing};
    copyFloats(env, floats, f, 4);
    if (ints != nullptr) env->SetIntArrayRegion(ints, 0, std::min<jsize>(env->GetArrayLength(ints), 6), i);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetPlaying(JNIEnv*, jobject,
                                                                     jlong handle,
                                                                     jboolean playing) {
    if (EngineHandle* h = handleOf(handle)) h->engine.setPlaying(playing == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeIsPlaying(JNIEnv*, jobject,
                                                                    jlong handle) {
    EngineHandle* h = handleOf(handle);
    return h != nullptr && h->engine.isPlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetTempo(JNIEnv*, jobject,
                                                                   jlong handle,
                                                                   jfloat bpm) {
    if (EngineHandle* h = handleOf(handle)) h->engine.setTempo(bpm);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetQuantize(JNIEnv*, jobject,
                                                                      jlong handle,
                                                                      jint steps) {
    if (EngineHandle* h = handleOf(handle)) h->engine.setQuantize(steps);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetSwing(JNIEnv*, jobject,
                                                                   jlong handle,
                                                                   jfloat swing) {
    if (EngineHandle* h = handleOf(handle)) h->engine.setSwing(swing);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetMasterGain(JNIEnv*, jobject,
                                                                        jlong handle,
                                                                        jfloat gain) {
    if (EngineHandle* h = handleOf(handle)) h->engine.setMasterGain(gain);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeConfigureClip(
    JNIEnv* env, jobject, jlong handle, jint pad, jint kind, jint root, jint length,
    jfloat gain, jfloat pan, jint fxType, jboolean fxEnabled, jboolean sampleLoop,
    jboolean mute, jboolean solo, jfloatArray params) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr) return;
    float values[rep::ClipEngine::kParams] = {};
    const jsize count = params != nullptr ? env->GetArrayLength(params) : 0;
    if (count > 0) {
        env->GetFloatArrayRegion(params, 0, std::min<jsize>(count, rep::ClipEngine::kParams),
                                 values);
    }
    h->engine.configureClip(pad, kind, root, length, gain, pan, fxType,
                            fxEnabled == JNI_TRUE, sampleLoop == JNI_TRUE,
                            mute == JNI_TRUE, solo == JNI_TRUE, values,
                            rep::ClipEngine::kParams);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeUpdateParams(
    JNIEnv* env, jobject, jlong handle, jint pad, jfloatArray params, jfloat gain,
    jfloat pan, jboolean mute, jboolean solo, jboolean fxEnabled) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr) return;
    float values[rep::ClipEngine::kParams] = {};
    const jsize count = params != nullptr ? env->GetArrayLength(params) : 0;
    if (count > 0) {
        env->GetFloatArrayRegion(params, 0, std::min<jsize>(count, rep::ClipEngine::kParams),
                                 values);
    }
    h->engine.updateParams(pad, values, rep::ClipEngine::kParams, gain, pan,
                           mute == JNI_TRUE, solo == JNI_TRUE, fxEnabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetStep(
    JNIEnv*, jobject, jlong handle, jint pad, jint step, jboolean gate, jint note,
    jint velocity, jboolean accent, jboolean slide) {
    if (EngineHandle* h = handleOf(handle)) {
        h->engine.setStep(pad, step, gate == JNI_TRUE, note, velocity,
                          accent == JNI_TRUE, slide == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetDrumStep(
    JNIEnv*, jobject, jlong handle, jint pad, jint voice, jint step, jboolean on,
    jint velocity) {
    if (EngineHandle* h = handleOf(handle)) {
        h->engine.setDrumStep(pad, voice, step, on == JNI_TRUE, velocity);
    }
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetMidiNote(
    JNIEnv*, jobject, jlong handle, jint pad, jboolean on, jint note, jint start,
    jint length, jint velocity) {
    if (EngineHandle* h = handleOf(handle)) {
        h->engine.setMidiNote(pad, on == JNI_TRUE, note, start, length, velocity);
    }
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeClearMidiNotes(
    JNIEnv*, jobject, jlong handle, jint pad) {
    if (EngineHandle* h = handleOf(handle)) h->engine.clearMidiNotes(pad);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativePreviewNote(
    JNIEnv*, jobject, jlong handle, jint pad, jboolean on, jint note, jint velocity) {
    if (EngineHandle* h = handleOf(handle)) {
        h->engine.previewNote(pad, on == JNI_TRUE, note, velocity);
    }
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeEnsureFx(JNIEnv*, jobject,
                                                                   jlong handle, jint pad) {
    if (EngineHandle* h = handleOf(handle)) h->engine.ensureFx(pad);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeClearClip(JNIEnv*, jobject,
                                                                    jlong handle, jint pad) {
    if (EngineHandle* h = handleOf(handle)) h->engine.clearClip(pad);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeLaunchPad(JNIEnv*, jobject,
                                                                    jlong handle, jint pad) {
    if (EngineHandle* h = handleOf(handle)) h->engine.launchPad(pad);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeStopPad(JNIEnv*, jobject,
                                                                  jlong handle, jint pad) {
    if (EngineHandle* h = handleOf(handle)) h->engine.stopPad(pad);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeLaunchScene(JNIEnv*, jobject,
                                                                      jlong handle, jint row) {
    if (EngineHandle* h = handleOf(handle)) h->engine.launchScene(row);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeStopScene(JNIEnv*, jobject,
                                                                    jlong handle, jint row) {
    if (EngineHandle* h = handleOf(handle)) h->engine.stopScene(row);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeStopAll(JNIEnv*, jobject,
                                                                  jlong handle) {
    if (EngineHandle* h = handleOf(handle)) h->engine.stopAll();
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativePanic(JNIEnv*, jobject,
                                                                jlong handle) {
    if (EngineHandle* h = handleOf(handle)) h->engine.panic();
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeLoadSample(
    JNIEnv* env, jobject, jlong handle, jint pad, jfloatArray data, jint frames,
    jint channels, jint sampleRate) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr || data == nullptr) return;
    const jsize available = env->GetArrayLength(data);
    const jsize needed = frames * channels;
    if (needed <= 0 || needed > available) return;
    jfloat* elements = env->GetFloatArrayElements(data, nullptr);
    if (elements == nullptr) return;
    h->engine.loadSample(pad, elements, frames, channels, sampleRate);
    env->ReleaseFloatArrayElements(data, elements, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeClearSample(JNIEnv*, jobject,
                                                                      jlong handle, jint pad) {
    if (EngineHandle* h = handleOf(handle)) h->engine.clearSample(pad);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeGetPadStates(JNIEnv* env,
                                                                       jobject, jlong handle,
                                                                       jintArray out) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr || out == nullptr) return;
    int values[rep::ClipEngine::kPads] = {};
    h->engine.getPadStates(values, rep::ClipEngine::kPads);
    env->SetIntArrayRegion(out, 0, std::min<jsize>(env->GetArrayLength(out),
                                                   rep::ClipEngine::kPads),
                           values);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeGetPadMeters(JNIEnv* env,
                                                                      jobject, jlong handle,
                                                                      jfloatArray out) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr || out == nullptr) return;
    float values[rep::ClipEngine::kPads] = {};
    h->engine.getPadMeters(values, rep::ClipEngine::kPads);
    env->SetFloatArrayRegion(out, 0, std::min<jsize>(env->GetArrayLength(out),
                                                     rep::ClipEngine::kPads),
                             values);
}

JNIEXPORT jfloat JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeGetMasterMeter(JNIEnv*, jobject,
                                                                          jlong handle) {
    EngineHandle* h = handleOf(handle);
    return h != nullptr ? h->engine.masterMeter() : 0.0f;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeGetScope(JNIEnv* env, jobject,
                                                                    jlong handle,
                                                                    jfloatArray out) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr || out == nullptr) return;
    const jsize size = env->GetArrayLength(out);
    std::vector<float> values(static_cast<size_t>(size), 0.0f);
    h->engine.getScope(values.data(), size);
    env->SetFloatArrayRegion(out, 0, size, values.data());
}

JNIEXPORT jint JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeGetSampleRate(JNIEnv*, jobject,
                                                                         jlong handle) {
    EngineHandle* h = handleOf(handle);
    return h != nullptr ? h->engine.sampleRate() : 48000;
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeStartMasterRecording(
    JNIEnv* env, jobject, jlong handle, jstring path) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr || path == nullptr) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return JNI_FALSE;
    const bool ok = h->engine.startMasterRecording(chars);
    env->ReleaseStringUTFChars(path, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeStartPadRecording(
    JNIEnv* env, jobject, jlong handle, jstring path, jint pad) {
    EngineHandle* h = handleOf(handle);
    if (h == nullptr || path == nullptr) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return JNI_FALSE;
    const bool ok = h->engine.startPadRecording(chars, pad);
    env->ReleaseStringUTFChars(path, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeStopRecording(JNIEnv*, jobject,
                                                                        jlong handle) {
    if (EngineHandle* h = handleOf(handle)) h->engine.stopRecording();
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeIsRecording(JNIEnv*, jobject,
                                                                      jlong handle) {
    EngineHandle* h = handleOf(handle);
    return h != nullptr && h->engine.recording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeGetRecordingFrames(JNIEnv*,
                                                                             jobject,
                                                                             jlong handle) {
    EngineHandle* h = handleOf(handle);
    return h != nullptr ? static_cast<jlong>(h->engine.masterRecordedFrames()) : 0;
}

JNIEXPORT jlong JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeGetPadRecordingFrames(JNIEnv*,
                                                                                jobject,
                                                                                jlong handle) {
    EngineHandle* h = handleOf(handle);
    return h != nullptr ? static_cast<jlong>(h->engine.padRecordedFrames()) : 0;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicantsynth_audio_NativeEngine_nativeSetOfflineRendering(JNIEnv*,
                                                                              jobject,
                                                                              jlong handle,
                                                                              jboolean offline) {
    if (EngineHandle* h = handleOf(handle)) {
        h->engine.setOfflineRendering(offline == JNI_TRUE);
    }
}

} // extern "C"
