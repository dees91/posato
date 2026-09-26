# PR #92: required Mac setup UI

- **Status:** blocked on test-iPhone UI automation; implementation and Mac verification complete.
- **Plan:** revise the setup hierarchy, route missing helper setup before the
  session form, make schedule requirements visible, align authorities and the
  project mirror, verify native hosts and review the completed change.

## Result

Mac onboarding leads with required blocking setup. Missing or unknown helper
readiness replaces Start with Finish setup, including the menu Start route.
The existing helper actions remain connected; a ready result admits duration
selection without starting restrictions. Schedule setup lists the helper,
login launch and password-free automatic-start consent as requirements.
Editor exploration is explicitly a preview; saving and execution stay inactive.
No new authorization, persistence, synchronization or scheduling behavior was added.
Roadmap revision 12 and the three changed GitHub Projects outcomes match.

## Review and checks

- Independent completed-change review inspected the final UI routes, callbacks,
  previews and authorities. Its P2 removal callback finding was corrected;
  focused re-review found no remaining P0/P1/P2 defects.
- `./gradlew quality` passed after the final code correction: 204 tasks;
  shared JVM 695, shared iOS 667 and desktop 179 tests, all without failures.
  Swift tests and iOS host builds passed. Markdown links and `git diff --check` passed.
- Signed Mac build and native Tart run passed onboarding deferral, the persistent
  notice, Finish setup, menu Start, helper enable/approval/recheck, and admission
  to duration selection. An initial wait used a title without its actual line
  break; the subsequent snapshot and Review session assertion confirmed success.
- Schedule setup showed all requirements and disabled Continue. Editor name/time
  changes worked, Save stayed disabled, and Cancel left no plan. Navigation and
  setup deferral passed. Screenshots were visually inspected.
- Evidence: `build/verification/runs/pr92-required-setup-mac/` and
  `build/verification/runs/pr92-required-onboarding-top/`. Scenarios are
  `build/verification/scenarios/mac-required-onboarding.json`,
  `schedules-required-desktop.json`, `schedule-requirement-actions.json` and
  `mac-onboarding-heading.json` in that directory. Tested sources are the
  working-tree correction based on `785c2ee`, committed with this record.
- The disposable VM was destroyed; the device app and driver were stopped and
  cleaned up. The temporary Compose skill activation was released.

## Remaining blocker

The signed iPhone build, installation and launch passed. UI automation again
returned `DEVICE_AUTOMATION_LOCKED`; no iPhone interaction success is claimed.
Failing command:

```shell
tools/posato-control/build/install/posato-control/bin/posato-control run -t device --run-id pr92-required-setup-iphone --scenario build/verification/scenarios/schedules-device.json
```

Evidence: `build/verification/runs/pr92-required-setup-iphone/`. Rerun this
scenario when the dedicated device permits XCTest automation; no attended
workaround was used. Schedule implementation and automatic-Apply authorization
remain with `SCHEDULE-001` and `SCHEDULE-002`.
