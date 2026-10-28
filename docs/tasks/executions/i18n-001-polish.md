# Execution: `I18N-001`

- **Brief:** [i18n-001-polish.md](../specifications/i18n-001-polish.md)
- **Status:** `closed`
- **Review tier:** `standard`
- **Implementer:** Grok, then Claude Code for the review corrections and verification
- **Reviewer:** an independent Claude Code agent (Standard)
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
- Detekt length and function-count findings were fixed by splitting functions and merging two store-client overloads, with no new suppression.
- After the review, the desktop publishes a control id to accessibility only on a verification host: `posato-control` creates `~/.posato-control-ids` at every desktop, Linux, and Windows `launch`, so a release never has a screen reader announce a catalog key (`agent-delegated` under the maintainer's standing delegation). The Mac takes the first per-app language, declares `en` and `pl` in `CFBundleLocalizations`, and ships the helper's Polish `InfoPlist.strings`. The iOS file-type description is keyed by its English value.
- Set names and the copy suffix load once per entry point (`SetNameText`), so the unnamed first set has a Polish name and the view model no longer calls Compose resources, which failed in JVM tests and blocked the copy. `store prepare` again refuses a missing `en-US` with `LOCALIZATION_MISSING` and creates only another locale; a regression test failed first. `verifyPolishCatalog` requires one, few, many, and other in every Polish plural. The Polish catalog writes 1024 and 65 536 without an English comma. The Polish policy source is `_PRIVACY.md`, which Astro does not route, so the `readdirSync` patch is gone.
- Flows press native iOS alert buttons by their English title, and the iOS `scrollTo` scrolls until a control is hittable: an identifier stays in the tree under the tab bar, so `flow session` tapped the bar instead of Review on the iPhone.
- The Polish iPhone 6.9-inch and iPad 13-inch store captures are in `docs/store/pl-PL/screenshots`; About shows 1.5.2 until `DOCS-007` recaptures for 1.6.

## Completed-change review

- **Verdict:** approve after the Required fixes; no Critical findings.
- **Critical or Required findings:** control ids announced by desktop screen readers; the Mac per-app language misread (`defaults` quotes only some entries); no `CFBundleLocalizations` in the Mac app; `flow set` pressing an iOS alert's Save by id; the iOS file-type description keyed by `UTTypeDescription`; acceptance not yet proven.
- **Resolution:** all fixed as described under Result and proven below. The implementer's own check added the failing duplicate tests, the untranslated "My set", and the iOS reveal under the tab bar.
- **Advisory findings:** taken: the Polish thousands separator, the silent `en-US` creation, the Astro patch, and stronger plural parity. Left for later: key parity for Android `values-pl`, the helper strings, and the Windows service properties; generic `action_done` and `action_ok` ids instead of `sync_folder_code_done` and `presence_first_close_ok`; `hreflang` on the 404 pages; the "Polski" link label in `Base.astro`; unused device-noun keys. `StorePreparationTest` "writes an explicit locale" landed with its code before this review; the later regression test was red first.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` on `9b3c0df6` | pass | `BUILD SUCCESSFUL` in 9m 8s, after the review corrections. |
| Site build, `/privacy/` and `/pl/privacy/` at 390 and 1280 px | pass | No horizontal scroll; `lang`, self canonical, `hreflang` en, pl, and x-default; the "English" link; ten pages built. Run `site-20261028-123609`. |
| Mac `primary` guest, English: `vm onboard`, `flow set` three times, `flow session --set Work`, `observe --expect blocked` | pass | Controls pressed by id; `outcome: paused`. Runs `20261028-130151-039b`, `20261028-130419-4477`, `20261028-130428-abdb`. |
| Mac, app only in Polish (`AppleLanguages` pl, guest English) | pass | Safari shows the Polish pause headline with a 24-hour end time before Posato relaunches; after it, Session, Pause sets with the one, few, and many website counts and the Polish first-set name, and Schedules, all in Polish; End early by id; `observe --expect allowed`. Runs `20261028-130948-bfab`, `20261028-131006-4c48`, `20261028-131018-ac13`, `20261028-131028-3414`, `20261028-131056-9659`. |
| Test iPhone, English: `flow set` (alert Save), `flow session --end-early-after 10` (alert End session) | pass after the `scrollTo` fix | The first session run stopped on Review hidden under the tab bar. Runs `20261028-131233-f9fa`, `20261028-131648-01cb`. |
| Android phone: onboarding, Clock chosen, `flow session`, `observe --application` blocked, app locale `pl`, block screen, End early, `observe` allowed | pass | The block screen and Session are Polish, including the first-set name. Runs `20261028-132206-a2f6`, `20261028-132228-59b4`, `20261028-132243-b3e9`, `20261028-132254-67b0`. |
| Polish store captures on the iPhone 17 Pro Max and iPad Pro 13-inch Simulators | pass | 1320 × 2868 and 2064 × 2752 RGB PNGs. Runs `20261028-133057-4995`, `20261028-133715-1cd5`. |

The Mac and Android runs used `9b3c0df6`; the iPhone rerun and the Simulator captures used it with the iOS driver change of the next commit, which touches no Mac or Android code. That a release build without the marker publishes no id is `inferred` from `ControlId.jvm.kt`, not run.

## Blockers and accepted risks

- The maintainer reviews the Polish copy only in the pull request. Shipping waits on that review.
- An identifier never reaches a screen reader: iOS and Android carry it as a test tag, and the desktop publishes it as the accessibility description only where `~/.posato-control-ids` exists.
- The second half of the Polish product line differs between the app (`about_tagline`, `session_hero_room_matters`) and the site and listing (`website/src/pages/pl/index.astro`, `docs/store/pl-PL/listing.md`); the maintainer chooses one in the copy review.
- `flow set --rename-from` cannot rename on iOS, whose set rows have no "More actions" button; this predates the task.

## Final

- **Status:** closed
- **Outcome:** met; `AC-01` to `AC-06` are proven above. Shipping waits on the maintainer's review of the Polish copy.
