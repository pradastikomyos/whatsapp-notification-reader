# Reviewed Native Architecture

## Review Verdict

The product should be rebuilt as a native Kotlin Android application. Its main
capabilities are Android system integrations, while the old Flutter activity is
currently a required bridge for notification delivery. Native Kotlin removes
that lifecycle dependency and keeps the critical path in one runtime.

Use one Gradle application module initially. Package boundaries provide enough
separation for this project; multiple Gradle modules would add build and wiring
cost before they provide a measurable benefit.

## Technology Baseline

- Kotlin and Kotlin Coroutines.
- Jetpack Compose for the configuration UI.
- ViewModel plus StateFlow with unidirectional UI state.
- DataStore for application settings.
- Room only for an observed-conversation catalogue, if approved.
- Native `NotificationListenerService`.
- Native `TextToSpeech` with `UtteranceProgressListener`.
- Native `AudioManager` and `AudioFocusRequest`.
- A short-lived foreground playback service for attempted speech on API 35+;
  no-FGS is permitted only on API 26-34. Startup/focus failure skips speech.
- A manual dependency container initially; use Hilt only if wiring complexity
  materially grows.

Project scaffolding must apply the minimum SDK, target SDK, Compose BOM, Kotlin,
AGP, and dependency versions approved from current stable guidance in Phase 0.

## Package Layout

```text
app/src/main/java/com/ridenotify/app/wa_reader/
  WaReaderApplication.kt
  MainActivity.kt
  di/
    AppContainer.kt
  model/
    NotificationSnapshot.kt
    ParsedMessage.kt
    AppSettings.kt
    ReadingDecision.kt
    SpeechRequest.kt
  notification/
    WhatsAppNotificationListenerService.kt
    NotificationSnapshotFactory.kt
    NotificationIngress.kt
    ListenerConnectionRepository.kt
    parser/
      NotificationParser.kt
      WhatsAppNotificationParser.kt
      MessagingStyleParser.kt
      LegacyExtrasParser.kt
    dedup/
      NotificationDeduplicator.kt
      NotificationFingerprint.kt
  policy/
    ReadingPolicyEvaluator.kt
    MessageSanitizer.kt
    SpeechTextFormatter.kt
  speech/
    SpeechCoordinator.kt
    SpeechPlaybackService.kt
    SpeechQueue.kt
    TtsEngine.kt
    AndroidTtsEngine.kt
    AudioFocusController.kt
  data/
    settings/
      SettingsRepository.kt
      DataStoreSettingsRepository.kt
    conversations/
      ConversationRepository.kt
      ObservedConversation.kt
      room/
  riding/
    RidingModeRepository.kt
  platform/
    NotificationAccessController.kt
    BatteryOptimizationController.kt
  ui/
    WaReaderApp.kt
    navigation/
    onboarding/
    home/
    settings/
    groups/
    riding/
```

Do not create empty abstractions only to match this tree. Add an interface where
it creates a test boundary or supports more than one implementation.

## Runtime Flow

### Notification ingress

1. `WhatsAppNotificationListenerService` receives `StatusBarNotification`.
2. It permits exact package IDs only: `com.whatsapp` and `com.whatsapp.w4b`.
3. `NotificationSnapshotFactory` immediately copies required framework data into
   an immutable model.
4. `NotificationIngress` submits the snapshot to one serialized, bounded pipeline.
5. Parsing, deduplication, policy evaluation, formatting, and speech submission
   happen outside the callback's critical section.

The listener must not cancel WhatsApp notifications, mutate their channels,
change interruption policy, launch an activity, or depend on `MainActivity`.

### Snapshot contract

Capture only fields needed for parsing and diagnosis:

- package name, notification key/ID, post time, and group key;
- title, text, big text, text lines, subtext, and summary text;
- category and group-summary state;
- conversation title and shortcut/conversation identifier when available;
- `MessagingStyle` messages, sender `Person`, and message timestamps.

Framework `Notification` objects must not flow into pure domain components.

### Parser chain

Use conservative ordered parsing:

1. Structured `MessagingStyle` content.
2. Structured conversation metadata.
3. Legacy title/text fallback.
4. Explicit unsupported, summary, call, security, attachment, or redacted result.

A colon in message text is not evidence of a group. Locale-specific phrase
matching may be a fallback but cannot be the primary classifier.

### Deduplication

Use a bounded, time-aware in-memory cache keyed from normalized package,
conversation, sender, content, and source timestamp. The contract must cover:

- identical callback repeats;
- bundled-notification updates;
- newly appended `MessagingStyle` messages;
- rapid distinct messages from one conversation;
- stale notifications delivered after reconnect.

Do not persist message bodies merely for deduplication unless an ADR explicitly
accepts the privacy and retention consequences.

### Reading policy

The evaluator is pure Kotlin and receives `ParsedMessage`, current settings,
selected conversations, riding state, and a clock. It returns a reasoned result:

```text
Speak
SkipReaderDisabled
SkipRidingModeInactive
SkipPrivateDisabled
SkipGroupNotSelected
SkipRedacted
SkipUnsupported
SkipTooOld
```

Duplicate suppression is a pipeline outcome before policy evaluation, not a
`ReadingPolicyEvaluator` decision. The ingress pipeline owns duplicate diagnostic
events and their tests.

Group behavior is explicit:

```text
ALL_OBSERVED_GROUPS
SELECTED_GROUPS_ONLY
NO_GROUPS
```

An empty selection must never ambiguously mean both all groups and no groups.

### Speech pipeline

`SpeechCoordinator` is application/service scoped, never ViewModel scoped. It
owns one bounded queue and one active utterance. The queue policy must define:

- ordering and capacity;
- maximum message age and text length;
- overflow behavior;
- interruption versus append behavior;
- whether related burst messages are combined;
- what disable or riding-mode changes do to queued speech;
- behavior during calls, alarms, focus loss, TTS timeout, and engine errors.

`AndroidTtsEngine` waits for asynchronous initialization, verifies locale
availability, assigns unique utterance IDs, reports progress, and shuts down only
when its owning service is permanently released.

`AudioFocusController` requests speech-appropriate transient focus, handles loss,
and abandons focus after completion or failure. It never changes global volume
and never emits media-button pause/play commands.

## UI And State

The activity is configuration UI only. Closing it must not disable the listener
or speech pipeline. The home screen displays independent facts:

- notification access granted;
- listener connected, when currently known;
- reader preference enabled;
- riding policy and effective state;
- TTS engine and requested locale availability.

DataStore is the settings source of truth for both service and ViewModels. UI
controls update repositories rather than maintaining disconnected local state.
During process cold start, ingress must fail closed until the first valid settings
snapshot is loaded, using a bounded initialization timeout. It must never speak
using guessed defaults when persisted settings are not yet available.

Group discovery and group selection are separate. Only observed conversations
may appear automatically. A newly observed group starts unselected when policy is
`SELECTED_GROUPS_ONLY`.

## Background And Lifecycle Rules

- Notification access is special system access, not a runtime permission.
- Android binds the listener; do not call `startService()` on it.
- Process-local static state is not durable.
- Initialize service dependencies from application/service context.
- Do not assume `onDestroy()` runs after process termination.
- A foreground service is not a permanent keep-alive mechanism.
- If needed, foreground playback exists only while the speech queue is active.
- If required audio focus or a legally startable playback service is unavailable,
  speech fails closed; it must not proceed by bypassing Android restrictions.
- Version 1 handles calls and alarms through approved audio-focus callbacks and
  non-privileged audio state only. Additional phone-state permissions require a
  separate ADR and are not implied by this architecture.
- Android force-stop cannot be bypassed and must be documented honestly.
- Battery-optimization exclusion is optional guidance, not a reliability promise.
- A boot receiver is omitted unless device tests establish a concrete need.

## Manifest Policy

Create a minimal manifest rather than copying the old one. Expected components:

- Main activity, not exported unless launcher requirements make it necessary.
- Notification listener service protected by
  `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`.
- TTS engine query required by the supported Android versions.
- Optional playback service and matching foreground-service permissions only
  after the technical spike approves that design.

Do not request privileged or unrelated permissions such as `DEVICE_POWER`,
`PREVENT_POWER_KEY`, `MEDIA_CONTENT_CONTROL`, or `DISABLE_KEYGUARD`.

## Storage And Privacy

- Persist settings and, if approved, observed conversation identifiers/names.
- Do not persist notification content or message history by default.
- Redact sender and body content from production logs and crash reports.
- Decide Android backup behavior explicitly.
- Document retention, deletion, and reset behavior.
- Keep network and analytics dependencies out of version 1 unless approved.

## Old-To-New Migration Map

| Old source | New responsibility | Decision |
|---|---|---|
| `lib/main.dart` | Compose activity/screens | Rebuild UI; discard TTS lifecycle ownership |
| `lib/notification_service.dart` | ingress, policy, settings, speech | Split and redesign |
| `MyNotificationListener.kt` | native listener and snapshot | Rewrite; preserve only service concept |
| `MainActivity.kt` | Compose host | Remove all method channels |
| `MediaControlHelper.kt` | audio focus controller | Full rewrite; no media key events |
| `NotificationHelper.kt` | riding repository | Remove DND behavior |
| `WorkaroundService.kt` | none | Do not migrate |
| `settings_page.dart` | validated settings UI | Rebuild without placeholders |
| `group_selection_page.dart` | observed-group UI | Remove fabricated groups |
| `riding_mode_page.dart` | manual riding policy UI | Remove fabricated statistics |
| old manifest | minimal native manifest | Rewrite from scratch |
| Flutter template test | native test suites | Discard |

## Required Architecture Decisions

Each decision is recorded as an ADR before dependent implementation starts:

1. `ADR-001`: preserve or change application ID and signing identity.
2. `ADR-002`: minimum SDK and supported Android/OEM matrix.
3. `ADR-003`: parser contract, supported WhatsApp variants/locales, fixtures.
4. `ADR-004`: conversation identity and observed-group catalogue.
5. `ADR-005`: speech queue, expiry, aggregation, and interruption policy.
6. `ADR-006`: API 35+ audio focus and foreground playback service.
7. `ADR-007`: exact manual riding-mode semantics.
8. `ADR-008`: omit or retain boot behavior based on evidence.
9. `ADR-009`: privacy, storage, logging, backup, and reset.
10. `ADR-010`: manual dependency container versus Hilt.
11. `ADR-011`: TTS locale, voice selection, and missing-engine UX.
12. `ADR-012`: evidence-based battery and OEM guidance.

`ADR-001` must also decide whether to preserve the old enabled listener component
identity `com.ridenotify.app.wa_reader.MyNotificationListener` or require users to
reauthorize a renamed component. It must inventory each old SharedPreferences key
and define migrate, transform, or reset behavior; old selected-group semantics
must not be mapped implicitly to the new three-mode policy.

## Highest-Risk Spikes

### Spike A: background TTS and audio focus

Validate notification-triggered TTS with the activity absent on the exact OS and
target-SDK combinations approved in ADR-002, including API 33, 34, 35, and the
current release target. Verify whether a media-playback foreground service is
required and may legally be started from an actual listener callback. Record the
platform flow and failure modes. Unsupported combinations must skip speech rather
than bypass focus or foreground-service restrictions.

### Spike B: notification format corpus

Capture sanitized fixtures for WhatsApp and Business across supported Android
versions, private/group messages, summaries, multiple messages, attachments,
redaction, Indonesian/English UI, and updates. No production message content may
be committed.

## Recheck Result

The architecture is approved conditionally. Implementation may proceed after
Phase 0, but the foreground playback design cannot be finalized until Spike A
passes. The old architecture's static MethodChannel, Flutter lifecycle cleanup,
global volume manipulation, colon parser, self-selecting groups, notification
cancellation, DND changes, fake statistics, missing BootReceiver, and privileged
permissions are explicitly rejected.
