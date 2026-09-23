# Execution: `IOS-004`

- **Brief:**
  [`../specifications/ios-004-ipad-copy.md`](../specifications/ios-004-ipad-copy.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** independent agent, completed-change review
- **Branch:** `feature/ios-004-ipad-copy`
- **Updated:** 2026-09-23

## Plan

1. Add a semantic device interface (`platformDevice()`: Mac, iPhone, iPad from
   the platform idiom) and a pure navigation-placement rule: Mac sidebar, iPad
   sidebar in landscape only, iPhone bottom navigation.
2. Derive every device noun and label from the device, not from placement:
   the Session and Paused items labels, the sidebar label, the permission body,
   the Summary scope, and the short-pause caption.
3. Amend `DESIGN.md` with the accepted iPad landscape sidebar.
4. Verify on the iPad Pro 13-inch and iPad mini Simulators in four
   orientations, the iPhone Simulator, and the Mac.

## Result

- `platformDevice()` replaces `platformNavigationPlacement()`; the scaffold
  derives placement from the device and the window aspect. Selected-item
  details stay a sheet on iOS and a dialog on Mac by platform.
- The traffic-light inset applies only on Mac; the onboarding wordmark hides
  under the keyboard on both iOS devices.
- Deviation: the first build moved the content between a `Column` and a `Row`
  when the placement changed. On the iPad Simulator that ended the focused
  field's input session on rotation; the keyboard hid, and the bottom
  navigation stayed hidden after Done. `main` kept the keyboard in the same
  probe. `PosatoNavigationScaffold` now keeps the content under one parent and
  moves only the header and navigation.
- Scope addition (`user-confirmed`): `posato-control` gains `orient` (command
  and scenario step, XCUITest `XCUIDevice.orientation`, refused on desktop),
  after synthesized Simulator keystrokes landed on the maintainer's windows.
  A target that shares the current aspect goes through the other aspect
  first, so every rotation is observable.
- `AC-04`: no captured surface changed. The iPad store set is portrait Paused
  items websites, Session duration, and About; none carries a device noun, and
  portrait placement is unchanged.

## Completed-change review

- **Verdict:** `changes-required` on `5b19e6a`; `approved` on `5b73d72`
- **Critical or Required findings:** `orient` returned before a same-aspect
  rotation finished (two landscapeRight captures were mid-rotation) and passed
  iPhone upside down silently
- **Resolution:** same-aspect targets go through the other aspect; iPhone
  upside down now fails with `WAIT_TIMEOUT`; landscapeRight rerun settled
- **Advisory findings:** portrait-restore note taken; per-component
  `platformDevice()` reads and iPhone-only recipe wording kept
- **Diff examined:** every hunk of `origin/main...5b19e6a`, with the full
  `PosatoNavigationScaffold.kt` and `ApplicationNavigation.kt`, the platform
  actuals, onboarding, Session, and driver call sites, `DESIGN.md:255-280,355-410`;
  then `5b73d72` (`ScenarioExecutor.swift:272-305`, `README.md:152`,
  `SKILL.md:174-184`, this record)
- **Tests run by the reviewer:** `./gradlew :posato-control:test --rerun`
  (100 tests, 0 failures, three for `orient`),
  `./gradlew :posato-control:swiftFormatCheck --rerun` (pass), and
  `./gradlew :shared:jvmTest --tests 'app.posato.core.designsystem.NavigationPlacementTest' --rerun`
  (3 tests, 0 failures)
- **Paths checked:** iOS supported orientations in `project.pbxproj`, the
  short-pause caption producers, the AC-04 screens (`SessionScreen.kt`,
  `ApplicationBrowser.kt`), driver plumbing (`IosInteraction.kt`,
  `ControlJson.kt`, `build.gradle.kts`), and every `ios004-*` run directory
  cited below, including branch and `main` landscape snapshots

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | after the last correction; `NavigationPlacementTest` on JVM and iOS Simulator, `orient` CLI and runner tests |
| `orient` rotation checks | pass | iPhone upside down fails with `WAIT_TIMEOUT` and portrait restores; landscapeLeft to landscapeRight takes about 2 s through portrait: `build/verification/runs/ios004-orient-check/` |
| iPad Pro 13-inch, onboarding, four orientations (`AC-01`, `AC-02`) | pass | "on this iPad." and "Saved on this iPad" asserted, no "iPhone": `build/verification/runs/ios004-ipad13-portrait-onboarding/`, `build/verification/runs/ios004-ipad13-landscapeLeft-onboarding/`, `build/verification/runs/ios004-ipad13-landscapeRight-onboarding-v3/`, `build/verification/runs/ios004-ipad13-portraitUpsideDown-onboarding/` |
| iPad Pro 13-inch, Session, review, active, Paused items, About, four orientations (`AC-01`, `AC-02`) | pass | "On this iPad only" asserted; landscape sidebar with **On this iPad** and **About Posato**, portrait bottom navigation: `build/verification/runs/ios004-ipad13-portrait-destinations-v2/`, `build/verification/runs/ios004-ipad13-landscapeLeft-destinations-v2/`, `build/verification/runs/ios004-ipad13-landscapeRight-destinations-v3/`, `build/verification/runs/ios004-ipad13-portraitUpsideDown-destinations-v2/` |
| iPad keyboard across four rotations | fail, then pass | draft and destination kept; before the fix the keyboard hid and Done left navigation hidden: `build/verification/runs/ios004-ipad13-keyboard-rotation-2/`; `main` passed: `build/verification/runs/ios004-main-ipad13-keyboard-rotation/`; after the fix pass: `build/verification/runs/ios004-ipad13-keyboard-rotation-fix/` |
| iPad mini, portrait and landscape (`AC-02`) | pass | no clipping at 744 points: `build/verification/runs/ios004-ipadmini-portrait-onboarding/`, `build/verification/runs/ios004-ipadmini-portrait-destinations/`, `build/verification/runs/ios004-ipadmini-landscapeLeft-onboarding/`, `build/verification/runs/ios004-ipadmini-landscapeLeft-destinations/` |
| iPhone 17 (`AC-03`) | pass | iPhone wording, bottom navigation in both orientations, keyboard kept across rotation: `build/verification/runs/ios004-iphone17-portrait-onboarding/`, `build/verification/runs/ios004-iphone17-portrait-destinations-v2/`, `build/verification/runs/ios004-iphone17-keyboard-rotation-iphone-v3/`; in landscape the branch (`build/verification/runs/ios004-iphone17-landscape-v4/`) and `main` (`build/verification/runs/ios004-main-iphone17-landscape/`) stop at the same collapsed Apps header with identical snapshots |
| Mac desktop smoke | pass | **On this Mac**, traffic-light inset, Session, Paused items, About unchanged: `build/verification/runs/ios004-desktop-smoke-2/` |

## Blockers and accepted risks

- The short-pause caption needs Family Controls, which the Simulator lacks; it
  reads the same `platformDevice()` noun as the observed labels. No physical
  iPad was available, as in `IOS-003`.
- Follow-up candidate, present on `main`: on the iPhone in landscape, the Apps
  header row (device label and **Choose apps**) collapses to zero height and
  Session leaves little room to scroll.
- Observed on the Simulator after an early end, not investigated here:
  Session shows "Restrictions may still apply — retry clearing them."

## Final

- **Status:** `done`
- **Outcome:** met; `AC-04` met by stating that no captured surface changed
