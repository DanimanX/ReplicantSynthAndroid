# Releasing

## Versioning

`app/build.gradle.kts` defaults to `versionName = "0.0.2"` and
`versionCode = 2`. CI can override both:

```bash
./gradlew :app:assembleRelease \
  -PreplicantVersionName=0.0.2 \
  -PreplicantVersionCode=2
```

## Tag build

`.github/workflows/android.yml` runs on:

- every push/PR to `main`
- tags matching `0.*`
- manual `workflow_dispatch`

The Android job installs platform 36, build-tools 36, NDK 27 and CMake 3.22.1,
runs `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`, uploads
both APKs as an artifact and, for a tag, creates a GitHub Release with
`app-release.apk`.

The host job independently builds and runs the 36k-check DSP suite.

## Publish 0.0.2

```bash
git tag 0.0.2
git push origin main
git push origin 0.0.2
```

The tag workflow creates `ReplicantSynth 0.0.2` with the APK asset. The README
links to the tag release.

## Release checklist

- [ ] `./build-host/engine_tests` → 0 failures
- [ ] `./gradlew :app:testDebugUnitTest` → 0 failures
- [ ] `./gradlew :app:assembleRelease` installs on a device
- [ ] Screenshots refreshed with `tools/capture_screenshots.py`
- [ ] Demo audio re-rendered and MP3 encoded
- [ ] README screenshots and release link updated
- [ ] Tag pushed and Actions green
- [ ] Release asset downloaded and installed

## Signing

Unsigned local/CI release builds fall back to the debug key so the APK remains
installable. Production signing uses `REPLICANT_KEYSTORE`,
`REPLICANT_KEYSTORE_PASSWORD`, `REPLICANT_KEY_ALIAS` and
`REPLICANT_KEY_PASSWORD`. Never commit a keystore or its passwords.
