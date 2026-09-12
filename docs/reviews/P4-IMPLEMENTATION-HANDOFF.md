# P4 Implementation Handoff

Date: 2026-09-12
Status: `P4-T01` through `P4-T04` implemented and verified locally;
physical-device work remains pending.

## Implemented

- `P4-T01`: `AndroidTtsEngine` initializes asynchronously, accepts only an
  installed Indonesian (`id-ID`) voice, creates unique utterance IDs, reports
  terminal progress events, applies a 45-second watchdog, and supports stop
  and idempotent shutdown.
- `P4-T02`: `AndroidAudioFocusController` requests transient guidance/speech
  focus, maps loss callbacks safely, guards stale callbacks, and abandons only
  after a terminal utterance.
- `P4-T03`: `SpeechCoordinator` owns one active utterance and a bounded queue:
  20 pending items, 180-second expiry, oldest-pending overflow eviction,
  same-conversation dequeue aggregation, 240-character word-boundary
  truncation, and immediate clear/stop on reader or riding deactivation.
- `P4-T04`: API 35+ calls a private `mediaPlayback` foreground service and
  waits for successful promotion before requesting focus. API 26-34 does not
  start this service. Promotion failure is a fail-closed per-item skip.

The application container starts the coordinator before the notification
pipeline. The coordinator begins fail-closed until it observes an eligible
settings snapshot.

## Automated Coverage

Focused tests cover TTS initialization, locale/voice failure, unique IDs,
progress, timeout, shutdown; audio focus lifecycle; service promotion and
cancellation cleanup; and coordinator aggregation, overflow, focus loss, and
reader-disable flushing.

The iterative review additionally fixed coordinator compilation, burst
aggregation order, permanent focus loss after a transient loss, coordinator
cancellation cleanup, and Robolectric manifest/resource configuration. Local
Robolectric runs use SDK 34 because Robolectric 4.12.1 does not provide SDK 35;
the injected API-35 gate branch is covered locally and real API-35 behavior
remains part of `P4-T05`.

## Local Verification

Passed from native PowerShell on 2026-09-12:

- `./gradlew.bat --no-daemon --rerun-tasks testDebugUnitTest --tests "com.ridenotify.app.wa_reader.speech.*"`
- `./gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug`
- `git diff --check`

Run from native PowerShell at the repository root:

```powershell
.\gradlew.bat --no-daemon --rerun-tasks testDebugUnitTest --tests "com.ridenotify.app.wa_reader.speech.*"
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
git diff --check
```

The WSL runner could not execute Gradle because `cmd.exe` fails before Gradle
starts with `UtilBindVsockAnyPort:307`. This is an environment limitation, not
a passing test result.

## Remaining P4-T05 Device Matrix

A partial API 35 campaign is recorded in
`docs/reviews/DEVICE_QUALIFICATION_2026-09-12.md`. Notification-listener access,
UI-absent operation, a screen-off transition, and process recreation passed on
the available Xiaomi Mi MIX 2S. The device reports Android 15/API 35 but retains
an Android 8 release fingerprint, so it is not sufficient representative OEM
evidence.

Run on API 26-34 and API 35+ devices with speaker, wired headset, and Bluetooth
routes. Verify incoming/active call and alarm interruption, no Indonesian voice,
foreground-service startup rejection, reader/riding disable while speaking,
and process recreation. Capture only redacted structural logs; never record
message bodies, sender names, or phone numbers.

`P4-R01` must wait for the required device evidence.
