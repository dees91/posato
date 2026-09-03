# Execution: `QUALITY-002`

- **Brief:** [Drive, inspect, and reset the Posato apps from one agent-facing CLI](../specifications/quality-002-verification-driver.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude Code (Fable 5.1)
- **Reviewer:** Claude Code (Fable 5.1), independent review agent
- **Branch:** `feature/quality-002-verification-driver`
- **Updated:** 2026-09-03

## Plan

1. Verify the risky assumptions on the supported Mac, the Simulator, and the
   connected iPhone before writing the tool: Compose Desktop exposes its
   controls through macOS accessibility; XCUITest finds Compose controls by
   label; the driver signs with the development team without an entitlement;
   `xcodebuild` passes `TEST_RUNNER_` environment into the driver; and
   `xcresulttool` exports attachments on the installed Xcode.
2. Add the `:posato-control` module with the JSON envelope, exit codes, local
   configuration, run state, artifacts, and the lifecycle commands for all
   three targets, wiring only its static checks and unit tests into the
   aggregate gate.
3. Add the macOS accessibility bridge and the desktop interaction commands.
4. Add the iOS driver project, the scenario runner, and the Simulator and
   device interaction commands.
5. Document the tool for agents, run the per-target verification sequences,
   obtain one independent completed-change review, and close this record.

## Result

- Added `tools/posato-control` (Kotlin CLI, Swift accessibility bridge, and
  the `PosatoDriver` Xcode project) with the documented command set, one
  scenario format shared by every target, and evidence under the ignored
  `build/verification/` directory. The aggregate gate gained only the
  module's ktlint, Detekt, unit tests, and Swift format check.
- Spike outcomes (`observed`): Compose Desktop 1.10.3 exposes buttons with
  their label as the accessibility description and a press action, text
  fields accept focus but reject a direct value write, and `testTag` is not
  exposed; typing goes through keyboard events after an accessibility press.
  XCUITest resolves Compose controls by label on the Simulator and the
  iPhone; `TEST_RUNNER_` variables reach the runner; the driver signs with the
  wildcard development profile without an entitlement; `xcresulttool`
  appends `_<n>_<uuid>` to attachment names, which the CLI strips; a
  `devicectl --console` attachment owns the process, so ending it ends the
  application; `simctl` cannot synthesize input.
- Deviation from the plan: iOS list rows expose no container element and
  Compose drops the accessibility label of content hidden behind the software
  keyboard, so queries gained a `near` relation (closest match to an anchor)
  and the canonical scenario submits the domain with the Return key instead
  of tapping the button that the keyboard covers.
- Product `testTag`s stay a follow-up after `TARGETS-004` merges, as the
  brief records; the CLI already accepts `id` queries.

## Completed-change review

- **Verdict:** `approve` after corrections
- **Critical or Required findings:** three Required findings on the first
  pass: `terminate`, `launch`, `reset`, and `cleanup` destroyed whatever
  process held a recorded pid without verifying its identity; the documented
  `TCC_ACCESSIBILITY_DENIED` path was never produced because the bridge
  swallowed accessibility errors; and the record lacked a performed
  `reset --yes` and several AC-01 commands per target.
- **Resolution:** tracked processes now carry their start instant and the
  desktop executable path, and every liveness or termination check verifies
  both (a decoy process holding a recorded pid survived `terminate`); the
  bridge refuses `snapshot`, `press`, `type`, and `key` without accessibility
  trust and `doctor` names the host to grant; the missing evidence was
  collected on all three targets (rows below). Recommended findings were
  also corrected: `placeholder` in the snapshot model, `--run-id` reuse on
  iOS, raw `--human` text output, an envelope for every failure type, a
  device `status` that queries the device and an idempotent device
  `terminate`, `launch.fresh` honored on iOS, transcript redaction of
  configured identities, and an atomic state-file write.
- **Advisory findings:** none left open. The reviewer verified statically
  that the desktop reset scope is confined to the two databases and their
  sidecars, that the Kotlin and Swift contracts agree, and that no identity,
  identifier, or personal path entered tracked files.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew :posato-control:test :posato-control:detekt :posato-control:ktlintCheck :posato-control:swiftFormatCheck` | `pass` | 25 unit tests over synthetic fixtures after the review corrections; no lint finding; no suppression added. |
| `./gradlew quality` | `pass` | 126 actionable tasks passed on the worktree after the last correction, including the module's checks and the unchanged suppression allowlist. |
| Desktop sequence | `pass` | `doctor`, `build`, `install`, `launch`, `status`, `logs`, `snapshot`, `screenshot`, `find`, the `add-website` and `remove-website` scenarios, `db query` (row count 1 then 0), `reset --dry-run` listing exactly the two databases, `reset` refused without `--yes` (exit 3), `reset --yes` leaving an empty database (count 0) with the deleted files backed up in the run directory, `terminate`, `tap` on a missing element (exit 4 with failure evidence), `db` on the device target (exit 6), `snapshot` with accessibility trust withheld (`TCC_ACCESSIBILITY_DENIED`, exit 3) and `doctor` naming the host to grant, and `terminate` against a recorded pid that belonged to a decoy process (the decoy survived). |
| Simulator sequence | `pass` | `devices boot`, `doctor`, `build --driver`, `install`, `status`, `launch --fresh`, `screenshot`, `snapshot`, `find`, `tap`, `wait`, both scenarios with the row count confirmed through `db query`, `logs` and `logs --stream-seconds`, `reset --dry-run --keep-install`, `reset --yes` (uninstall, `status.installed` false, reinstall, empty state on relaunch), a scenario with `launch.fresh`, `--run-id` reuse, `terminate`. |
| Device sequence | `pass` | `doctor`, `build --driver`, `install`, `launch`, `status`, `terminate` (also idempotent on a second call, and `status.running` false afterwards), `launch --capture-logs` with `logs`, `screenshot`, `snapshot`, both scenarios, `find` confirming the removed row, `reset --dry-run`, `reset` refused without `--yes`, `reset --yes` (uninstall, reinstall, empty state on relaunch). |
| Tracked-content scan | `pass` | `git diff --check` passed and a search of the tracked diff found no team identifier, device identifier, signing identity, or personal path; fixtures use synthetic identifiers. |

## Blockers and accepted risks

- The desktop interaction evidence was collected from a terminal host that
  held the Accessibility and Screen Recording grants. The closeout session
  ran under a host without them, where `doctor` reported both as missing and
  `snapshot` returned `TCC_ACCESSIBILITY_DENIED`; granting them to the agent's
  host application is a one-time manual step.
- Driver invocations on the iPhone occasionally failed with xcodebuild exit
  65 before the runner settled; reruns passed, and every run now keeps its
  xcodebuild transcript as an artifact for diagnosis.
- The `Add website` button sits behind the software keyboard on the iPhone
  because the screen does not pad for it (`observed`); recorded as a
  Recommended product follow-up, outside this task.
- The ignored local `.run/iOS Device.run.xml` still hard-codes a team
  identifier; rewiring the run configurations to the CLI is a Recommended
  follow-up.

## Final

- **Status:** `done`
- **Outcome:** met. AC-01 through AC-05 are evidenced above; the driver runs
  on the desktop, the Simulator, and the connected iPhone with only ignored
  local configuration and evidence.
