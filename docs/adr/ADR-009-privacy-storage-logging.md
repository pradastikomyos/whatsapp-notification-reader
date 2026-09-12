# ADR-009: Privacy, Storage, Logging, Backup, And Reset

## Status

Accepted except observed-group storage, which is superseded by ADR-013 and removed.
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`PROJECT_CHARTER.md` requires that message bodies are not persisted by
default (Product Outcome 8, Success Metric "Message bodies are neither
persisted nor logged by default"), that message text and sender names are
sensitive data that must not appear in production logs (Constraints), and
that Non-Goals exclude persisting notification/message history. It also
requires v1 to avoid analytics/network dependencies unless approved
(`ARCHITECTURE.md`, "Storage And Privacy": "Keep network and analytics
dependencies out of version 1 unless approved").

`ARCHITECTURE.md` requires this ADR to decide: message/conversation
retention, production logging redaction, Android backup policy, reset
behavior, and analytics/network policy. It also states the technology
baseline: DataStore for settings, Room only for an observed-conversation
catalogue "if approved," and that deduplication must not persist message
bodies "unless an ADR explicitly accepts the privacy and retention
consequences" - this ADR does not grant that exception; `ADR-009` leaves
in-memory-only deduplication as the approved baseline.

### Evidence from the old app's actual behavior (negative examples)

Direct inspection of `C:\Users\prada\Documents\prjkwanotif\lib\notification_service.dart`
via the `terminal` tool (not reachable through project-scoped file tools)
surfaced concrete practices this ADR explicitly does not carry forward:

- Line 201: `debugPrint('Ignoring message from unselected group: $group')` -
  logs a real conversation/group name to the debug console.
- Line 111-113: an invalid-payload branch does
  `debugPrint('Ignoring notification callback with invalid payload: $args')`,
  where `$args` is the raw method-channel map that, on the valid-payload path
  a few lines later, is known to contain `sender` and `message` fields. If an
  otherwise-valid payload ever failed the `is! Map` check in a way that still
  interpolated raw content, sender/message data could reach the log; this is
  exactly the class of mistake this ADR's redaction rule is written to
  prevent by construction (see Decision, "Logging redaction rules").
- `lib/settings_page.dart`'s `_resetSettings()` (verified in `ADR-001`)
  resets only 3 of the old app's 7 known preference keys, leaving
  `isServiceActive`, `speechRate`, `selectedGroups`, and
  `isRidingModeActive` untouched. This is cited as the concrete negative
  example motivating the exhaustive reset requirement below.
- No analytics, crash-reporting, or network dependency appears in
  `pubspec.yaml`; the old app's own `README.md` states no data is sent to
  any server. This is one data point supporting (not proving) that a
  no-analytics/no-network v1 is achievable without losing parity with the
  old app's actual behavior.

## Decision

### 1. Message and conversation retention

- Message bodies, sender display names, and any other notification text
  content (title, text, big text, text lines, subtext, summary text,
  `MessagingStyle` message bodies) are **never persisted** to DataStore,
  Room, disk cache, or any other durable store, in any build variant,
  including debug builds. They exist only in memory, for the duration of one
  pipeline pass (`NotificationSnapshotFactory` -> parser -> deduplicator ->
  policy -> speech), and must not be retained after the corresponding speech
  request completes, fails, or is dropped.
- Deduplication (`NotificationDeduplicator`) uses an in-memory, bounded,
  time-aware cache of fingerprints only (normalized package, conversation,
  sender, content, and timestamp hashed/fingerprinted per
  `ARCHITECTURE.md`'s "Deduplication" section), never raw message text. This
  ADR does not accept the "privacy and retention consequences" exception
  `ARCHITECTURE.md` reserves for persisting message bodies for
  deduplication; that exception remains closed for v1.
- The only conversation-related data that may be persisted is the
  **observed-conversation catalogue** scoped by `ADR-004`: a stable
  conversation identifier and a display name/title sufficient for the group
  ALL/SELECTED/NONE picker UI. This is metadata about which conversations
  exist, not message content, and it must contain no message bodies, no
  message timestandard beyond "last observed at" if `ADR-004` requires it for
  UI/retention purposes, and no per-message history.
- Settings (reader enabled, private/group policy, riding mode, TTS rate,
  selected-conversation identifiers) persist in DataStore as the source of
  truth per `ARCHITECTURE.md`, "UI And State." Settings values themselves are
  not sensitive content in the same sense as message bodies, but selected
  -conversation identifiers are still conversation metadata and are covered
  by the backup and reset rules below.

### 2. Production logging redaction rules

- Message text, sender display names, and conversation/group display names
  must never appear in any log statement, log tag, log message, structured
  log field, or crash/exception report, in release builds, **or in debug
  builds**, with no build-variant exception. The old app's
  `debugPrint('Ignoring message from unselected group: $group')` pattern
  (Context) is the exact shape of defect this rule forbids; the equivalent
  native statement should log a policy-decision enum value from
  `ReadingPolicyEvaluator`'s `Skip*`/`Speak` result. If event correlation is
  essential, use a process-local, one-way truncated hash with an ephemeral
  per-process salt. Raw conversation IDs, notification keys, shortcut IDs,
  display names, and text are forbidden.
- Any exception whose message or stack trace could embed a framework object
  that carries notification content (for example, a `Notification` or
  `StatusBarNotification` object's default `toString()`) must not be logged
  directly; if the exception itself is logged, its type and a redacted
  summary may be logged, but the framework object must not be interpolated
  as-is. `ARCHITECTURE.md`'s snapshot boundary (`NotificationSnapshotFactory`
  copying only required fields into an immutable model) is also a logging
  safety boundary: components downstream of the snapshot never hold a
  reference to the original `Notification`, so they structurally cannot leak
  it into a log call.
- Crash reporting: version 1 ships with **no third-party crash-reporting or
  analytics SDK** (see Decision item 5). Any crash data collected is
  whatever Android's own Play Console "Android vitals"/ANR & crash reporting
  captures automatically, which is outside the app's control and outside
  this ADR's scope to redact; the app must not additionally attach message
  content, sender names, or conversation names to any custom crash
  metadata, breadcrumb, or non-fatal report field it does add (for example,
  a manually reported non-fatal exception around TTS or audio-focus
  failures - those reports may include enum-valued state such as the
  `ReadingDecision` result, the parser's structured failure reason, or
  Android API level, but never the message text or names).
- Log level discipline: reader/riding/private/group policy decisions may be
  logged at debug/verbose level using their sealed-result enum values only
  (e.g. `SkipGroupNotSelected`, `SkipRedacted`) plus coarse timing/API-level
  metadata or the ephemeral correlation hash described above. This is sufficient for
  diagnosing pipeline behavior without ever needing message content in logs.

### 3. Android auto-backup policy

- Android's default auto-backup (`android:allowBackup`, Auto Backup for Apps
  on API 23+, and any use of `BackupAgent`/cloud backup) must **exclude** all
  files that can contain settings or observed-conversation data, because the
  observed-conversation catalogue is conversation metadata (Decision item 1)
  and the DataStore file can contain selected-conversation identifiers that,
  while not message content, the user may reasonably not want silently
  copied to a cloud backup tied to their Google account.
- Concretely: set `android:allowBackup="false"` in the application manifest
  for v1, which is the simplest verifiable way to guarantee no DataStore
  Preferences file, no Room database file (if `ADR-004` approves Room), and
  no other app-private file is included in any backup mechanism (cloud
  auto-backup, `adb backup`, or OEM backup agents that respect this flag).
  This is a stronger guarantee than an inclusion/exclusion XML rule (which
  is easy to get wrong by omission when a new file is added later) and
  matches the "no message bodies persisted, minimize what leaves the
  device" posture already required by the charter.
- If a future version needs `allowBackup="true"` for legitimate settings
  continuity across device migration, that requires a superseding ADR that
  explicitly lists every file/tag to include and confirms the
  observed-conversation catalogue and any selected-conversation identifiers
  are excluded via a `fullBackupContent`/`dataExtractionRules` XML with named
  `exclude` entries for the DataStore Preferences file and the Room database
  file (by their concrete file names, once `P1-T01`/`P2-T02`/`P2-T07`
  establish them) - not a blanket allow.
- No message content is ever at risk from this decision either way, because
  Decision item 1 already forbids persisting it; this backup rule is about
  settings/conversation metadata, not message bodies.

### 4. Reset behavior

A "reset app data" action (whether an in-app control or Android's system
"Clear storage"/"Clear data") must result in a state indistinguishable from
a fresh install, specifically clearing:

- The entire DataStore Preferences file (all settings: reader enabled,
  private/group message policy, group mode and any per-conversation
  selection, riding-mode state, TTS rate/locale/announcement settings, and
  any other approved settings field), not a subset. The old app's own
  `_resetSettings()` clearing only 3 of 7 known keys (Context) is the
  concrete failure mode this rule forbids repeating.
- The entire observed-conversation catalogue (Room database or equivalent
  store from `ADR-004`), including conversation identifiers, display names,
  and selection state.
- The bounded in-memory deduplication cache and the bounded in-memory speech
  queue (trivially true since neither is durable, but an in-app "reset"
  control must also flush these live in-process structures immediately
  rather than only clearing durable storage, so behavior after reset is
  actually clean rather than merely appearing clean at next cold start).
- Any file excluded from backup per Decision item 3 is, by definition, not
  restorable after reset; reset must not leave an orphaned backup copy the
  user believes was cleared. Because `allowBackup="false"` is the v1
  decision, this is automatically satisfied and requires no extra work.
- An in-app "reset app data" control, if implemented as a UI feature, is
  itself optional for v1 scope and must not be built as placeholder UI; if
  it is not implemented in v1, this ADR's reset requirement still binds
  Android's own "Clear storage" system action, which the app cannot prevent
  and does not need explicit code for beyond correctly using DataStore/Room
  in default app-private storage locations (no external storage, no
  `MediaStore`, no locations Android's own clear-data does not already
  cover).

### 5. Analytics and network policy for v1

- Version 1 includes **no analytics SDK, no crash-reporting SDK beyond
  Android/Play's own built-in vitals collection, and no network
  dependency of any kind** (no HTTP client, no remote config, no remote
  logging endpoint, no ad SDK, no telemetry pipeline). This matches
  `ARCHITECTURE.md`'s "Keep network and analytics dependencies out of
  version 1 unless approved" and the charter's Non-Goals.
- The manifest must not request `INTERNET` or `ACCESS_NETWORK_STATE`
  permissions in v1. Their absence is itself a verifiable, user-auditable
  proof that no network path exists, independent of code review.
- Any future analytics/crash-reporting/network addition requires a new ADR
  that names the exact SDK, exact data fields transmitted, and confirms
  message content, sender names, and conversation names remain excluded
  under the redaction rules in Decision item 2; it cannot be added silently
  as a "minor dependency update."

## Alternatives Considered

- **Persist message bodies briefly for dedup/debugging, with a short TTL** -
  rejected. `ARCHITECTURE.md` reserves this as an explicit opt-in exception
  requiring its own ADR acceptance of the privacy consequences; this ADR
  does not grant that exception, and an in-memory bounded fingerprint cache
  already satisfies the deduplication contract without ever writing content
  to disk.
- **`allowBackup="true"` with an inclusion/exclusion rules file naming only
  "safe" files** - considered, rejected for v1 in favor of
  `allowBackup="false"`. An allow-list-by-omission approach silently
  includes any new file added later unless the rules file is updated in
  lockstep, which is a fragile guarantee for a privacy property; a hard
  `false` is simpler to verify and audit.
- **Ship a bundled crash-reporting SDK (e.g. Firebase Crashlytics) "since
  it's industry standard"** - rejected for v1. It is a network dependency
  and a third-party data pipeline that the charter's non-analytics posture
  and this task's scope do not approve; it also risks capturing content
  inadvertently unless every custom log/crash call site is independently
  audited, which is more risk than the diagnostic benefit justifies before
  v1 ships.
- **Partial reset (settings only, keep observed-conversation catalogue)** -
  rejected as the default reset behavior, because a partial reset that
  retains conversation metadata after a user explicitly asks to reset is a
  believable privacy surprise; matches the old app's demonstrated failure
  mode (Context) that this ADR is written to avoid repeating.

## Consequences

- `P2-T02` (DataStore settings) must implement full-file reset, not
  per-key clearing, and must not add any settings field that requires
  storing message content.
- `P2-T07` (conversation repository) and any Room usage from `ADR-004` must
  implement full-table/full-database reset and must be named/tagged so a
  future `allowBackup` exclusion list (if ever needed) can target them
  precisely.
- `P1-T02` (manifest) must set `android:allowBackup="false"` and must not
  request `INTERNET`/`ACCESS_NETWORK_STATE`.
- Every worker touching logging (notification ingress, parser, policy,
  speech, UI ViewModels) must follow the redaction rules in Decision item 2;
  `P6-T03` (security and privacy review) must specifically re-audit every
  log call site and crash-report call site against this ADR before release.
- No code change is authorized to add analytics, crash SDKs, or network
  calls without a superseding ADR, per Decision item 5.
- If a future version wants cross-device settings continuity via backup,
  that requires a superseding ADR with named file/tag exclusions, not a
  quiet flip of `allowBackup`.

## Dependent Tasks

- `P1-T02` (minimal manifest and application container) - backup flag and
  permission omissions.
- `P2-T02` (DataStore settings) - full-reset behavior, no content fields.
- `P2-T05` (deduplicator) - in-memory-only fingerprint cache, no persisted
  content.
- `P2-T07` (conversation repository) - catalogue-only persistence, full
  reset.
- `P3-T02` (pipeline orchestration) - redacted diagnostic events only.
- `P4-T01`/`P4-T03` (TTS engine, speech coordinator) - never log spoken text.
- `P6-T03` (security and privacy review) - audits this ADR's rules against
  the shipped code.

## Evidence

- `PROJECT_CHARTER.md`, "Product Outcomes" item 8, "Constraints", "Success
  Metrics", and "Non-Goals For Version 1" - retention, logging, and
  no-history requirements.
- `ARCHITECTURE.md`, "Storage And Privacy", "Deduplication", and "Required
  Architecture Decisions" item 9 - task scope for this ADR.
- `C:\Users\prada\Documents\prjkwanotif\lib\notification_service.dart`
  (lines 111-113, 201) - concrete old-app logging practice used as a
  negative example. Accessed via the `terminal` tool on 2026-09-11 (not
  reachable via project-scoped file tools).
- `C:\Users\prada\Documents\prjkwanotif\lib\settings_page.dart`
  (`_resetSettings()`, verified in `ADR-001`) - concrete old-app partial
  -reset practice used as a negative example. Accessed 2026-09-11.
- `C:\Users\prada\Documents\prjkwanotif\pubspec.yaml` and `README.md` -
  absence of analytics/network dependency in the old app, supporting
  feasibility of the same posture in v1. Accessed 2026-09-11.
