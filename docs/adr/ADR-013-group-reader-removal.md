# ADR-013: Group Reader Removal

## Status

Accepted
Date: 2026-09-12

## Decision

RideNotify speaks direct messages only. Snapshot and parser fields that identify
group conversations remain because fail-closed classification is a safety input.
Every parsed `GROUP` returns `SkipGroupReadingDisabled` before speech formatting,
and the formatter independently rejects `GROUP`.

Unstructured legacy notifications are never inferred as `DIRECT`; direct speech
requires explicit structured direct-chat metadata. Strong multi-sender legacy
evidence may still classify a notification as `GROUP`, which is then rejected.

The group policy enum, selected IDs, observed-groups UI, catalogue repositories,
Room schema, and Room/KSP dependencies are removed. Startup idempotently deletes
`observed_conversations.db` before constructing `AppContainer`.

The sender-announcement setting is direct-only and is named `announceSender`.
DataStore retirement copies the old `announce_sender_and_group` boolean when
needed, then removes that key, `group_read_mode`, and
`selected_conversation_ids`, including for stores whose old migration marker is
already complete. Legacy `flutter.selectedGroups` is also removed.

## Consequences

- No setting or stored selection can enable group speech.
- Group metadata is processed only transiently for classification and diagnostics.
- Historical ADR-004 catalogue behavior and ADR-005 group settings are superseded;
  their records remain unchanged except for explicit status notices.
