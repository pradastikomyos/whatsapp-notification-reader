# ADR-007: Manual Riding-Mode Semantics And Reading-Policy Interaction

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`PROJECT_CHARTER.md`'s Product Outcomes require the app to "support a clearly
defined manual riding mode," and its Non-Goals explicitly exclude "automatically
detecting whether the user is riding" and "automatically selecting a vehicle
from nearby Bluetooth devices." `ARCHITECTURE.md` reserves a `riding/` package
(`RidingModeRepository.kt`), lists `SkipRidingModeInactive` as one of the fixed
outcomes of `ReadingPolicyEvaluator`, and requires (`ADR-007`) the "exact manual
riding-mode semantics." `IMPLEMENTATION_PLAN.md`'s `P5-T05` must "implement the
exact `ADR-007` policy and effective-state display" with "service and UI
observ[ing] one persisted source of truth," and its worker prompt forbids
cancelling notifications, changing DND, acquiring wake locks, or claiming
automatic detection. None of this can be implemented correctly until riding
mode's exact states, toggle mechanism, and interaction with the reading policy
are fixed, which is this ADR's job.

This ADR treats "manual" as a hard constraint, not a tunable default: no
sensor, radio, location, time schedule, or paired-device signal may ever change
this state. Only a direct, explicit user tap changes it.

## Decision

### A. States

Riding mode has exactly two mutually exclusive states:

- `RIDING_ACTIVE`
- `RIDING_INACTIVE`

Default state is `RIDING_INACTIVE` on first install and after any full data
reset. This is the conservative choice: nothing about installing the app or
opening it for the first time implies the user is currently riding, and
starting inactive means no message is spoken until the user takes a second,
distinct, deliberate action beyond enabling the reader.

### B. Persistence and toggle mechanism

1. The state is a single boolean-equivalent field persisted in DataStore and
   exposed as a `Flow`, read by both the background pipeline and the UI, per
   `ARCHITECTURE.md`'s "DataStore is the settings source of truth for both
   service and ViewModels."
2. It survives process death and app restart: it is whatever the user last
   explicitly set, not reset merely because the process was recreated. It is
   also not reset by device reboot; `ARCHITECTURE.md` already defers any boot
   receiver to evidence-based `ADR-008`, and this ADR does not assume one
   exists. If a future `ADR-008` revision adds boot handling, it must not
   change this field's value on boot without a further explicit decision.
3. The **only** way this value changes is a direct tap by the user on one of
   two UI controls that both read and write the exact same persisted field:
   a quick toggle on the Home screen (`P5-T02`) and a dedicated Riding screen
   (`P5-T05`) that also explains what the mode does. There is no third
   control.
4. Reader enablement (`ADR-005`, setting 1) and riding-mode state are
   independent, orthogonal settings. The UI must not auto-disable, hide, or
   grey out the riding-mode control based on the reader-enabled value, or vice
   versa; a user may set either one in either order.
5. No other entry point exists in v1: no home-screen widget, no Quick Settings
   tile, no notification action, no voice command, no gesture, no gyroscope
   or accelerometer trigger, no GPS/speed/geofence trigger, and no Bluetooth
   pairing/proximity trigger. See Section F.

### C. Interaction with the reading policy

1. Riding mode is a mandatory second gate, not an optional filter behind its
   own enable/disable meta-setting. When `RIDING_INACTIVE`, every otherwise
   eligible message evaluates to `SkipRidingModeInactive`, regardless of the
   reader-enabled value or the private/group settings. There is no v1 setting
   to bypass or ignore riding-mode gating.
2. This mandatory gate reflects the product's stated purpose: reading
   WhatsApp messages aloud specifically to a rider who cannot look at the
   screen, not reading messages aloud generally whenever the reader happens to
   be left on. It reduces the chance of the app speaking notification content
   aloud in a context (for example, at home, or with the phone in a bag while
   parked) the user did not intend.
3. Evaluation precedence follows the fixed outcome order already listed in
   `ARCHITECTURE.md`'s `ReadingPolicyEvaluator` contract: `SkipReaderDisabled`
   is checked before `SkipRidingModeInactive`, which is checked before the
   private/group/redacted/unsupported/stale checks, which are checked before
   `Speak`. Each message receives exactly one skip reason (or `Speak`); this
   ADR does not require reporting every reason that would independently apply.
4. Skip decisions caused by `RIDING_INACTIVE` are terminal for that message.
   Turning riding mode to `RIDING_ACTIVE` later never retroactively speaks a
   message that was skipped while inactive; this matches `ARCHITECTURE.md`'s
   reasoned, non-deferred result model and the Non-Goal against persisting
   message history for later replay.

### D. Effect on the speech queue

Full queue mechanics are owned by `ADR-005`; this section restates only the
riding-specific trigger so this ADR is self-contained for implementers of
`P5-T05`:

- A transition from `RIDING_ACTIVE` to `RIDING_INACTIVE` immediately flushes
  the entire pending speech queue and stops any currently speaking utterance
  (`ADR-005`, Section E.8). There is no partial resume.
- A transition from `RIDING_INACTIVE` to `RIDING_ACTIVE` always starts from an
  empty queue; only messages that arrive after the transition can be spoken.

### E. Effective-state display

The UI must display riding mode as its own independent fact (per
`ARCHITECTURE.md`'s "UI And State" home-screen requirements), separate from
reader-enabled state and from listener-connection state. It must never be
inferred, estimated, or displayed as "probably riding" - it is exactly the
persisted `RIDING_ACTIVE` / `RIDING_INACTIVE` value, nothing else.

### F. Non-Goals restated (must not be implemented in v1)

To prevent downstream workers from inventing scope, the following
`PROJECT_CHARTER.md` Non-Goals apply directly to riding mode and are
explicitly reiterated:

- No automatic detection of riding via GPS, speed, accelerometer, gyroscope,
  activity-recognition APIs, or any other sensor.
- No automatic vehicle selection or detection via nearby Bluetooth devices, or
  any Bluetooth-triggered state change at all.
- No geofencing, calendar, or time-of-day based automatic scheduling of riding
  mode.
- No emergency contacts, priority contacts, or keyword classification tied to
  riding mode.
- No cancelling, rewriting, or hiding WhatsApp notifications as part of
  entering or leaving riding mode.
- No Do Not Disturb changes and no global media-volume changes as part of
  entering or leaving riding mode.
- No persisted history of past riding sessions, durations, or statistics
  (including no fabricated statistics, which the old Flutter
  `riding_mode_page.dart` is explicitly rejected for in `ARCHITECTURE.md`'s
  migration map).
- No wake locks acquired to keep riding mode "alive"; it is a plain persisted
  value, not a running session that needs to be kept alive.

## Alternatives Considered

- **Automatic detection via GPS/speed/accelerometer** - rejected; explicit
  `PROJECT_CHARTER.md` Non-Goal.
- **Automatic vehicle detection via Bluetooth pairing/proximity** - rejected;
  explicit `PROJECT_CHARTER.md` Non-Goal.
- **A single combined reader+riding toggle** - rejected; `ARCHITECTURE.md`
  defines `SkipReaderDisabled` and `SkipRidingModeInactive` as distinct
  outcomes, implying two independently meaningful, independently testable
  gates rather than one.
- **Riding-mode gating as an optional setting the user can turn off (always
  read regardless of riding state)** - rejected as the v1 default because it
  weakens the product's stated purpose and is a less conservative choice than
  requiring an explicit "I am riding" signal before speaking. A future product
  owner may add such a bypass setting later; because the evaluator already
  takes riding state as one explicit input, adding an "ignore riding state"
  setting later does not require architectural rework, only a new input and a
  new test case.
- **Default state `RIDING_ACTIVE`** - rejected; it is less conservative and
  could cause the app to speak immediately after install/reset without any
  deliberate context-setting action from the user.
- **Time-boxed automatic revert to inactive after a period of inactivity** -
  rejected as a speculative feature not present in the charter; the user must
  explicitly turn riding mode off, matching the "manual" requirement exactly.
- **Quick Settings tile or notification-action toggle as an additional manual
  entry point** - rejected for v1 as out of the currently approved UI scope
  (`P5-T02`/`P5-T05` do not describe one); it remains a possible low-risk
  future addition specifically because it would still be a manual user tap,
  not a sensor trigger, so it would not violate this ADR's "manual" definition
  if approved later.
- **Resetting riding-mode state on device reboot** - rejected as speculative;
  no boot receiver exists in v1 pending `ADR-008` evidence, so there is no
  mechanism to reset it on boot, and inventing one here would pre-empt
  `ADR-008`.

## Consequences

- Positive: riding mode is fully deterministic and testable in isolation from
  sensors, Bluetooth, or any platform signal; `P5-T05` and `P2-T06` have an
  unambiguous specification; the design cannot regress toward automatic
  detection without a visible, explicit change to this ADR.
- Negative: manual toggling adds a small amount of friction each trip (the
  rider must remember to tap the control), which is an accepted trade-off of
  removing automatic detection, consistent with the charter's Non-Goals rather
  than an oversight of this ADR.
- The mandatory AND-gate between reader-enabled and riding-active (Section C.1)
  is the most product-shaping decision in this ADR. It is stated as a default,
  not an immutable law: a human product owner may later approve a bypass
  setting or a different gating relationship without requiring any change to
  the persisted-state model, the two manual UI entry points, or the
  queue-flush behavior defined here.
- No contradiction between `PROJECT_CHARTER.md` and `ARCHITECTURE.md` was
  found for riding-mode semantics during this task's self-check.

## Dependent Tasks

`P2-T06` (reading policy evaluator needs the exact riding-state input and
precedence defined here), `P4-T03` (speech coordinator needs the riding-mode
transition trigger, in conjunction with `ADR-005`), `P5-T05` (explicitly
requires implementing "the exact `ADR-007` policy").

## Evidence

N/A - this is a product/policy decision, not a platform-fact finding. Internal
sources used: `PROJECT_CHARTER.md` (Product Outcomes 5; Non-Goals; "Users And
Core Journeys"), `ARCHITECTURE.md` (Package Layout `riding/`; "Runtime Flow >
Reading policy"; "UI And State"; "Required Architecture Decisions" item 7;
"Old-To-New Migration Map" row for `riding_mode_page.dart`),
`IMPLEMENTATION_PLAN.md` (`P0-T01`, `P2-T06`, `P4-T03`, `P5-T05`), and
`ADR-005` (queue flush behavior on riding-mode transitions).
