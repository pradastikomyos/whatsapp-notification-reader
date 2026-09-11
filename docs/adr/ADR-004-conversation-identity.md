# ADR-004: Conversation Identity, Rename, Collision, And Persistence

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`ARCHITECTURE.md`'s "Required Architecture Decisions" requires ADR-004 to
define "conversation identity and observed-group catalogue." Its package
layout already reserves `data/conversations/` (`ConversationRepository`,
`ObservedConversation`, `room/`) and states: "Room only for an
observed-conversation catalogue, if approved." `PROJECT_CHARTER.md` requires
group conversations to be "discover[ed] ... only from notifications observed
on the device," an empty selection must never ambiguously mean "all" or
"none," discovery must never implicitly select a group, and message bodies
must not be persisted by default. `ARCHITECTURE.md`'s "Storage And Privacy"
section permits persisting "observed conversation identifiers/names," never
content or history. `IMPLEMENTATION_PLAN.md` task `P0-T04` requires this ADR
to cover conversation identity, rename handling, collision handling, and
persistence behavior. `P2-T07` (conversation repository), `P3-T04`
(observation integration), and `P5-T04` (conversation UI) all depend on this
decision.

The underlying platform fact this ADR relies on -
`Notification.getShortcutId()` returning a stable, WhatsApp-assigned
per-conversation shortcut ID on modern Android/WhatsApp combinations - was
not independently re-verified against official Android documentation in this
task (that sourcing work belongs to `P0-T02`/Bee P0-B). This ADR states it as
a documented engineering assumption to be confirmed, not as a verified fact,
and flags it under Evidence.

## Decision

### 1. Primary identity key: shortcut ID

When a snapshot carries a non-empty `shortcutId`, the conversation's stable
`conversationKey` is:

```text
<packageName>::shortcut::<shortcutId>
```

`packageName` is always included so `com.whatsapp` and `com.whatsapp.w4b`
conversations are never merged into the same catalogue row even if a
`shortcutId` string were ever identical by coincidence. This is the preferred
identity path: WhatsApp assigns one shortcut per chat, and it is expected to
remain stable across contact/group renames because it is derived from the
underlying chat identity (JID), not from the display name shown in the
notification.

### 2. Fallback identity key: normalized metadata

When no `shortcutId` is present (older WhatsApp versions, or a notification
that does not carry a "people"/conversation shortcut), derive:

```text
<packageName>::fallback::<direct|group>::<normalizedTitle>::<senderKeyOrUnknown>
```

Where:

- `direct|group` comes from the parser's `isGroupConversation` result (ADR-003
  tier 1/2), never guessed from `groupKey` or a colon.
- `normalizedTitle` is `conversationTitle` (or `title` if no
  `conversationTitle` is available) after trimming, collapsing internal
  whitespace runs to one space, and Unicode NFC normalization. **Case is
  preserved, never folded.** Lowercasing was considered and rejected: it
  would increase collision risk (two distinct contacts named "Budi" and
  "BUDI" would wrongly collide) rather than reduce it.
- `senderKeyOrUnknown` is the `MessagingStyle` sender `Person` key/URI for the
  single most-recently-observed sender when available (direct chats have one
  natural sender to use here), or the literal string `unknown` when no
  structured sender is available (e.g. legacy-only tiers). This exists purely
  to reduce - not eliminate - the collision risk described below.

### 3. Rename handling

- **Shortcut-keyed conversations**: the `conversationKey` never changes when
  `conversationTitle` changes, because the key is derived only from
  `shortcutId`. On a rename, update only display metadata (latest observed
  title, last-seen timestamp) on the existing catalogue row. A rename must
  never reset the user's selection state for that conversation.
- **Fallback-keyed conversations**: `normalizedTitle` is part of the key, so a
  rename produces a **new** `conversationKey`; the old row becomes stale and
  orphaned rather than being renamed in place. This is an accepted, documented
  v1 limitation of the no-shortcut path, not a defect: the alternative
  (mutating a fallback key in place on an observed title change) cannot
  distinguish "this conversation was renamed" from "a different conversation
  now coincidentally shares the old title," which is a strictly worse
  failure mode (silent merge of two different people/groups). The degraded
  outcome - the user occasionally has to reselect a renamed group under
  `SELECTED_GROUPS_ONLY` - is safe and visible, never silent data leakage
  across conversations. `P5-T04` may add a manual "remove stale entries"
  affordance as a UX improvement; it is not required for v1 functional
  correctness.

### 4. Collision handling

Two different underlying conversations can only produce the same
`conversationKey` on the fallback path (see above); the shortcut path is
assumed collision-free per WhatsApp's own shortcut uniqueness. When new
traffic arrives under an existing fallback `conversationKey` whose observed
`senderKeyOrUnknown` (or, for groups, the most-recent sender-set fingerprint)
differs from every value previously observed for that key:

- Do **not** silently merge the traffic into the existing catalogue row's
  identity, and do not silently overwrite its stored display metadata as if
  nothing happened.
- Keep the existing catalogue row as-is, and set a `collisionDetected` flag
  (plus an internal counter) on it - metadata only, never message content -
  so the UI/future work can surface "this entry may represent more than one
  conversation" rather than presenting false certainty.
- This is an accepted, documented best-effort edge case for the no-shortcut
  path, not a v1-blocking defect: it can only ever be detected after
  conflicting traffic actually arrives, never guaranteed in advance.

### 5. Persistence (`ObservedConversation` catalogue)

Backed by Room, which `ARCHITECTURE.md` conditionally allows "for an
observed-conversation catalogue, if approved" - this ADR is that approval.
The persisted catalogue contains group conversations only. Direct-message
identity may exist transiently for deduplication and formatting, but direct
contact names/identities are not stored because v1 has no direct-conversation
picker. Persistence is scoped exactly to the fields below and nothing else:

| Field | Content |
|---|---|
| `conversationKey` | Per sections 1-2 above |
| `packageName` | `com.whatsapp` or `com.whatsapp.w4b` |
| `kind` | Always `group` in persisted rows |
| `displayTitle` | Latest observed `conversationTitle`/`title` |
| `shortcutId` | Latest observed value, or null on the fallback path |
| `firstSeenAtMillis` / `lastSeenAtMillis` | Observation timestamps |
| `selectionState` | User selection, meaningful only under `SELECTED_GROUPS_ONLY` |
| `collisionDetected` | Boolean/counter per section 4 |

**Never stored**: message text, message timestamps as a history log, sender
lists beyond the single most-recently-observed display name/key already
folded into `displayTitle`/the fallback key, or attachment metadata. This
matches `PROJECT_CHARTER.md`'s "avoid storing message bodies by default" and
`ARCHITECTURE.md`'s "do not persist notification content or message history
by default."

**Retention**: catalogue rows persist indefinitely until a user-triggered
reset (settings reset / app data clear). No automatic time-based expiry
exists in v1; this ADR deliberately does not invent an unrequested retention
timer. Automatic expiry (and, if ever needed, an explicit stale-fallback-row
pruning policy per section 3) is left as a candidate follow-up for ADR-009 or
a superseding revision of this ADR, not decided here.

**Discovery never selects**: recording an `ObservedConversation` row must
never, by itself, change `selectionState`. A newly observed row always starts
unselected, consistent with `ARCHITECTURE.md`'s "A newly observed group
starts unselected when policy is `SELECTED_GROUPS_ONLY`."

Direct messages never create or update a Room catalogue row.

## Alternatives Considered

Deriving identity purely from `conversationTitle` with no `shortcutId`
preference - rejected: it would not survive any rename even on modern
Android/WhatsApp versions where a stable shortcut is actually available,
needlessly taking the worst-case (section 3's fallback limitation) as the
default case.

Silently merging fallback-key collisions optimistically - rejected as unsafe:
it could group one person's/group's messages under another's catalogue
identity without evidence, which can misapply the private-vs-group reading
policy to the wrong conversation. Silence here is a correctness and privacy
risk, not just a UX inconvenience.

Automatic time-based expiry/pruning of catalogue rows - deferred rather than
adopted: no retention timer was requested by `PROJECT_CHARTER.md` or
`ARCHITECTURE.md`, and inventing one here would be an unapproved product
decision; left for ADR-009 or a superseding ADR.

Using `Notification.getChannelId()` as the conversation identity - rejected:
WhatsApp is understood to use a small, fixed set of category-level channels
(e.g. a general messages channel) rather than one channel per conversation,
so a channel ID would not distinguish conversations. This specific platform
behavior claim is not verified against official documentation in this task
and should be reconfirmed during `P2-T07`/device qualification before being
relied upon; it is only used here to justify not choosing channel ID as the
key, not as a fact this ADR asserts with certainty.

## Consequences

Positive: resilient identity across renames whenever Android/WhatsApp provide
a shortcut; collisions are surfaced rather than silently merged; persistence
is minimal and privacy-preserving; discovery still never auto-selects,
consistent with the charter.

Negative: the no-shortcut fallback path has a known rename-creates-orphan
limitation and best-effort-only collision detection; both are explicitly
accepted rather than solved, because solving them fully would require
inventing user-facing merge/dedup UI outside this task's scope. A future ADR
must revisit automatic retention/expiry if a privacy review requires it, and
must revisit the channel-ID assumption above once verified against official
sources or real-device evidence.

## Dependent Tasks

- `P2-T07` (conversation repository - primary; implements this exact identity,
  rename, collision, and persistence contract, with Room only as approved
  here).
- `P3-T04` (observation integration at the ADR-approved pipeline point).
- `P5-T04` (conversation UI; must surface duplicate names, renamed identities,
  and `collisionDetected` per section 4).
- `P0-R01` (phase review of this decision's privacy/collision behavior).

## Evidence

N/A for official external sources in this task (out of `P0-T04`'s scope; see
`P0-T02`/Bee P0-B for platform-fact sourcing). This ADR explicitly flags two
platform-behavior assumptions as unverified-in-this-task and requiring
confirmation before `P2-T07` relies on them without re-checking:

1. `Notification.getShortcutId()` stability across renames for
   WhatsApp/WhatsApp Business conversation notifications.
2. WhatsApp's typical use of a small fixed set of category-level notification
   channels rather than per-conversation channels.

Design reasoning is otherwise derived directly from `PROJECT_CHARTER.md` and
`ARCHITECTURE.md` as cited throughout the Context and Decision sections above.
