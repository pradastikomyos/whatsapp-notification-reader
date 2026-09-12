# Project Charter

## Mission

Build an Android-only application that reads eligible WhatsApp notifications
aloud in Indonesian while remaining independent from the activity lifecycle,
respecting user privacy, and behaving safely around other audio applications.

## Primary Goal

Replace the Flutter/Dart prototype with a native Kotlin application whose core
pipeline continues to work when the configuration UI is closed:

```text
NotificationListenerService
  -> immutable notification snapshot
  -> WhatsApp parser
  -> deduplication
  -> reading policy
  -> speech formatter
  -> bounded speech queue
  -> native TTS and audio focus
```

## Product Outcomes

1. Read supported direct-message WhatsApp and WhatsApp Business notifications.
2. Let the user enable or disable reading without revoking notification access.
3. Reject every classified group message before speech.
4. Support a clearly defined manual riding mode.
5. Speak through Android TextToSpeech without changing global media volume.
6. Expose accurate service, permission, TTS, and policy status in the UI.
7. Store settings locally and avoid storing message bodies by default.

## Non-Goals For Version 1

- iOS, desktop, or web support.
- Reading notifications from every installed application.
- Reading group conversations.
- Enumerating WhatsApp groups through an unofficial API.
- Persisting notification or message history.
- Automatically detecting whether the user is riding.
- Automatically selecting a vehicle from nearby Bluetooth devices.
- Emergency contacts, priority contacts, or keyword classification.
- Cancelling, rewriting, or hiding WhatsApp notifications.
- Changing Do Not Disturb policy or global media volume.
- Guaranteeing operation after Android force-stop.
- Guaranteeing identical behavior on every OEM or WhatsApp version.

Future features require separate requirements, privacy review, and acceptance
criteria. Placeholder UI must not be shipped.

## Users And Core Journeys

### First run

1. User sees why notification access is required.
2. User opens Android notification-listener settings.
3. App reports access and listener connection as separate states.
4. App validates that an Indonesian TTS voice is available.
5. User enables the reader and runs a local test utterance.

### Daily use

1. WhatsApp posts a supported message notification.
2. The listener captures it while the activity may be absent.
3. Duplicate, stale, unsupported, or disallowed messages are skipped.
4. Eligible text is queued; known duplicate updates within the configured
   deduplication window produce at most one speech request.
5. Audio focus is abandoned after completion or failure.

## Constraints

- Preserve application ID `com.ridenotify.app.wa_reader` if this is an update to
  an already distributed application. This requires an explicit Phase 0 decision.
- Use only supported Android APIs and normal application permissions.
- The system binds `NotificationListenerService`; the app does not keep it alive
  by starting it as an ordinary service.
- The process may be killed without `onDestroy()` being called.
- Notification contents can be redacted, summarized, localized, or changed by a
  WhatsApp update.
- Foreground-service and audio-focus rules vary by Android API level.
- Message text and sender names are sensitive data and must not appear in
  production logs.

## Success Metrics

Technical metrics for the agreed test corpus and device matrix:

- Zero dependency on Flutter, Dart, MethodChannel, or FlutterEngine.
- Eligible notifications reach the native pipeline with the activity removed
  from recents.
- No duplicate speech for known notification update scenarios.
- No system media-volume changes and no synthetic media play/pause events.
- Reader, direct-message, riding, and unconditional group-rejection policies pass deterministic tests.
- Queue remains bounded during message bursts.
- Message bodies are neither persisted nor logged by default.
- Clean release build, static analysis, unit tests, and instrumented smoke tests pass.

Product metrics must be defined after the supported-device and WhatsApp-version
matrix is approved. Avoid unmeasured reliability or safety claims.

## Definition Of Done

Version 1 is done only when all phase gates in `IMPLEMENTATION_PLAN.md` pass,
the architecture decisions are recorded, real-device testing is documented,
and user-facing claims match observed behavior.
