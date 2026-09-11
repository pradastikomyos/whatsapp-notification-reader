# ADR-010: Manual Dependency Container Versus Hilt

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`ARCHITECTURE.md`'s "Technology Baseline" states: "A manual dependency
container initially; use Hilt only if wiring complexity materially grows,"
and its package layout names a concrete file, `di/AppContainer.kt`, as part
of the single-module scaffold. `ARCHITECTURE.md`'s "Required Architecture
Decisions" item 10 requires this ADR to make that choice explicit rather
than leaving it as an implicit default, and to give a future worker a
non-subjective basis for deciding whether wiring complexity has "materially
grown" enough to justify Hilt, so no one switches prematurely based on
personal preference alone.

The project is explicitly scoped as a single Gradle module
(`ARCHITECTURE.md`, "Review Verdict": "Use one Gradle application module
initially... multiple Gradle modules would add build and wiring cost before
they provide a measurable benefit"). The object graph described by the
package layout is small and mostly linear: one `Application` class, one
`NotificationListenerService`, one settings repository, one
conversation-repository, a handful of policy/formatter singletons, and a
speech coordinator - not a large multi-module graph with many
scope-crossing lifecycles, which is the scenario where Hilt's compile-time
graph validation and generated scoped components earn back their build-time
and cognitive cost.

## Decision

1. **Default for v1**: use a manual dependency container,
   `di/AppContainer.kt`, exactly as named in `ARCHITECTURE.md`'s package
   layout. `AppContainer` is constructed once from `Application.onCreate()`
   (for application-scoped singletons: settings repository, conversation
   repository, speech coordinator, TTS engine, audio focus controller) and
   is read by `MainActivity`/ViewModels and by
   `WhatsAppNotificationListenerService` through the `Application` reference,
   per `ARCHITECTURE.md`'s "Background And Lifecycle Rules" ("Initialize
   service dependencies from application/service context"). No service
   locator singleton scattered across arbitrary classes; `AppContainer` is
   the single, explicit composition root.
2. **Do not add Hilt (or Dagger, Koin, or any other DI framework) at project
   start.** No DI framework dependency is added in `P1-T01`/`P1-T02`.
3. **Concrete Hilt-adoption trigger criteria** - Hilt becomes justified only
   when at least one of the following becomes true, and the worker proposing
   the switch must cite which criterion is met and where:
   - **(a) Multi-module split.** The project moves from one Gradle module to
     multiple modules (for example, splitting `notification`, `speech`, or
     `ui` into separate modules) and manual construction would require
     passing dependencies across module boundaries through public
     constructor parameters that leak internal wiring details into public
     module APIs. `ARCHITECTURE.md`'s "Review Verdict" currently rejects a
     multi-module split for this project's size, so this criterion is not
     met at present and its own reversal would itself need a new ADR first.
   - **(b) Scope proliferation.** The app needs three or more independently
     -lived, framework-tied lifecycle scopes with real fan-out between them
     (for example, a genuine `@ServiceScoped` graph for the listener, a
     separate `@ActivityRetainedScoped` graph for UI, and a separate
     foreground-service scope for `SpeechPlaybackService` from `ADR-006`),
     such that `AppContainer` would need hand-written scope-tracking
     equivalent to what Hilt generates (matching child containers to
     component lifecycle callbacks, tearing them down correctly on
     `onDestroy`/unbind). A single extra service scope (the notification
     listener, or a conditional playback service) is not, by itself, enough;
     that is exactly the two-or-three-object case a manual container handles
     with a plain constructor parameter or a small factory function.
   - **(c) Constructor-injection fan-out.** A single class's manually-written
     constructor call in `AppContainer` requires passing more than
     approximately 6-8 dependencies by hand, or the same leaf dependency
     (for example, `SettingsRepository`) needs to be threaded manually
     through more than roughly 5 intermediate constructors just to reach a
     deeply nested consumer, indicating the manual graph has stopped being
     readable at a glance. This is a readability signal, not a hard
     numeric gate; the worker must show the actual `AppContainer` diff that
     motivated the count, not an estimate.
   - **(d) Testing friction caused specifically by manual wiring.** Unit or
     instrumented tests need to substitute more than one or two leaf
     dependencies deep inside a manually-constructed object graph, and doing
     so requires threading fakes through multiple intermediate constructors
     that themselves have no test-relevant behavior (pure pass-through
     wiring). If fakes can be substituted at the top of `AppContainer`
     (which is the common case for this project's shallow graph), this
     criterion is not met.
   - None of these criteria are met by the object graph described in
     `ARCHITECTURE.md`'s current package layout (Context). This ADR
     therefore records that Hilt is **not** adopted at project start, and
     remains not adopted unless a future worker documents which specific
     criterion above is met, with concrete evidence (an actual
     `AppContainer.kt` diff or a specific failing/awkward test), in a
     superseding ADR.
4. Adopting Hilt later, if ever justified, requires: adding the Hilt Gradle
   plugin and KSP/KAPT dependency, converting `Application` to
   `@HiltAndroidApp`, converting the listener service and any
   activities/ViewModels to their Hilt-supported annotations
   (`@AndroidEntryPoint`, `@HiltViewModel`), and replacing `AppContainer`'s
   manual object graph with `@Module`/`@Provides` (or `@Binds`) definitions.
   That migration is out of scope for this ADR; this ADR only decides the
   starting point and the trigger for reconsidering it.

## Alternatives Considered

- **Adopt Hilt from project start "to avoid a painful later migration"** -
  rejected. `ARCHITECTURE.md`'s baseline explicitly defers Hilt, the current
  object graph is small enough that a later migration (if ever triggered) is
  a bounded, mechanical refactor rather than an architectural rewrite, and
  adopting Hilt now adds KSP/KAPT build-time cost, generated code, and a
  steeper onboarding curve with no present benefit.
- **Adopt Koin instead of Hilt or a manual container** - rejected. Koin's
  service-locator style (resolved by type at call time rather than
  constructor-injected) is harder to statically verify than either a manual
  container or Hilt's compile-time graph, and `ARCHITECTURE.md` does not
  list it in the technology baseline; introducing an unlisted DI framework
  would itself require a new ADR with justification this project's current
  scale does not have.
- **No container at all; construct dependencies ad hoc wherever needed** -
  rejected. `ARCHITECTURE.md`'s package layout names `di/AppContainer.kt`
  explicitly as the composition root; ad hoc construction would duplicate
  singleton lifetimes (for example, creating more than one
  `SpeechCoordinator`, which `ARCHITECTURE.md` requires to be
  application/service-scoped, never per-call), which manual-container
  discipline is specifically meant to prevent.

## Consequences

- `P1-T02` implements `di/AppContainer.kt` as the single composition root;
  no DI framework dependency is added.
- Every later phase task that introduces a new class needing shared
  dependencies (settings repository, conversation repository, speech
  coordinator, TTS engine, audio focus controller, policy evaluator) wires
  it through `AppContainer`, not through a new ad hoc singleton or a second
  container.
- A future worker proposing Hilt adoption must cite one of the four
  numbered criteria in Decision item 3 with concrete evidence, and must
  write a superseding ADR before making the change; this prevents a
  mid-project switch based on unstructured preference.
- If `ADR-006` (audio focus and foreground playback service) or `ADR-004`
  (conversation identity) introduce a materially larger object graph than
  currently described in `ARCHITECTURE.md`'s package layout, their owning
  workers should explicitly re-check this ADR's criteria rather than
  silently adding a DI framework as part of an unrelated task.

## Dependent Tasks

- `P1-T01` (scaffold project) - must not add a DI framework dependency.
- `P1-T02` (minimal manifest and application container) - implements
  `AppContainer.kt` as the composition root.
- All Phase 2-5 tasks that add classes with shared dependencies wire through
  `AppContainer` per this ADR until/unless a superseding ADR changes it.

## Evidence

- `ARCHITECTURE.md`, "Technology Baseline" ("A manual dependency container
  initially; use Hilt only if wiring complexity materially grows."),
  "Package Layout" (`di/AppContainer.kt`), "Review Verdict" (single-module
  scope), and "Required Architecture Decisions" item 10 - direct source of
  this ADR's scope and starting default.
- N/A for external sources - this is a project-scoping engineering decision,
  not a platform-fact research task; no official Android/Hilt documentation
  claim is made beyond general, undisputed knowledge of what Hilt requires
  (Gradle plugin, KSP/KAPT, annotation-based entry points), which is not
  time-sensitive platform behavior requiring an access-dated citation.
