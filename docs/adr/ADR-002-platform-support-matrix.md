# ADR-002: Platform Support Matrix (SDK, OEM, And API-Level Constraints)

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`ARCHITECTURE.md` requires Phase 0 to approve "the minimum SDK, target SDK,
Compose BOM, Kotlin, AGP, and dependency versions approved from current
stable guidance," and its "Highest-Risk Spikes" section requires Spike A to
be validated "on the exact OS and target-SDK combinations approved in
ADR-002, including API 33, 34, 35, and the current release target."
`PROJECT_CHARTER.md` explicitly refuses to promise "identical behavior on
every OEM or WhatsApp version" and requires success metrics to be scoped to
"the agreed test corpus and device matrix."

This task's environment provided the following facts about the local
toolchain and test hardware (from the task assignment itself, not an
external source, and therefore not independently citable, but recorded here
as the factual baseline this ADR must reconcile with official guidance):

- Installed Android SDK platforms: `android-31`, `android-33`, `android-34`,
  `android-35`. **`android-36` (Android 16) is not installed.**
- Installed build-tools: `30.0.3`, `33.0.1`, `34.0.0`, `35.0.0`, `35.0.1`,
  `36.0.0`. Build-tools `36.0.0` is present even though the matching
  `android-36` platform is not.
- One physical test device is attached: Xiaomi Mi Mix 2S ("polaris"),
  running LineageOS 22.2 nightly, Android 15 (API 35), with `com.whatsapp`
  installed.
- No other real device (no Samsung, no stock MIUI/HyperOS, no other
  LineageOS build) is currently available.

Today's date (this ADR's access date for all official sources below) is
2026-09-11.

## Decision

### 1. Minimum SDK: `minSdkVersion = 26` (Android 8.0, "Oreo")

Rationale, all traceable to `ARCHITECTURE.md`'s required technology
baseline:

- `android.media.AudioFocusRequest`, the class `ARCHITECTURE.md` mandates
  for `AudioFocusController`, was added in API level 26 (Evidence #4). A
  `minSdkVersion` below 26 would force a legacy `requestAudioFocus()`
  code path the architecture does not want maintained.
- `android.service.notification.NotificationListenerService` itself only
  requires API 18 (Evidence #5), so the listener is not the binding
  constraint; the speech/audio baseline is.
- This is a from-scratch native rewrite (see `ADR-001`'s unresolved finding
  that the old Flutter app's real distribution history is unverified), so
  there is no confirmed installed base below API 26 to preserve.

### 2. Target/compile SDK: `targetSdkVersion = compileSdkVersion = 36` (Android 16), with a documented, temporary fallback to 35 for local development only

As of today (2026-09-11), Google Play's official target API level policy
(Evidence #1, #2) states:

> Starting August 31, 2026: New apps and app updates must target Android 16
> (API level 36) or higher to be submitted to Google Play... Existing apps
> must target Android 15 (API level 35) or higher to remain available to
> new users on devices running Android OS higher than your app's target API
> level.

Because today's date is past August 31, 2026, API level 36 is the currently
binding Play submission requirement for both new apps and app updates
(an extension to November 1, 2026 is available if needed, per the same
source). `ADR-001` has not yet resolved whether this app is a "new app" or
an "app update" of a previously distributed app, but the API-36 requirement
applies to both categories as of today, so the distinction does not change
this decision.

**Known gap, blocking `P1-T01` before release, not blocking Phase 0**: the
local SDK installation does not include the `android-36` platform
(`android.jar`), even though `build-tools;36.0.0` is present. `compileSdk 36`
cannot succeed until the platform component is installed (e.g. via
`sdkmanager "platforms;android-36"`). Until that is installed:

- Local development and CI may temporarily build with `compileSdk 35` /
  `targetSdk 35` (fully supported by the currently installed platform and
  build-tools, and still meets today's "existing app" floor).
  This is only acceptable if the project has no existing published listing
  yet; it must not be the version submitted to Play.
- `P1-T01` (scaffold project) must record installing `platforms;android-36`
  as an explicit setup step, and the release configuration (`P6-T05`) must
  raise `compileSdk`/`targetSdk` to 36 before any Play submission, or the
  submission will be rejected per Evidence #1.

### 3. Supported OEM/device matrix approach

Given exactly one real device is currently available:

- **Tier 1 - verified on real hardware**: Xiaomi Mi Mix 2S, LineageOS 22.2
  nightly, Android 15 (API 35). This is the only device against which any
  "works on my device" claim may be made until more hardware is available.
- **Tier 2 - documented-only, explicitly unverified**: any other OEM skin
  (Samsung One UI, stock/AOSP, other MIUI/HyperOS builds, other LineageOS
  builds) or API level. No claim of support, partial support, or
  compatibility may be made for Tier 2 until a real device in that tier is
  added to the matrix and passes `P6-T02` (device qualification). This
  directly implements `PROJECT_CHARTER.md`'s refusal to guarantee
  "identical behavior on every OEM."
- Automated/instrumented and emulator-based testing should still cover the
  full API range required by `ARCHITECTURE.md`'s Spike A (33, 34, 35, and
  the current release target 36) using AVD images, even though only API 35
  has real-hardware coverage today. Emulator coverage is not a substitute
  for Tier 1 verification; it only prevents build/API regressions on
  untested levels.
- Broadening Tier 1 (e.g. adding a Samsung or stock Pixel device) is an
  explicit, separately tracked follow-up and is out of scope for Phase 0.

### 4. Local SDK/build-tools compatibility confirmation

- `compileSdk 35` / `targetSdk 35` is fully supported today with the
  installed `android-35` platform and `build-tools;35.0.1` (or `35.0.0`).
- `compileSdk 36` / `targetSdk 36` requires installing the `android-36`
  platform; `build-tools;36.0.0` is already present and sufficient once the
  platform is added.
- `build-tools;30.0.3` / `33.0.1` / `34.0.0` are not needed for the primary
  build and should only be relied on if a dependency's build script pins an
  older AGP/build-tools combination; they must not be used to justify a
  lower `compileSdk`.

### Foreground-service and audio-focus facts for `ADR-006` (reference only, not a decision made by this ADR)

`ARCHITECTURE.md`'s Spike A and `ADR-006` need the following official,
API-level-specific facts. `ADR-006` is a separate, still-pending task being
executed by another worker on the real device; this section only supplies
documented inputs, and makes no design decision on their behalf.

- **Audio focus, API 35+**: "If an app targets Android 15 (API level 35) or
  higher, it cannot request audio focus unless it's the top app or running
  a foreground service... the method returns
  `AudioManager.AUDIOFOCUS_REQUEST_FAILED`." (Evidence #4)
- **Audio focus, API 31+ (Android 12)**: audio focus is system-managed;
  the system fades out a losing app's playback and mutes playback during
  an incoming call. Before API 31, ducking/focus loss was app-managed only.
  (Evidence #6)
- **Foreground service types, API 34+**: every foreground service must
  declare an `android:foregroundServiceType` and request the matching
  `FOREGROUND_SERVICE_<TYPE>` permission, in addition to
  `FOREGROUND_SERVICE`; a `mediaPlayback`-typed foreground service has no
  extra runtime-permission prerequisite. Calling `startForeground()` without
  a manifest-declared type throws `MissingForegroundServiceTypeException`.
  (Evidence #7, #8)
- **Foreground-service background-start restrictions, API 31+**: apps
  cannot start a foreground service while running in the background except
  for a documented, closed list of exemptions (transition from a
  user-visible state; starting an activity from background; FCM high-priority
  message; user interaction with a bubble/notification/widget/activity;
  an app-invoked exact alarm; being the current input method; a
  geofencing/activity-recognition event; `BOOT_COMPLETED` /
  `ACTION_LOCKED_BOOT_COMPLETED` / `ACTION_MY_PACKAGE_REPLACED`;
  `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` / `ACTION_LOCALE_CHANGED`;
  an NFC transaction event; certain system roles/permissions; Companion
  Device Manager with the relevant permission; the user turning off battery
  optimization for the app; or holding `SYSTEM_ALERT_WINDOW` with a
  currently visible overlay). **A `NotificationListenerService` callback
  firing while the app has no visible `Activity` is not explicitly named in
  this exemption list.** Whether the system nonetheless treats an
  already-bound listener process as exempt (because it is not "started" the
  way a killed background app would be) is not stated in this document and
  is exactly the kind of fact Spike A must establish empirically, not
  assume. (Evidence #9)
- **While-in-use permission restriction, API 34+**: a foreground service
  needing a while-in-use permission (camera/microphone/location/some health
  sensors) cannot be *created* while the app is in the background even if
  one of the above exemptions applies; a narrower, separate exemption list
  covers this case (system component start, app-widget interaction,
  notification interaction, a `PendingIntent` from a different visible app,
  device-owner start, `VoiceInteractionService`, or
  `START_ACTIVITIES_FROM_BACKGROUND`). This is likely not directly relevant
  to a `mediaPlayback` service (no while-in-use permission involved), but is
  recorded because `ADR-006` must confirm that assumption. (Evidence #9)
- **`BOOT_COMPLETED` foreground-service restriction, API 35+**: apps
  targeting Android 15+ may not launch `camera`, `dataSync`,
  `mediaPlayback`, `mediaProjection`, or `phoneCall`-typed foreground
  services from a `BOOT_COMPLETED` receiver. Not directly relevant unless a
  boot receiver is later added (see `ADR-008`, which recommends against
  one). (Evidence #7)

## Alternatives Considered

- `minSdkVersion = 21` - rejected. Materially increases legacy audio-focus
  and permission code paths for no confirmed benefit, since there is no
  verified pre-Android-8 installed base to protect (see `ADR-001`).
- `minSdkVersion = 24` - rejected. No AudioFocusRequest benefit over 26;
  moving to 26 is a clean, well-documented baseline with no intermediate
  advantage at 24.
- `targetSdkVersion = 35` only, deferring 36 indefinitely - rejected. As of
  today's date this would already be non-compliant with Google Play's
  binding target-API requirement for new submissions and app updates.
- Declaring broad OEM support ("works on all Android 15 devices") based on
  the single available device - rejected as an unsupported claim, directly
  contradicting `PROJECT_CHARTER.md`'s non-goal of guaranteeing identical
  OEM behavior.

## Consequences

- Positive: the target/compile SDK decision is compliant with Google Play's
  policy as it stands today, avoiding a forced re-target shortly after
  Phase 1 begins.
- Positive: the OEM tiering makes today's single-device reality explicit
  rather than implying broader coverage than has been tested.
- Negative / action required: `android-36` platform must be installed
  before any `compileSdk 36` build succeeds; this is a concrete blocking
  setup step for `P1-T01`, not merely a suggestion.
- Negative / residual risk: Tier 2 OEMs (including any non-LineageOS
  Xiaomi build, Samsung, or stock Pixel/AOSP) remain completely unverified
  until additional hardware is available; any pre-launch claims must stay
  scoped to Tier 1 until `P6-T02` broadens it.
- The foreground-service/audio-focus facts recorded above must be treated
  by `ADR-006`'s author as documented facts only; whether they permit a
  `mediaPlayback` foreground service to be started synchronously from
  `WhatsAppNotificationListenerService`'s callback is explicitly unresolved
  here and is Spike A's job to determine on the real device.

## Dependent Tasks

`P1-T01` (SDK/platform install and project scaffold), `P0-T05` (audio/FGS
spike must run across this matrix), `ADR-006` (consumes the
foreground-service/audio-focus facts above), `P6-T02` (device/OEM
qualification, which may extend the Tier 1 list), `P6-T05` (release
configuration must confirm `compileSdk`/`targetSdk` 36 before submission).

## Evidence

All links accessed and verified resolving on 2026-09-11.

1. Google Play target API level policy (official Play Console Help):
   https://support.google.com/googleplay/android-developer/answer/11926878
2. "Meet Google Play's target API level requirement" (developer.android.com):
   https://developer.android.com/google/play/requirements/target-sdk
3. `<uses-sdk>` manifest element reference (developer.android.com):
   https://developer.android.com/guide/topics/manifest/uses-sdk-element
4. `AudioFocusRequest` API reference, "Added in API level 26" and the
   Android-15 audio-focus restriction note:
   https://developer.android.com/reference/android/media/AudioFocusRequest
5. `NotificationListenerService` API reference, "Added in API level 18":
   https://developer.android.com/reference/kotlin/android/service/notification/NotificationListenerService
6. "Manage audio focus" (developer.android.com/media):
   https://developer.android.com/media/optimize/audio-focus
7. "Foreground service types" (developer.android.com/develop/background-work):
   https://developer.android.com/develop/background-work/services/fgs/service-types
8. "Foreground service types are required" (Android 14 behavior change):
   https://developer.android.com/about/versions/14/changes/fgs-types-required
9. "Restrictions on starting a foreground service from the background":
   https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

Local SDK/device inventory (platforms, build-tools, and the attached Mi
Mix 2S device): N/A - supplied directly by the task assignment, not an
external source.
