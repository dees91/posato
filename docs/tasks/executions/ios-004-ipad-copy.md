# Execution: `IOS-004`

- **Brief:**
  [`../specifications/ios-004-ipad-copy.md`](../specifications/ios-004-ipad-copy.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** pending
- **Branch:** `feature/ios-004-ipad-copy`
- **Updated:** 2026-09-22

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
- `AC-04`: no captured surface changed. The iPad store set is portrait Paused
  items websites, Session duration, and About; none carries a device noun, and
  portrait placement is unchanged.

## Completed-change review

- **Verdict:** pending
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | after the scaffold and `orient` corrections; `NavigationPlacementTest` on JVM and iOS Simulator, `orient` CLI and runner tests |
| iPad Pro 13-inch, onboarding, four orientations (`AC-01`, `AC-02`) | pass | "on this iPad." and "Saved on this iPad" asserted, no "iPhone"; runs `ios004-ipad13-portrait-onboarding`, `ios004-ipad13-{landscapeLeft,landscapeRight,portraitUpsideDown}-onboarding` |
| iPad Pro 13-inch, Session, review, active, Paused items, About, four orientations (`AC-01`, `AC-02`) | pass | "On this iPad only" asserted; landscape sidebar with **On this iPad** and **About Posato**, portrait bottom navigation; runs `ios004-ipad13-*-destinations-v2` |
| iPad keyboard across four rotations | fail, then pass | draft and destination kept; before the fix the keyboard hid and Done left navigation hidden (`ios004-ipad13-keyboard-rotation-2`); `main` passed (`ios004-main-ipad13-keyboard-rotation`); after the fix pass (`ios004-ipad13-keyboard-rotation-fix`) |
| iPad mini, portrait and landscape (`AC-02`) | pass | no clipping at 744 points; runs `ios004-ipadmini-{portrait,landscapeLeft}-{onboarding,destinations}` |
| iPhone 17 (`AC-03`) | pass | iPhone wording, bottom navigation in both orientations, keyboard kept across rotation; runs `ios004-iphone17-portrait-onboarding`, `ios004-iphone17-portrait-destinations-v2`, `ios004-iphone17-keyboard-rotation-iphone-v3`; landscape identical to `main` (`ios004-iphone17-landscape-v4`, `ios004-main-iphone17-landscape`) |
| Mac desktop smoke | pass | **On this Mac**, traffic-light inset, Session, Paused items, About unchanged; run `ios004-desktop-smoke-2` |

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

- **Status:** `active`
- **Outcome:** pending review
