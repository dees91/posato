# Execution: `QUALITY-010`

- **Brief:** [Verify every task without the maintainer](../specifications/quality-010-unattended-verification.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review pending; completed-change review per stage
- **Branch:** `feature/quality-010-unattended-verification`
- **Updated:** 2026-09-24

## Plan

1. Stage 1 setup: maintainer checklist for the test Apple Account; agent installs Tart, builds the golden image, and stores credentials in the host Keychain.
2. Stage 1 measurements, each with a recorded go or no-go: CloudKit and Keychain in the VM, VNC control of privileged prompts, XCUITest through Screen Time consent and the picker, network-service switching in the VM.
3. Plan review of Stage 2 on the Stage 1 evidence, then driver and skill extension for both targets.
4. Stage 3 observed blocking recipes on both targets.
5. Completed-change review per stage, `./gradlew quality`, and closeout.

## Result

- Opened: worktree, brief, and draft pull request. No setup or measurement yet.

## Verification

- Worktree provisioned from the main checkout's complete ignored `local.properties`. `:posato-control:installDist` and the driver's `--help` pass.

## Blockers and accepted risks

- The test Apple Account and the dedicated test iPhone are maintainer inputs. The physical Mac stays serialized with `MACOS-011` Stage 2 notarized experiments when they switch network services.
