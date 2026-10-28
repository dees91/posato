# Execution: `I18N-002`

- **Brief:** [i18n-002-de-fr-es-pt.md](../specifications/i18n-002-de-fr-es-pt.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude Code, with one translating agent per language in its own worktree
- **Reviewer:** an independent Claude Code agent for the copy, with back-translation; another for the completed change
- **Branch:** `i18n-002-de-fr-es-pt`
- **Updated:** 2026-10-28

## Plan

1. Add the row as roadmap revision 37 and idea 60, and add it to the GitHub Projects mirror in the same step.
2. Move the four languages into `PosatoLocales.SHIPPED` and extend the system lists the guide names.
3. Translate the catalogs, the site, and the store listings, one agent per language, each writing only its own language's directories.
4. Have one independent agent review the copy and translate the key screens back into English, then apply its Required and Recommended findings.
5. Capture the store screenshots with `store capture`, and prove each language on the Mac in Tart, the test iPhone, and Android, with the long-text pass.

## Result

- German, French, Spanish (`es-ES`), and Portuguese (`pt-BR`) are shipped languages. The app catalogs, the Android block screen, the macOS pause page and Automation description, the iOS and helper `InfoPlist.strings`, and the Windows service description exist for each. `posato.app` serves `/de/`, `/fr/`, `/es/`, and `/pt/`, and `docs/store` holds four prepared listings with screenshots. Nothing was uploaded.
- With six languages the header no longer fit a phone, so the site lists its languages in a labelled footer list, with the current one marked `aria-current` (`agent-delegated`). Each `chrome.json` gained `languages`, Polish included.
- The copy review found 19 Required findings across the four languages: Android's lock-screen term used for Posato's block screen (de, es, pt), "at" before an end value that can be a date (de, fr, pt), agreement with an inserted count (fr, pt), two softened or strengthened limits (de, es), the Spanish Login Items pane, German imperatives in status lines, and a Portuguese "catch up". All were applied, together with the Recommended findings; Optional ones are left to the maintainer. The report and its back-translations are attached to the pull request.
- The narrow pass on the test iPhone 13 mini broke the French and Portuguese schedule tabs mid-word. The French, Spanish, and Portuguese pause-set and schedule tabs now use the short names Polish already uses (`Ensembles`, `Conjuntos`, `Planning`, `Agenda`), and the sentences that point to those tabs follow (`agent-delegated`).
- `posato-control` gained `resize-window` for the Mac minimum width, since `osascript` in the guest timed out. `launch -t device --arg -AppleLanguages ...` failed because `devicectl` read the argument as its own options; the result-file flag now precedes `--` and the application's arguments. `DevicectlCommandTest` failed before that fix.
- Deviation: the brief's `AC-05` names a small iPhone Simulator. The test iPhone 13 mini is narrower and found the tab defect first; the iPhone 16e captures were also run.

## Completed-change review

- **Verdict:** approve after one Required fix.
- **Critical or Required findings:** the four new `InfoPlist.strings` file references in `project.pbxproj` had the file type `text.deist.strings` and similar, from a `pl` replacement inside `plist`.
- **Resolution:** set to `text.plist.strings` in `3a40d703`, and the guide now says the file type stays unchanged.
- **Advisory findings:** taken: run evidence for a device launch with and without arguments (below). Left: `--capture-logs` drops launch arguments on a device (older than this task); `aria-current="true"` instead of `page` on the current language.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pending | |

## Blockers and accepted risks

- The maintainer accepts the copy of all four languages from the review report in the pull request.
- Android dialog buttons such as Save in "New set" and Delete in the delete-set question carry no resource id, because `testTagsAsResourceId` covers only the main window, so `flow set` cannot save a new set on Android. This predates the task; the runs pressed those buttons by their English text. Follow-up candidate.
- The test iPhone's Screen Time access needs renewing: sessions start, but Posato shows "Restrictions need attention". The language proof does not depend on it.
- Wide labels in the iOS navigation bar, such as "Ajouter une programmation", wrap to two lines without clipping.

## Final

- **Status:** active
- **Outcome:** pending the final runs at the pull request's head.
