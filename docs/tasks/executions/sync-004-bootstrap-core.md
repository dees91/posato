# Execution: `SYNC-004`

- **Brief:** [Implement the one-workspace bootstrap coordinator](../specifications/sync-004-bootstrap-core.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending until assigned
- **Branch:** `feature/sync-004-bootstrap-core`
- **Updated:** 2026-09-03

## Plan

1. Obtain the independent plan review and the maintainer's confirmation that
   explicit workspace removal stays with `SYNC-009`.
2. Define the platform-neutral ports and typed outcomes for the exact zone,
   anchor, workspace-key item, account binding, and random bytes, with
   redacted carriers for the identifiers and binding.
3. Add the SQLDelight migration and store for candidate and established
   bootstrap state with the opaque binding, committed atomically and read back
   fail-closed.
4. Implement the serialized coordinator as the ADR 0007 ten-step protocol with
   binding preflight and postflight around every provider access, exact
   reconciliation of unknown outcomes, and losing-candidate cleanup.
5. Write deterministic fakes and the contract-test matrix from the ADR 0007
   evidence list, including two coordinators over one shared fake provider,
   crash-and-resume at each persistence boundary, and account change around
   indeterminate saves.
6. Run `./gradlew quality` on JVM and iOS Simulator, complete the independent
   completed-change review, rerun affected checks, update the synchronization
   wiki page, and close this record with the single wiki-log entry in the
   closeout commit.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- pending

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- Hosted CI stays manual-only through 2026-09-05; a fresh local
  `./gradlew quality` after the last correction is the merge gate.
- Physical CloudKit and Keychain behavior is not claimed here; it belongs to
  `SYNC-005` through `SYNC-009`.

## Final

- **Status:** `active`
- **Outcome:** pending
