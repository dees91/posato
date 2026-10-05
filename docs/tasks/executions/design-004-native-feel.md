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
  the auditor confirmed no new P0 or P1. Of the open P2 items the maintainer
  had two fixed in the PR review round (schedule actions in sight in the
  editor, a drawn fallback for an alert that cannot present) and left two as
  they are (the brand back mark, a schedule draft discarded on back).
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

## PR review round

- Hosted review of `b50863e`: two P2 findings.
  - The selected-items screen padded for the keyboard twice; the tab frame
    alone now reserves it (`ce4b063`).
  - The verification did not name the final revision; the table below is
    rerun at one revision.
  - The review's quality failure was the reviewer's own network
    (`:desktopApp:downloadSparkle`, connection refused); the branch has no CI
    checks, and `./gradlew quality` passes locally at the revision below.
- The maintainer asked in the same round for the two P2 fixes above
  (`2d99edb`) and for UI recordings in the pull request description.

## Verification

Every row below ran at `522c8b7`, the last revision with executable changes;
later commits change only this record. Commands use
`PC=tools/posato-control/build/install/posato-control/bin/posato-control` and
`S=tools/posato-control/fixtures/scenarios`; each scenario is
`$PC run -t <target> --scenario $S/<name>.json`, and evidence lives in the
ignored `build/verification/runs/<run>/`. One-off checks whose scenarios are
not tracked are described by what they do.

| Check | Result | Runs |
| --- | --- | --- |
| `./gradlew quality` | Passed | local, 5 min 20 s |
| iPhone 17 Simulator (`-t sim`), light: first-install, add-website, website-edit, website-batch-list, website-batch-list-cleanup, remove-website, add-website, licenses-ios, schedules-device, session-start, session-early-end, session-start-saved-items, session-early-end, session-expiry, then `--fresh` and first-install-skip | Passed | `20261005-203202-1b34` to `20261005-203953-3731`, `20261005-204802-00ac` |
| iPhone 17 Simulator flows: `$PC flow set --name "Flow set" --website flow.example`, `flow schedule --name "Editor check" --start 09:30 --end 10:45 --set "Flow set"`, `flow session --set "Flow set" --minutes 30`, then session-early-end | Passed | `20261005-204510-dce5`, `20261005-204531-6f49`, `20261005-204628-f18d`, `20261005-204651-6d09` |
| iPhone 17 Simulator one-off: the selected-items filter with the keyboard up (the review's P2), and the schedule editor's Skip next and Delete schedule with the system alert | Passed | `20261005-205146-fdcf` |
| iPhone 17 Simulator one-off: in a set, search, open a result's editor, one edge swipe closes it while the hidden search stays active | Passed (the check steps; an optional cleanup step after them failed for lack of data) | `20261005-205206-b906` |
| iPad Pro 13 Simulator, landscape, dark: a tour through the sidebar, a new pause, a set, the schedule editor, About as a sidebar screen, and Licenses; then in portrait, About open, turned to landscape and back, and Back to Session | Passed | `20261005-204846-8729`, `20261005-204937-1fef`, `20261005-204955-d878` |
| Mac, fresh Tart VM (`-t desktop --vm primary`): schedules-desktop, add-website-desktop, remove-website-desktop, add-website-desktop, licenses, session-start-desktop, session-early-end; flows set, schedule (`--off`), session (30 minutes), session-early-end | Passed | `20261005-203529-6c7f` to `20261005-203834-ab47` |
| Mac, second fresh Tart VM: pause-sets-desktop with its iCloud-only update notice optional; one-off keyboard focus ring after clicks and Tab | Passed | `20261005-204226-9f33`, `20261005-204316-a5fc` |
| Test iPhone 13 mini (`-t device`, wired, with maintainer consent): driver probe, session-relaunch-ios (three relaunches, Calculator and example.com blocked after them), observe-unblocked-ios, pause-sets-device, schedules-device, website-edit, licenses-ios, `flow schedule --name "Editor check"`, and the one-off schedule editor check | Passed | `20261005-205438-ec48` to `20261005-210141-a2cc` |

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
  Mac in a Tart VM, and the test iPhone; the two remaining audit P2 items
  stay recorded in the wiki by maintainer choice.
