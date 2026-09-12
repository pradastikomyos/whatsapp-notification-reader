# RideNotify

RideNotify is an Android app that can read eligible direct-message WhatsApp and
WhatsApp Business notifications aloud in Indonesian while the configuration UI is closed.
It uses Android notification access, a persisted manual riding mode, a bounded
speech queue, Android TextToSpeech, and transient audio focus.

Group notifications are classified but never spoken. The app does not read every installed application, store message history, detect
riding automatically, manage Bluetooth vehicles, change Do Not Disturb, or alter
global media volume. Real-device compatibility is qualified separately; see the
current status before relying on a feature on a particular phone.

## Build

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The unsigned local release APK is under `app\build\outputs\apk\release\`.
For a signed release, supply all of these Gradle properties outside source
control: `RIDENOTIFY_STORE_FILE`, `RIDENOTIFY_STORE_PASSWORD`,
`RIDENOTIFY_KEY_ALIAS`, and `RIDENOTIFY_KEY_PASSWORD`.

## Documentation

- `docs/CURRENT_STATUS.md`: current implementation and qualification status.
- `docs/PHASE_STATUS.md`: phase gates and remaining debt.
- `docs/DEVELOPMENT.md`: local development commands.
- `docs/PRIVACY_POLICY.md`: data handling and deletion policy.
- `docs/RELEASE_OPERATIONS.md`: signing, rollout, rollback, and monitoring.
- `docs/reviews/DEVICE_QUALIFICATION_CAMPAIGN.md`: final device campaign.
