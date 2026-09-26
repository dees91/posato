# PR #92: unified Mac setup UI

- **Status:** blocked on test-iPhone UI automation; implementation and Mac verification complete.
- **Plan:** replace separate Mac setup choices with one shared explanation and
  inactive action, retain working helper controls under disclosure, align
  authorities and the project mirror, verify native screens and review the diff.

## Result

Onboarding, Session's Finish setup and Schedules share one setup explanation
for blocking, quiet login launch and starts without repeated passwords.
The upgrade offer uses the same shell. The new action is disabled, with an
explicit preview notice. Advanced helper controls remain usable under disclosure;
their readiness still admits the current manual-session form without claiming
unified setup completion. Schedule saving and execution remain inactive.
No authorization, persistence, synchronization or scheduler behavior was added.
Roadmap revision 13 and the three changed GitHub Projects outcomes match.

The accepted connected flow resumes missing steps, verifies actual permissions
before completion and preserves access revocation. Exactly one password entry
is not promised. Implementation and the automatic-Apply amendment remain pending.

## Review and checks

- Independent completed-change review inspected the UI routes, callbacks,
  authorities and driver recipe. Follow-up review covered the component
  extraction and final compact copy. No P0/P1/P2 findings remain.
- Final `./gradlew quality` passed: 204 tasks; shared JVM 695, shared iOS 667
  and desktop 179 tests, all without failures. Swift tests and iOS host builds
  passed. Markdown targets, fixture JSON and `git diff --check` passed.
- Native Tart verification passed onboarding deferral, the disabled unified
  action, persistent Session notice, Finish setup, retained helper controls,
  schedule setup, editor name/time changes, disabled Save, Cancel without a
  plan and navigation. The setup effects fit above the onboarding action at
  the tested desktop size. Screenshots were visually inspected.
- Existing helper enable, system approval and readback admitted duration
  selection without starting restrictions. After the final copy refinement,
  the aggregate gate's ad-hoc package could not check the helper; rebuilding
  with the development signature and repeating the scenario passed.
- Evidence: `build/verification/runs/pr92-unified-final-mac/` for final UI,
  `build/verification/runs/pr92-unified-setup-mac/` for helper approval/readback,
  and `build/verification/pr92-unified-quality.log` for the aggregate gate.
  Repeatable recipes: `build/verification/scenarios/mac-unified-onboarding.json`
  and `build/verification/scenarios/schedules-unified-desktop.json`.
  Tested sources are the correction based on `250557e`, committed with this record.
- Disposable VMs were destroyed, the test-device app and driver were cleaned
  up, and the temporary Compose skill activation was released.

## Remaining blocker

The signed iPhone build, installation and launch passed. UI automation again
returned `DEVICE_AUTOMATION_LOCKED`; no iPhone interaction success is claimed.
Failing command:

```shell
tools/posato-control/build/install/posato-control/bin/posato-control run -t device --run-id pr92-unified-setup-iphone --scenario build/verification/scenarios/schedules-device.json
```

Evidence: `build/verification/runs/pr92-unified-setup-iphone/`. Rerun this
scenario when the dedicated device permits XCTest automation. No attended
workaround was used. Schedule implementation and automatic-Apply authorization
remain with `SCHEDULE-001` and `SCHEDULE-002`.
