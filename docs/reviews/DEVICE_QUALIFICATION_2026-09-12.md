# Device Qualification Evidence - 2026-09-12

Status: Partial campaign. The available device passed the lifecycle checks
below, but the required device, audio, notification-source, accessibility, and
signed-release matrices remain incomplete.

## Device And Build

- Device: Xiaomi Mi MIX 2S (`polaris`); serial retained only in local ADB state.
- Reported platform: Android 15, API 35.
- Build fingerprint: Xiaomi `polaris` release fingerprint based on Android 8.
  Because the reported platform and fingerprint do not agree, treat this as a
  custom/upgraded device and not as representative Xiaomi Android 15 evidence.
- App: debug APK installed with `adb install -r`; target SDK 36.
- WhatsApp: installed. WhatsApp Business: not installed.

No notification dump, message body, sender identity, phone number, screenshot,
or unredacted log was retained.

## Results

| ID | Scenario | Result | Evidence reference | Limitation/defect |
|---|---|---|---|---|
| DQ-01 | Clear app data and launch fresh state | Pass | `pm clear`, launcher start, and privacy-safe UI hierarchy showed notification access inactive | Debug build, not signed release |
| DQ-02 | Enable notification access | Pass | Secure setting contained `MyNotificationListener`; ActivityManager showed the listener requested, received, and bound to system | One custom/upgraded API 35 device only |
| DQ-03 | UI absent | Pass | After Home, ActivityManager still showed the listener bound with PID 31935 | No notification payload was injected |
| DQ-04 | Screen-off transition | Pass | Listener remained bound with PID 32534 while the screen was off | Credential-locked behavior was not separately established |
| DQ-05 | Process recreation | Pass | `am force-stop` replaced PID 31935 with PID 32534; Android immediately recreated and rebound the listener without reopening the UI | OEM behavior on other devices remains unknown |
| DQ-06 | Landscape responsive render | Pass with limits | Privacy-safe UI hierarchy showed scrollable route content and horizontally scrollable navigation without missing controls | No TalkBack, large-font, contrast, portrait, or tablet check |
| DQ-07 | Activity startup | Measured | `am start -S -W`: `LaunchState: WARM`, total 2,765 ms, wait 2,773 ms | Not a cold-process measurement because the enabled listener immediately recreates the process; one sample |
| DQ-08 | UI-absent memory | Measured | After Home and 10 seconds: total PSS 73,406 KB; total RSS 179,204 KB; swap 0 KB | Debug build, one short sample; not a release baseline or leak result |

## Remaining Matrix

- Speaker, wired-headset, and Bluetooth speech routes with media playing and
  paused.
- Incoming/active call, alarm, audio-focus denial/loss, unavailable Indonesian
  voice, and API 35+ foreground-service rejection.
- Reader/riding disable during speech, callback duration, burst handling, and
  meaningful battery observation.
- Supported synthetic or privacy-safe WhatsApp traffic. WhatsApp Business
  cannot be checked on this device because it is not installed.
- TalkBack traversal, large font scale, OEM contrast, portrait, and tablet.
- API 26-34, another API 35+ device with a consistent production fingerprint,
  and other available OEMs.
- Signed-release fresh install and upgrade from the prior signed build.

These gaps continue to block `P4-R01`, final `P5-R01`, and `P6-R01`.
