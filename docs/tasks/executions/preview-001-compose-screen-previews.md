# Execution: `PREVIEW-001`

- **Brief:**
  [`../specifications/preview-001-compose-screen-previews.md`](../specifications/preview-001-compose-screen-previews.md)
- **Status:** `blocked`
- **Review tier:** `standard`
- **Implementer:** `Codex`
- **Reviewer:** `independent local Codex review`
- **Branch:** `targets-001-exact-domains`
- **Updated:** `2026-08-28`

## Plan

1. Add the narrow Android KMP library and Compose tooling dependencies required
   for common previews, then compile its generated Android target.
2. Add provider-backed exact-domain screen previews using synthetic immutable
   render states and the existing state-and-callback overload.
3. Record the tooling-only architecture exception and future preview convention,
   then run focused and aggregate verification followed by independent review.

## Result

- Added the Android KMP library target required by common Compose preview
  tooling, without an Android application, host, identifier, or runtime claim.
- Added one separate deterministic provider for every visually distinct
  exact-domain screen path. The phone and desktop preview functions live in the
  screen file, consume the same complete sequence, and render only synthetic
  states through the existing state-and-callback screen overload.
- Recorded the tooling exception and lightweight future-screen convention in
  the architecture, quality, development, and wiki authorities.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** None. The independent reviewer confirmed
  the target remains tooling-only, previews contain no live state or platform
  I/O, both preview functions consume the same complete provider sequence, and
  the implementation is internally consistent.
- **Resolution:** No correction required.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `:shared:compileAndroidMain` | `pass` | The Android KMP library configured and compiled shared resources and source. |
| `:shared:ktlintCheck :shared:detekt :shared:compileAndroidMain` | `pass` | New common and Android source passed formatting, static analysis, and compilation. |
| `./gradlew quality` | `pass` | The aggregate gate passed all 92 tasks, including Android, JVM, iOS, Detekt, ktlint, tests, and the macOS distributable. |
| Credential-free iOS Simulator host build | `pass` | `xcodebuild` built the `iosApp` scheme for the generic iOS Simulator with code signing disabled. |
| Independent completed-change review | `pass` | No Critical or Required defect in the PREVIEW-001 diff. |
| Hosted CI correction | `pass` | The first hosted quality job reached an obsolete Android SDK installation step and failed because `sdkmanager` was unavailable. The redundant install step was removed because the `macos-15` runner already supplies the required Android 36 platform and build tools; follow-up run `33145856613` passed aggregate quality, the credential-free iOS host build, and report upload. |

## Blockers and accepted risks

- Android Studio visual inspection remains required for `AC-01`. Open
  `ExactDomainsScreen.kt` after Gradle sync and confirm all ten named cases in
  both the phone and desktop preview groups. This environment lacks the macOS
  Accessibility permission needed to automate that IDE action.

## Final

- **Status:** `blocked`
- **Outcome:** All automated checks and independent review passed; manual IDE
  preview inspection is the clearing condition.
