# Audio/FGS Background Spike (P0-T00 / P0-T05)

Disposable experiment only. **Not production code.** Nothing here may be
referenced by the future production `app` module. It exists solely to
produce real-device evidence for `docs/adr/ADR-006-audio-focus-and-foreground-service.md`.

It prototypes notification-triggered `TextToSpeech` + `AudioFocusRequest`
from an actual `NotificationListenerService.onNotificationPosted` callback,
with the app's activity absent, comparing:

- `NO_FGS`: speak directly from the listener callback, in-process.
- `WITH_FGS`: start a short-lived `mediaPlayback` foreground service
  (`SpikePlaybackService`) from the callback, speak, then stop on drain.

It speaks a single fixed synthetic Indonesian phrase only
("Pesan WhatsApp baru dari kontak uji.") - it never reads or logs real
notification text or sender names, only structural trial metadata (audio
focus results, TTS status codes, timings). See `ExperimentLog.kt`.

## Requirements

- Android SDK with platform 35 and a compatible build-tools version
  installed locally (this experiment was built against
  `compileSdk/targetSdk 35`, `minSdk 26`).
- A JDK 17+ (Gradle/AGP requirement).
- A physical device or emulator with `com.whatsapp` installed, reachable via
  `adb`, for realistic triggering. The recorded evidence run used a real
  device: Xiaomi Mi Mix 2S, LineageOS 22.2, Android 15 (API 35).
- No network access is required to build if a compatible Gradle
  distribution and the AGP/Kotlin artifacts below are already present in the
  local Gradle cache (`~/.gradle`), since `local.properties` and the build
  scripts here are pinned to exactly what this environment had cached:
  Gradle 8.12, AGP 8.7.3, Kotlin 2.1.0. If your environment lacks these in
  cache, allow network access once, or update the versions in
  `build.gradle.kts` / `app/build.gradle.kts` to versions available in your
  environment.

Edit `local.properties`'s `sdk.dir` if your Android SDK is not at
`C:/Users/prada/AppData/Local/Android/Sdk`.

## Build

```sh
./gradlew.bat assembleDebug --offline
```

(Drop `--offline` if your environment needs to fetch dependencies from
`google()`/`mavenCentral()`.)

## Install

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Grant notification access (debug-only shortcut)

Production apps must not do this programmatically - notification-listener
access is normally granted by the user through
`Settings > Apps > Special app access > Notification access`, which the
app's "Open notification access settings" button opens. For fast repeatable
testing on a `userdebug`/rootable build, the same effect can be reached via:

```sh
adb shell cmd notification allow_listener com.ridenotify.spike.audiobackground/com.ridenotify.spike.audiobackground.SpikeNotificationListenerService
```

## Run a trial

1. Launch the app once (`adb shell am start -n com.ridenotify.spike.audiobackground/.MainActivity`)
   and pick a trial mode (`NO_FGS` or `WITH_FGS`) with the radio buttons; the
   choice is persisted to `SharedPreferences` and read by the listener on
   every notification.
2. Send the activity to the background (`adb shell input keyevent KEYCODE_HOME`)
   so the trial genuinely runs with the UI absent.
3. Trigger a real WhatsApp notification (have someone message you, or
   message yourself from a second number/device).
4. Read results either:
   - In the app UI (`Refresh log` button), or
   - Via `adb logcat -d -s SpikeAudioExperiment:I`, or
   - By pulling the on-device file:
     `adb shell run-as com.ridenotify.spike.audiobackground cat files/experiment_log.txt`

## Cleanup

```sh
adb shell cmd notification disallow_listener com.ridenotify.spike.audiobackground/com.ridenotify.spike.audiobackground.SpikeNotificationListenerService
adb uninstall com.ridenotify.spike.audiobackground
```

Also safe to simply delete this entire `spikes/audio-background/` directory
once `ADR-006` no longer needs revisiting - it was never wired into the
production module.

## Recorded evidence

See `docs/adr/ADR-006-audio-focus-and-foreground-service.md` for the
decision and summarized evidence, and `evidence-logcat-2026-09-11.txt` in
this directory for the full raw (redacted-by-construction) capture from the
real-device run referenced there.
