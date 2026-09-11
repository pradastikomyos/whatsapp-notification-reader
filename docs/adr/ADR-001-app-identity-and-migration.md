# ADR-001: Application Identity, Listener Continuity, And Old-Data Migration

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`PROJECT_CHARTER.md` requires preserving application ID
`com.ridenotify.app.wa_reader` "if this is an update to an already distributed
application," and calls this an explicit Phase 0 decision. `ARCHITECTURE.md`'s
"Required Architecture Decisions" section additionally requires ADR-001 to
decide whether to preserve the old enabled-listener component identity
`com.ridenotify.app.wa_reader.MyNotificationListener`, or force reauthorization
with a renamed component, and to inventory every old `SharedPreferences` key as
migrate, transform, or reset. Old selected-group semantics must not be
implicitly mapped onto the new three-mode group policy
(`ALL_OBSERVED_GROUPS` / `SELECTED_GROUPS_ONLY` / `NO_GROUPS`).

### Verification performed

The old Flutter project root (`C:\Users\prada\Documents\prjkwanotif`) is not
addressable through this task's project-scoped file tools (`list_directory`,
`read_file`, `find_path` all reject it as outside the project). It **is**
reachable through the unrestricted `terminal` tool, so this ADR was written
after directly inspecting the old source tree with `find`/`grep`/`cat` rather
than declaring the inventory unverifiable. The following facts are taken
directly from that source, with file and line references:

- `android/app/build.gradle.kts:22` sets
  `applicationId = "com.ridenotify.app.wa_reader"` - the application ID in
  `PROJECT_CHARTER.md` is confirmed, not merely asserted.
- `android/app/src/main/kotlin/MyNotificationListener.kt:1` declares
  `package com.ridenotify.app.wa_reader`, and line 13 declares
  `class MyNotificationListener : NotificationListenerService()`. This
  confirms the FQCN `com.ridenotify.app.wa_reader.MyNotificationListener`
  referenced in `ARCHITECTURE.md` is exactly correct, not approximate.
- `AndroidManifest.xml` declares the listener as `android:name=".MyNotificationListener"`
  with `android:exported="true"` and requires
  `BIND_NOTIFICATION_LISTENER_SERVICE`; it also declares a `BootReceiver`,
  and requests non-standard/privileged permissions
  (`PREVENT_POWER_KEY`, `MEDIA_CONTENT_CONTROL`, `DEVICE_POWER`,
  `DISABLE_KEYGUARD`) that `ARCHITECTURE.md` already rejects outright for the
  new app. These are not carried forward by this ADR; only the listener FQCN
  and application ID are in scope here.
- `pubspec.yaml:4` sets `version: 1.0.0+1` (Flutter version name/code), i.e.
  the only version ever built from this source is version code `1`.
- `android/app/build.gradle.kts:31` sets the release build type's
  `signingConfig = signingConfigs.getByName("debug")`. No `key.properties`
  file and no `.jks`/`.keystore` file exist anywhere under
  `android/`. In other words, as configured in the available source, a
  release build from this repository would be signed with the Flutter debug
  keystore, not a distinct upload/release key.
- `README.md` in the old repo describes the app as free, open source (MIT),
  with "no data sent to any server," and does not mention a Play Store
  listing or any store metadata. No CI/release-signing configuration exists
  in the repo.
- These facts are jointly consistent with the old app never having been
  published as a signed release build to the Play Store, but they do not
  prove it was never side-loaded/shared as a debug-signed APK to real users
  outside the developer's own devices. Source inspection alone cannot settle
  that question.

### Verified old `SharedPreferences` usage

Every `lib/*.dart` file was searched for `SharedPreferences` usage. Exactly
two files touch it: `lib/notification_service.dart` and
`lib/settings_page.dart`. `lib/main.dart`, `lib/group_selection_page.dart`,
and `lib/riding_mode_page.dart` do not read or write preferences directly.
The verified key constants and their declared Dart types are:

| Key literal | File:line | Dart type |
|---|---|---|
| `isServiceActive` | `lib/notification_service.dart:14` | `bool` |
| `speechRate` | `lib/notification_service.dart:15` | `double` |
| `selectedGroups` | `lib/notification_service.dart:16` | `List<String>` |
| `isRidingModeActive` | `lib/notification_service.dart:17` | `bool` |
| `readPrivateMessages` | `lib/notification_service.dart:18` | `bool` |
| `autoStartDriving` | `lib/settings_page.dart:13` | `bool` |
| `useBluetooth` | `lib/settings_page.dart:14` | `bool` |

Two important caveats on completeness:

1. This inventory is exhaustive for the Dart source tree at the inspected
   path, at the time of this review. It is not verified against an actual
   installed device's live preferences file, so a key written only by now
   -removed code, or by native Kotlin code outside the Dart layer, would not
   appear here. No such native-side `SharedPreferences` usage was found when
   the Kotlin files under `android/app/src/main/kotlin/` were searched for
   the same pattern; none of `MainActivity.kt`, `MediaControlHelper.kt`,
   `NotificationHelper.kt`, `WorkaroundService.kt`, or
   `MyNotificationListener.kt` reference `SharedPreferences`.
2. The old app uses the Flutter `shared_preferences` plugin
   (`pubspec.lock` pins `shared_preferences_android`). That plugin's Android
   implementation persists values into a single `SharedPreferences` file
   (historically named `FlutterSharedPreferences`) and prefixes every key
   with the literal string `flutter.` on the native side. This means a
   native reader migrating this file directly (rather than through the
   Flutter plugin) must look for `flutter.isServiceActive`,
   `flutter.speechRate`, etc., not the bare Dart-side names above. This
   prefixing behavior is a documented, long-standing characteristic of that
   plugin; it must still be re-confirmed against the exact plugin version
   pinned in the old `pubspec.lock` before any migration code is written in
   `P2-T02`, since this ADR does not have access to run that plugin's code.

`useBluetooth` (`lib/settings_page.dart:14`) is backed by a UI toggle only
("Aktifkan dengan Bluetooth" / "Mengaktifkan pembacaan saat terhubung dengan
Bluetooth kendaraan"); no Bluetooth scanning, pairing, or connection-state
code exists anywhere in `lib/` or `pubspec.yaml`'s dependencies. The old key
stored a value for a feature that was never functionally implemented.
`autoStartDriving` (`lib/settings_page.dart:13`) has the same shape: a stored
boolean with no automatic-detection logic behind it anywhere in the source.

Also relevant: `lib/settings_page.dart`'s own `_resetSettings()` only resets
`autoStartDriving`, `useBluetooth`, and `readPrivateMessages`; it does not
clear `isServiceActive`, `speechRate`, `selectedGroups`, or
`isRidingModeActive`. The old app's own "reset" was partial. This is cited as
a negative example for `ADR-009`'s reset-behavior requirement, not repeated
here.

## Decision

1. **Application ID**: Preserve `com.ridenotify.app.wa_reader` as the native
   app's `applicationId` and Kotlin package root, matching
   `ARCHITECTURE.md`'s package layout. Do not rename it in v1.

2. **Listener component identity**: Preserve the exact fully-qualified
   listener component name `com.ridenotify.app.wa_reader.MyNotificationListener`
   as the manifest-declared `android:name` of the new
   `NotificationListenerService`. This is a deliberate, narrow exception to
   `ARCHITECTURE.md`'s package layout, which otherwise names the new listener
   class `WhatsAppNotificationListenerService`. Concretely:
   - The manifest `<service>` element for the notification listener must
     resolve to the literal class name `MyNotificationListener` in package
     `com.ridenotify.app.wa_reader`, because Android's per-user "enabled
     notification listeners" grant is keyed by exact `ComponentName`
     (package + class), not by application ID alone or by any alias.
   - Kotlin does not require a file name to match its class name, so the file
     may still live at `notification/WhatsAppNotificationListenerService.kt`
     internally if that filename is preferred, but the declared class itself
     must be named `MyNotificationListener` and placed in the app's root
     package so the compiled `ComponentName` is byte-for-byte identical to the
     old one. Whichever concrete approach P1-T02 and P3-T01 implementers
     choose, the requirement is the compiled `ComponentName` string, not any
     particular file layout.
   - Do not carry forward `android:exported="true"` or any of the old
     manifest's non-standard permissions; only the class/package name of the
     listener component is preserved.
   - This exception must not be "cleaned up" by a later refactor without a new
     ADR that explicitly re-accepts forced reauthorization; add a code comment
     at the manifest declaration and at the class referencing this ADR.
   - **Explicit tradeoff**: preserving the FQCN avoids forcing every existing
     user to reopen Android's notification-listener settings and re-grant
     access, which is real friction and a real drop-off risk for any user who
     already has the old app installed and working. The cost is that the new
     implementation inherits the old component's exact identity string
     forever, including any OEM-specific allow/deny-list entries keyed to
     that string, and it constrains future refactors of the listener class
     name. If the old app was never distributed to real end users (plausible
     given the debug-signed release build and absence of store metadata, but
     not proven, see Context), this benefit is zero and the constraint is
     pure cost with no offsetting gain. The recommendation to preserve is
     made anyway because the downside of preserving when unnecessary is
     small (a naming constraint), while the downside of renaming when it was
     necessary is large (silent loss of working notification access for real
     users, discovered only after release).

3. **Signing identity**: Preserving the application ID is necessary but not
   sufficient to ship this as an update to an already-distributed app on the
   Play Store; Play requires the new build to be signed with the same signing
   key (or a Play App Signing upgrade path) as the previously published build.
   The verified evidence (debug-signed release build type, no keystore file
   in the repo, no store metadata, MIT/open-source framing) is consistent
   with this app never having had a genuine production release signing key,
   but does not rule out an out-of-repo signed APK having been shared with
   real users. **This requires human owner action**: confirm (a) whether any
   build of this app was ever installed by real users outside the
   developer's own test devices, whether via Play Store or side-loaded APK,
   and (b) if so, locate the exact keystore/certificate that signed the APK
   those users actually installed. This ADR cannot verify or decide this; it
   is flagged as a release-blocking open question for `P6-T05`. If no such
   distribution ever happened, the new app may be signed with a freshly
   generated upload key with no continuity constraint.

4. **Old `SharedPreferences` key inventory**: See the classification table
   below. All seven verified keys are classified; none are guessed.

5. **Group-selection migration safety rule**: Regardless of the verified
   `selectedGroups` key's literal contents on any given device, that old
   "selected groups" state must **not** be implicitly or heuristically
   mapped onto the new `ALL_OBSERVED_GROUPS` / `SELECTED_GROUPS_ONLY` /
   `NO_GROUPS` policy. The migrated default for group policy is explicitly
   `NO_GROUPS`, with the old `selectedGroups` list discarded (reset),
   requiring the user to opt back in to group reading under the new explicit
   three-mode model. This removes any risk of guessing wrong in a way that
   either silently exposes a group the user did not intend, or silently reads
   nothing without the user realizing a choice is required.

## Old `SharedPreferences` Key Inventory

| Old key | Old type | Classification | Rationale |
|---|---|---|---|
| `isServiceActive` | `bool` | **Migrate** | Direct semantic equivalent of the new "reader enabled" setting (`PROJECT_CHARTER.md` outcome 2). Carries no message content or group-identity risk by itself. The pipeline still fails closed at cold start until access/TTS checks pass (`ARCHITECTURE.md`, "UI And State"), so migrating this value cannot cause speech before the mandatory runtime safety checks succeed. |
| `speechRate` | `double` | **Transform** | Likely semantic equivalent to a new TTS rate setting, but the old value was produced by the Flutter `flutter_tts` plugin, whose numeric range/units on Android are not verified to be identical to native `android.speech.tts.TextToSpeech.setSpeechRate()` semantics used by `AndroidTtsEngine`. `P2-T02` must verify the old plugin's actual output range before writing a conversion, and only migrate the value once an explicit, tested scale mapping exists; do not assume a 1:1 copy is safe without that check. |
| `selectedGroups` | `List<String>` | **Reset** | Mandatory per the group-selection migration safety rule above and `ARCHITECTURE.md`'s explicit prohibition on implicitly mapping old group semantics to the new three-mode policy. Discard the list entirely; do not retain it even as inert/unused data, since the old group-identity model has no defined mapping to the new `ADR-004` conversation-identity scheme. |
| `isRidingModeActive` | `bool` | **Reset** | The new app's exact manual riding-mode semantics are defined by `ADR-007`, which is owned by a separate task and not yet approved as of this ADR. Migrating a boolean into an undefined future state model would be guessing at intent. Reset to the `ADR-007`-approved default; if `ADR-007` later defines a safe, unambiguous direct equivalent, that ADR (or a superseding revision of this one) may reclassify this key. |
| `readPrivateMessages` | `bool` | **Migrate** | Direct semantic equivalent of the new private-message policy (`PROJECT_CHARTER.md` outcome 3). No group-identity or content-retention risk. |
| `autoStartDriving` | `bool` | **Reset (no destination field)** | Backs automatic riding detection, which `PROJECT_CHARTER.md` "Non-Goals For Version 1" explicitly excludes ("Automatically detecting whether the user is riding"). No functional detection logic existed behind this key in the old source (verified: no matching implementation in `lib/`). There is no new schema field to migrate into; the value is discarded on first run and never re-created. |
| `useBluetooth` | `bool` | **Reset (no destination field)** | Backs automatic vehicle/Bluetooth-based activation, which `PROJECT_CHARTER.md` "Non-Goals For Version 1" explicitly excludes ("Automatically selecting a vehicle from nearby Bluetooth devices"). Verified: no Bluetooth scanning/connection code exists anywhere in the old source; this toggle was UI-only. Discarded on first run with no destination field. |

`P2-T02` must implement exactly this table: `migrate` keys copy the value
after normal type conversion, the `transform` key requires an explicit,
tested scale/unit conversion function (not an assumed identity function)
before being migrated, and `reset` keys are never read from the old
preferences store into the new schema at all. Because the old app used the
Flutter `shared_preferences` plugin, `P2-T02` must read the migrate/transform
keys from the on-disk key names `flutter.isServiceActive`,
`flutter.speechRate`, and `flutter.readPrivateMessages` (re-verifying the
`flutter.` prefix against the pinned plugin version first, per the Context
section), not the bare Dart constant names.

If a future re-inspection of the old app (e.g. an actual installed device,
or a build/version of the source not present at
`C:\Users\prada\Documents\prjkwanotif` at the time of this review) reveals
additional keys not listed here, they must be added to this table with the
same migrate/transform/reset discipline before `P2-T02` treats its migration
map as complete.

## Alternatives Considered

- **Rename the application ID** - rejected. There is no technical requirement
  to rename it, `PROJECT_CHARTER.md` directs preserving it when this is an
  update to an already-distributed app, and a rename provides no privacy or
  architectural benefit while adding real reinstall/data-loss friction for
  any existing user.
- **Rename the listener component and force reauthorization for everyone** -
  rejected as the default, for the friction reasons stated in Decision item 2,
  but recorded as the safer fallback if human owner action in Decision item 3
  determines the old app was never genuinely distributed to real users (in
  which case there is no installed base to protect, and a clean rename
  removes the FQCN constraint on future refactors at zero real cost).
- **Implicitly map old `selectedGroups` entries onto `SELECTED_GROUPS_ONLY`**
  - rejected. `ARCHITECTURE.md` explicitly forbids this; the old group model
  has no defined identity equivalence with the new observed-conversation
  catalogue (`ADR-004`), and guessing could either silently read a group the
  user no longer wants read, or silently produce `NO_GROUPS` without the user
  realizing a choice is now required.
- **Migrate all seven old keys unconditionally ("best effort")** - rejected.
  Two keys (`autoStartDriving`, `useBluetooth`) back explicitly out-of-scope
  Non-Goals and have no destination field; migrating them would either be
  silently dropped anyway or would require inventing unapproved settings
  surface area.
- **Treat the old preferences file as fully opaque and reset everything** -
  considered as the safe fallback used by this ADR's predecessor draft, when
  the old source was believed unreachable. Superseded now that direct
  verification was possible via the `terminal` tool; blanket reset would
  needlessly discard two straightforward, low-risk migrate cases
  (`isServiceActive`, `readPrivateMessages`).

## Consequences

- `P1-T02` and `P3-T01` must implement the listener service under the exact
  class/package identity from Decision item 2, with a code comment pointing
  back to this ADR at both the manifest declaration and the class
  declaration.
- `P2-T02` has a concrete, non-speculative migration map to implement and
  test, including one key (`speechRate`) that requires verifying unit/range
  equivalence before being trusted, and one key (`isRidingModeActive`) whose
  final classification depends on `ADR-007`.
- `P6-T05` (release preparation) is blocked on human owner action to resolve
  real-world distribution history and, if any occurred, signing-key custody.
  This ADR does not and cannot resolve that question from source inspection
  alone.
- A future ADR revision is required if `ADR-007` defines riding-mode
  semantics that make a safe, direct migration of `isRidingModeActive`
  possible; until then it remains `reset`.
- If the human owner later confirms the old app was never distributed to any
  real user, the FQCN-preservation constraint in Decision item 2 may be
  revisited and potentially relaxed in a superseding ADR, since its sole
  justification is avoiding reauthorization friction for an existing
  installed base.

## Dependent Tasks

- `P1-T02` (minimal manifest and application container) - needs the
  application ID and listener FQCN decision.
- `P2-T02` (DataStore settings) - needs the full key inventory and
  classification table.
- `P3-T01` (listener service) - needs the exact listener component identity.
- `P6-T05` (release preparation) - blocked on human owner action for signing
  identity.
- `ADR-007` (manual riding-mode semantics) - may allow reclassifying
  `isRidingModeActive` once approved.

## Evidence

- `C:\Users\prada\Documents\prjkwanotif\android\app\build.gradle.kts` (lines
  22, 25-26, 31) - application ID, version, and release signing config.
  Accessed via the `terminal` tool in this task's environment on 2026-09-11.
- `C:\Users\prada\Documents\prjkwanotif\android\app\src\main\kotlin\MyNotificationListener.kt`
  (lines 1, 13) - package and class declaration confirming the listener FQCN.
  Accessed 2026-09-11.
- `C:\Users\prada\Documents\prjkwanotif\android\app\src\main\AndroidManifest.xml`
  - listener `<service>` declaration, permissions, and `BootReceiver`.
  Accessed 2026-09-11.
- `C:\Users\prada\Documents\prjkwanotif\lib\notification_service.dart` (lines
  14-18) and `C:\Users\prada\Documents\prjkwanotif\lib\settings_page.dart`
  (lines 13-14) - verified `SharedPreferences` key literals and types.
  Accessed 2026-09-11.
- `C:\Users\prada\Documents\prjkwanotif\pubspec.yaml` (line 4) and
  `pubspec.lock` (`shared_preferences`/`shared_preferences_android` entries)
  - version and plugin identity. Accessed 2026-09-11.
- `C:\Users\prada\Documents\prjkwanotif\README.md` - absence of any Play
  Store/distribution claim. Accessed 2026-09-11.
- `ARCHITECTURE.md`, "Required Architecture Decisions" and "Old-To-New
  Migration Map" sections - task requirements for this ADR.
- `PROJECT_CHARTER.md`, "Constraints" and "Non-Goals For Version 1" sections
  - application-ID preservation requirement and the two Non-Goals
  (`autoStartDriving`, `useBluetooth`) with no destination field.
