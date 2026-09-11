# ADR-012: Evidence-Based Battery/Doze Guidance And OEM Gaps

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`ARCHITECTURE.md` states "battery-optimization exclusion is optional
guidance, not a reliability promise" and requires `ADR-012` to record
"evidence-based battery and OEM guidance." `PROJECT_CHARTER.md` forbids
"unmeasured reliability or safety claims" and lists "guaranteeing operation
after Android force-stop" and "guaranteeing identical behavior on every OEM
or WhatsApp version" as explicit non-goals. The only real test device
available in this environment is a Xiaomi Mi Mix 2S running **LineageOS
22.2**, a community-maintained, non-stock ROM; no stock MIUI/HyperOS,
Samsung One UI, or stock AOSP/Pixel device is currently available.

## Decision

### Documented facts about Doze and App Standby (official, AOSP-level)

1. **Doze** activates when a device is stationary, unplugged, and
   screen-off for a period; while active it suspends network access,
   ignores wake locks, defers standard `AlarmManager` alarms (unless set
   with `setExactAndAllowWhileIdle()`/`setAndAllowWhileIdle()`), disables
   Wi-Fi scans, and defers sync adapters and `JobScheduler`/`WorkManager`
   work to periodic "maintenance windows." (Evidence #1)
2. **App Standby** marks an app idle - deferring its background network
   access - when the user has not interacted with it and the app has no
   foreground activity/foreground service and has not posted a
   lock-screen/notification-tray notification. (Evidence #1)
3. Both mechanisms are documented as applying to "all apps running on
   Android 6.0 or higher, regardless of whether they are specifically
   targeting API level 23," i.e. Doze/Standby cannot be avoided merely by
   choice of `targetSdkVersion`. (Evidence #1)
4. Apps can query their own exemption state via
   `isIgnoringBatteryOptimizations()`, and can direct users to the general
   settings screen via `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, or, for
   a narrower documented set of "acceptable use cases," request direct
   exemption via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. The same
   source states: "Google Play policies prohibit apps from requesting
   direct exemption from Power Management features... unless the core
   function of the app is adversely affected." (Evidence #1)
5. Google's own acceptable-use-case table (same source) explicitly lists
   "Instant messaging, chat, or calling app" that "requires delivery of
   real-time messages... while device is in Doze" as **Not Acceptable**
   for direct exemption when the app can use FCM high-priority messages,
   and Acceptable only if the app has "a technical dependency on another
   messaging service" preventing FCM use.

### Analysis specific to this app's architecture

This app does not send or receive its own push/network messages; it reads
`StatusBarNotification`s that WhatsApp itself already posted through the
system's `NotificationListenerService` binding (a local IPC callback, not
a network wakeup), and then runs a short local `TextToSpeech` synthesis.
Neither of these steps is described by the official Doze/Standby
documentation as something Doze suspends: Doze suspends *this app's own*
network access, wake locks, alarms, and jobs, not the system's delivery of
notifications from *another already-running app* (WhatsApp) to the
notification framework, nor a synchronous local TTS call triggered by that
callback. Because of this, and because this app does not use FCM at all
(no network dependency to point to), it does not cleanly match either the
"Acceptable" or "Not Acceptable" rows of Google's own acceptable-use-case
table, which is written for apps that *would* otherwise use FCM. Given
that ambiguity plus the Play policy language quoted above ("unless the core
function of the app is adversely affected" - which has not been
demonstrated with evidence at this stage of Phase 0), this ADR makes the
conservative choice:

1. **Do not request the direct exemption intent**
   (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) by default. Doing so
   without a demonstrated, evidence-backed "core function adversely
   affected" case risks a Play policy rejection and is not justified by the
   facts gathered in this task.
2. **May offer only the generic informational settings screen**
   (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), presented as optional,
   dismissible guidance, worded to state that excluding the app from
   battery optimization *may* help notifications and speech remain timely,
   but is **not guaranteed** and does not override Android force-stop.
   This exactly matches `ARCHITECTURE.md`'s "optional guidance, not a
   reliability promise" instruction.
3. **No wake locks, `DEVICE_POWER`, `PREVENT_POWER_KEY`, or other privileged
   power-management permissions** are requested, consistent with
   `ARCHITECTURE.md`'s manifest policy.
4. If a future ADR revision wants to request the direct exemption intent,
   it must first collect concrete evidence (e.g. from `P6-T02` device
   qualification) that Doze/Standby measurably breaks the listener-to-speech
   pipeline on a real, supported device, and must document that evidence
   before changing this decision.

### Explicit, named gap: LineageOS (and other non-stock ROM/OEM) battery management is undocumented by Android's official sources

The Doze/App Standby documentation above describes AOSP-level behavior.
LineageOS is a community-maintained ROM, not a Google-published product;
Android's official developer documentation does not describe LineageOS's
specific battery-management stack (if any exists beyond AOSP Doze/Standby),
and no equivalent "LineageOS battery optimization developer guide" was found
during this research task. Likewise, stock Xiaomi (MIUI/HyperOS) devices
are widely known in community discussion to add additional non-AOSP
"autostart manager" and aggressive background-kill behavior, but this is
also not described in any official Android source, and this task found no
authoritative citation for it.

This ADR explicitly states: **LineageOS 22.2's actual battery/background
management behavior on the Mi Mix 2S is unverified by official
documentation and must be established empirically**, via `P0-T05`
(where relevant to audio/FGS) and `P6-T02` (device qualification), not
assumed from AOSP documentation or from unverified community claims about
either LineageOS or stock MIUI/HyperOS. Any user-facing copy about battery
behavior must stay generic and AOSP-sourced (as decided above) precisely
because no ROM/OEM-specific guarantee can currently be made.

## Alternatives Considered

- Request `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` proactively at
  first run - rejected. Not clearly justified under Google's own
  acceptable-use-case policy language, and risks Play policy rejection
  without first gathering the evidence the policy asks for.
- Claim the app is "unaffected by battery optimization" - rejected as an
  unmeasured reliability claim, and specifically false with respect to
  Doze's ability to suspend anything the app itself schedules
  independently in the future (alarms, jobs, network).
- Silently do nothing and show no battery-related guidance at all -
  rejected. `ARCHITECTURE.md` calls for "optional guidance," so the generic,
  honestly-worded settings link is retained as a middle ground.
- Attempt to describe LineageOS-specific or MIUI-specific battery behavior
  from community forum knowledge - rejected as guessing; explicitly flagged
  as a gap requiring device evidence instead.

## Consequences

- Positive: keeps the app's battery-related claims strictly inside what
  official Android documentation supports, avoiding both over-promising to
  users and Play policy risk from an unjustified exemption request.
- Positive: makes the LineageOS/OEM knowledge gap explicit and assigns it to
  the correct evidence-gathering tasks instead of leaving it implicit.
- Negative / residual risk: without a confirmed battery-optimization
  exemption, background reliability on aggressive OEM battery managers
  (documented gap above) may be worse than users expect; this must be
  communicated honestly in UI copy and store listing rather than concealed.
- Follow-up: `P6-T02`'s device/OEM qualification report must include a
  dedicated section recording actual observed LineageOS 22.2 behavior on
  the Mi Mix 2S, which this ADR intentionally leaves open pending that
  evidence.

## Dependent Tasks

`P5-T01`/`P5-T03` (any UI surfacing of battery guidance must follow the
wording constraints above), `P0-T05` (audio/FGS spike may surface related
background-restriction evidence), `P6-T02` (device/OEM qualification -
must record actual LineageOS 22.2 battery-management behavior), `P6-T05`
(release notes/store listing must not overstate reliability).

## Evidence

All links accessed and verified resolving on 2026-09-11.

1. "Optimize for Doze and App Standby" (developer.android.com, Doze/App
   Standby mechanics, exemption APIs, and the acceptable-use-case table for
   `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`):
   https://developer.android.com/training/monitoring-device-state/doze-standby

No official Android or AOSP source describing LineageOS-specific or
MIUI/HyperOS-specific battery-management behavior was found; this is
recorded above as an explicit, named documentation gap rather than filled
with an unverified secondary source.
