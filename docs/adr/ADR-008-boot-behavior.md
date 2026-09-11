# ADR-008: Notification Listener Lifecycle And Boot-Receiver Necessity

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`ARCHITECTURE.md`'s "Background And Lifecycle Rules" section already states
"a boot receiver is omitted unless device tests establish a concrete need,"
and its "Recheck Result" explicitly rejects the old app's "missing
BootReceiver" framing as evidence of a defect. `ARCHITECTURE.md`'s "Required
Architecture Decisions" list requires `ADR-008` to record the decision to
"omit or retain boot behavior based on evidence." This ADR supplies the
official-documentation evidence supporting that default and states the
concrete conditions under which it should be revisited.

## Decision

### Documented facts about `NotificationListenerService` lifecycle

1. The listener is a manifest-declared, system-bound `Service` protected by
   `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, matched by an
   intent filter for `android.service.notification.NotificationListenerService`.
   The app never calls `startService()`/`startForegroundService()` on it;
   the system binds it once the user grants notification access in system
   settings. (Evidence #1)
2. The service must wait for `onListenerConnected()` before performing any
   operation. `requestRebind(ComponentName)` is documented as "the only
   [method] that is safe to call before `onListenerConnected()` or after
   `onListenerDisconnected()`," implying the system may disconnect the
   listener and that the app has a documented, narrow, non-privileged way to
   ask for reconnection. A companion method, `requestUnbind()`, lets the app
   voluntarily ask the system to unbind it. (Evidence #1, #2)
3. From API level 24 (`Build.VERSION_CODES.N`) onward, all listener callbacks
   run on the main thread; before that, no thread guarantee is documented.
   (Evidence #1)
4. Notification listeners are documented as unable to obtain notification
   access, or be bound at all, "on low-RAM devices running Android Q (and
   below)," and are ignored entirely inside a work profile; a
   `DevicePolicyManager` may additionally block work-profile notifications
   from reaching a listener. (Evidence #1) None of this applies to the
   primary test device (a non-work-profile, non-low-RAM Android 15 phone),
   but it is recorded because it constrains any future device matrix
   expansion (`ADR-002`).
5. Nothing in the official `NotificationListenerService` reference states
   or implies that a `BOOT_COMPLETED` broadcast receiver is required,
   recommended, or has any effect on whether the system (re)binds a granted
   listener. The binding model described is entirely driven by the user's
   persistent "notification access" grant plus the manifest `<service>`
   declaration, not by any broadcast the app must handle.

### Hypothesis requiring a device test (not asserted as fact here)

- Whether, and how quickly, the system automatically rebinds the listener
  process after the hosting process is killed (e.g. by the user, by
  low-memory eviction, or by an OEM/LineageOS-specific background-app
  policy) is **not stated** in the official `NotificationListenerService`
  reference beyond the existence of `requestRebind()`/`requestUnbind()` as
  manual escape hatches. Community reports (a Stack Overflow thread,
  explicitly a secondary, non-official source, not cited as fact) describe
  the system automatically restarting and rebinding a killed listener
  process in at least one observed case, but this is not confirmed by any
  primary Android source and must not be treated as guaranteed behavior.
  `P3-T03` (listener recovery) and `P6-T02` (device qualification on the
  real Mi Mix 2S / LineageOS 22.2 device) are the correct places to observe
  and
 record actual rebind timing and reliability.
- Whether LineageOS 22.2 specifically delays, throttles, or otherwise
  changes this rebind behavior relative to stock/AOSP Android 15 is
  unverified; see `ADR-012` for the parallel battery-management gap.

### Boot-receiver decision

**No `BOOT_COMPLETED` broadcast receiver is added.** This preserves
`ARCHITECTURE.md`'s existing default and is justified because:

1. No official documentation states or implies that
   `NotificationListenerService` binding depends on, or benefits from, a
   `BOOT_COMPLETED` receiver. The listener's activation is controlled by the
   persistent per-user notification-access grant, re-evaluated by the
   system as part of its own service-binding lifecycle, not by app code
   run at boot.
2. Adding a `BOOT_COMPLETED` receiver would require the
   `RECEIVE_BOOT_COMPLETED` permission, which `ARCHITECTURE.md`'s manifest
   policy does not list among expected components, and which would need its
   own justification and privacy review under `ADR-009` if ever proposed.
3. If a boot receiver were later added specifically to pre-warm a foreground
   playback service, note that apps targeting Android 15+ are explicitly
   **not** allowed to launch a `mediaPlayback`-typed foreground service from
   a `BOOT_COMPLETED` receiver (Evidence #3), which would make that specific
   motivation moot on the primary target API level regardless.

### Condition for revisiting this decision

This default must be revisited only if `P3-T03` or `P6-T02` produce
reproducible real-device evidence (not speculation) that:

- the listener fails to (re)bind within an unacceptable, user-visible delay
  after a normal boot on a supported device, **and**
- no narrower fix (e.g. relying on `requestRebind()`, or documenting a
  one-time manual re-open of notification-access settings) resolves it.

Any such revisit must be a new ADR revision citing the specific
reproducible evidence, not a guess.

## Alternatives Considered

- Add a `BOOT_COMPLETED` receiver "just in case" - rejected. No official
  documentation supports a functional need, it requires an additional
  permission `ARCHITECTURE.md` does not pre-approve, and it directly
  contradicts the explicit architecture default and the Phase 0 rejection of
  the old app's "missing BootReceiver" framing.
- Treat the unverified community report of automatic process rebind as a
  confirmed guarantee - rejected. It is a secondary source and is recorded
  only as a hypothesis for device testing, not as evidence for this
  decision.

## Consequences

- Positive: keeps the manifest minimal, consistent with `ARCHITECTURE.md`'s
  "Manifest Policy" and avoids an unjustified privileged-feeling permission.
- Positive: matches the charter's explicit non-goal of "guaranteeing
  operation after Android force-stop" - a boot receiver would not achieve
  that guarantee anyway, since Android's force-stop state persists across
  boots for the affected app until the user manually reopens it.
- Negative / residual risk: actual rebind timing on the real Mi Mix 2S /
  LineageOS 22.2 device, and any other future device, is unverified until
  `P3-T03`/`P6-T02` run. If it proves poor, the UI must surface an honest
  "listener disconnected" state (per `ARCHITECTURE.md`'s home-screen status
  requirements) rather than the app silently failing.

## Dependent Tasks

`P3-T01` (listener service implementation must implement
`onListenerConnected`/`onListenerDisconnected`/`requestRebind` correctly),
`P3-T03` (listener recovery, which must produce the real rebind-timing
evidence this ADR defers to), `P6-T02` (device qualification).

## Evidence

All links accessed and verified resolving on 2026-09-11.

1. `NotificationListenerService` API reference (binding model, lifecycle,
   low-RAM/work-profile restriction, main-thread callback guarantee since
   API 24):
   https://developer.android.com/reference/kotlin/android/service/notification/NotificationListenerService
2. API diff confirming `onListenerDisconnected()`, `requestRebind()`, and
   `requestUnbind()` as the methods added for listener lifecycle management:
   https://developer.android.com/sdk/api_diff/24/changes/android.service.notification.NotificationListenerService
3. "Changes to foreground service types for Android 15" - `BOOT_COMPLETED`
   restriction on `mediaPlayback` (and other) foreground service types:
   https://developer.android.com/about/versions/15/changes/foreground-service-types

Secondary, non-official source recorded only as a hypothesis prompt (not as
evidence for the decision): a Stack Overflow thread describing observed
automatic listener-process restart after a killed debug session -
https://stackoverflow.com/questions/76238237/android-notificationlistenerservice-app-unwanted-restart
(content confirmed via search-engine index snippet on 2026-09-11; the `fetch`
tool could not independently retrieve this page directly because
stackoverflow.com returned an anti-bot interstitial to the automated fetch
request. This link is explicitly secondary/anecdotal, and this limitation is
noted rather than concealed.
