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

- **Verdict:** `pass with required revisions incorporated`
- **Critical or Required findings:** three Required findings, all resolved
  in the approved plan revision: (1) preflight maps to `Retryable` when
  unavailable or restricted, `AccountChanged` on a different account, and
  `UnknownOutcome` on postflight mismatch, since the key ports expose no
  `Unavailable` or `Restricted` variant; (2) the Swift provider builds the
  full access-group value from `$(DEVELOPMENT_TEAM)` injected into
  `Info.plist` via a build setting, read at runtime, with hardcoding and
  app-group derivation explicitly rejected; (3) the Swift provider takes an
  injected `SecItem` backend seam, the Simulator exercises mapping only over
  the fake backend with real-Keychain cases skipped, and `AC-01`
  real-Keychain proof is iPhone-only.
- **Resolution:** the revised plan carries all three fixes plus the
  additional notes (owned-buffer `R-05` boundary, PoC read from the main
  checkout, device preconditions and propagation note, UDID hygiene); the
  maintainer approved the revised plan and confirmed the four recommended
  brief decisions, device availability, and `SYNC-007` resolver reuse.

## Result

- The Kotlin `iosMain` provider interface, the `IosBootstrapKeychainAdapter`,
  and 15 `iosTest` contract and redaction tests are implemented and green.
- The Swift `SynchronizableKeychainProvider` with injectable account and
  `SecItem` seams, plus 19 `iosAppTests` (18 Simulator-safe, one
  device-only), is implemented and green on the Simulator and the physical
  iPhone.
- The iOS target carries `keychain-access-groups`, the iCloud container and
  CloudKit service entitlements, and the `PosatoDevelopmentTeam` Info.plist
  key expanded from `$(DEVELOPMENT_TEAM)`; automatic signing provisioned the
  profile with no manual gate needed.
- `commonMain` is untouched; no DI wiring, UI, CloudKit, or macOS work was
  added.

## Completed-change review

- **Verdict:** `pass after corrections` (second pass; the first pass was
  requested but its delivery truncated twice, so a concise re-review is the
  authoritative pass)
- **Critical or Required findings:** three Required findings. (1) Fixed: the
  account-change flag is now a lock-guarded box instead of a captured
  variable, so a signal posted from a notification thread cannot be missed.
  (2) Fixed: the found-value carrier is now constructed only after a passing
  postflight with no observed signal, so a mismatch drops no unzeroed copy;
  owned read bytes are still cleared on every exit. (3) Declined with
  evidence: backend-error paths return `Retryable` without an extra
  postflight; the frozen `SYNC-004` `BootstrapItemCheck` maps key-port
  `Retryable` and `UnknownOutcome` to the same retryable outcome, every retry
  re-runs the binding preflight, and the change signal itself still yields
  `UnknownOutcome` through the observation window, so no unsafe behavior
  follows.
- **Resolution:** both fixes are implemented; the Simulator suite (18 tests)
  and the physical-iPhone suite (19 tests, including the real-Keychain
  cycle) were rerun green after the last correction, as were `./gradlew
  quality` and the Kotlin `iosTest` suite.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Kotlin `iosTest` on iOS Simulator (`:shared:iosSimulatorArm64Test`) | pass | 15 adapter tests green on a forced rerun |
| `./gradlew quality`, `git diff --check`, suppression and private-data scans | pass | clean; no `Suppress` tokens; write surface matches the brief |
| Swift tests on iOS Simulator (`xcodebuild test`, iPhone 17) | pass | `TEST SUCCEEDED`; 18 `SynchronizableKeychainProviderTests` green |
| Swift tests on the physical iPhone (signed Debug, random workspace id) | pass | 19/19 green incl. create, exact read, duplicate, conflict without replacement, delete-and-verify-absent, and teardown cleanup |
| Signed entitlements (`codesign -d --entitlements`) | pass | shared group with the team prefix, `iCloud.app.posato.sync` container, CloudKit service |
| Credential-free CI-style Simulator and device builds | pass | unsigned `BUILD SUCCEEDED` for `iphonesimulator` and `iphoneos` SDKs |

## Blockers and accepted risks

- Physical-iPhone evidence is done; no open blocker remains.
  The device was unlocked with its passcode set and signed into iCloud, which
  the available binding outcome proves per run.
- Device tests create one real 84-byte synchronizable item in the
  maintainer's iCloud Keychain per run and delete it in teardown; an
  interrupted run can leave one inert item, which is accepted and never
  enumerated or swept.
- Clearing boundary (`R-05`): owned Kotlin `ByteArray` copies and the owned
  Swift read buffer are cleared; immutable bridging copies are released, not
  zeroed, which is the accepted limit with no erasure claim.
- Follow-up for `SYNC-009`: the Swift provider bridges async CloudKit calls
  with a bounded semaphore, so callers must invoke it off the main thread;
  production provider construction (account source, backend, bundle team key)
  and the coordinator wiring stay with `SYNC-009`.
- Cross-device propagation, delay, and real account switching are outside
  this task's evidence and remain with `SYNC-009`.

## Final

- **Status:** `done`
- **Outcome:** the iOS synchronizable-Keychain adapter meets `AC-01`
  through `AC-04` with physical-iPhone evidence and independent review
  complete
