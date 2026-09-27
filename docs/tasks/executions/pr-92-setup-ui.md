# PR #92: unified Mac setup UI

- **Status:** Mac verified; iPhone UI automation blocked until the device is unlocked for XCTest (Simulator evidence recorded).
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
Roadmap revision 10 and the changed GitHub Projects outcomes match.

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

## Corrections under the maintainer's delegation (2026-09-26 night)

The maintainer asked to bring this PR to a mergeable state in the spirit of
the unified setup, deciding for a simple user. Changes:

- **No false Finish setup.** Session reads the helper state once, quietly,
  when it appears (`MacHelperSetupUiState.readQuietly`, two failing-first
  tests). Start is gated only by a read that names a state other than ready.
  A ready Mac shows Start after every relaunch, and the tracked desktop
  session scenarios run unchanged.
- **Visible controls when needed.** The Finish setup screen and the onboarding
  step show the existing helper controls expanded while the helper is not
  ready. When it is ready, onboarding's Continue is the primary action.
- **Authorities.**
  - ADR 0009 records that the unified setup supersedes D3 for login launch.
  - ADR 0004 records that the `MACOS-014` opt-in becomes one effect of the
    setup action, with the grant and its limits unchanged.
  - `DESIGN.md` replaces the on-demand-read rule.
- **Roadmap.**
  - Revisions 10-13 are collapsed into revision 10.
  - `ONBOARDING-004` is High-risk, uses integration group `PR-MAC-SETUP`,
    and states that its grant covers manual Start and Resume until
    `SCHEDULE-002` integrates the reviewed amendment.
  - `SCHEDULE-001` owns the `PRIVACY.md` update.
  - A release guard keeps the shells off any release; 1.1.x branches from
    `v1.1.0`.
  - The coverage evidence names the unified setup.
- **Copy and recipes.** New onboarding copy moved to `strings.xml`, and the
  two orphaned strings were removed. The recipes are tracked:
  `mac-unified-onboarding-desktop.json`, `schedules-desktop.json`, and
  `schedules-device.json`.

Verification after the corrections, on a development build in a Tart clone:
- The unified onboarding recipe passed: defer, Finish setup, and controls
  visible.
- Enabling the helper from Finish setup went through background approval to
  the duration form.
- After a relaunch, no Finish setup appeared.
- The tracked `session-start-desktop.json` passed with the administrator
  prompt.
- `schedules-desktop.json` passed.

On the iPhone, `launch -t device --build` installed and started the app, but
UI automation again returned `DEVICE_AUTOMATION_LOCKED`: iOS waits for the
owner to unlock the phone and enter the passcode for XCTest. The Simulator
(iPhone 17) ran onboarding and rendered the Schedules empty state and editor.
Its time-control step did not match, and it is left for `SCHEDULE-002`, which
replaces the editor. Rerun `schedules-device.json` on the unlocked iPhone.


## Review corrections (2026-09-27)

- **P2, status-only automatic read.** `recheck()` installs a missing
  authorization rule through Enable, which ADR 0004 reserves for a deliberate
  action. `MacHelperPort.status()` now reads without changing anything, and
  Session's quiet read uses it; **Check again** and **Set up Posato** keep
  `recheck()`. A desktop test shows a rule-repair status issues no Enable, and
  the quiet-read test expects `status`; both failed before the change.
