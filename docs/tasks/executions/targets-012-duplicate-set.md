# Execution: `TARGETS-012`

- **Brief:** [targets-012-duplicate-set.md](../specifications/targets-012-duplicate-set.md)
- **Status:** `done`
- **Review tier:** Standard
- **Implementer:** Claude Code agent, under the maintainer's 2026-10-20
  mandate to finish release 1.5 unattended
- **Reviewer:** independent agent
- **Branch:** `targets-012-duplicate-set`
- **Updated:** 2026-10-20

## Plan

1. Failing tests: the view model's duplicate, and a copy in each store.
2. `LocalApplicationMappings.copyChoices` on the Mac, Android and Linux,
   and iOS (Kotlin wrapper and Swift provider); `PauseSetsViewModel.duplicate`.
3. **Duplicate** in the set menu and the iOS swipe actions; runs on Android
   and a Mac VM.

## Result

- `duplicate` copies this device's apps, adds "<name> (copy)" with the
  source's websites through the same set authoring as **New set**, and then
  copies the apps again, in case a sync merge between the two writes
  dropped the first copy. If the set write fails, it clears the copied apps.
  A copy that cannot be made leaves the new set without apps, which the list
  shows (`agent-delegated`). At ten sets it adds nothing, and the menu item
  is disabled. A long name is shortened by whole characters.
- `copyChoices` replaces the target set's choices with the source's: one
  SQLite transaction on the Mac, one file write on Android and Linux, and
  one stored selection on iOS. The interface default answers `Unavailable`.
- Detekt's function limits moved some private helpers to file level
  (`ApplicationMappingRows.kt` on the Mac, `readRecords` for the catalog,
  and two `PauseSetsInputs` extensions). The behavior is unchanged.

## Completed-change review

- Independent agent, 2026-10-20: no Critical; two Required, both done: the
  Mac VM run, and `DESIGN.md` naming **Duplicate** in the set menus
  (idea 43 is `user-confirmed`). Recommended taken: the copy is made again
  after the set is written; shortening keeps whole characters (an emoji
  test, red first); a failed copy is recorded as accepted.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `PauseSetDuplicateTest`, the Mac, catalog, and iOS wrapper copy tests on `main` `462d8fbe` | Failed as expected (the copy is missing, or the store answers `Unavailable`) | gradle runs of the session |
| `:shared:jvmTest`, `:desktopApp:test`, `:shared:iosSimulatorArm64Test` for the wrapper, Detekt, ktlint, and iOS and Android compiles | Passed | gradle runs of the session |
| Galaxy XCover7: My set with example.org and Clock, then **Duplicate** | "My set (copy), 1 website · Apps on this phone: 1" | `build/verification/e2e/targets-012-android.log`, runs `20261020-225239-f83a`, `20261020-225251-a086` |
| Mac VM: Calculator chosen for My set, then **Duplicate** | "My set (copy), 0 websites · Apps on this Mac: 1" | `build/verification/e2e/targets-012-mac.log`, runs `20261020-230002-6bd4`, `20261020-230016-9c2b` |
| `./gradlew quality` on the head | See the pull request's Verified comment | |

## Blockers and accepted risks

- The iOS Swift copy was not run on the test iPhone, which was locked while
  the maintainer was away. The quality gate builds it, and the wrapper test
  covers the call.
