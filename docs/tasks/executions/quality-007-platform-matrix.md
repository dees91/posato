# Execution: `QUALITY-007`

- **Brief:** [Verify the supported platform matrix](../specifications/quality-007-platform-matrix.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** independent completed-change review pending
- **Branch:** `feature/quality-007-platform-matrix`
- **Updated:** 2026-09-24

## Plan

1. D1-D3 are settled. Before `MACOS-011` merges, prepare the Tart macOS 15 VM and the iOS 18 iPhone with a short maintainer checklist. Then wait for the final 1.1 candidates.
2. On macOS 15:
   - install the recorded 1.1 candidate in the VM;
   - run the core flow with the helper and sync;
   - record each step.
3. On iOS 18: closed by the maintainer's confirmation (see Result); no 1.1 candidate run is required unless the iOS binary changes materially before `RELEASE-003`.
4. Update the availability wording and the website copy to match the evidence.
5. Obtain the completed-change review, run the link checks, and close the record.

## Result

- Opened: worktree, brief, and draft pull request. No runs yet.
- `user-confirmed`, 2026-09-24: the maintainer installed the App Store release 1.0.0 on a private iPhone running iOS 18 and reports that everything works as expected. This closes the iOS 18 half of the matrix (`AC-02`); the run was manual, not driver-recorded, so the availability page cites the maintainer's confirmation rather than a `build/verification/` directory. Only the macOS 15 virtual-machine run remains.

## Verification

- Worktree provisioned from the main checkout's complete ignored `local.properties`. `:posato-control:installDist` and the driver's `--help` pass.

## Blockers and accepted risks

- The macOS 15 run waits for `MACOS-011` to merge (D3) and depends on the maintainer's Tart VM (D1). D2 is satisfied by the maintainer's iOS 18 confirmation; no separate iOS 18 device run is planned. The Tart image is shared with `QUALITY-010`.
