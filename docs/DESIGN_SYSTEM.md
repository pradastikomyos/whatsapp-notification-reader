# RideNotify Design System

Status: Active
Updated: 2026-09-12

## Direction

RideNotify uses a native Android Material 3 interface with a calm, focused
control-surface character. The visual grammar is informed by Namida's tonal
surfaces, cohesive geometry, adaptive navigation, and strong content hierarchy;
no Namida code, branded asset, or screen is copied.

The quality bar adapts the platform-independent rules from Appllama's
`appllama-app-design-skill` to Jetpack Compose. Expo, React Native, and iOS-only
instructions do not apply to this Android project.

References:

- https://github.com/Appllama/appllama-skills
- https://github.com/Appllama/appllama-skills/tree/main/skills/appllama-app-design-skill
- https://github.com/namidaco/namida

## Rules

- Use one restrained teal accent and one neutral color family across all routes.
- Support semantic light and dark color schemes from the first implementation.
- Follow the device theme by default. The top-right moon/sun action stores an
  explicit dark or light override after the user chooses one.
- Use native Material controls rather than visually imitating switches, sliders,
  navigation, or progress indicators.
- Use an 8dp spacing rhythm and the locked 12dp, 16dp, and 24dp shape scale.
- Keep interactive rows at least 56dp high and all individual targets at least
  48dp, with primary phone actions placed in thumb-reachable content areas.
- Use bottom navigation with visible labels on phones and a navigation rail from
  600dp width.
- Respect system insets, edge-to-edge rendering, long Indonesian labels, and
  route-owned scrolling.
- Use typography, text, and control state as the primary communication. Color is
  supporting information, never the only status signal.
- Do not add decorative gradients, glass effects, emoji icons, mixed icon
  families, fabricated statistics, or motion without a functional purpose.
- Keep complete loading, error, unavailable, enabled, and disabled states.

## Current Information Architecture

The four peer destinations are Status, Kontrol, Berkendara, and Pengaturan.
Status is the readiness dashboard; Kontrol owns reader and speech controls;
Berkendara owns the explicit manual driving state; Pengaturan owns direct-message
privacy and announcement preferences.

Group-message reading is not part of the current product. Group notifications
remain classified only so they can be rejected before speech.
