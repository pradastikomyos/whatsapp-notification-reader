# ADR-011: TTS Package Visibility, Locale/Voice Checking, And Missing-Voice UX

## Status

Accepted
Owner: Worker Bee (engineering default, pending product owner override)
Date: 2026-09-11

## Context

`ARCHITECTURE.md` requires `AndroidTtsEngine` to "wait for asynchronous
initialization, verif[y] locale availability, assign[] unique utterance
IDs, report[] progress, and shut down only when its owning service is
permanently released," and requires `ADR-011` to decide "TTS locale, voice
selection, and missing-engine UX." `PROJECT_CHARTER.md`'s first-run journey
requires the app to "validate that an Indonesian TTS voice is available"
before the user runs a test utterance, and forbids unmeasured reliability
claims. `ARCHITECTURE.md`'s "Manifest Policy" requires "TTS engine query
required by the supported Android versions" as an expected manifest
component.

## Decision

### 1. Manifest `<queries>` declaration (package visibility, API 30+)

The manifest must declare:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

This is the official, documented requirement: "Apps targeting Android 11
[API 30] that use text-to-speech should declare
`TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE` in the `queries` elements of
their manifest" (Evidence #1, #2). Since `ADR-002` sets `targetSdkVersion`
to 36, this element is mandatory, not optional, for this app. Without it,
`PackageManager` queries for installed TTS engines are filtered by Android's
general package-visibility rules (Evidence #3), and engine discovery /
`TextToSpeech` construction can silently fail to find an engine on some
devices.

### 2. Locale/voice availability checking APIs to use

- `TextToSpeech.isLanguageAvailable(Locale)` returns one of
  `LANG_AVAILABLE`, `LANG_COUNTRY_AVAILABLE`, `LANG_COUNTRY_VAR_AVAILABLE`,
  `LANG_MISSING_DATA`, or `LANG_NOT_SUPPORTED` (Evidence #4).
- `TextToSpeech.getVoices()` / `getVoice()` / `setVoice(Voice)` (API 21+)
  expose the engine's actual `Voice` objects per locale, which is the
  documented, more granular mechanism layered on top of locale checks:
  "Since API level 21 `TextToSpeech.setLanguage` is implemented by calling
  `TextToSpeech.setVoice` with the voice returned by
  `onGetDefaultVoiceNameFor(...)`" (Evidence #5).
- `AndroidTtsEngine` must call `isLanguageAvailable(Locale("id", "ID"))`
  (Indonesian) after `OnInitListener.onInit(SUCCESS)`, and additionally
  inspect `getVoices()` for at least one non-null `Voice` whose `locale`
  matches Indonesian, before reporting the locale as usable to the rest of
  the app. Checking only `isLanguageAvailable()` is not sufficient by
  itself (see the documented gap below).

### 3. Documented gap: `isLanguageAvailable()` can be a false positive for missing voice data

This is flagged as a **secondary-sourced, reasoned caveat**, not an official
statement, but it directly affects the implementation and is corroborated
by the official `LANG_MISSING_DATA` constant existing specifically to cover
this case: multiple developer reports (Stack Overflow, Evidence #6,
secondary) describe `isLanguageAvailable()` / `setLanguage()` returning
`LANG_COUNTRY_AVAILABLE` even when the actual voice data for that locale has
not finished downloading on the device, particularly with no network
connectivity. Because the architecture's charter step is "run a local test
utterance" during onboarding (not just an API check), the implementation
must treat a successful *test utterance* (an `UtteranceProgressListener`
`onDone` callback for a short synthetic phrase) as the authoritative signal
that Indonesian speech actually works, and must not claim voice availability
from the locale/voice query APIs alone.

### 4. UX when the Indonesian voice is unavailable

- The onboarding/status UI must show a distinct, explicit state - e.g.
  "Indonesian voice unavailable" - separate from "reader disabled" and from
  "notification access not granted." These are independent facts per
  `ARCHITECTURE.md`'s "UI And State" section and must not be collapsed into
  one generic error.
- The app must not silently substitute a different language/locale for
  reading messages. If Indonesian is unavailable, the reading pipeline fails
  closed (produces no speech) rather than speaking in an unintended
  language, consistent with `ARCHITECTURE.md`'s general fail-closed
  principle for unmet platform prerequisites.
- The UI must offer a way to reach the platform's own remediation flow: the
  `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA` activity intent, documented
  as the "activity that installs the resource files on the device that are
  required for TTS to be operational" (Evidence #7). This keeps the app out
  of the business of managing voice-data downloads itself.
- No claim of "Indonesian speech will always work" may be made anywhere in
  the UI or store listing; this matches `PROJECT_CHARTER.md`'s instruction
  to avoid unmeasured reliability claims, and mirrors the fact that TTS
  engine/voice availability is itself device- and user-configuration
  dependent, not guaranteed by any Android API level.

## Alternatives Considered

- Rely on `isLanguageAvailable()` alone and skip a real test utterance -
  rejected. Documented `LANG_MISSING_DATA` semantics and the corroborating
  developer reports show this can produce false positives; the charter's
  own first-run flow already requires a real test utterance, which this
  ADR simply makes authoritative.
- Silently fall back to English (or the device's default TTS locale) when
  Indonesian is unavailable - rejected. Contradicts the product's
  Indonesian-only reading requirement and would surprise the user without
  their consent.
- Omit the `<queries>` element on the theory that the default TTS engine is
  always visible - rejected. The official reference explicitly calls out
  this requirement for apps targeting API 30+, and `ADR-002` targets API 36.

## Consequences

- Positive: aligns exactly with `ARCHITECTURE.md`'s stated `AndroidTtsEngine`
  responsibilities and the charter's first-run journey.
- Positive: avoids a documented false-positive failure mode instead of
  discovering it later on a real device.
- Negative: requires an extra local test-utterance round trip during
  onboarding (acceptable latency cost for correctness).
- Future revisit trigger: if a later ADR approves additional
  locales/voices (not in this task's scope), the same isLanguageAvailable +
  test-utterance pattern should be reused per locale rather than assuming
  parity across locales.

## Dependent Tasks

`P1-T02` (manifest `<queries>` declaration), `P4-T01` (TTS engine adapter
implementation), `P5-T01` (onboarding/status UI surfacing the distinct
"Indonesian voice unavailable" state).

## Evidence

All links accessed and verified resolving on 2026-09-11.

1. `TextToSpeech` API reference, package-visibility `<queries>` requirement
   for apps targeting Android 11+:
   https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech
2. `TextToSpeech.Engine` API reference, same `<queries>` requirement and
   `INTENT_ACTION_TTS_SERVICE` constant:
   https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine
3. "Declare package visibility needs" (developer.android.com/training):
   https://developer.android.com/training/package-visibility/declaring
4. `TextToSpeech` API reference, `LANG_AVAILABLE` / `LANG_COUNTRY_AVAILABLE`
   / `LANG_COUNTRY_VAR_AVAILABLE` / `LANG_MISSING_DATA` /
   `LANG_NOT_SUPPORTED` constants (same URL as #1).
5. `TextToSpeechService` API reference, voice/locale relationship since API
   21:
   https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeechService
6. Secondary source (developer discussion, not official), corroborating the
   `isLanguageAvailable()` false-positive gap for missing voice data:
   https://stackoverflow.com/questions/44619128/android-tts-checking-for-supported-locale-with-missing-not-downloaded-voice-data
   (content confirmed via search-engine index snippet on 2026-09-11; the
   `fetch` tool could not independently retrieve this page directly because
   stackoverflow.com returned an anti-bot interstitial to the automated fetch
   request. Noted rather than concealed; this source is explicitly secondary
   and is not relied on alone - the official `LANG_MISSING_DATA` constant's
   documented existence, evidence #1/#4, independently corroborates that a
   missing-data state is distinct from a supported-locale state.)
7. `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA` and the general TTS data
   check/install flow (same reference as #2; also see the historical
   overview at
   https://android-developers.googleblog.com/2009/09/introduction-to-text-to-speech-in.html,
   fetched and verified resolving on 2026-09-11, cited only for the general
   check/install flow description, labeled secondary/historical).
