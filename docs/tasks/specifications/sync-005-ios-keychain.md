# `SYNC-005`: Implement the iOS synchronizable-Keychain adapter

- **Review tier:** `high-risk`
- **Tier reason:** The adapter handles workspace-key material in the
  synchronizable Keychain, derives the local account binding that gates every
  key operation, and adds Keychain Sharing and iCloud entitlements to the
  signed iOS target; a defect can expose or destroy the sole workspace key or
  act under the wrong Apple Account.
- **Dependencies:** completed `SYNC-003`; `SYNC-004` supplies the frozen
  `BootstrapAccountPort` and `BootstrapKeyPort` contract and the item codec
- **Integration group:** `PR-IOS-KEYCHAIN`
- **Authority:** `SYNC-005` in MVP roadmap revision 7,
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md),
  ADR 0006, ADR 0002, the `APPLE-001` record (public access-group suffix
  `app.posato.sync`), and the threat model (`A-05`, `TB-02`, `TB-06`,
  `TB-08`, `T-04`, `R-05`)

## Outcome

On a physical iPhone the iOS application performs ADR 0007 exact read,
create-if-absent, and delete-and-verify-absent on the format-1 synchronizable
workspace-key item behind the current-account binding preflight and
postflight, returning only the `SYNC-004` port outcomes, never a replaced,
partially deleted, or account-crossed item.

## Boundaries

- Add a Swift provider in `iosApp` that resolves the 32-byte account binding
  from the current CloudKit user record with CryptoKit SHA-256, and that runs
  each `SecItem` operation with the exact selector (generic password, service
  `app.posato.sync.workspace-key.v1`, canonical lowercase UUID account,
  access group with public suffix `app.posato.sync`, synchronizable,
  `AfterFirstUnlock`) between a binding preflight and postflight. Only
  platform-neutral status values, an 84-byte value, or nothing cross into
  Kotlin; the raw record name, the resolved team prefix, `OSStatus` values,
  and error text stay in Swift.
- Add the Kotlin `iosMain` adapter that implements `BootstrapAccountPort` and
  `BootstrapKeyPort` over that provider, mirroring `IosSyncCryptoProvider`,
  maps a byte-identical duplicate to `AlreadyExists`, a different duplicate,
  a wrong length, or a wrong selector to `IntegrityFailure`, an unavailable or
  different postflight to `UnknownOutcome`, and clears every copied buffer.
- Forbidden: `SecItemUpdate`, persistent references, any query without the
  complete selector, deletion by anything but the exact account, logging or
  diagnostics carrying bytes, identifiers, or raw errors.
- Add `keychain-access-groups` and the iCloud/CloudKit entitlement for
  `iCloud.app.posato.sync` to the iOS target only; no CloudKit zone, anchor,
  or mailbox operation (`SYNC-007`), no DI wiring, UI, or **Sync with iCloud**
  action (`SYNC-009`), and no macOS work (`SYNC-006`).
- The `SYNC-004` ports are frozen. If one is insufficient, record a blocker
  and stop instead of editing `commonMain`.
- `.research/blocker` `KeychainStore.swift` is read-only behavioral
  evidence; re-derive the shape and tests here.
- Exclusive write surface while `SESSION-001` and `SYNC-006` run in
  parallel: `iosApp/**` (the new Swift provider, `PosatoDebug.entitlements`,
  the Xcode project capability changes, `iosAppTests/**`),
  `shared/src/iosMain/**/feature/sync/**`, and
  `shared/src/iosTest/**/feature/sync/**`. Shared with `SYNC-006` and
  resolved by rebase: `docs/wiki/topics/cross-device-synchronization.md` and
  `docs/wiki/log.md`. Do not touch `shared/src/commonMain/**`,
  `MainViewController.kt`, `shared/src/iosMain/kotlin/app/posato/di/**`,
  `feature/targets/**`, `feature/session/**`, `desktopApp/**`,
  `macosSyncCompanion/**`, or any Gradle file.

## Acceptance

- `AC-01` — On the device, create-if-absent, exact read, and
  delete-and-verify-absent under one random test workspace id return
  `Created`, `Found` with byte-identical 84-byte value, `AlreadyExists` for
  the same bytes, `IntegrityFailure` for different bytes without replacement,
  and `DeletedAndAbsent` confirmed by an independent exact read; the stored
  item carries exactly the format-1 attributes.
- `AC-02` — A preflight that is unavailable, restricted, or bound to a
  different account invokes no `SecItem` query; a postflight mismatch or an
  account-change signal during the operation returns `UnknownOutcome`,
  discards read bytes, and confirms no deletion; the same selector reconciles
  after the original binding returns.
- `AC-03` — The signed Debug build accesses the shared access group through
  its entitlements; a query outside that group or with an incomplete selector
  fails closed, and no other Keychain item is touched or enumerated.
- `AC-04` — No workspace-key bytes, account text, binding, or raw status
  appear in any `toString()`, log, diagnostic, or test artifact; copied
  buffers are cleared; the Kotlin `iosTest` suite and `./gradlew quality`
  pass on the iOS Simulator.

## Verification

- Swift unit tests in `iosAppTests` for selector construction, 84-byte
  length gating, outcome mapping, and binding preflight/postflight over an
  injectable account source; the same tests run on the physical iPhone
  against the real Keychain with a random test workspace id and prove
  cleanup in teardown.
- Kotlin `iosTest` contract tests with a fake provider on the Simulator,
  plus the enumerated redaction test for the new carriers.
- Physical-iPhone checklist recorded with pass or blocked per step: signed
  entitlements (`codesign -d --entitlements`), locked-device behavior after
  first unlock, and account-changed handling through the test seam; no device
  or account value in tracked files.
- `./gradlew quality`, `git diff --check`, suppression and private-data
  scans, an independent plan review before implementation, and an
  independent completed-change review with evidence.

## Decisions or blockers

- Recommended: the binding resolver lives in `SYNC-005`, so the iCloud/
  CloudKit entitlement for `iCloud.app.posato.sync` is added now for the
  user-record fetch; `SYNC-007` reuses the resolver and adds zone and mailbox
  work.
- Recommended: `account-changed` is exercised on one device through an
  injectable account source in the Swift provider, not by signing the
  maintainer's iPhone out of iCloud; real account switching and delayed
  cross-device propagation are `SYNC-009` evidence.
- Recommended: device tests use a fresh random UUIDv4 test workspace id per
  run under the production service and access group, delete it in teardown,
  and verify absence; an interrupted run can leave one inert 84-byte test
  item that the next run's exact delete removes.
- Manual gate: if automatic signing does not add Keychain Sharing and iCloud
  to the `app.posato.ios` App ID and regenerate the development profile, the
  maintainer adds the capabilities in Xcode; record pass or blocked without
  private values.
- Not provable with one iPhone: propagation delay, cross-device identical
  bytes, and before-first-unlock behavior beyond a reboot check; these stay
  with `SYNC-009` and the accepted `R-03` limit.
