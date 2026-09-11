# Notification Fixture Corpus

**All files in this directory are synthetic, fabricated test data.** No file
was captured from a real WhatsApp account, a real device, or any real person.
Every name, phone-adjacent identifier, group name, message body, and URL is
invented for this project. This corpus is the acceptance corpus referenced by
`docs/adr/ADR-003-parser-contract-and-fixtures.md`; see that ADR for the full
schema definition, the parser contract each fixture exercises, and the
supported-variant/locale scope.

Each file's `syntheticDataNotice` field repeats this notice so it survives
even if a file is copied or read in isolation.

## Schema

See `ADR-003-parser-contract-and-fixtures.md` section "1. Fixture
serialization schema" for the authoritative field-by-field definition. In
short: each file has case metadata (`fixtureId`, `caseDescription`,
`waVariant`, `localeUi`, `expectedParserOutcome`) plus a `captures` array. Most
cases have exactly one capture; cases that model an in-place update or a
repeated callback have two, sharing the same `notificationKey`/`notificationId`.

`groupKey` is Android's OS-level notification-bundling key
(`Notification.Builder#setGroup`), unrelated to whether the WhatsApp chat
itself is a group chat. Every `groupKey` value here is an illustrative
placeholder, not a value captured from a real device.

## Case Index

| File | Case | Expected outcome |
|---|---|---|
| `direct-private-message.json` | Direct/private message, `com.whatsapp` | `MessagingStyleMessages` |
| `group-message.json` | Group-chat message, `com.whatsapp` | `MessagingStyleMessages` |
| `direct-message-business.json` | Direct message, `com.whatsapp.w4b` | `MessagingStyleMessages` |
| `messaging-style-multi-message-bundle.json` | Multi-message bundle, one conversation | `MessagingStyleMessages` |
| `notification-summary.json` | OS group-summary notification | `Summary` |
| `multi-message-update.json` | In-place update adding one message | `MessagingStyleMessages` |
| `attachment-only-message.json` | Image/video/document, no text body | `Attachment` |
| `redacted-locked-notification.json` | Lock-screen-redacted content | `Redacted` |
| `message-with-url.json` | Message body containing a URL | `MessagingStyleMessages` |
| `baseline-timestamp-only.json` | Ordinary baseline/control case | `MessagingStyleMessages` |
| `direct-message-with-colon.json` | Direct message, colon in body (guard case) | `MessagingStyleMessages` |
| `locale-id-notification.json` | Indonesian-UI generated string | `Attachment` |
| `locale-en-notification.json` | English-UI equivalent scenario | `Attachment` |
| `duplicate-callback.json` | Identical notification delivered twice | `MessagingStyleMessages` |

14 files, covering every case required by `IMPLEMENTATION_PLAN.md` `P0-T04`
(direct, group, summary, bundled update, attachment, redacted, URL, timestamp,
colon-containing direct message, Indonesian/English UI, and duplicate
callbacks), plus the WhatsApp Business variant and a `MessagingStyle`
multi-message bundle called out separately in `WORKER_BEE_PROMPTS.md`.

## Known Gaps

No `Call` or `Security`-outcome fixture exists yet; `PROJECT_CHARTER.md` and
`ARCHITECTURE.md` do not currently define call-reading or security-banner
behavior, so no synthetic fixture was fabricated to imply a decision that was
never made. See `ADR-003-parser-contract-and-fixtures.md` Consequences.

## Adding A New Fixture

1. Add a new file following the schema above; never redefine an existing
   file's meaning in place.
2. Use only fabricated names/numbers/text; keep the `syntheticDataNotice`
   field.
3. Update the case index table above and, if it changes parser scope, update
   `ADR-003-parser-contract-and-fixtures.md`.
4. Validate the file as JSON before committing.
