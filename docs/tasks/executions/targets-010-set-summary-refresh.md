# Execution: `TARGETS-010`

- **Brief:** [targets-010-set-summary-refresh.md](../specifications/targets-010-set-summary-refresh.md)
- **Status:** `done`
- **Review tier:** Standard
- **Implementer:** Claude Code agent, under the maintainer's 2026-10-20
  mandate to finish release 1.5 unattended
- **Reviewer:** independent agent
- **Branch:** `targets-010-set-summary-refresh`
- **Updated:** 2026-10-20

## Plan

1. Failing store tests on the Mac and iOS: every successful write is
   announced, and a cancelled, refused, or failed one is not.
2. Emit `invalidations` from both stores.
3. Check the readers, then run on a Mac VM, the test iPhone, and Android.

## Result

- `DesktopLocalApplicationMappings` and `IosLocalApplicationMappings` emit
  after each successful choice, removal, clearing, and `retainOnly`. iOS
  merges this with its Screen Time authorization signal. A signal that
  arrives while another waits is folded into it (`DROP_OLDEST`), and readers
  reload after the latest change.
- Readers, as the brief asked: the Pause sets list, the set editor, the
  Schedules set source, the Mac and Linux schedule hosts, session recompose,
  and Mac presence all observe the signal. Mac presence runs in the store's
  process (`Main.kt`). The iOS schedule host has no `targetChanges`, because
  its monitor extension composes each occurrence (`claimsCompose`). This is
  left as is (`agent-delegated`).

## Completed-change review

- Independent agent, 2026-10-20: no Critical or Required findings. It
  confirmed that every emission follows a durable write, that no reader
  writes back into a loop, and that the iOS merge keeps per-collector
  cancellation. Recommended: commit the closeout and keep `AC-03` open
  until the iPhone run (both done); one Optional taken (test comment).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| The new Mac store test and iOS wrapper test on `main` `78c4c6c0` | Failed as expected (`expected:<1> but was:<0>`; `Expected <1>, actual <0>`) | `build/verification/e2e/targets-010-red.txt` |
| `:desktopApp:test` and `:shared:iosSimulatorArm64Test` with the fix | Passed | gradle run of the session |
| Mac VM: choose Calculator for My set, then **Back to pause sets** | "My set, Default, 0 websites · Apps on this Mac: 1" at once | `build/verification/e2e/targets-010-mac.log`, run `20261020-211427-7974` |
| Mac VM: a pause runs with Calculator; TextEdit is chosen during it | TextEdit blocked 5 s after the choice; after removing it the list again reads 1 | runs `20261020-211442-65d0`, `20261020-211544-9c60` |
| Galaxy XCover7: choose Clock, then Back | "Apps on this phone: 1" at once | `build/verification/e2e/targets-010-android.log`, run `20261020-212124-9817` |
| Test iPhone (`AC-03`) on `88539818`: from "none chosen", choose Calculator, add Clock, untick Clock in the picker, then **Clear selection**, going **Back to Pause sets** after each | My set reads "Apps on this iPhone: 1", then 2, 1, and "none chosen", each within 0.1 s of Back | runs `20261020-223645-f55c`, `20261020-223730-4f08`, `20261020-223748-3ec8`, `20261020-223808-806c`; scenarios in `build/verification/e2e/targets-010-ios-*.json` |
| `./gradlew quality` on the head | See the pull request's Verified comment | |

## Blockers and accepted risks

- None. The first iPhone attempt was blocked by a locked phone
  (`DEVICE_AUTOMATION_LOCKED`); after the maintainer unlocked it, `AC-03`
  passed on the same head.
