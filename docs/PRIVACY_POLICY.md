# RideNotify Privacy Policy

Effective date: 2026-09-12

RideNotify processes supported WhatsApp and WhatsApp Business notification data
locally to decide whether to speak it aloud. The app does not include network,
analytics, advertising, account, contact-upload, or message-history features.
Group notifications may be classified in memory but are never spoken.

## Data Used Locally

- Notification text and sender data are used in memory for parsing, policy, and
  speech. They are not stored as message history.
- Settings are stored in Android DataStore: reader/riding state, direct-message
  policy, direct-sender announcement preference, speech rate, and the persisted
  theme preference (`SYSTEM`, `LIGHT`, or `DARK`).
- No conversation catalogue or group identity metadata is retained. Upgrades
  delete the retired observed-conversation database and group-selection settings.

## Retention And Deletion

- Notification content is discarded after in-memory processing.
- Settings remain on the device until the user resets app settings, clears app
  storage, or uninstalls the app.
- Android backups are disabled for this app (`allowBackup=false`).

## System Access

Notification access is granted and revoked in Android's special-access settings.
RideNotify uses Android TextToSpeech and selects only an installed Indonesian
voice that does not require a network connection. If no such local voice exists,
notification content is not spoken. The app does not request contacts, location,
Bluetooth, phone-state, media-control, or Do Not Disturb access.

## Changes

Material changes to data collection or sharing require a revised privacy review
and policy before release.
