# ADR-003: Parser Contract, Supported Variants, And Fixture Corpus

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`ARCHITECTURE.md`'s "Snapshot contract" and "Parser chain" sections require an
ordered, conservative parser: structured `MessagingStyle` content first,
structured conversation metadata second, legacy title/text fallback third,
then an explicit non-message result (unsupported, summary, call, security,
attachment, or redacted). It states a colon in message text is not evidence
of a group and that locale-specific phrase matching may only be a fallback,
never the primary classifier. `ARCHITECTURE.md`'s "Highest-Risk Spikes" (Spike
B) and "Required Architecture Decisions" both require ADR-003 to define the
parser contract, supported WhatsApp variants/locales, and fixtures.
`IMPLEMENTATION_PLAN.md` task `P0-T04` requires a repeatable, privacy-safe
fixture-capture protocol, a synthetic-first corpus, and this ADR plus ADR-004.
`PROJECT_CHARTER.md` requires message text and sender names to never appear in
production logs, and forbids storing message bodies by default; the fixture
corpus itself must honor the same rule even though it is test data, not
production data, because these files are committed to source control.

This task's scope (`P0-T04`, "research/design only, no application code") does
not include building the actual Kotlin parser (`P2-T04`) or the snapshot
extraction adapter (`P2-T03`). It defines the contract and the acceptance
corpus those later tasks must satisfy. No official Android documentation
lookup was performed in this task; the extras/constant names referenced below
(`EXTRA_MESSAGES`, `CATEGORY_MESSAGE` / `"msg"`, `Notification.MessagingStyle`,
`getShortcutId()`) are well-known, non-sensitive public Android API surface
used only to make the contract concrete for implementers, not personal or
proprietary data. `P2-T03`/`P2-T04` implementers must still verify current
platform behavior against official sources before writing extraction code.

## Decision

### 1. Fixture serialization schema

Every file under `docs/fixtures/*.json` uses one common schema:

```text
{
  "syntheticDataNotice": string,   // must always state the data is fabricated
  "fixtureId": string,             // kebab-case, matches the filename stem
  "caseDescription": string,
  "waVariant": "consumer" | "business",
  "localeUi": "id-UI" | "en-UI" | "n/a",
  "expectedParserOutcome": string, // see taxonomy below
  "notes": string?,                // optional clarifying note
  "captures": [
    {
      "captureLabel": string,      // e.g. "initial", "update-1", "delivery-2"
      "packageName": "com.whatsapp" | "com.whatsapp.w4b",
      "notificationKey": string,   // schematic placeholder, not a captured value
      "notificationId": integer,
      "postTimeMillis": integer,
      "groupKey": string | null,   // OS-level bundling key, see note below
      "title": string | null,
      "text": string | null,
      "bigText": string | null,
      "textLines": string[],
      "subText": string | null,
      "summaryText": string | null,
      "category": string | null,
      "isGroupSummary": boolean,
      "conversationTitle": string | null,
      "shortcutId": string | null,
      "messagingStyle": {
        "userDisplayName": string,
        "isGroupConversation": boolean,
        "conversationTitle": string,
        "messages": [
          {
            "text": string,
            "timestampMillis": integer,
            "sender": { "key": string, "name": string, "isBot": boolean }
          }
        ]
      } | null
    }
  ]
}
```

This schema is the canonical fixture-file contract. It is a superset of
`ARCHITECTURE.md`'s "Snapshot contract" field list (package name, notification
key/ID, post time, group key, title, text, big text, text lines, subtext,
summary text, category, group-summary state, conversation title,
shortcut/conversation id, `MessagingStyle` messages/sender/timestamps). `P2-T01`
(domain models) and `P2-T03` (snapshot extraction) must map their Kotlin model
field-for-field onto this schema unless a superseding ADR revises it; any
field rename must update both the Kotlin model and this ADR together.

**Two different "group" concepts must not be confused**: `groupKey` is
Android's OS-level notification-bundling key (`Notification.Builder#setGroup`)
and is unrelated to whether the underlying WhatsApp chat is a group chat.
Group-chat-ness is decided only from `messagingStyle.isGroupConversation` /
conversation metadata, never from the presence or shape of `groupKey`. Every
fixture's `groupKey` value is a schematic placeholder for this reason, not a
value captured from a real device.

### 2. Parser contract (ordered chain)

Matches `ARCHITECTURE.md`'s "Parser chain" exactly, made concrete per tier:

1. **Structured `MessagingStyle` content.** If the snapshot carries one or
   more `MessagingStyle` messages, each becomes one `ParsedMessage` per
   message. Conversation identity comes from `conversationTitle` +
   `shortcutId` per ADR-004, never from the legacy `text` field. This tier
   wins whenever it is present, even if legacy `text`/`title` also exist and
   disagree (for example, `text` may carry a locale-specific
   `"Sender: message"` legacy rendering while `MessagingStyle` carries the
   clean per-message text and structured sender — see
   `group-message.json` and the `locale-*-notification.json` pair).
2. **Structured conversation metadata.** If `MessagingStyle` is absent but a
   `shortcutId`/`conversationTitle` is present (a "people" notification
   without a message list, e.g. some conversation-shortcut-only postings),
   attribute `title`/`text` to that conversation instead of guessing from
   plain legacy heuristics.
3. **Legacy title/text fallback.** Used only when tiers 1 and 2 produce
   nothing. `title` and `text` are interpreted heuristically. A leading
   `"Name: "` prefix in `text` is permitted **only as a secondary signal**,
   and only when combined with other evidence (an already-known group
   `shortcutId`, or multiple distinct sender prefixes across `textLines`).
   **A colon alone is never sufficient evidence of a group**, matching
   `ARCHITECTURE.md` verbatim. `direct-message-with-colon.json` is the guard
   fixture for this rule: it is a direct (non-group) conversation whose body
   text contains a colon and must not be misclassified as a group. If this
   tier cannot reach a confident result, it must fail closed to `Unsupported`
   rather than guess.
4. **Explicit non-message result.** One of:
   - `Summary` - `isGroupSummary == true` (OS-level bundle summary row).
   - `Call` - call-banner notifications (not represented in this v1 corpus;
     no synthetic call fixture was authored because `ARCHITECTURE.md` and
     `PROJECT_CHARTER.md` do not define call-reading behavior; flagged below
     as a follow-up gap, not silently assumed).
   - `Security` - end-to-end-encryption/security-banner notifications (also
     not represented in this v1 corpus for the same reason; flagged below).
   - `Attachment` - a message-shaped notification whose only content is a
     WhatsApp-generated attachment placeholder ("Photo", "Video", "Document",
     or their localized equivalents) with no user-authored text.
   - `Redacted` - lock-screen-redacted content: no `conversationTitle`, no
     `shortcutId`, no `MessagingStyle`, and only a generic, non-attributable
     placeholder string.
   - `Unsupported` - anything not confidently classified above.

`expectedParserOutcome` on every fixture file uses this exact taxonomy:
`MessagingStyleMessages`, `ConversationMetadata`, `LegacyTextFallback`,
`Summary`, `Call`, `Security`, `Attachment`, `Redacted`, `Unsupported`.

### 3. Supported WhatsApp variants and locales for v1

- **Package allowlist**: exactly `com.whatsapp` (consumer) and
  `com.whatsapp.w4b` (Business), matching `ARCHITECTURE.md`'s exact-package
  allowlisting rule. No other package is parsed.
- **Message body language is out of scope for "supported locale."** The two
  chatting humans may write in any language; tiers 1-2 read structured extras
  and do not need to interpret WhatsApp's own UI strings, so ordinary message
  parsing is language-independent by construction.
- **"Supported locale" means WhatsApp's own UI-generated strings** that
  appear in notification metadata and matter for classification: attachment
  placeholders ("Photo"/"Foto", "Video", "Document"/"Dokumen"), accumulated
  summary banners ("N new messages" / "N pesan baru"), and redaction
  placeholders ("New message" / "Pesan baru"). v1 explicitly supports
  recognizing these generated strings in **Indonesian (`id`) and English
  (`en`)** device/app UI locales only, matching the charter's Indonesian
  product focus plus the common English fallback. Any other UI locale is
  explicitly unsupported for this generated-string recognition in v1: the
  legacy-fallback/placeholder matcher must fail closed to `Unsupported`
  rather than guess, per the non-goal "identical behavior on every ... WhatsApp
  version" not being guaranteed. This scope may be widened only by a
  superseding ADR with matching new fixtures.
- This locale scope is about parser string recognition only; it does not
  override or duplicate the OS/OEM/API-level matrix owned by ADR-002/ADR-012.

### 4. Fixture corpus as the parser acceptance corpus

`docs/fixtures/*.json` is the acceptance corpus for `P2-T04` (parser chain).
That task must add automated tests that load every fixture file, map serialized
captures directly to `NotificationSnapshot`, run the real parser, and assert
the result matches `expectedParserOutcome` (and, for
`MessagingStyleMessages`/`ConversationMetadata`/`LegacyTextFallback`, the
expected parsed message text/sender per capture) before it may be marked
complete. `P2-T03` instead uses Robolectric-built Android `Notification` and
`MessagingStyle` objects to test framework-to-snapshot extraction against the
same field contract; serialized snapshots cannot test framework extraction.
A newly discovered real-world WhatsApp behavior must be added
as a **new** fixture file; an existing fixture must not be silently redefined
to mean something else, since later ADRs and tests depend on its filename and
meaning staying stable. `docs/fixtures/README.md` indexes every file with a
one-line purpose for implementers.

Corpus contents (14 files, all synthetic, see `docs/fixtures/README.md` for
the authoritative index):

| File | Case |
|---|---|
| `direct-private-message.json` | Direct/private message, `com.whatsapp` |
| `group-message.json` | Group-chat message, `com.whatsapp` |
| `direct-message-business.json` | Direct message, `com.whatsapp.w4b` |
| `messaging-style-multi-message-bundle.json` | Multi-message bundle, one conversation |
| `notification-summary.json` | OS group-summary notification |
| `multi-message-update.json` | In-place update adding one message |
| `attachment-only-message.json` | Image/video/document, no text body |
| `redacted-locked-notification.json` | Lock-screen-redacted content |
| `message-with-url.json` | Message body containing a URL |
| `baseline-timestamp-only.json` | Ordinary baseline/control case |
| `direct-message-with-colon.json` | Direct message, colon in body (guard case) |
| `locale-id-notification.json` | Indonesian-UI generated string |
| `locale-en-notification.json` | English-UI equivalent scenario |
| `duplicate-callback.json` | Identical notification delivered twice |

### 5. Fixture capture protocol (for any future real-device fixtures)

Synthetic authoring (as used for this entire v1 corpus) requires no device
capture. If a later task chooses to add real-device-derived fixtures, the
protocol is:

1. Use only test accounts, test devices, and fabricated contacts/groups
   created solely for this purpose; never a real user's WhatsApp account.
2. Capture the raw `StatusBarNotification`/`Notification` fields listed in
   the schema above into a scratch file outside version control first.
3. Replace every human name, phone number, group name, and message body with
   a fabricated placeholder before the file is ever staged for commit; keep
   only the structural shape (which fields are present/absent, string
   lengths in the same order of magnitude, timestamp deltas).
4. An independent reviewer (not the capturing worker) must re-read the
   sanitized file for residual real data before it is committed.
5. Record in the fixture's `notes` field that it originated from a sanitized
   real capture and on what date, so its provenance is distinguishable from
   the synthetic-only corpus below.

No fixture in this corpus used this real-capture path; all 14 files are
authored synthetic data from step zero.

## Alternatives Considered

Real-device-captured-and-redacted fixtures as the first corpus - rejected for
this task: redaction mistakes would leak real personal data into version
control, and `P0-T04`'s assigned scope requires a synthetic-first corpus with
real fixtures only ever added under explicit later review.

A binary parse/fail-only result model - rejected: `ARCHITECTURE.md` requires
an explicit enumerated non-message outcome set (summary/call/security/
attachment/redacted) so the reading-policy layer can short-circuit correctly
instead of treating every non-message notification as a parse failure.

Colon-prefixed sender name as the primary group classifier (the old
Flutter-era approach) - explicitly rejected, matching `ARCHITECTURE.md`'s
"Recheck Result" rejection of "the colon parser."

Supporting an open-ended set of UI locales at v1 launch - rejected as
unaffordable per `P0-T02`'s "affordable" test-matrix constraint; scope is
fixed to Indonesian and English generated-string recognition, with any other
UI locale failing closed rather than being guessed at.

Treating call and security-banner notifications as in-scope for this fixture
pass - deferred rather than fabricated: no product requirement in
`PROJECT_CHARTER.md`/`ARCHITECTURE.md` currently defines call-reading or
security-banner behavior, so inventing synthetic fixtures for them risked
implying a decision that was never made. Recorded as a gap below instead.

## Consequences

Positive: a concrete, testable, ordered parser contract; an explicit
non-message taxonomy the policy layer can switch on; a committed, privacy-safe
corpus usable immediately by `P2-T03`/`P2-T04` without further legal/privacy
review; the OS-bundling-vs-WhatsApp-group distinction is documented once,
preventing a recurring class of misclassification bugs.

Negative / gaps requiring follow-up:

- No `Call` or `Security` fixture exists yet; `P2-T01`'s outcome enum should
  still reserve these variants (per `ARCHITECTURE.md`'s explicit list), but
  `P2-T04` cannot table-test them until a product decision defines expected
  behavior and a fixture is added. This does not block `P2-T04` for the
  message/summary/attachment/redacted paths, which are fully covered.
- Locale-generated-string recognition is fixed to `id`/`en`; widening it later
  requires both a superseding ADR and new fixtures.
- The extras/constant names cited here were not re-verified against current
  official Android documentation in this task; `P2-T03` must do that
  verification before relying on exact extra/category names.

## Dependent Tasks

- `P2-T01` (domain models must model the explicit parser-outcome enum).
- `P2-T03` (snapshot extraction must implement this exact schema).
- `P2-T04` (parser chain - primary dependent; acceptance corpus is this ADR's
  fixtures).
- `P0-R01` (must review fixture privacy as part of the phase gate).

## Evidence

Self-audit performed in this task:

- Manually authored all 14 fixture files with fabricated Indonesian names
  (e.g. "Budi Santoso", "Sari Wijaya"), a fabricated `.test`-TLD URL
  (`https://contoh-sintetik.test/promo`, `.test` is reserved for testing use
  by RFC 2606), and no real phone numbers, addresses, or account identifiers
  anywhere in any file.
- Searched the completed corpus for common real-data shapes (phone-number-like
  digit runs, `@`-containing strings, real-looking domains) using
  `grep`/terminal search across `docs/fixtures/*.json`; no matches other than
  the intentional fabricated placeholders described above (see this task's
  verification output for the exact command run).
- Validated every fixture file as syntactically valid JSON using a scripted
  parse check (see this task's verification output for the exact command and
  result).

No official Android documentation was consulted in this task (out of scope
for `P0-T04`; see `P0-T02`/Bee P0-B for platform-fact sourcing). N/A for
external links.
