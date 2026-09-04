# Execution: `SYNC-006`

- **Brief:** [Implement the macOS synchronizable-Keychain companion boundary](../specifications/sync-006-macos-keychain.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending
- **Branch:** `feature/sync-006-macos-keychain`
- **Worktree:** `~/Projects/Polyglot/posato-sync-006`
- **Updated:** 2026-09-04

## Plan

1. Obtain the independent plan review and the maintainer's confirmation of
   the module placement, the separate IPC operation table, the
   companion-side binding resolution, and the provisioning-profile manual
   gate.
2. Create the `macosSyncCompanion` Swift package and Gradle module: item
   encoding and checksum validation, exact Keychain queries, binding
   resolution from the CloudKit user record, parent verification, framing
   with limits and allowlist, single-request lifecycle, and Swift tests;
   register its `check` in the aggregate quality gate.
3. Implement the JVM launcher and the `BootstrapKeyPort` and
   `BootstrapAccountPort` adapters in `shared/src/jvmMain`, with
   embedded-companion verification, deadlines, cancellation, timeout
   reconciliation, buffer clearing, and JVM tests over fake companions.
4. Embed, sign, and provision the companion in both packaging modes and
   extend the strict verifier; prove that a wrong identifier or entitlement
   set fails packaging.
5. Run the physical Mac checklist with a synthetic workspace id on the Apple
   Development package, then `./gradlew quality`, the independent
   completed-change review, affected reruns, the synchronization wiki update,
   and the single wiki-log entry in the closeout commit.

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

- A development provisioning profile for `app.posato.macos.sync` with iCloud
  and Keychain Sharing must exist on the maintainer's Mac before the Apple
  Development package gate; it is never tracked.
- Delayed propagation and byte-identical selectors against the iOS adapter
  are `SYNC-009` evidence, not claimed here.

## Final

- **Status:** pending
- **Outcome:** pending
