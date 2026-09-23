# Execution: `QUALITY-007`

- **Brief:** [Verify the supported platform matrix](../specifications/quality-007-platform-matrix.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** independent completed-change review pending
- **Branch:** `feature/quality-007-platform-matrix`
- **Updated:** 2026-09-23

## Plan

1. D1-D3 are settled. Before `MACOS-011` merges, prepare the Tart macOS 15 VM and the iOS 18 iPhone with a short maintainer checklist. Then wait for the final 1.1 candidates.
2. On macOS 15:
   - install the recorded 1.1 candidate in the VM;
   - run the core flow with the helper and sync;
   - record each step.
3. On iOS 18:
   - install the matching candidate on the physical iPhone;
   - run the core flow, including Screen Time, applications, and sync with the Mac;
   - record each step.
4. Update the availability wording and the website copy to match the evidence.
5. Obtain the completed-change review, run the link checks, and close the record.

## Result

- Opened: worktree, brief, and draft pull request. No runs yet.

## Verification

- Worktree provisioned from the main checkout's complete ignored `local.properties`. `:posato-control:installDist` and the driver's `--help` pass.

## Blockers and accepted risks

- The runs wait for `MACOS-011` to merge (D3). The targets depend on the maintainer's Tart VM and iOS 18 iPhone (D1, D2).
