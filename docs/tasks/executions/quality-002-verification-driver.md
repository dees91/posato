# Execution: `QUALITY-002`

- **Brief:** [Drive, inspect, and reset the Posato apps from one agent-facing CLI](../specifications/quality-002-verification-driver.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude Code (Fable 5.1)
- **Reviewer:** pending until assigned
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

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew :posato-control:test :posato-control:detekt :posato-control:ktlintCheck :posato-control:swiftFormatCheck` | `pass` | 25 unit tests over synthetic fixtures; no lint finding; no suppression added. |
| `./gradlew quality` | `pending` | Rerun after the last correction. |
| Desktop sequence | `pass` | `doctor`, `launch`, `snapshot`, `screenshot`, `find`, the `add-website` and `remove-website` scenarios, `db query` (row count 1 then 0), `reset --dry-run` listing exactly the two databases, `reset` refused without `--yes` (exit 3), `tap` on a missing element (exit 4 with failure evidence), `db` on the device target (exit 6). |
| Simulator sequence | `pass` | `devices boot`, `build --driver`, `install`, `launch --fresh`, `screenshot`, `snapshot`, both scenarios with the row count confirmed through `db query`, `logs --stream-seconds`, `reset --dry-run --keep-install`, `terminate`. |
| Device sequence | `pass` | `doctor`, `build --driver`, `install`, `launch`, `status`, `terminate`, `launch --capture-logs` with `logs`, `screenshot`, `snapshot`, both scenarios, `find` confirming the removed row, `reset --dry-run`, `reset` refused without `--yes`. |
| Tracked-content scan | `pending` | `git diff --check` and a search of the tracked diff for team identifiers, device identifiers, and personal paths. |

## Blockers and accepted risks

- Driver invocations on the iPhone occasionally failed with xcodebuild exit
  65 before the runner settled; reruns passed, and every run now keeps its
  xcodebuild transcript as an artifact for diagnosis.
- The `Add website` button sits behind the software keyboard on the iPhone
  because the screen does not pad for it (`observed`); recorded as a
  Recommended product follow-up, outside this task.
- The tracked `.run/iOS Device.run.xml` still hard-codes a team identifier;
  rewiring the run configurations to the CLI is a Recommended follow-up.
