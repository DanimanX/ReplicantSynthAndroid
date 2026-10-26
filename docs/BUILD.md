# Build

## Toolchain

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Gradle | 8.11.1 (wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| NDK | 27.0.12077973 |
| CMake | 3.22.1 |
| Oboe | 1.9.3 |
| ABI | arm64-v8a, x86_64 |

## Android build

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk17
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

The native library is `libreplicantsynth_engine.so`; the wavetable banks are
built in native code at engine creation, so the APK does not ship sample data
beyond the 563 kB demo vocal.

## Signing

The release build uses the debug key when no release keystore is configured, so
every CI APK installs. For a production key:

```bash
export REPLICANT_KEYSTORE=/absolute/path/release.jks
export REPLICANT_KEYSTORE_PASSWORD=...
export REPLICANT_KEY_ALIAS=...
export REPLICANT_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

## Host DSP suite

The engine has no Android or Oboe dependency outside `audio/AudioDriver.cpp`:

```bash
cmake -S host -B build-host -DCMAKE_BUILD_TYPE=Release
cmake --build build-host -j
./build-host/engine_tests
```

Expected tail: `36103 checks, 0 failures`.

## Demo renderer

```bash
./build-host/render_demo assets/replicantsynth_demo.wav
```

The renderer runs the same clip engine, captures with the offline blocking
recorder, and writes a 24-bit/48 kHz WAV. The checked-in
`assets/replicantsynth_demo.mp3` is a 192 kbps encode of this render.

## Emulator

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
           "ndk;27.0.12077973" "cmake;3.22.1" \
           "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n phone35 -k "system-images;android-35;google_apis;x86_64" -d pixel_7
$ANDROID_HOME/emulator/emulator -avd phone35 -no-window -no-snapshot \
    -no-boot-anim -gpu swiftshader_indirect -accel on
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.danimanx.replicantsynth/.MainActivity
```

`tools/capture_screenshots.py` captures the README images on a connected
emulator/device. The emulator reports AAudio shared mode (or silent-clock mode
when started with `-no-audio`); do not read its latency as a hardware result.

## Runtime checks

- First launch creates the NEON GRID demo in `filesDir/project.json` and copies
  `assets/demo/neon_stab.wav` into `filesDir/samples`.
- Tap pads; the app writes/starts the Oboe stream and the status bar shows
  `AAUDIO 48k` or `SILENT`.
- `adb logcat -s ReplicantSynthAudio ReplicantSynthJNI` shows stream and JNI
  errors.
- Recordings: `adb shell run-as com.danimanx.replicantsynth ls files/Recordings`.
