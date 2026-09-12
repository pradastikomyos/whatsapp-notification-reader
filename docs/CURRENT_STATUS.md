# Current Project Status

Last updated: 2026-09-12
Workspace: `C:\Users\prada\Documents\whatsapp notification reader`
Source prototype: `C:\Users\prada\Documents\prjkwanotif`
Version control: Git repository initialized on branch `main`; public GitHub
remote is `https://github.com/pradastikomyos/whatsapp-notification-reader`.

This document is the canonical pause/resume handoff. Read it before making any
new change.

## Executive Status

- Current product behavior is direct-message reading only. Parsed group messages
  are rejected unconditionally before formatting or speech.
- Group policy, selection, observed-group UI/catalogue, and Room/KSP dependencies
  were removed under `ADR-013`. Startup retires the old catalogue database.
- Legacy DataStore cleanup preserves the sender-announcement boolean under
  `announce_sender` and removes all retired group keys even on already-migrated installs.

- Phase 0, architecture decisions and evidence: **Passed**.
- Phase 1, native Android foundation: **Passed locally**.
- Phase 2, domain and data: **Passed locally**.
- Current completed Phase 2 tasks: `P2-T01` through `P2-T07` and `P2-R01`.
- Phase 3, listener integration: **Passed locally** in `docs/reviews/P3-R01.md`.
- Phase 4 `P4-T01` through `P4-T04` are implemented and verified locally;
  device-audio qualification remains pending.
- `P5-T01` onboarding and independent platform/repository status are complete
  locally. Phase 5 coding may continue while the Phase 4 physical-device gate
  debt remains open; neither phase may be marked passed before its required
  review and device evidence.
- P5 routes are integrated locally and P6 local hardening/release preparation
  is complete. Phase 5 and Phase 6 remain open pending physical qualification,
  signed artifact install/upgrade evidence, and their final reviews.

## Qualification Strategy

All coding, local automated verification, UI review, hardening, and release
preparation run before the physical-device campaign. The final campaign combines
`P4-T05`, `P6-T02`, the physical measurements from `P6-T04`, and the install and
upgrade checks from `P6-T05`. It must cover the approved API/OEM, audio-route,
interruption, notification-privacy, WhatsApp/Business, process-recreation, and
foreground-service outcome matrix. This sequencing authorizes implementation
work only; it does not close Phase 4 or support release claims.

## Active Work

- AndroidX Core is pinned to compatible stable version `1.17.0`; Core 1.18+
  requires the API 36.1 toolchain while this project remains on compileSdk 36.
- `P3-T01 Listener service` is complete locally.
- `P3-T02 Pipeline orchestration` is complete locally.
- `P3-T03 Listener recovery` starts the UI-independent pipeline from the
  application process, reloads settings with a bounded fail-closed wait, and
  rate-limits valid rebind attempts to once per 30 seconds.
- The former `P3-T04` observed-conversation integration is superseded and removed
  by `ADR-013`; group classification remains only as a fail-closed policy input.
- `P3-R01` passed locally after the independent AI review and PowerShell suite.
- `P4-T01` through `P4-T04` are implemented: Indonesian-only asynchronous TTS,
  transient speech audio focus, the bounded coordinator, and an API 35+
  `mediaPlayback` foreground-service gate. The focused speech suite, full unit
  suite, lint, debug assembly, and diff check pass from native PowerShell.
- A partial campaign on the Xiaomi Mi Mix 2S confirmed notification access,
  listener binding with the UI absent, a screen-off transition, and immediate
  listener rebind after process replacement. Privacy-safe evidence is in
  `docs/reviews/DEVICE_QUALIFICATION_2026-09-12.md`; WhatsApp callback and audio
  evidence remain pending.
- `P5-T01` opens Android's notification-listener settings and, on return,
  separately displays notification access, listener connection, reader
  preference, manual riding state, and Indonesian TTS readiness. Settings are
  displayed as loading until DataStore produces a snapshot, never as guessed
  defaults. Access lookup fails closed and TTS discovery does not claim playback
  success before a test utterance. Focused status tests, the full unit suite,
  `lintDebug`, and `assembleDebug` pass.
- The former `P5-T04` observed-groups route is superseded and removed.
- `P5-T05` provides a manual riding route backed only by DataStore. Its control
  remains independent from reader enablement and reports the exact effective
  reader/riding gate state.
- `P5-T02` provides repository-backed reader, riding, and speech-rate controls.
  Test speech uses the shared coordinator and reports terminal playback rather
  than treating locale discovery as proof of playback.
- `P5-T03` now provides direct-message and direct-sender announcement settings.
  Locale and queue policy are fixed v1 facts, not unimplemented controls.
- Shared navigation now exposes status, controls, riding mode, and reader
  settings without taking ownership of the listener or speech
  lifecycle. The UI follows system dark mode and scrolls all route content.
- P5-T06 static accessibility/responsive review and P5-R01 local pre-review are
  recorded; physical TalkBack and form-factor evidence remain pending.
- The direct-message-only UI redesign is accepted as passed for its current
  product scope in `docs/reviews/UI-REDESIGN-2026-09-12.md`. Its active rules and
  external design references are recorded in `docs/DESIGN_SYSTEM.md`.
- Theme selection follows Android by default and offers a persistent moon/sun
  override from the top app bar.
- P6 local work completed: unit/lint/debug/release builds, static
  security/privacy review, release artifact measurement, shrink configuration,
  release documentation, and the device campaign checklist. A partial device
  campaign has run, but its remaining matrix still blocks final reviews. No
  emulator or instrumented suite is configured.

## Completed Work

### Phase 0

- Project charter, architecture, implementation phases, and worker prompts.
- ADR-001 through ADR-012.
- Fourteen synthetic notification fixtures under `docs/fixtures/`.
- Disposable audio/background spike tested on a real Android 15 device.
- Raw spike evidence retained at
  `spikes/audio-background/evidence-logcat-2026-09-11.txt`.
- `P0-R01` completed at `docs/reviews/P0-R01.md`.

The spike application was removed from the test device. Its local source and
evidence remain intentionally available for audit and must not be referenced by
the production `app` module.

### Phase 1

- Native Kotlin/Compose project scaffold.
- Single Gradle `app` module.
- Gradle wrapper and version catalog.
- `compileSdk` and `targetSdk` 36; `minSdk` 26.
- Application ID and namespace preserved as
  `com.ridenotify.app.wa_reader`.
- Listener component FQCN preserved as
  `com.ridenotify.app.wa_reader.MyNotificationListener`.
- Minimal manifest with `allowBackup=false` and TTS package visibility query.
- Notification listener protected by
  `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`.
- Application-scoped manual composition root in `di/AppContainer.kt`.
- Local developer commands in `docs/DEVELOPMENT.md`.
- GitHub Actions workflow prepared for the public repository.
- `P1-R01` completed at `docs/reviews/P1-R01.md`.

### Phase 2

`P2-T01` is complete and reviewed in `docs/reviews/P2-T01.md`.
`P2-T02` is complete and reviewed in `docs/reviews/P2-T02.md`.
`P2-T03` is complete and reviewed in `docs/reviews/P2-T03.md`.
`P2-T04`, `P2-T05`, and `P2-T07` are complete and reviewed in their matching
files under `docs/reviews/`.
`P2-T06` and the Phase 2 gate are complete in `docs/reviews/P2-T06.md` and
`docs/reviews/P2-R01.md`.

Implemented pure Kotlin models:

- `NotificationSnapshot` and MessagingStyle snapshots.
- `ConversationId`, conversation type, and riding state.
- Explicit parsed-notification outcomes.
- `ParsedMessage` and parse source.
- Conservative `AppSettings` defaults.
- Explicit `ReadingDecision` outcomes.
- Bounded `SpeechRequest` model.
- Domain invariant tests.

Implemented DataStore settings:

- DataStore is the settings source of truth and is wired through `AppContainer`.
- First read or write automatically runs the one-time legacy migration.
- Migration is concurrency-safe and follows the ADR-001 migrate/reset map.
- Flutter legacy encoded doubles are decoded and range-checked.
- Invalid persisted enums and rates fail to safe defaults.
- Retirement migration removes old group policy/selection keys and legacy Flutter
  selection data while preserving the previous sender-announcement value.
- Robolectric integration tests exercise real Preferences DataStore files.

Implemented notification snapshot extraction:

- Android `StatusBarNotification` and `Notification` data is copied immediately
  into immutable domain snapshots.
- Extras, OS grouping metadata, conversation metadata, and MessagingStyle
  messages/senders/timestamps are covered by Robolectric tests.
- Framework notification objects do not cross into downstream domain APIs.

Implemented parser and deduplication:

- Ordered WhatsApp parser covers all 14 synthetic fixtures / 18 captures and
  fails closed for unsupported packages and ambiguous legacy notifications.
- In-memory fingerprint deduplication is bounded and clock-driven; raw message
  bodies are never stored or persisted.

Implemented reading policy and speech formatting:

- Reader and riding gates have fixed precedence before direct-message policy.
- Every parsed group is rejected as `SkipGroupReadingDisabled`; redacted,
  unsupported, and stale notifications also fail closed.
- Indonesian speech text is sanitized and bounded to 240 characters without
  dropping all message content or splitting Unicode surrogate pairs.

## Current Architecture Decisions

The production pipeline remains:

```text
NotificationListenerService
  -> immutable NotificationSnapshot
  -> WhatsApp parser
  -> deduplication
  -> reading policy
  -> speech formatter
  -> bounded single-active speech queue
  -> TTS and audio focus
```

Important binding decisions:

- Activity lifecycle must never own notification or speech processing.
- Exact WhatsApp packages only: `com.whatsapp`, `com.whatsapp.w4b`.
- No notification cancellation, DND manipulation, global volume writes, or
  synthetic media play/pause events.
- DataStore will be the settings source of truth.
- No conversation identifiers or names are persisted.
- Message bodies and sender/group names must not be logged or persisted.
- Reader defaults disabled; riding mode defaults inactive.
- Group reading has no enablement and is always disabled.
- Speech queue capacity is 20 pending items, maximum age 180 seconds, maximum
  utterance text length 240 characters, and only one utterance may be active.
- API 35+ production speech must start a short-lived `mediaPlayback` foreground
  service before requesting audio focus. If startup, promotion, or focus fails,
  speech is skipped. No-FGS is limited to API 26-34.
- No boot receiver in version 1.

## Important Review Correction

The real-device spike showed audio focus succeeding without an FGS on one
LineageOS Android 15 device. Official Android 15 behavior nevertheless requires
an app targeting API 35+ to be topmost or running a foreground service before it
requests audio focus. `ADR-006` was revised during `P0-R01` so production follows
the official rule rather than relying on this OEM-specific observation.

## Toolchain And Test Device

Local toolchain:

- Windows 11.
- JDK 21.0.8 locally; CI is configured for JDK 17.
- Android SDK Platform 36 installed.
- Android Build Tools 36.0.0 installed.
- AGP 8.13.0.
- Gradle 8.13.
- Kotlin 2.3.21.
- Compose BOM 2026.06.01.

Connected test device used:

- Xiaomi Mi Mix 2S (`polaris`).
- LineageOS 22.2.
- Android 15 / API 35.
- Device coverage is not representative of stock Pixel, Samsung, MIUI/HyperOS,
  or other OEM behavior.

## Last Verification Results

Commands and results:

```text
gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
BUILD SUCCESSFUL - 55 tasks; 95 tests

gradlew assembleRelease
BUILD SUCCESSFUL - 51 tasks

adb install -r app/build/outputs/apk/debug/app-debug.apk
Success

adb shell am start -W -n com.ridenotify.app.wa_reader/.MainActivity
Status: ok
LaunchState: COLD
TotalTime: 1144 ms
```

The later partial qualification run, after notification access was enabled,
reported a warm activity start of 2,765 ms because Android immediately rebound
the listener process. UI-absent memory after 10 seconds was 73,406 KB PSS and
179,204 KB RSS on the debug build. These are single-device samples, not release
performance claims.

Package-manager inspection confirmed:

- version code 2 / version name 2.0.0;
- min SDK 26 / target SDK 36;
- `.MainActivity` registered as launcher;
- `.MyNotificationListener` registered with the correct service action and
  `BIND_NOTIFICATION_LISTENER_SERVICE` permission;
- no product runtime permission is currently requested.

The release APK is currently unsigned by design. Release signing belongs to
`P6-T05`.

## Current File Map

```text
app/
  build.gradle.kts
  proguard-rules.pro
  src/main/AndroidManifest.xml
  src/main/java/com/ridenotify/app/wa_reader/
    MainActivity.kt
    MyNotificationListener.kt
    WaReaderApplication.kt
    di/AppContainer.kt
    model/*.kt
  src/main/res/values/
  src/test/java/com/ridenotify/app/wa_reader/

docs/
  CURRENT_STATUS.md
  DEVELOPMENT.md
  PHASE_STATUS.md
  PROJECT_CHARTER.md
  ARCHITECTURE.md
  IMPLEMENTATION_PLAN.md
  WORKER_BEE_PROMPTS.md
  adr/
  fixtures/
  reviews/

spikes/audio-background/
  Disposable test project and retained evidence only
```

Generated `.gradle/` and `**/build/` directories are not source and may be
recreated by Gradle.

## Next Ready Tasks

1. Complete the remaining physical-device matrix for `P4-T05`, `P6-T02`,
   physical `P6-T04`, and `P6-T05` install/upgrade verification; retain the
   partial evidence already recorded.
2. Resolve or explicitly accept every critical/high campaign defect.
3. Complete `P4-R01`, finalize `P5-R01`, run final release verification, and
   complete `P6-R01`.

## Open Risks And Blockers

- The prepared GitHub Actions workflow has not run until the initial push.
- Existing release/distribution history and signing-key custody are unknown.
  This blocks release continuity, not local development.
- Background FGS startup from an NLS callback succeeded only on the connected
  LineageOS device and is not documented as a universal Android exemption.
  Production must catch rejection and skip speech.
- API 33, 34, 36 and additional OEM/device tests remain outstanding.
- Release R8 succeeds but emits Kotlin-metadata compatibility warnings; review
  AGP/R8/Kotlin compatibility before a public signed rollout.
- No emulator or `androidTest` suite is configured, so instrumentation evidence
  remains part of the final physical qualification campaign.
- Notification fixtures are synthetic. Real WhatsApp structure still requires
  privacy-safe validation during later device qualification.
- Phase 3 listener behavior still needs real-device WhatsApp validation before
  it can be presented as a working product feature.

## Version-Control Procedure

Git is now the project history and rollback mechanism:

1. Treat this document and `docs/PHASE_STATUS.md` as the state ledger.
2. Work in small reviewed commits and inspect `git status` and `git diff`
   before every commit.
3. Never commit `.gradle/`, `**/build/`, `local.properties`, signing keys,
   passwords, or tokens.
4. After each phase task, record changed files, verification commands, result, and
   next ready task under `docs/reviews/`.
5. Do not delete `docs/adr/`, `docs/fixtures/`, `docs/reviews/`, or the audio
   spike evidence without an explicit reviewed decision.
6. Push reviewed commits to the public GitHub remote so local disk is not the
   only copy.

## Resume Checklist

- [ ] Read this file and `docs/PHASE_STATUS.md`.
- [ ] Confirm the workspace path is correct.
- [ ] Confirm no concurrent edits occurred during the pause.
- [ ] Run `gradlew testDebugUnitTest lintDebug assembleDebug`.
- [ ] Confirm the result is `BUILD SUCCESSFUL` before editing.
- [ ] Claim exactly one next task and update the task ledger.
- [ ] Preserve the architecture and privacy rules above.
