# WhatsApp Notification Reader - Native Android Migration

This folder is the target workspace for rebuilding the Flutter prototype in
`C:\Users\prada\Documents\prjkwanotif` as a native Android application.

Planning documents:

- `docs/CURRENT_STATUS.md` - canonical pause/resume handoff and actual state.
- `docs/PROJECT_CHARTER.md` - product goal, scope, constraints, and success metrics.
- `docs/ARCHITECTURE.md` - reviewed target architecture and technical decisions.
- `docs/IMPLEMENTATION_PLAN.md` - phases, tasks, dependencies, and quality gates.
- `docs/WORKER_BEE_PROMPTS.md` - reusable and phase-specific worker prompts.
- `docs/DEVELOPMENT.md` - local build, verification, and device smoke commands.
- `docs/reviews/` - recorded independent phase reviews.
- `docs/PHASE_STATUS.md` - current gate status and next ready task.

No production implementation should start before Phase 0 decisions are approved.
Phase 0 may create a disposable spike project only under `spikes/`; spike code
cannot enter the production app without an explicit review. The old application
is a behavior reference, not code to port mechanically.
