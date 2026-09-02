# Execution: `QUALITY-002`

- **Brief:** [Drive, inspect, and reset the Posato apps from one agent-facing CLI](../specifications/quality-002-verification-driver.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude Code (Fable 5.1)
- **Reviewer:** pending until assigned
- **Branch:** `feature/quality-002-verification-driver`
- **Updated:** 2026-09-02

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

- Pending.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- None known before implementation.
