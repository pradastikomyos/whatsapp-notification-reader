# Release Notes: 2.0.0

## Included

- Native Android notification processing for supported WhatsApp variants.
- Manual reader and riding controls, direct-message policy, direct-sender
  announcements, Indonesian text-to-speech testing, and bounded speech playback.
- Group messages are classified and rejected before speech. Group controls,
  observed-group storage, and their dependencies have been removed.
- Upgrade cleanup preserves the previous sender-announcement choice while
  deleting retired group settings and the observed-conversation database.

## Important Limitations

- Notification structure, background behavior, audio routing, and foreground
  service behavior vary by Android version, OEM, WhatsApp version, and device
  configuration.
- The final real-device qualification campaign is required before these notes
  can be used for a production rollout.
