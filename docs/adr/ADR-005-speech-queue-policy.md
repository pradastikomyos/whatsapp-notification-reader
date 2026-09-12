# ADR-005: Speech Queue Policy And V1 Product Settings Contract

## Status

Partially superseded by ADR-013. Queue decisions remain accepted; group-reader
settings and group speech formatting are retired.
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`ARCHITECTURE.md`'s "Speech pipeline" section requires `SpeechCoordinator` to own
one bounded queue and one active utterance, and states the queue policy must
define ordering and capacity, maximum message age and text length, overflow
behavior, interruption versus append behavior, whether related burst messages
are combined, what disable or riding-mode changes do to queued speech, and
behavior during calls, alarms, focus loss, TTS timeout, and engine errors.
`ARCHITECTURE.md`'s "Required Architecture Decisions" item 5 assigns exactly
this scope to `ADR-005`. `IMPLEMENTATION_PLAN.md` (`P4-T03`) explicitly forbids
deciding queue policy during implementation and requires an approved `ADR-005`
first; `P0-T06`'s acceptance criteria states `ADR-005` must be approved before
`P4-T03` starts.

`IMPLEMENTATION_PLAN.md` (`P0-T01`) additionally assigns this task the broader
job of defining reader enablement, private/group defaults, and supported
WhatsApp variants, with the acceptance criterion that "every visible version 1
setting has deterministic behavior," but instructs the output to be exactly two
ADR files (`ADR-005` and `ADR-007`). No separate "product contract" ADR number
exists in `ARCHITECTURE.md`'s required-decisions list. To avoid leaving any v1
setting undefined, this ADR therefore also records the full v1 settings
inventory and the reader-enablement and private/group-policy defaults, in
addition to its named topic (the speech queue). Settings whose deep technical
detail is properly owned by a narrower future ADR (for example exact TTS voice
selection under `ADR-011`, or storage/retention under `ADR-009`) are marked
below with the ADR expected to refine them; that future detail must not
contradict the default and on/off behavior fixed here without an explicit
superseding revision.

`PROJECT_CHARTER.md`'s Non-Goals explicitly exclude automatic riding detection,
Bluetooth vehicle selection, emergency/priority contacts, keyword
classification, and persisted notification/message history. This ADR does not
introduce any of them.

`PROJECT_CHARTER.md`'s "Daily use" journey mentions speech is queued and that
"known duplicate updates within the configured deduplication window produce at
most one speech request." `ARCHITECTURE.md`'s "Deduplication" section describes
a bounded, time-aware in-memory cache but does not list a deduplication window
as a user-facing setting, and no such setting appears in `P5-T03`'s (Settings
UI) task description. This ADR treats the deduplication window as an internal
pipeline constant owned by `P2-T05`, not a v1 user-visible setting, to avoid
inventing an undocumented switch.

## Decision

### A. Supported WhatsApp variants (confirmed, unchanged)

Exactly the two package IDs already fixed in `ARCHITECTURE.md`:
`com.whatsapp` and `com.whatsapp.w4b`. No other package, web client, desktop
client, or fork is supported in v1. This ADR does not change that decision; it
is restated here only so downstream workers do not need to re-derive it.

### B. Full v1 settings inventory

Every control a user can see in v1, with a deterministic default and effect.
"Persisted" means stored in DataStore as the single source of truth read by
both the UI and the background pipeline, per `ARCHITECTURE.md`'s "UI And
State" section.

| # | Setting | Type | Default | Persisted | Where shown | Deterministic behavior |
|---|---|---|---|---|---|---|
| 1 | Reader enabled | Boolean | `false` (off) | Yes | Home (`P5-T02`) | Master gate. When `false`, every message evaluates to `SkipReaderDisabled` before any private/group/riding check. See Section E for effect on the queue when toggled off mid-session. |
| 2 | Riding mode active | Boolean (`RIDING_ACTIVE` / `RIDING_INACTIVE`) | `RIDING_INACTIVE` | Yes | Home quick toggle + dedicated Riding screen (`P5-T02`, `P5-T05`) | Second mandatory gate, fully specified in `ADR-007`. When inactive, every message evaluates to `SkipRidingModeInactive`. |
| 3 | Private-message reading | Boolean | `true` (on) | Yes | Settings (`P5-T03`) | When `false`, one-to-one (non-group) messages evaluate to `SkipPrivateDisabled`. Does not affect group messages. |
| 4 | Group-message policy | Enum: `ALL_OBSERVED_GROUPS`, `SELECTED_GROUPS_ONLY`, `NO_GROUPS` | `NO_GROUPS` | Yes | Settings (`P5-T03`) | Exactly the three-mode model from `ARCHITECTURE.md`. `ALL_OBSERVED_GROUPS` speaks every group message that otherwise passes policy. `SELECTED_GROUPS_ONLY` speaks only messages from conversations present in the selected-conversation set; an empty selection means no group is spoken (never "all"). `NO_GROUPS` speaks no group message regardless of selection contents. |
| 5 | Selected groups (conversation set) | Set of conversation IDs | Empty | Yes | Groups screen (`P5-T04`, backed by `P2-T07`) | Meaningful only under `SELECTED_GROUPS_ONLY`. Discovery never adds a conversation to this set automatically; a newly observed group always starts unselected, per `ARCHITECTURE.md`. |
| 6 | Announce sender/group name | Boolean | `true` (on) | Yes | Settings (`P5-T03`) | When `true`, the formatted utterance is introduced with the sender's display name (private) or the group and sender display name (group) before the message text. When `false`, only the message text is spoken. Exact spoken phrasing is owned by `SpeechTextFormatter` (`P2-T06`); this ADR fixes only the existence, default, and on/off effect. |
| 7 | Speech rate | Float, range 0.5x-2.0x | `1.0x` | Yes | Home (`P5-T02`) | Passed to the TTS engine's rate control only. Never changes system/global media volume. Exact UI widget and validation are owned by `P4-T01`/`ADR-011`; this ADR fixes the default, range, and that it is a v1 setting. |
| 8 | Test speech ("speak test utterance") | Action (not persisted state) | N/A | No | Home (`P5-T02`) | Available whenever the TTS engine reports ready. Speaks one fixed, non-message sample phrase directly through the shared `SpeechCoordinator`, so it still respects the one-active-utterance and audio-focus rules, but it bypasses reader-enabled, riding-mode, private, and group checks and is exempt from age/duplicate checks, because it is a diagnostic action, not a notification-derived message. |
| 9 | Spoken language / locale | Fixed, not user-selectable in v1 | Indonesian (`id-ID`) | N/A | Onboarding/Home status only | There is no language switcher in v1. The UI only reports whether a usable Indonesian voice is installed, per `PROJECT_CHARTER.md`'s first-run journey. Voice-selection UX detail is owned by `ADR-011`. |
| 10 | Notification access status | Read-only status | N/A | N/A (reflects OS state) | Onboarding/Home | Not an app switch; deep-links to Android's notification-listener settings. Included here only to confirm it is not an undefined v1 control. |

Deduplication window, snapshot capture fields, and parser rules are internal
pipeline behavior, not user-visible settings, and are intentionally absent from
this table.

### C. Reader enablement behavior

1. Default is `false` (off) on first install and after any full data reset.
   This is the conservative choice: notification-listener access is granted
   separately from reading, and nothing is spoken until the user explicitly
   opts in, matching `PROJECT_CHARTER.md`'s first-run journey.
2. Toggling reader enablement is immediate: the new value is written to
   DataStore and observed by both the UI and the background pipeline with no
   separate "apply" step.
3. Turning the reader off does not merely stop new messages from entering the
   queue; it also flushes the queue and stops in-flight speech. See Section E.
4. Turning the reader back on does not retroactively speak anything that
   arrived, or was skipped, while it was off. Skip decisions are terminal, not
   deferred or replayed, consistent with `ARCHITECTURE.md`'s reasoned-result
   design for `ReadingPolicyEvaluator` and the Non-Goal against persisting
   message history.

### D. Private- and group-message defaults

1. Private-message reading defaults to `true` (on). Once a user has opted into
   the reader at all, reading direct messages is the primary value of the
   product and carries no group-sized audience-selection ambiguity.
2. Group-message policy defaults to `NO_GROUPS`. This is the more conservative
   default: group conversations can contain messages not authored by, or
   intended primarily for, the device owner, and typically carry a higher
   message volume and higher risk of reading content aloud that the rider did
   not expect. It also matches the migration default already fixed in
   `ADR-001` for updated installs, so fresh installs and migrated installs
   behave identically for this setting.
3. The three-mode model (`ALL_OBSERVED_GROUPS` / `SELECTED_GROUPS_ONLY` /
   `NO_GROUPS`) is exactly the model already fixed in `ARCHITECTURE.md`. This
   ADR does not add a fourth mode, a priority tier, or per-contact rules
   (excluded by `PROJECT_CHARTER.md`'s Non-Goals).

### E. Speech queue policy

1. **Ordering**: strict FIFO by the message's original notification post time.
   No priority reordering, no per-conversation fairness scheme.
2. **Capacity**: up to 20 pending (not-yet-spoken) items, plus the single item
   currently being spoken. 20 is a concrete, bounded engineering default
   chosen to absorb a realistic group-chat burst without unbounded memory or
   unbounded eventual playback delay; the product owner may retune this number
   later without any architecture change.
3. **Maximum message age**: 180 seconds, measured from the message's original
   notification post time (not from enqueue time, so pipeline latency counts
   against the budget). Age is checked at two points: immediately before an
   item is dequeued to become the active utterance, and opportunistically
   during every new enqueue (a bounded sweep removes any pending item whose
   age has already exceeded the limit). Expired items are discarded silently;
   no "message skipped" utterance is spoken.
4. **Maximum text length**: 240 characters per utterance, including any
   combined text from aggregation (Section E.6). If the limit would cut the
   text mid-word, truncation backs up to the previous whitespace boundary. No
   ellipsis or "message truncated" notice is appended or spoken.
5. **Overflow behavior**: when a new eligible message arrives while the
   pending (non-active) queue already holds 20 items, the single oldest
   pending item (by original post time) is evicted and discarded, then the new
   item is appended at the end. Dropping the oldest favors the most current,
   most likely still-relevant conversation state over an already-stale
   backlog. The currently active (already-speaking) utterance is never evicted
   by overflow.
6. **Burst aggregation**: performed only at dequeue time, not eagerly at
   arrival. When an item is selected to become the active utterance, the
   coordinator collects every other still-pending item that shares the same
   conversation identity (per `ADR-004`) and removes them from the queue,
   concatenating their message bodies in original arrival order into one
   combined utterance separated by a short pause marker. The combined text is
   then subject to the same 240-character cap as any single message
   (Section E.4). If sender/group announcement (setting 6) is enabled, the
   combined utterance announces the sender/group once, not once per merged
   message.
7. **Interruption versus append**: a newly arriving eligible message is always
   appended to the end of the queue and never interrupts the currently
   speaking utterance. The only events that stop an in-progress utterance are
   the explicit triggers in Section E.8, not ordinary overflow or ordinary
   arrival of a new message.
8. **Reader-disable and riding-mode-inactive transitions**: both events are
   treated identically and immediately:
   - the entire pending queue is cleared (all not-yet-spoken items are
     discarded, not deferred);
   - the currently speaking utterance, if any, is stopped immediately and
     audio focus is abandoned.
   This is the conservative choice: once the user's explicit intent changes
   (reading turned off, or the ride has ended), continuing to speak
   already-queued content contradicts that stated intent. There is no partial
   resume; a later re-enable or re-activation starts from an empty queue and
   only speaks messages that arrive after that point.
9. **Calls, alarms, TTS timeout, and engine errors**: these are per-item,
   "skip and continue" events, deliberately distinct from the "flush
   everything" events in Section E.8, because they represent a transient
   platform interruption rather than a change in the user's intent:
   - On audio-focus loss associated with an incoming/active call, or with an
     alarm, the current utterance is stopped and not resumed mid-sentence, but
     the rest of the pending queue is **not** flushed. Once focus is regained,
      the coordinator resumes by speaking the next queued item (subject to its
      own age check; an item that expired while focus was lost is dropped
      instead of spoken late).
    - Transient focus retention is bounded to 30 seconds. This conservative
      window accommodates ordinary navigation prompts and brief call/alarm
      transitions without allowing a missing gain callback to retain audio
      focus or the foreground playback gate indefinitely. At expiry, all work
      accumulated during the interruption is discarded, focus and foreground
      resources are released, and later newly arriving work can drain normally.
   - If the TTS engine reports no progress callback within 45 seconds of
     starting an utterance, the coordinator treats it as hung, stops waiting
     on it, discards that single utterance, and proceeds to the next queued
     item. It does not retry the same text.
   - If the TTS engine reports an error for an utterance via
     `UtteranceProgressListener`, that single utterance is discarded and the
     coordinator proceeds to the next queued item.
   - If audio focus is denied outright for a given utterance, that single
     utterance is skipped (not spoken, not requeued) and the coordinator
     proceeds to the next queued item.
   The exact platform mechanics for detecting calls/alarms, the legality and
   design of any foreground playback service, and precise focus-callback
   wiring remain owned by `ADR-006` and its dependent tasks; this ADR fixes
   only the product-level "skip this one item and keep going" contract so that
   `P4-T03` is not blocked on `ADR-006`.
10. Items reach the queue only after deduplication and policy evaluation
    already returned `Speak`. The queue never re-runs policy or deduplication
    logic; it only manages ordering, capacity, age, aggregation, and the
    events above.

## Alternatives Considered

- **Unbounded queue** - rejected; contradicts `PROJECT_CHARTER.md`'s success
  metric that "queue remains bounded during message bursts."
- **Drop-newest on overflow** - rejected; the newest message is the most
  likely to still be relevant to an ongoing conversation, while the oldest
  pending item is the most likely to already be stale.
- **Reject-new on overflow (refuse to enqueue new messages once full)** -
  rejected; silently losing the newest, most relevant message is a worse
  outcome than discarding an already-stale one, and gives no path to recover
  as the burst tapers off.
- **No burst aggregation (speak every queued message individually)** -
  rejected; under a real burst this maximizes total speaking time and queue
  pressure, increasing the chance that unrelated later messages age out or get
  overflow-dropped before ever being heard.
- **Whole-queue rolling summary regardless of conversation** - rejected; it
  discards per-conversation attribution and is materially harder to reason
  about and test than a same-conversation-only merge.
- **Keep speaking through disable or riding-mode-off (let the current queue
  drain)** - rejected; it directly contradicts the user's most recent explicit
  action and does not match the conservative-default instruction for this
  task.
- **Pause and resume the queue across a riding-mode toggle instead of
  flushing it** - rejected; it adds meaningful state-machine complexity for a
  benefit (resuming possibly minutes-old queued speech later) that is more
  likely to confuse a rider than help them, and conflicts with the Non-Goal
  against implying stronger reliability/continuity guarantees than are
  measured.
- **User-configurable deduplication window as a v1 setting** - rejected as
  unnecessary complexity; kept as an internal `P2-T05` constant, avoiding an
  undocumented or speculative switch.
- **Reader-enabled default `true`** - rejected as less conservative for a
  first run before the user has seen and confirmed reading behavior.

## Consequences

- Positive: memory and playback-delay bounds are concrete and testable;
  `P4-T03` has an unambiguous specification instead of an implementation-time
  guess; behavior under bursts, disable, and riding-mode changes is fully
  deterministic for table-driven tests.
- Negative: some legitimate burst messages will be aggregated, truncated, or
  dropped under sustained load; aggregation changes the literal
  message-by-message pacing a user would have heard into one combined
  utterance. This is an accepted, documented trade-off, not an oversight.
- The numeric constants (20-item capacity, 180-second age limit, 240-character
  length limit, 45-second stuck-utterance watchdog) are engineering defaults
  set by this task in the absence of a synchronously available product owner.
  A human owner may retune any of them later; because they are coordinator
  configuration values rather than public API shape, doing so requires no
  architectural rework.
- `ADR-006` still owns the exact audio-focus and foreground-service mechanics
  for calls, alarms, and engine failures; this ADR only fixes the product-level
  "skip one item and continue" behavior so that dependency is not a hard block
  for `P4-T03`.
- `ADR-004` owns conversation identity; if it defines a materially different
  identity model than assumed here, the "same conversation" key used for
  aggregation (Section E.6) must be reconciled, but the aggregation policy
  itself (whether to combine, and the length cap) does not change.
- **Flagged risk (no contradiction found, but recorded per instructions):**
  `PROJECT_CHARTER.md`'s "Daily use" journey refers to a "configured
  deduplication window," which could be misread as implying a user-facing
  setting. This ADR resolves that reading by treating it as an internal
  pipeline constant (see Context and Section B). If a future product owner
  intended it to be user-configurable, that requires a new setting and a
  revision to this ADR, not a silent reinterpretation downstream.

## Dependent Tasks

`P2-T06` (reading policy and formatter cannot be implemented without the
reader/private/group defaults and skip-reason inputs fixed here), `P4-T03`
(explicitly blocked on this ADR per `IMPLEMENTATION_PLAN.md`), `P5-T02` (home
reader controls: master toggle, test speech, speed), `P5-T03` (settings
screen: private/group, announcement, and queue-adjacent controls). `P3-T02`
depends transitively through `P2-T06`.

## Evidence

N/A - this is a product/policy decision, not a platform-fact finding. Internal
sources used: `PROJECT_CHARTER.md` (Mission; Product Outcomes 1-3, 6, 8;
Non-Goals; "Daily use" journey; Success Metrics), `ARCHITECTURE.md` ("Runtime
Flow > Reading policy" and "Speech pipeline" sections; "Required Architecture
Decisions" item 5), `IMPLEMENTATION_PLAN.md` (`P0-T01`, `P2-T06`, `P4-T03`,
`P5-T02`, `P5-T03`), and `ADR-001` (group-policy migration default).
