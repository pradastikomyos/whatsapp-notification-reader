# Implementation Plan

## Execution Model

Work is organized as gated phases. A worker claims one ready task, reads all
required context, makes the smallest coherent change, runs specified checks, and
returns evidence. A task is not complete because code exists; its acceptance
criteria and verification must pass.

Only parallelize tasks marked safe to run concurrently. No worker may overwrite,
revert, or reformat unrelated work.

## Global Quality Rules

- No Flutter or Dart dependencies in the target project.
- No placeholder UI or fabricated data.
- No real or personal message bodies or sender names in production logs or
  committed fixtures. Clearly synthetic fixture values are permitted.
- No global media-volume manipulation, DND changes, notification cancellation,
  or synthetic play/pause media events.
- Use exact WhatsApp package allowlisting.
- Pure domain logic receives no Android framework objects.
- Keep one Gradle app module until an ADR justifies more.
- New behavior requires tests at the lowest practical layer.
- Every phase ends with a review worker who did not author its main code.

## Phase 0 - Decisions And Evidence

**Goal:** freeze version 1 behavior and remove architectural ambiguity before
creating implementation that depends on it.

### P0-T00 Disposable spike scaffold

- Create an isolated native test project under `spikes/audio-background/` only.
- It is exempt from the production implementation freeze solely for P0-T05.
- Keep it dependency-minimal and provide build/install/test instructions.
- Acceptance: spike builds independently and no spike source is referenced by
  the future production `app` module.

### P0-T01 Product contract

- Define reader enablement, private/group defaults, manual riding semantics,
  queue disable behavior, and supported WhatsApp variants.
- Mark future features as excluded rather than leaving placeholders.
- Output: approved product contract section or ADR references.
- Acceptance: every visible version 1 setting has deterministic behavior.
- Draft ADR-005 and ADR-007 from the approved queue/riding semantics.

### P0-T02 Platform support matrix

- Decide minimum SDK, target SDK, supported OEMs, WhatsApp/Business variants,
  supported locales, and audio routes.
- Acceptance: test matrix is affordable and covers Android 33-35+ constraints.
- Draft ADR-002, ADR-008, ADR-011, and ADR-012.

### P0-T03 Identity and privacy ADRs

- Decide application ID/signing continuity, backups, stored conversation data,
  logging/redaction, analytics/network policy, and reset semantics.
- Acceptance: `ADR-001`, `ADR-009`, and release identity plan are approved.
- ADR-001 must decide old listener FQCN continuity/reauthorization and inventory
  every old SharedPreferences key as migrate, transform, or reset.
- Draft ADR-010 for the initial dependency-container choice.

### P0-T04 Notification fixture protocol

- Define a repeatable method to capture and sanitize notification structures.
- Produce synthetic fixtures first; real fixtures must contain no personal data.
- Acceptance: fixtures cover direct, group, summary, bundled update, attachment,
  redacted, URL, timestamp, and colon-containing direct-message cases.
- Draft ADR-003 and ADR-004, including observed-conversation identity, rename,
  collision, and persistence behavior.

### P0-T05 Audio/FGS technical spike

- Use only the disposable P0-T00 project.
- Prototype listener-triggered TTS with UI absent across exact OS/target-SDK
  combinations in the support matrix, including the current release target.
- Compare no-FGS and short-lived media-playback FGS where platform rules permit.
- Trigger the test from an actual notification-listener callback.
- Record start restrictions, audio-focus result, queue completion, and cleanup.
- Acceptance: `ADR-006` selects a verified architecture or declares a release
  blocker. If focus/FGS eligibility fails, the approved behavior is no speech.

### P0-T06 ADR approval

- Review and approve ADR-001 through ADR-012 after spike evidence is available.
- Record owner, date, status, alternatives, consequences, and dependent tasks.
- A deferred ADR blocks every dependent task; `deferred` is not approval.
- Acceptance: every Phase 1 dependency is approved and later blocked work is
  explicitly identified. ADR-005 must be approved before P4-T03.

### P0-R01 Independent phase review

- Review product scope, fixture privacy, spike evidence, ADR consistency, and
  all unresolved blockers without authoring the primary decisions.
- Acceptance: critical/high findings are resolved or owner-accepted in writing.

### Phase 0 Gate

- ADR-001 through ADR-012 are approved, or each deferred ADR explicitly blocks
  all dependent tasks and those tasks are removed from the active plan.
- Product scope has no undefined version 1 switches.
- Audio architecture is proven on required API levels.
- Privacy-safe fixture corpus exists.
- P0-R01 approves the phase.

## Phase 1 - Native Foundation

**Goal:** create a reproducible, minimal native Android project.

### P1-T01 Scaffold project

- Create Kotlin/Compose project using approved package and SDK levels.
- Add version catalog, deterministic repositories, lint, and test dependencies.
- Do not add Room, Hilt, networking, analytics, or FGS until required.
- Acceptance: clean debug build and JVM test task pass.

### P1-T02 Minimal manifest and application container

- Add application, launcher activity, and correctly protected listener service.
- Add only approved permissions and TTS queries.
- Create application-scoped dependency container with test substitution points.
- Apply ADR-001 listener-component identity and settings upgrade decisions.
- Acceptance: manifest contains no nonexistent or privileged components.

### P1-T03 CI and developer commands

- Add documented format/lint/test/build commands and CI for pull requests.
- Acceptance: a clean environment can reproduce all checks.

### P1-R01 Independent phase review

- Review scaffold, dependency graph, manifest, permissions, exports, component
  identity, reproducibility, and absence of Flutter artifacts.

### Phase 1 Gate

- No Flutter artifacts or dependencies.
- Debug app launches.
- Listener component resolves.
- Build, lint, and empty smoke suite pass in CI.
- P1-R01 approves the phase.

## Phase 2 - Domain And Data

**Goal:** implement testable models, settings, parser, deduplication, and policy
before connecting Android services.

### P2-T01 Domain models

- Add immutable snapshot, parsed-message, conversation identity, settings,
  reading decision, and speech request models.
- Acceptance: models encode unsupported/redacted states without nullable ambiguity.

### P2-T02 DataStore settings

- Implement approved defaults and the explicit old SharedPreferences migration,
  transformation, or reset map from ADR-001.
- Expose Flow-based reads and atomic updates.
- Acceptance: restart and concurrent update tests pass.

### P2-T03 Snapshot extraction

- Convert Android notifications into immutable snapshots.
- Add Robolectric tests for extras and MessagingStyle extraction.
- Acceptance: downstream parser uses no framework Notification object.

### P2-T04 Parser chain

- Implement structured parsing first and conservative legacy fallback second.
- Acceptance: fixture corpus passes; colon-only group inference is impossible.

### P2-T05 Deduplicator

- Implement bounded, clock-driven cache and notification update semantics.
- Acceptance: repeats are suppressed while distinct burst messages survive.

### P2-T06 Reading policy and formatter

- Implement pure policy decisions and safe Indonesian speech formatting.
- Acceptance: table-driven tests cover every policy skip reason and group mode.

### P2-T07 Conversation repository contract

- Implement ADR-004 identity and repository contract, including observed versus
  selected state, collisions, renames, and retention/reset behavior.
- Add Room only if ADR-004 requires persistence; otherwise use its approved store.
- Acceptance: discovery cannot mutate selection and repository tests pass.

### P2-R01 Independent phase review

- Review pure/framework boundaries, migrations, parser fixtures, deduplication,
  conversation identity, policy completeness, privacy, and test determinism.

Safe parallelism: T02, T03, initial T05, and T07 may run after T01 and their
ADRs. T04 depends on P0-T04 fixtures and the P2-T03 snapshot contract. T06
depends on T01 and the approved product contract.

### Phase 2 Gate

- Domain tests pass deterministically without device or network.
- Discovery and selection are separate concepts.
- Unsupported notifications fail closed and are not spoken.
- No sensitive payload logging.
- P2-R01 approves the phase.

## Phase 3 - Native Listener Integration

**Goal:** deliver eligible notification snapshots independently of the UI.

### P3-T01 Listener service

- Implement exact package allowlist, connection tracking, rapid snapshotting,
  and serialized ingress submission.
- Keep business rules out of callback methods.
- Acceptance: activity-free ingress demonstrated on emulator and real device.

### P3-T02 Pipeline orchestration

- Connect snapshot, parser, deduplicator, settings, and policy.
- Emit structured redacted diagnostics and speech requests.
- Acceptance: end-to-end fake ingress tests cover speak and all skip paths.

### P3-T03 Listener recovery

- Handle connection/disconnection and use `requestRebind()` only where valid.
- Verify process recreation reloads settings. Notification arrival before the
  first settings snapshot must fail closed after a bounded wait.
- Acceptance: no static activity/channel state and no ordinary service start.

### P3-T04 Conversation observation integration

- Record an approved observed conversation at the ADR-004 pipeline point,
  including messages later rejected by selection when discovery policy permits.
- Reconcile rename/collision behavior without auto-selecting the conversation.
- Acceptance: P2-T07 integration tests prove discovery and selection independence.

### P3-R01 Independent phase review

- Review listener lifecycle, callback duration, cold start, serialization,
  deduplication ownership, conversation observation, redaction, and UI independence.

### Phase 3 Gate

- Notification reaches policy pipeline with activity removed from recents.
- Duplicate callbacks do not create duplicate speech requests.
- Listener never cancels or mutates source notifications.
- P3-R01 approves the phase.

## Phase 4 - Speech And Audio

**Goal:** serialize speech safely under the approved Android background model.

### P4-T01 TTS engine adapter

- Implement async initialization, locale validation, unique utterance IDs,
  progress callbacks, timeout, failure, stop, and shutdown semantics.
- Acceptance: fake-engine state tests and device engine checks pass.

### P4-T02 Audio focus controller

- Implement approved AudioAttributes and focus gain/loss handling.
- Acceptance: focus is abandoned only after terminal utterance state.

### P4-T03 Bounded speech coordinator

- Implement approved ordering, age limit, capacity, overflow, interruption,
  aggregation, and policy-change behavior.
- Acceptance: deterministic concurrency and burst tests pass.
- Dependency: approved ADR-005; do not decide queue policy during implementation.

### P4-T04 Playback service for API 35+

- Implement the short-lived foreground playback path required by `ADR-006` for
  API 35+; API 26-34 retain the no-FGS path.
- Add accurate notification channel and API-specific permissions.
- Start foreground within deadline and stop after queue drain.
- Acceptance: API 35+ never requests focus before foreground promotion;
  rejected startup/promotion fails closed; device tests extend spike evidence.

### P4-T05 End-to-end audio tests

- Test speaker, wired, Bluetooth, media playing/paused, focus denial/loss,
  call/alarm conditions, unavailable Indonesian voice, and process recreation.
- Acceptance: no volume writes or synthetic media control events occur.

### P4-R01 Independent phase review

- Review FGS legality, fail-closed paths, call/alarm handling through approved
  non-privileged signals, focus lifecycle, TTS cleanup, queue bounds, and evidence.

Safe parallelism: T01 and T02 after Phase 0 spike. T03 depends on their contracts.

### Phase 4 Gate

- One active utterance maximum.
- Queue is bounded and stale entries expire.
- Audio behavior passes approved device matrix.
- Background behavior matches claims and ADR-006.
- P4-R01 approves the phase.

## Phase 5 - Persistence And UI

**Goal:** expose only implemented behavior through an accurate Compose UI.

### P5-T01 Onboarding and status

- Explain notification access, launch system settings, validate return state,
  expose listener connection, reader enabled state, and TTS status separately.
- Acceptance: denial, grant, unavailable TTS, and reconnect flows are clear.

### P5-T02 Home and reader controls

- Implement master reader toggle, test speech, speed, and effective policy state.
- Acceptance: controls update DataStore and background pipeline immediately.

### P5-T03 Settings

- Implement only validated private/group, locale, sender/group announcement,
  and queue settings.
- Acceptance: every setting has persistence and policy tests.

### P5-T04 Observed conversations

- Build observed-group selection on P2-T07/P3-T04; never use fabricated groups.
- Acceptance: discovery never auto-selects in selected-only mode.

### P5-T05 Manual riding mode

- Implement the exact `ADR-007` policy and effective-state display.
- Acceptance: service and UI observe one persisted source of truth.

### P5-T06 Accessibility and responsive review

- Verify TalkBack labels, touch targets, font scaling, dark theme, and small/large
  screen behavior.
- Acceptance: Compose accessibility checks and manual review pass.

### P5-R01 Independent phase review

- Review repository-driven state, navigation, truthfulness, accessibility,
  process recreation, and absence of unimplemented settings/placeholders.

Safe parallelism: screen UI workers can run after repository/ViewModel contracts
are stable, with one owner for navigation and shared design components.

### Phase 5 Gate

- No placeholder controls or fabricated statistics.
- UI does not own service lifecycle.
- All displayed states are derived from repositories/platform state.
- Settings survive process death.
- P5-R01 approves the phase.

## Phase 6 - Hardening And Release

**Goal:** qualify privacy, reliability, compatibility, and release readiness.

### P6-T01 Full automated suite

- Run unit, Robolectric, instrumented, lint, and release-build checks.
- Add regression tests for every resolved defect.

### P6-T02 Device and OEM qualification

- Execute approved API/OEM matrix with screen locked/unlocked, UI absent,
  WhatsApp/Business, notification privacy modes, and audio routes.
- Record pass/fail evidence and known limitations.

### P6-T03 Security and privacy review

- Audit manifest exports, permissions, logs, backups, local storage, dependencies,
  and network access.
- Acceptance: no sensitive content appears in logs or unintended backups.

### P6-T04 Performance and resource measurement

- Measure release APK/AAB size, cold startup, idle memory, burst queue behavior,
  listener callback duration, and battery impact under a repeatable scenario.
- Compare against the Flutter baseline only with like-for-like release builds.

### P6-T05 Release preparation

- Configure non-debug signing outside source control, shrinking rules, versioning,
  release notes, privacy policy, README, and store claims.
- Produce rollback criteria/procedure and a privacy-safe release monitoring plan.
- Acceptance: signed release artifact installs and upgrade path is verified.

### P6-R01 Final independent review

- Trace representative direct, group, duplicate, redacted, cold-start, and
  focus-loss cases through the complete system and review all release evidence.

### Final Gate

- All prior gates pass.
- No unresolved critical/high defects.
- Known limitations are documented.
- Claims are supported by test evidence.
- Rollback and release-monitoring plan exists.
- P6-R01 approves release.

## Dependency Summary

```text
Phase 0 decisions and spikes
  -> Phase 1 scaffold
  -> Phase 2 domain/data
  -> Phase 3 listener integration
  -> Phase 4 speech/audio
  -> Phase 5 UI
  -> Phase 6 qualification
```

UI mockups may be explored earlier, but production UI implementation must not
create settings whose domain semantics are unresolved.

## Worker Handoff Contract

Every worker response must include:

1. Claimed task ID and result.
2. Files changed and why.
3. Design decisions or assumptions introduced.
4. Commands run and exact outcomes.
5. Acceptance criteria evidence.
6. Risks, blockers, or follow-up task IDs.
7. Confirmation that unrelated changes were not reverted.
