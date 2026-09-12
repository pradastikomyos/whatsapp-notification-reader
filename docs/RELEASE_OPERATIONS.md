# Release Operations

## Signing

Never commit a keystore, password, token, or local signing path. A release build
uses external Gradle properties: `RIDENOTIFY_STORE_FILE`,
`RIDENOTIFY_STORE_PASSWORD`, `RIDENOTIFY_KEY_ALIAS`, and
`RIDENOTIFY_KEY_PASSWORD`. All four are required to produce a signed artifact.

## Required Release Evidence

1. `testDebugUnitTest`, lint, debug build, and release build pass.
2. The final device campaign in `docs/reviews/DEVICE_QUALIFICATION_CAMPAIGN.md`
   passes or records accepted limitations.
3. A signed artifact installs fresh and upgrades the immediately previous signed
   production version on a qualified device.
4. The privacy policy, release notes, and store listing use only supported
   claims.

## Rollout And Rollback

Start with the smallest supported store rollout cohort. Stop rollout immediately
for a confirmed notification privacy exposure, unwanted speech while disabled,
crash loop, loss of notification access after upgrade, or any critical/high
audio/foreground-service defect. Roll back by halting rollout and promoting the
last known-good signed release; do not attempt to replace an installed APK with
a differently signed artifact.

## Privacy-Safe Monitoring

Monitor aggregate store crash/ANR reports and install/upgrade outcomes only.
Do not add notification text, sender names, phone numbers, group names, or raw
notification extras to logs, crash reports, tickets, or release dashboards.
