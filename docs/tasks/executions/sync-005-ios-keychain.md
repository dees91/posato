# Execution: `SYNC-005`

- **Brief:** [Implement the iOS synchronizable-Keychain adapter](../specifications/sync-005-ios-keychain.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending
- **Branch:** `feature/sync-005-ios-keychain`
- **Worktree:** `~/Projects/Polyglot/posato-sync-005`
- **Updated:** 2026-09-04

## Plan

1. Obtain the independent plan review and the maintainer's confirmation of
   the four recommended decisions in the brief (binding resolver and iCloud
   entitlement here, account-changed through a test seam, random test
   workspace id with teardown cleanup, manual capability gate).
2. Define the public Kotlin `iosMain` provider interface (`NSData` values and
   platform-neutral status values only) and the internal adapter that maps it
   onto `BootstrapAccountPort` and `BootstrapKeyPort`, with redacted carriers
   and buffer clearing; cover it with `iosTest` contract and redaction tests
   over a fake provider.
3. Implement the Swift provider: account-binding derivation from the current
   CloudKit user record with CryptoKit, the exact format-1 selector, and
   binding preflight and postflight around `SecItemAdd`, `SecItemCopyMatching`,
   and `SecItemDelete` plus the independent absence read; inject the account
   source for tests.
4. Add `keychain-access-groups` and the iCloud/CloudKit entitlement to the
   iOS target and Xcode project; confirm the Simulator build and the device
   build with the ignored development team still sign.
5. Run the Swift tests on the Simulator and the physical iPhone, record the
   entitlement, locked-device, and account-changed checklist, run
   `./gradlew quality`, complete the independent completed-change review,
   rerun affected checks, update the synchronization wiki page, and close
   this record with the single wiki-log entry in the closeout commit.

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

- A physical iPhone with the maintainer's development team in the ignored
  `local.properties` is required for `AC-01` through `AC-03`.
- Cross-device propagation, delay, and real account switching are outside
  this task's evidence and remain with `SYNC-009`.

## Final

- **Status:** pending
- **Outcome:** pending
