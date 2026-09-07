# `SYNC-007`: Exchange bounded encrypted mailbox bundles through iOS private CloudKit

- **Review tier:** `high-risk`
- **Tier reason:** The iOS application writes to the maintainer's private
  CloudKit database in-process, arbitrates the fixed workspace anchor that
  decides whether a second workspace can exist, and gates every mailbox
  access on the local account binding; a defect can create a parallel
  workspace, accept a foreign bundle, or leave records in an account that was
  never opted in. It runs in parallel with `SESSION-002` on the same iPhone.
- **Dependencies:** completed `SYNC-003`; `SYNC-004` supplies the frozen
  `BootstrapCloudPort`; `SYNC-005` supplies the iOS account-binding source
  (`CloudKitAccountSource`), the CloudKit entitlements on the iOS target, and
  the Swift-provider-behind-`iosMain`-adapter pattern; `SYNC-008` supplies the
  record layout, the wire outcomes, and the mailbox result vocabulary this
  task mirrors; `SYNC-002` supplies the bundle limits
- **Integration group:** `PR-IOS-CLOUDKIT`
- **Authority:** `SYNC-007` in MVP roadmap revision 10 (wave P3/W3.4),
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md),
  ADR 0006, ADR 0003, the `SYNC-008` brief (wire layouts and outcomes are the
  contract both platforms return), and the threat model (`A-04`, `A-06`,
  `TB-06`, `T-02`, `T-03`, `T-04`, `T-06`, `T-07`, `R-03`)

## Outcome

Through a Swift CloudKit provider behind a Kotlin `iosMain` adapter, the iOS
application performs the ADR 0007 exact `PosatoSyncV1` zone fetch and save,
fixed anchor read and create-if-absent, create-only immutable bundle save,
bounded change fetch from an opaque cursor, and delete-zone-and-verify-absent,
each between a binding preflight and postflight, returning only the `SYNC-004`
cloud-port outcomes and the `SYNC-008` mailbox outcomes (`BundleSaveResult`,
`ChangeFetchResult` with `ChangePage`, `ZoneDeleteResult`) with byte-identical
results to the macOS companion, and never a raw CloudKit record, token,
account value, or error text.

## Boundaries

- Add a Swift provider in `iosApp/iosApp/` mirroring
  `SynchronizableKeychainProvider`: an injectable CloudKit backend protocol
  (zone fetch and save, record fetch, create-only record save with the
  server-record-unchanged policy, record-zone-changes fetch with the server
  change token, zone delete), the `SYNC-005` account source for the binding,
  and full validation of zone, owner, record type, record name, field set,
  field types, and lengths before any value crosses into Kotlin. Only
  platform-neutral status values and bounded byte arrays cross the boundary.
- Add the Kotlin `iosMain` adapters implementing `BootstrapCloudPort`
  (mirroring `MacOsBootstrapCloudAdapter`) and the mailbox primitives
  `saveBundle`, `fetchChanges`, `deleteZoneAndVerifyAbsent` with the same
  outcomes as `MacOsMailboxAdapter`; the 16-byte bundle identifier, the
  65,536-byte payload bound, the 16,384-byte opaque cursor, one bundle per
  page, and the more-changes flag are identical to `SYNC-008`. Apple types
  stay in Swift; no `expect`/`actual`.
- Run the `SYNC-005` binding preflight and postflight plus a per-operation
  `CKAccountChanged` observation window around every CloudKit call (the app
  is long-lived, so the window opens before the preflight and closes after
  the postflight); a mismatch or account-change signal after the call returns
  no record or cursor and reports `unknown-outcome`; an unavailable,
  restricted, or undetermined preflight returns `retryable` with no CloudKit
  call. Zone absence, anchor absence, and account failure never collapse.
- Explicit record-zone-changes operations with the server change token as the
  opaque cursor; no `CKSyncEngine`, subscriptions, automatic scheduling, retry
  timing, quota policy, or retention (`SYNC-010` and release work decide).
- Deadline 30 s per CloudKit call, mirroring the JVM adapter; cancellation
  and timeout yield `UnknownOutcome` with every copied buffer cleared.
- No DI wiring, UI, **Sync with iCloud** action, coordinator wiring,
  publish/consume loop, status, or production schema deployment; those belong
  to `SYNC-009`, `SYNC-010`, and `RELEASE-001`. No macOS change.
- Exclusive write surface while `SESSION-002` and the `TARGETS-003` picker
  follow-up run in parallel: `iosApp/**` (the new Swift provider and its
  tests, `iosApp/iosApp.xcodeproj/**` for the new files; the CloudKit
  entitlements are already present), `shared/src/iosMain/**/feature/sync/**`,
  `shared/src/iosTest/**/feature/sync/**`, and
  `docs/wiki/topics/cross-device-synchronization.md`; if the decision below
  lifts the mailbox result types, also
  `shared/src/commonMain/**/feature/sync/mailbox/**` and the import updates in
  `shared/src/jvmMain/**/feature/sync/**` and `shared/src/jvmTest/**/feature/sync/**`.
  Shared by rebase: `docs/wiki/log.md`. Do not touch `iosApp/iosApp/iosApp.swift`,
  `MainViewController.kt`, the DI graphs, `feature/session/**`,
  `feature/enforcement/**`, `feature/targets/**`, `macosSyncCompanion/**`,
  `macosHelper/**`, `desktopApp/**`, Gradle files, or the `verify-posato`
  skill and fixtures.

## Acceptance

- `AC-01` — Zone fetch and save return `Found`, `Missing`, `Created`,
  `AlreadyExists`, `Retryable`, `AccountChanged`, or `UnknownOutcome` exactly
  as ADR 0007 defines; a duplicate or lost save is reconciled only by the
  exact fetch, and no other zone is created, listed, or deleted.
- `AC-02` — Anchor read and create-if-absent return the frozen results; an
  existing anchor is `Conflict` whether byte-identical or different, a
  malformed field set, type, length, name, or zone is `IntegrityFailure`, and
  the anchor is never updated or replaced.
- `AC-03` — Bundle save is create-only and idempotent for identical bytes; a
  different existing record, a wrong record name, or a payload over 65,536
  bytes is rejected without a save; change fetch returns each bundle at most
  once per cursor, validates every record before exposing it, and advances no
  cursor when the postflight fails.
- `AC-04` — A binding mismatch before an operation returns `account-changed`
  with no CloudKit call; a mismatch, unavailable value, or account-change
  signal after it returns `unknown-outcome`, exposes no record or cursor, and
  confirms no creation or deletion.
- `AC-05` — An oversized cursor or payload and a malformed anchor or bundle
  are refused before any CloudKit access; a deadline or cancellation yields
  `UnknownOutcome`; the Simulator and an unsigned build report `unavailable`
  without opening a container.
- `AC-06` — On the development-signed iPhone with the maintainer's account
  and a private database without the zone, one controlled device test
  performs zone save and confirm, anchor create and conflict-on-identical
  re-create, bundle save and identical re-save, change fetch of that bundle,
  and different-bytes rejection, then deletes the exact zone and verifies its
  absence, leaving the database as found; it aborts before any write if the
  zone already exists.
- `AC-07` — No workspace identifier, bundle bytes, cursor, binding, record
  identifier, or CloudKit error text appears in logs, `toString()`,
  diagnostics, or fixtures; `./gradlew quality`, the Kotlin `iosTest` suite,
  and the Xcode Simulator suite pass with no new suppression.

## Verification

- Swift tests in `iosAppTests` over the injectable CloudKit backend and
  account source: record encoding and validation, zone and anchor outcome
  mapping including duplicate and lost-response paths, bundle size and name
  gating, change paging and cursor bounds, preflight-unavailable mapping to
  `retryable`, postflight and account-change handling.
- Kotlin `iosTest` contract tests over a fake provider for every port and
  mailbox outcome, cursor round trip, buffer clearing, and the enumerated
  redaction test for the new carriers; the same tests against the JVM types
  if the result types are lifted.
- Physical iPhone device test for `AC-06` with evidence under the ignored
  `build/verification/` and no record bytes or identifiers in tracked files.
  Cross-device exchange with the Mac and delayed delivery stay `SYNC-009` and
  `SYNC-010` evidence.
- `./gradlew quality`, `git diff --check`, suppression and private-data
  scans; independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

- Open (maintainer decision before implementation): where the mailbox result
  types live. `SYNC-008` left `BundleSaveResult`, `ChangeFetchResult`,
  `ChangePage`, `ZoneDeleteResult`, and `MailboxCursor` in `jvmMain` until a
  common port exists. A second implementation now makes them two copies.
  Recommended: move the platform-neutral result types (not the adapter
  interface) into `shared/src/commonMain/**/feature/sync/mailbox/**` in this
  task, with the JVM imports updated mechanically, so `SYNC-010` lifts one
  vocabulary; the alternative mirrors them in `iosMain` and accepts the
  duplicate until `SYNC-010`.
- Decided by authority: wire outcomes, record layout, bounds, one bundle per
  page, explicit change operations without `CKSyncEngine`, and the 30 s
  deadline mirror `SYNC-008`; the anchor create postflight does not re-read
  the anchor, because `SYNC-004` reconciles `UnknownOutcome` and `Conflict`
  through the exact read.
- Physical gates: development-signed iPhone with the `APPLE-002` profiles, the
  maintainer's iCloud account signed in, network, the CloudKit Development
  environment, and a private database whose `PosatoSyncV1` zone is absent
  when the run starts. The `SYNC-008` run already created the record types in
  the Development environment. The iPhone is shared with `SESSION-002`; the
  two device runs are scheduled one after the other.
