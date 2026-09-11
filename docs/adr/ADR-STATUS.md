# ADR Approval Record (P0-T06)

Consolidated status for `ADR-001` through `ADR-012`, per
`IMPLEMENTATION_PLAN.md`'s P0-T06 acceptance criteria: every Phase 1
dependency is approved, and any later-blocked work is explicitly identified.

Coordinator: Worker Bee (P0-T06), 2026-09-11.

| ADR | Title | Status | Owner | Blocks if deferred |
|---|---|---|---|---|
| ADR-001 | App identity and migration | Accepted | Worker Bee (engineering default, pending product owner override) | P1-T02, P2-T02, P6-T05 |
| ADR-002 | Platform support matrix | Accepted, with one flagged gap (see below) | Worker Bee | P1-T01, P6-T02 |
| ADR-003 | Parser contract and fixtures | Accepted | Worker Bee | P2-T03, P2-T04 |
| ADR-004 | Conversation identity | Accepted | Worker Bee | P2-T07, P3-T04, P5-T04 |
| ADR-005 | Speech queue policy | Accepted | Worker Bee | P2-T06, P4-T03 |
| ADR-006 | Audio focus and foreground service | Accepted API-aware baseline; release validation pending | Engineering, revised by P0-R01 | P4-T01 - P4-T04 |
| ADR-007 | Riding mode semantics | Accepted | Worker Bee | P2-T06, P4-T03, P5-T05 |
| ADR-008 | Boot behavior | Accepted | Worker Bee | P3-T03 (informs, does not block) |
| ADR-009 | Privacy, storage, logging | Accepted | Worker Bee | P2-T02, P2-T07, P6-T03 |
| ADR-010 | Dependency container | Accepted | Worker Bee | none (default already matches ARCHITECTURE.md baseline) |
| ADR-011 | TTS locale and voice | Accepted | Worker Bee | P1-T02, P4-T01, P5-T01 |
| ADR-012 | Battery/OEM guidance | Accepted | Worker Bee | P5-T01 (informational only) |

**All ADRs are Accepted. None are `deferred`.** Per plan rules, this means
Phase 1 may start. Two ADRs carry explicit, honestly-flagged residual risk
that must be tracked rather than hidden:

## Flagged residual risk 1 - ADR-002 / local toolchain gap

`ADR-002` recommends `compileSdk`/`targetSdk = 36` to match current Play
Store policy, but this machine's local Android SDK only has the
`android-35` platform installed (`android-36` is not present, even though
`build-tools 36.0.0` is). **Action required before `P1-T01`:** install the
`android-36` platform via `sdkmanager`, or explicitly re-decide to ship
`targetSdk 35` for the initial internal builds and revisit before the Nov
2026 Play policy deadline `ADR-002` cites. This is a concrete, mechanical
blocker, not a design ambiguity - `P1-T01`'s worker must resolve it as a
first step.

## Flagged residual risk 2 - ADR-006 / single-device evidence

`ADR-006` (audio focus / FGS) was verified end-to-end on exactly one real
device (Xiaomi Mi Mix 2S, LineageOS 22.2, Android 15/API 35), triggered from
a real `NotificationListenerService` callback with the UI absent, for both
the `NO_FGS` and `WITH_FGS` paths. Both paths worked. This is real evidence,
not a hypothesis, but it is **not yet OEM/API-general** - no API 33/34
device and no stock-Android/Samsung/other-OEM device has been tested. `P4-T05`
and `P6-T02` must extend this matrix before the audio architecture is
treated as release-final. P0-R01 reconciled this evidence with Android 15's
official requirement that a target-35+ app be topmost or run an FGS before
requesting audio focus. Production therefore uses a short-lived mediaPlayback
FGS on API 35+, fails closed if it cannot start, and uses no-FGS only on API
26-34. `ADR-006` also surfaced concrete evidence (two
overlapping `AudioFocusRequest`/TTS instances interfering with each other
during a real back-to-back-notification burst) that makes `ADR-005`'s
single-active-utterance queue a proven correctness requirement, not a
style preference - `P4-T03` must treat that as a hard acceptance criterion.

## Cross-ADR consistency check

- Group policy is consistently described as the three-mode
  `ALL_OBSERVED_GROUPS` / `SELECTED_GROUPS_ONLY` / `NO_GROUPS` model across
  `ADR-001`, `ADR-004`, `ADR-005`, and `ARCHITECTURE.md`. `ADR-001`
  correctly forces migrated old installs to `NO_GROUPS` rather than
  guessing intent from the old app's `selectedGroups` key.
- Package allowlist (`com.whatsapp`, `com.whatsapp.w4b`) is consistent
  across `ADR-003`, `ADR-005`, and the spike (`ADR-006`).
- No ADR introduces automatic riding detection, Bluetooth, emergency/priority
  contacts, keyword classification, message history, DND changes, or global
  volume changes - all explicitly re-checked against `PROJECT_CHARTER.md`'s
  Non-Goals during this coordination pass.
- `ADR-009`'s "no message bodies persisted or logged" rule is honored by the
  P0-T05 spike itself (it speaks only a fixed synthetic phrase and logs only
  structural trial metadata) and by every fixture in `docs/fixtures/`
  (synthetic data only, self-audited).

## Phase 0 Gate self-assessment

Per `IMPLEMENTATION_PLAN.md`'s Phase 0 Gate:
- [x] ADR-001 through ADR-012 approved (with the two flagged, trackable
      residual risks above - neither blocks Phase 1 start, both must be
      closed before their respective later gates).
- [x] Product scope has no undefined version 1 switches (`ADR-005`
      enumerates every visible setting).
- [x] Audio architecture proven on at least one required API level
      (API 35, the current release target) with real listener-callback
      evidence; broader matrix remains open per residual risk 2.
- [x] Privacy-safe fixture corpus exists (`docs/fixtures/`, 14 files,
      self-audited, no real personal data).
- [ ] P0-R01 independent phase review - see below.

P0-R01 is being dispatched as a separate, independent review task next.
