# ADR-006: API 34/35+ Audio Focus And Foreground Playback Service

## Status

Accepted as implementation baseline; release validation remains incomplete.
Revised by P0-R01 after reconciling device evidence with the official Android
15 audio-focus requirement. Broader OEM/API coverage remains required.
Owner: Engineering
Date: 2026-09-11

## Context

ARCHITECTURE.md's "Spike A" requires validating notification-triggered TTS
with the activity absent, on real API 33/34/35+ devices, comparing a no-FGS
path against a short-lived `mediaPlayback` foreground service, triggered from
an actual `NotificationListenerService.onNotificationPosted` callback - not a
simulated call. This ADR records that experiment's real result and the
resulting production architecture decision, per P0-T05's acceptance
criteria: "ADR-006 selects a verified architecture or declares a release
blocker. If focus/FGS eligibility fails, the approved behavior is no speech."

The experiment ran in the disposable project at `spikes/audio-background/`
(P0-T00), which is exempt from the production freeze and is not referenced
by the production `app` module.

## Decision

Use an API-aware architecture:

- On Android 15/API 35 and newer, start a short-lived `mediaPlayback`
  foreground service before requesting audio focus. Request focus and run TTS
  from that service, then stop it promptly when the queue drains.
- If foreground-service startup is rejected, promotion fails, or audio focus
  is denied, fail closed and do not speak. Never fall back to requesting focus
  from the background listener on these versions.
- On Android 8-14/API 26-34, the in-process no-FGS path may be used because the
  Android 15 top-app/FGS audio-focus restriction does not apply there. The same
  bounded coordinator and focus lifecycle remain mandatory.

Rationale from the real trial (see Evidence):
- `AudioFocusRequest` with `AUDIOFOCUS_GAIN_TRANSIENT` was granted on the
  tested LineageOS device even without an FGS. This remains device evidence,
  but official Android 15 behavior requires the app to be topmost or running
  a foreground service and therefore controls the production design.
- `TextToSpeech` initialized, reported `LANG_AVAILABLE` for `id-ID`, and
  completed every utterance (`utterance-outcome=done`) without a foreground
  service, in both single and back-to-back-notification scenarios.
- No `SecurityException`, no `ForegroundServiceStartNotAllowedException`,
  and no observable throttling occurred for the NO_FGS path on the tested
  device at Android 15 (API 35).

The `SpikePlaybackService` (`mediaPlayback` FGS) path was verified as
startable from the same callback on this device/API
(`fgs-start-succeeded` both times, clean `fgs-stop-on-drain`), so the
architecture has a tested path compatible with the official audio-focus
precondition on this device. This does not prove that an NLS callback is a
documented background-start exemption on every OEM: the official exemption
list does not name `NotificationListenerService`. Startup failure is therefore
an expected, handled skip outcome rather than a crash or no-FGS fallback.

The no-FGS API 35 result is useful for diagnosing OEM enforcement differences,
not for bypassing the documented requirement.

## Alternatives Considered

1. **Always use a `mediaPlayback` foreground service on every API.** Rejected:
   API 26-34 do not have the Android 15 focus eligibility rule, so the extra
   service lifecycle is not justified there.
2. **Never use FGS, hard-remove `SpikePlaybackService`/`SpeechPlaybackService`
   from the architecture entirely.** Rejected: only one device/API
   combination has been verified; removing the fallback would create a
   release blocker if a P6-T02 device shows NO_FGS failing.
3. **Skip the real listener-callback trigger and only unit-test audio focus
   in isolation.** Rejected: explicitly disallowed by P0-T05's acceptance
   criteria, which requires triggering from an actual
   `NotificationListenerService` callback.

## Consequences

- Production uses the no-FGS path only on API 26-34.
- `SpeechPlaybackService` (`P4-T04`) is required for attempted speech on API
  35+, with the matching permissions and service type. It catches background
  start/promotion failures and skips speech without requesting focus.
- **Residual risk, not yet tested and explicitly not claimed here:**
  - Only one physical device was available in this environment: Xiaomi Mi
    Mix 2S, LineageOS 22.2 (nightly), Android 15 / API 35. No stock
    Android/Samsung/Pixel/other-OEM device, and no API 33/34 device, was
    tested. `ADR-002`'s Tier 2 OEM caveat applies directly here - this
    decision is not yet OEM-general.
  - Audio focus interaction while another app is actively playing media
    (e.g. music) was not tested in this session - only the no-competing-audio
    case was verified. `AUDIOFOCUS_LOSS_TRANSIENT` behavior when a real
    third-party player is ducking/pausing must be verified by `P4-T05`.
  - Burst/back-to-back notifications were observed to trigger **two
    concurrent, unserialized trial runs** in the spike (the spike has no
    queue - it is not production code). The second trial's audio-focus
    request produced `audio-focus-change=-2` (`AUDIOFOCUS_LOSS_TRANSIENT`)
    against the first request's listener, i.e. **two overlapping
    `AudioFocusRequest`/`TextToSpeech` instances actively interfered with
    each other** in the spike. This is concrete, real-device evidence (not
    a hypothesis) that `ADR-005`'s bounded single-active-utterance queue is
    a hard correctness requirement, not just a style preference - the spike
    itself reproduced the exact failure mode `SpeechCoordinator` exists to
    prevent.
  - Only a synthetic fixed Indonesian phrase was spoken; no measurement of
    TTS engine swap, missing-voice-data, or degraded-audio-route (Bluetooth
    headset, wired) scenarios was performed.
- This ADR must be revisited (new evidence appended, not silently
  overwritten) after `P4-T05`'s full device/API matrix run, before Phase 4's
  gate can treat the audio architecture as release-final.

## Dependent Tasks

Unblocked by this ADR: `P4-T01`, `P4-T02`, `P4-T03`, `P4-T04` (required for
API 35+), `P4-T05` (must extend, not repeat, this evidence).

## Evidence

Experiment code: `spikes/audio-background/` (this repository, disposable,
not referenced by production `app`).

Device under test (real hardware, connected via `adb`):
- Model: Xiaomi Mi Mix 2S (`polaris`)
- OS: LineageOS 22.2-20260820-NIGHTLY-polaris
- Android version/API: 15 / 35
- `com.whatsapp` installed and used to generate the real trigger notification.

Method: `SpikeNotificationListenerService` was granted notification-listener
access via `adb shell cmd notification allow_listener <component>` (a
device-shell debug affordance, not a production access path). The spike
activity was sent to the background (`KEYCODE_HOME`) before each trial. A
real WhatsApp message was then received on-device, independently triggering
`onNotificationPosted` with the spike UI not in the foreground.

Two trial modes were run back-to-back, each triggered by real WhatsApp
notifications:

**Trial 1 - `NO_FGS`** (two notifications arrived close together, producing
two overlapping trials):
```
notification-observed package=com.whatsapp mode=NO_FGS
trial-start trigger=listener-callback mode=NO_FGS
audio-focus-request result=1 granted=true mode=NO_FGS
notification-observed package=com.whatsapp mode=NO_FGS
trial-start trigger=listener-callback mode=NO_FGS
audio-focus-request result=1 granted=true mode=NO_FGS
audio-focus-change=-2 mode=NO_FGS
tts-language-availability=1 mode=NO_FGS
tts-speak-call-result=0 mode=NO_FGS
tts-language-availability=1 mode=NO_FGS
tts-speak-call-result=0 mode=NO_FGS
utterance-start id=f5bc8dd0-... mode=NO_FGS
utterance-start id=fda70d2a-... mode=NO_FGS
utterance-outcome=done mode=NO_FGS
trial-end durationMs=4196 mode=NO_FGS
no-fgs-trial-complete
utterance-outcome=done mode=NO_FGS
trial-end durationMs=6792 mode=NO_FGS
no-fgs-trial-complete
```

**Trial 2 - `WITH_FGS`** (again two overlapping notifications):
```
notification-observed package=com.whatsapp mode=WITH_FGS
notification-observed package=com.whatsapp mode=WITH_FGS
fgs-start-succeeded mode=WITH_FGS
trial-start trigger=listener-callback mode=WITH_FGS
audio-focus-request result=1 granted=true mode=WITH_FGS
fgs-start-succeeded mode=WITH_FGS
trial-start trigger=listener-callback mode=WITH_FGS
audio-focus-request result=1 granted=true mode=WITH_FGS
audio-focus-change=-2 mode=WITH_FGS
tts-language-availability=1 mode=WITH_FGS
tts-speak-call-result=0 mode=WITH_FGS
tts-language-availability=1 mode=WITH_FGS
utterance-start id=6c7577e2-... mode=WITH_FGS
tts-speak-call-result=0 mode=WITH_FGS
utterance-start id=eeab0f24-... mode=WITH_FGS
utterance-outcome=done mode=WITH_FGS
trial-end durationMs=2821 mode=WITH_FGS
fgs-stop-on-drain mode=WITH_FGS
utterance-outcome=done mode=WITH_FGS
trial-end durationMs=5437 mode=WITH_FGS
fgs-stop-on-drain mode=WITH_FGS
```

Full raw capture: `spikes/audio-background/evidence-logcat-2026-09-11.txt`
(redacted by construction - the spike never logs real message text or
sender names, only a fixed synthetic phrase and structural trial metadata).

No real message content, sender names, or phone numbers appear in the
evidence file or this ADR.

Official constraints used by P0-R01:

- Android 15 target behavior changes: apps targeting API 35+ must be topmost
  or running a foreground service to request audio focus; otherwise the request
  returns `AUDIOFOCUS_REQUEST_FAILED`:
  https://developer.android.com/about/versions/15/behavior-changes-15
- Background FGS start restrictions and exemptions:
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
