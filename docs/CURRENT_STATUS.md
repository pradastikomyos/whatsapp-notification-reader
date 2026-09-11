# Current Project Status

Last updated: 2026-09-11
Workspace: `C:\Users\prada\Documents\whatsapp notification reader`
Source prototype: `C:\Users\prada\Documents\prjkwanotif`
Version control: Git repository initialized on branch `main`; public GitHub
remote is `https://github.com/pradastikomyos/whatsapp-notification-reader`.

This document is the canonical pause/resume handoff. Read it before making any
new change.

## Executive Status

- Phase 0, architecture decisions and evidence: **Passed**.
- Phase 1, native Android foundation: **Passed locally**.
- Phase 2, domain and data: **In progress**.
- Current completed Phase 2 tasks: `P2-T01 Domain models` and
  `P2-T02 DataStore settings`.
- No production notification parsing, TTS, audio focus,
  foreground playback service, or finished product UI exists yet.
- The app currently builds, installs, and opens as a native Compose foundation.

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

Implemented pure Kotlin models:

- `NotificationSnapshot` and MessagingStyle snapshots.
- `ConversationId`, conversation type, group mode, and riding state.
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
- Invalid persisted enums, rates, and blank conversation IDs fail to safe defaults.
- Robolectric integration tests exercise real Preferences DataStore files.

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
- Room is approved only for group conversation catalogue metadata.
- Direct-message identities/names must not be persisted.
- Message bodies and sender/group names must not be logged or persisted.
- Reader defaults disabled; riding mode defaults inactive.
- Group policy defaults to `NO_GROUPS`.
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
BUILD SUCCESSFUL - 53 tasks; 24 tests

gradlew assembleRelease
BUILD SUCCESSFUL - 49 tasks

adb install -r app/build/outputs/apk/debug/app-debug.apk
Success

adb shell am start -W -n com.ridenotify.app.wa_reader/.MainActivity
Status: ok
LaunchState: COLD
TotalTime: 1144 ms
```

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

The following Phase 2 tasks are ready after `P2-T02`:

1. `P2-T03 Notification snapshot extraction`
2. `P2-T05 Notification deduplication`
3. `P2-T07 Conversation repository`

Recommended resume order without workers:

1. Implement `P2-T03` with Robolectric-built Notification/MessagingStyle tests.
2. Implement `P2-T05` as a pure bounded, time-aware cache.
3. Implement `P2-T07` with Room for group metadata only.
4. Implement `P2-T04` after the snapshot contract is stable.
5. Implement `P2-T06` after parser outputs are stable.
6. Run `P2-R01` before entering Phase 3.

Do not implement listener callback processing yet. `MyNotificationListener` is
intentionally an empty registered shell until Phase 3.

## Open Risks And Blockers

- The prepared GitHub Actions workflow has not run until the initial push.
- Existing release/distribution history and signing-key custody are unknown.
  This blocks release continuity, not local development.
- Background FGS startup from an NLS callback succeeded only on the connected
  LineageOS device and is not documented as a universal Android exemption.
  Production must catch rejection and skip speech.
- API 33, 34, 36 and additional OEM/device tests remain outstanding.
- Notification fixtures are synthetic. Real WhatsApp structure still requires
  privacy-safe validation during later device qualification.
- No production feature should be presented as working yet; the installed app
  is only a foundation screen and registered empty listener.

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
