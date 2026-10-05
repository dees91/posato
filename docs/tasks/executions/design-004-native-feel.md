# Execution: `DESIGN-004`

- **Brief:** [design-004-native-feel.md](../specifications/design-004-native-feel.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude Code
- **Reviewer:** independent completed-change review (Claude Code subagent)
- **Branch:** `spike/ios-navigation-feel`
- **Updated:** 2026-10-05

## Plan

1. Start from the spike commits on PR #137, rebased on `main`.
2. Run a native `impeccable audit` of iOS and the Mac; the maintainer
   chooses the polish scope from its findings and the known gaps.
3. Run one polish round on that scope.
4. Adapt `posato-control` and `verify-posato` to the new interface and run
   the existing scenarios and flows.
5. Rewrite the affected `DESIGN.md` sections and route the wiki to them.
6. Verify the final head on the Simulators, the Mac in a Tart VM, and the
   test iPhone; request the independent review; describe the pull request.

## Result

- Audit and polish scope: the first native audit scored 12/20. The
  maintainer chose the defects the spike introduced, the back mark on the
  content line, Session on bars, system alerts, system time pickers,
  typography, Material remnants, the iPad in landscape, and performance.
  Mid-round corrections from the maintainer: schedule rows with the switch
  alone (actions under a swipe), and whole-pane pushes on the iPad.
- `AC-01`: the iOS stack moves whole screens with their bar; the edge swipe
  follows the finger and finishes with its velocity; the tab bar belongs to
  the destinations and stays under the keyboard. The screen below the top
  stays composed but unplaced and only the top hears back.
- `AC-02`: the shared controls appear on iPhone and iPad Simulators in light
  and dark and on the Mac in a Tart VM, without ripple on the Apple hosts;
  keyboard focus shows a ring on the Mac.
- `AC-03`: the repeated audit (independent subagent) scored 15/20 and found
  one new P1, schedule times cut off at the largest text size; it is fixed and
  the auditor confirmed no new P0 or P1. Open P2 items are recorded in the
  wiki: the brand back mark instead of a chevron (kept by maintainer choice),
  schedule actions only under a swipe on iOS, a schedule draft discarded on
  back, and no fallback when a system alert cannot present.
- `AC-04`: `DESIGN.md` has a Platform Adaptation section, the passages it
  supersedes point to it, and the wiki routes to it.
- `AC-05`: the scenarios and flows pass on the new interface (see
  Verification). Tooling changes: the `adjustWheels` action for system
  pickers, a tap that fails a step instead of aborting on an element off
  screen, scroll progress that counts moved text, a field clear that reads
  the field back, flows that follow both hosts, `licenses-ios.json` and
  `website-batch-list-cleanup-desktop.json` where the hosts now differ, and
  verify-posato's "iOS and the Mac drive differently".
- The idea queue entry became idea 30 after `main` took idea 29
  (`MACOS-027`) during the rebase.

## Completed-change review

- **Verdict:** no Critical findings; three Required findings resolved and
  confirmed by the reviewer:
  - a parked screen's back handler took the edge swipe (each screen now has
    its own dispatcher, enabled only on top);
  - a system alert could run another answer after its list changed (buttons
    bind to their own answer; the alert is replaced when answers change);
  - keyboard focus never showed on the Mac after a click, and the switches
    had no focus ring.
- Recommended findings fixed: stale stack record on iPad rotation, screen
  keys colliding through redacted route text, an alert replacement that
  could fail to present, bar Back enabled during a save or start. Declined:
  VoiceOver reading the compact picker as "Time Picker" (its inner element is
  private UIKit; the row label is read just before it). Optional findings
  fixed: times set on a reference day, the flow's back label per host, and
  alert buttons bound by position as well as title.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | Passed | local run on the reviewed code |
| Native audit, repeated | 15/20, no new P0/P1 | Simulator runs `20261005-130555-3b30`, `20261005-130625-6548`, `20261005-131538-ab00`, `20261005-132230-9bb4`, `20261005-132312-c786`, `20261005-133925-4c57` |
| iOS scenarios, iPhone 17 Simulator, `a125a85` | Passed: first-install, add-website, website-edit, website-batch-list and its cleanup, remove-website, licenses-ios, schedules-device, session-start, session-start-saved-items, session-early-end, session-expiry, first-install-skip | runs `20261005-154302-b92a` to `20261005-155822-0a15`; session-start after the driver waits for a still element: `20261005-160012-ec05` |
| iOS flows, iPhone 17 Simulator, `a125a85` | Passed: `flow set`, `flow schedule` (system time pickers), `flow session` (countdown wheel, 30 minutes) | same series |
| iPad Pro 13 Simulator | Passed: landscape sidebar, About as a sidebar root, rotation keeping state, whole-pane pushes | runs `20261005-134045-e7da`, `20261005-134101-74c1`, `20261005-152516-0ae1`; R1 edge swipe `20261005-152333-8ae4` |
| Mac scenarios, Tart VM `--vm primary` | Passed: add-website-desktop, remove-website-desktop, licenses, schedules-desktop, session-start-desktop, session-early-end, pause-sets-desktop without the iCloud-only notice, keyboard focus ring after a click | runs `20261005-154215-ef03`, `20261005-154226-730a`, `20261005-154243-a450`, `20261005-153559-5ce4`, `20261005-154407-5c19`, `20261005-154425-b82f`, `20261005-154032-d679`, `20261005-153010-d849` |
| Mac flows, Tart VM | Passed: `flow set`, `flow schedule`, `flow session`, then session-early-end | runs `20261005-155526-fff2`, `20261005-155534-1308`, `20261005-155639-f167`, `20261005-155649-768f` |
| Test iPhone 13 mini (iOS 26.5.2, `-t device`), `19d9995` build, with maintainer consent | Passed: choose-app-ios, session-relaunch-ios (three relaunches, both restrictions after them), observe-blocking-ios, session-early-end, observe-unblocked-ios, pause-sets-device, schedules-device, website-edit, licenses-ios | runs `20261005-160818-f134`, `20261005-190046-dc39`, `20261005-185108-d523`, `20261005-185124-e8a6`, `20261005-185135-1a86`, `20261005-185424-5b19`, `20261005-161359-aebb`, `20261005-161442-8b1c`, `20261005-185249-2cf8` |

## Blockers and accepted risks

- `pause-sets-desktop.json` expects the one-time "Update Posato on your
  other devices" notice, which needs a VM linked to iCloud; the Mac run used
  a copy with that step optional.
- The Screen Time and iCloud consent scenarios were not rerun: the test
  iPhone already had both, and this change does not touch them.
- Two fixture updates came from the device runs: example.com now reads "This
  domain is for use in …" instead of "Example Domain", and Safari's blank
  first load in a session gets one retry in `session-relaunch-ios.json`.
- The XCUITest driver sometimes misses a tap right after `scrollTo` at the
  largest text size; the same tap from the command line works.

## Final

- **Status:** done; ready for review
- **Outcome:** `AC-01` to `AC-05` met on the iPhone and iPad Simulators, the
  Mac in a Tart VM, and the test iPhone; the open audit P2 items stay
  recorded in the wiki for later rows.
