# Final Device Qualification Campaign

Run this campaign only after the current code is stable. Record one row per
device/API/OEM/route result. Never capture notification bodies, sender names,
phone numbers, screenshots with readable message content, or unredacted logs.

## Preparation

```powershell
adb devices -l
adb install -r <signed-release-apk>
adb shell pm clear com.ridenotify.app.wa_reader
adb logcat -c
```

Use synthetic fixture scenarios from `docs/fixtures/` where possible. For real
WhatsApp/Business traffic, record only package, API/OEM, scenario ID, timestamp,
terminal result, and an opaque evidence reference.

## Required Matrix

| Area | Required cases | Expected result |
|---|---|---|
| Audio route | Speaker, wired headset, Bluetooth; media playing and paused | No volume writes or media-button events; one active utterance maximum |
| Interruption | Incoming/active call, alarm, focus denial/loss | Current item stops/skips according to ADR-005; no unauthorized phone/DND access |
| TTS/FGS | Indonesian voice available/unavailable; API 35+ promotion success/rejection | Unavailable/rejected path fails closed; API 35+ requests focus only after promotion |
| Lifecycle | Locked/unlocked, UI absent, process recreation | Listener/pipeline remain UI-independent; persisted settings reload fail-closed |
| Notification source | WhatsApp and WhatsApp Business; privacy/redaction modes | Only supported parseable notifications are processed; no sensitive evidence retained |
| Platform | API 26-34 and API 35+ across available OEMs | Pass/fail and limitation recorded per actual device |
| Release | Signed fresh install and upgrade from prior signed build | Install/upgrade preserve supported data and notification access behavior is recorded |
| Performance | Cold start, idle memory, callback duration, burst, battery | Measured values and method recorded; no general guarantee inferred |

## ADB Evidence Commands

```powershell
adb shell am force-stop com.ridenotify.app.wa_reader
adb shell monkey -p com.ridenotify.app.wa_reader 1
adb shell dumpsys meminfo com.ridenotify.app.wa_reader
adb shell dumpsys activity services com.ridenotify.app.wa_reader
adb logcat -d -v threadtime
```

Notification dumps and logcat may contain sensitive content. Do not collect
them by default. If a specific defect requires local inspection, minimize and
redact the output before attaching evidence; never paste raw output here.

## Evidence Record

| ID | Device/OEM | API | App variant | Scenario | Result | Evidence reference | Limitation/defect |
|---|---|---:|---|---|---|---|---|
| Example only | Redacted | 0 | Debug/release | Scenario ID | Pass/fail | Redacted local reference | None or ticket |

The partial 2026-09-12 run is recorded in
`docs/reviews/DEVICE_QUALIFICATION_2026-09-12.md`.

Any critical/high defect blocks P4-R01, P5-R01, and P6-R01 until fixed or
explicitly accepted by the product owner.
