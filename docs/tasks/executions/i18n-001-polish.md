# Execution: `I18N-001`

- **Brief:** [i18n-001-polish.md](../specifications/i18n-001-polish.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Grok
- **Reviewer:** pending
- **Branch:** `i18n-001-polish`
- **Updated:** 2026-10-28

## Plan

1. Move the row into release 1.6, record this task, narrow the English-only rule to the localized resource directories, and update the GitHub Projects mirror in that same step.
2. Move hardcoded interface copy into the English catalog without changing it, except the camera sentence in the brief. Device sentences become full phrases. Add plurals where Polish inflects. Add Polish for every key, and a build check that fails when a key is missing.
3. Follow the system locale on every platform, including a Mac per-app language for the app and the pause-page helper. List Polish and English for Android 13 per-app language and for iOS. Localize the pause page, the Automation usage string, the file-type description, the Android block screen and notification, the Windows service description, the picker Cancel buttons, and the Rosetta refusal dialog.
4. Put a stable identifier on the Posato controls the flows press, and select those controls by that identifier. System dialogs stay on the English guest labels.
5. Add the Polish site and the `pl-PL` listing. Do not upload either.

## Result

- Revision 35 moves the row into release 1.6. The GitHub project item is In Progress, release 1.6, wave R1.6/W1, group `PR-I18N-PL`. Its PR field still names intake #79 until this branch's pull request exists. `DOCS-007` and `RELEASE-010` now depend on this row in the project as well.
- The pause page and the paused-app notice follow the app's per-app language, with English fallbacks. The helper bundle copy is assembled into the helper app. A language-independent page marker keeps the helper's own check and the verification classifier working when the headline is translated.
- Desktop snapshots read a stable id from accessibility help (Mac) or the description of a named button (Linux and Windows). The Mac process applies `AppleLanguages` before launch checks. Android declares Polish for per-app language and loads its block screen, notification, and app picker from resources. The Windows service description follows the installer's language.
- The Polish site is in `website/src/pages/pl`, with its own canonical URL, `hreflang` for English, Polish, and `x-default`, and no redirect. The production build in the site worktree emitted the five Polish pages. `store prepare --locale` defaults to `en-US` and creates a localization the version does not have yet. `docs/store/pl-PL` holds the description, subtitle, and current What's New. Nothing was uploaded. Screenshots from the Polish interface are not captured yet. `store status` still reports `en-US`.
- The shared catalog is in place, including Polish for every key. `verifyPolishCatalog` fails the build when a Polish string or plural is missing, blank, or uses a placeholder the English key does not. The Linux and Windows app picker reads its title, Choose, and Cancel from that catalog. The Rosetta refusal dialog reads its four labels from the catalog after the Mac process applies `AppleLanguages`. The Android main window publishes test tags as resource ids.
- Flows press Posato controls by those identifiers. System dialogs, the iOS back bar, iOS system menus, weekday toggles, session length chips, and the sentences a flow reads as state stay on their English text. A Polish session therefore still cannot be driven end to end; the English guest session is the one the identifiers keep working.
- Local compile, catalog parity, the English-text check, ktlint, and detekt pass. Detekt length and function-count findings were fixed by splitting functions, with no new suppression. The English guest session, the Polish-only-for-the-app proof, and the privacy-page viewport check have not been run.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** none yet
- **Resolution:** pending
- **Advisory findings:** none

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:compileDebugKotlin :posato-control:compileKotlin verifyPolishCatalog verifyEnglishText --offline` | pass | `BUILD SUCCESSFUL`. Shared JVM, desktop, Android debug, and posato-control compiled. Both catalog tasks passed. |
| `:shared:ktlintCheck`, `:desktopApp:ktlintCheck`, `:androidApp:ktlintCheck`, `:posato-control:ktlintCheck` | pass | Two assignment lines had to be joined by hand. Shared and posato-control were checked again after the detekt splits. |
| `:desktopApp:detekt` and `:androidApp:detekt` | pass | Green in the run that still failed shared and posato-control. Those two modules were not edited after that. |
| `./gradlew :shared:detekt :posato-control:detekt :shared:ktlintCheck :posato-control:ktlintCheck --offline` | pass | `BUILD SUCCESSFUL` in 10s after splitting long functions. No suppression added. |
| `./gradlew :shared:compileKotlinJvm :posato-control:compileKotlin :androidApp:compileDebugKotlin --offline` | pass | `BUILD SUCCESSFUL` in 20s after those splits. |
| English guest session, Polish-only-for-the-app proof, privacy pages at two widths | pending | Not started. |

## Blockers and accepted risks

- The maintainer reviews the Polish copy only in the pull request. Shipping waits on that review.
- Putting an identifier where a screen reader would speak it is avoided: on the desktop the identifier rides the help description, and on iOS and Android it rides the test tag. Text nodes do not carry it.

## Final

- **Status:** not closed
- **Outcome:** unmet; implementation is in progress
