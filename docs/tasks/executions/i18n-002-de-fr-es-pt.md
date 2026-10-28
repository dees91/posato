# Execution: `I18N-002`

- **Brief:** [i18n-002-de-fr-es-pt.md](../specifications/i18n-002-de-fr-es-pt.md)
- **Status:** `closed`
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
| `./gradlew quality` on `62baa6cf` | pass | `BUILD SUCCESSFUL` in 4m 45s. An earlier run beside the Simulator captures failed on a hung iOS test runner, and two on swift-format and ktlint, fixed in `c9110f47` and `b049fd95`. |
| `verifyEnglishText`, `verifyCatalogs`, `verifyLocaleLists` with five shipped languages | pass | Part of `quality`; also after each translation merge. |
| Site build and `/`, `/de/`, `/fr/`, `/es/`, `/pt/` with their privacy pages at 390 and 1280 px | pass | 30 pages; no horizontal scroll; `lang`, self canonical, `hreflang` for six languages and x-default; the footer list marks the current language. Run `site-20261028-171514`. |
| Mac `primary` guest at `62baa6cf`, English: `vm onboard`, `flow session --set Work`, `observe --expect blocked` | pass | `outcome: paused`. Runs `20261028-190349-ffdb`, `20261028-190540-a449`, `20261028-190554-01da`. |
| Mac, app only in each language: Safari pause page, then Session, Pause sets, Schedules after `resize-window` | pass | Pause page headline in German, French, Spanish, and Portuguese, each naming the pause end time 21:05; window 614 × 780 for each language with no clipped label; End early by id, then `observe --expect allowed` on `example.org`. Runs `20261028-190620-41b3`, `20261028-190705-ba6e`, `20261028-190749-abe6`, `20261028-190831-a5ea`, `20261028-190919-82bd`. |
| Test iPhone 13 mini at `62baa6cf`: `flow set`, `flow schedule --off`, `flow session --set Work`, then each language through `launch --arg -AppleLanguages` | pass | Session, Pause sets with one and other counts, and Schedules in each language, tabs on one line; then End early, and the set and schedule removed. Runs `20261028-190339-1bf0`, `20261028-190611-7be8`. |
| Android phone at `62baa6cf`: `flow session`, Clock blocked, app locale per language, End early | pass | The block screen, Session, Pause sets, and Schedules in each language; `observe` blocked, then allowed. Runs `20261028-190720-428d`, `20261028-190754-e7d8`, `20261028-190953-a140`. |
| `store capture --prepare` on iPhone 17 Pro Max and iPad Pro 13-inch (M5) for each locale | pass | 1320 × 2868 and 2064 × 2752 PNGs in `docs/store/<locale>/screenshots`. The German iPad set was recaptured after a run beside `quality` timed out. Runs `20261028-181826-c13f`, `20261028-185224-911a`, `20261028-183503-34da`, `20261028-183646-5f4b`, `20261028-184054-9f9b`, `20261028-184234-d080`, `20261028-184636-ee64`, `20261028-184817-f2c1`. |
| `store capture` on iPhone 16e for each locale (long-text pass) | pass | No clipped label; iOS shortens a long back-button title such as "Ensembl…" itself. Runs `20261028-185443-ece6`, `20261028-183907-0e57`, `20261028-184452-2f0f`, `20261028-185033-18c8`. |
| `DevicectlCommandTest` before and after the launch fix | fail, then pass | The red run showed the result-file flag after the application's arguments. |

Linux and Windows take the language from the session locale and share the catalog the runs above exercise; `posato-control` has no language recipe for them, as `I18N-001` recorded.

## Blockers and accepted risks

- The maintainer accepted the German, French, Spanish (`es-ES`), and Portuguese (`pt-BR`) copy as it stands, on the basis of the independent review and its back-translations in the pull request (`user-confirmed`, 2026-10-28).
- Android dialog buttons such as Save in "New set" and Delete in the delete-set question carry no resource id, because `testTagsAsResourceId` covers only the main window, so `flow set` cannot save a new set on Android. This predates the task; the runs pressed those buttons by their English text. Follow-up candidate.
- The test iPhone's Screen Time access needs renewing: sessions start, but Posato shows "Restrictions need attention". The language proof does not depend on it.
- Wide labels in the iOS navigation bar, such as "Ajouter une programmation", wrap to two lines without clipping.

## Final

- **Status:** closed
- **Outcome:** met; `AC-01` to `AC-05` are proven above. The maintainer accepted the copy of all four languages on 2026-10-28.
