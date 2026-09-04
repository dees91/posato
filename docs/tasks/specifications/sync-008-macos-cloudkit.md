# `SYNC-008`: Exchange bounded encrypted bundles through the macOS CloudKit boundary

- **Review tier:** `high-risk`
- **Tier reason:** The task writes to the maintainer's private CloudKit
  database, arbitrates the fixed workspace anchor that decides whether a
  second workspace can exist, extends the signed companion IPC (`TB-09`)
  with new operations, and gates every mailbox access on the local account
  binding; a defect can create a parallel workspace, accept a foreign bundle,
  or leave records in an account that was never opted in.
- **Dependencies:** completed `SYNC-003`; `SYNC-004` supplies the frozen
  `BootstrapCloudPort`; `SYNC-006` supplies the companion, its protocol with
  reserved operation codes 5–15, the entitlement guard, the deadline budget,
  and the account-binding source; `SYNC-002` supplies the bundle limits
- **Integration group:** `PR-MAC-CLOUDKIT`
- **Authority:** `SYNC-008` in MVP roadmap revision 8,
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md),
  ADR 0006, ADR 0003, and the threat model (`A-04`, `A-06`, `TB-06`, `TB-09`,
  `T-02`, `T-03`, `T-04`, `T-06`, `T-07`, `R-03`)

## Outcome

Through the verified `app.posato.macos.sync` companion, the JVM performs the
ADR 0007 exact `PosatoSyncV1` zone fetch and save, fixed anchor read and
create-if-absent, immutable bundle record save, and bounded change fetch from
an opaque cursor, each between a binding preflight and postflight, returning
only the `SYNC-004` cloud-port outcomes or an equally platform-neutral mailbox
outcome and never a raw CloudKit value, record, token, or error text.

## Boundaries

- Extend the companion with allowlisted operations `5 fetchZone`,
  `6 saveZone`, `7 readAnchor`, `8 createAnchor`, `9 saveBundle`,
  `10 fetchChanges`, and `11 deleteZoneAndVerifyAbsent` under capability bit
  `2` (`cloudkit`); codes 12–15 stay reserved and rejected. The protocol major
  stays `1`: the header, echo of magic, major, operation, and request
  identity, the deadline budget, and the entitlement guard are unchanged.
- Implement the ADR 0007 records exactly: zone `PosatoSyncV1` with the
  current-user owner in the private database of `iCloud.app.posato.sync`;
  anchor `PosatoWorkspaceV1` / `workspace` with three 16-byte `BYTES`
  fields; bundle `PosatoEncryptedBundleV1` named by the canonical lowercase
  UUID of the 16-byte bundle identifier with one `payload` field of at most
  65,536 bytes; create-only with the server-record-unchanged save policy;
  byte-identical existing record is `identical`, a different one is an
  integrity conflict; every zone, owner, type, name, field set, type, and
  length is validated before a value crosses the pipe.
- Run the `SYNC-006` binding preflight and postflight plus the
  `CKAccountChanged` observation window around every CloudKit call; a
  mismatch after the call discards fetched records and cursor, confirms no
  creation, and returns `unknown-outcome`. Zone absence, anchor absence, and
  account failure are separate outcomes and never collapse into each other.
- Bounded change fetch uses explicit record-zone-changes operations with the
  server change token as the opaque cursor (at most 16,384 bytes, never
  interpreted by the JVM). One response carries at most one bundle plus a
  more-changes flag; a batch is a sequence of requests. No `CKSyncEngine`
  runs inside the one-shot companion; automatic scheduling, subscriptions,
  retry timing, quota policy, and retention remain with `SYNC-010` and the
  release tasks, which decide whether an engine is introduced at all.
- Implement in `shared/src/jvmMain` the `BootstrapCloudPort` over the
  companion and a JVM-internal mailbox primitive interface (save bundle,
  fetch changes, delete zone) with platform-neutral types, sized so
  `SYNC-010` can lift it into `commonMain` unchanged; `commonMain` and the
  `SYNC-004` ports stay frozen, and an insufficiency is a recorded blocker.
- No DI wiring, UI, **Sync with iCloud** action, bootstrap coordinator
  wiring, publish/consume loop, status, retry, explicit workspace removal
  flow, iOS work, or production schema deployment; those belong to
  `SYNC-007`, `SYNC-009`, `SYNC-010`, and `RELEASE-001`.
- Exclusive write surface while `MACOS-004` and `IOS-001` run in parallel:
  `macosSyncCompanion/**`; `shared/src/jvmMain/**/feature/sync/**` and
  `shared/src/jvmTest/**/feature/sync/**`; `desktopApp/build.gradle.kts` and
  `desktopApp/Config/**` only if the companion entitlements or the packaging
  verifier change; `docs/wiki/topics/cross-device-synchronization.md`.
  Shared by rebase: `docs/wiki/log.md`. Do not touch `shared/src/commonMain/**`,
  the DI graphs, `desktopApp/src/**`, `macosHelper/**`, `iosApp/**`,
  `feature/session/**`, `feature/targets/**`, `feature/enforcement/**`,
  `settings.gradle.kts`, `gradle/libs.versions.toml`, or the verify-posato
  skill and fixtures.

## Acceptance

- `AC-01` — Zone fetch and save return `Found`, `Missing`, `Created`,
  `AlreadyExists`, `Retryable`, `AccountChanged`, or `UnknownOutcome`
  exactly as ADR 0007 defines, a save duplicate or lost response is reconciled
  only by the exact fetch, and no other zone is created, listed, or deleted.
- `AC-02` — Anchor read and create-if-absent return the frozen results; a
  byte-identical existing anchor is `AlreadyExists`, a different one is
  `Conflict`, a malformed field set, type, length, name, or zone is
  `IntegrityFailure`, and the anchor is never updated or replaced.
- `AC-03` — Bundle save is create-only and idempotent for identical bytes;
  a different existing record, a wrong record name, or a payload over 65,536
  bytes is rejected without a save; change fetch returns each bundle at most
  once per cursor, validates every record before exposing it, and advances no
  cursor when the postflight fails.
- `AC-04` — A binding mismatch before an operation returns `account-changed`
  without any CloudKit call; a mismatch, unavailable value, or account-change
  event after it returns `unknown-outcome`, exposes no record or cursor, and
  confirms no creation or deletion.
- `AC-05` — The companion rejects operations 12–15, a missing `cloudkit`
  capability bit, an oversized cursor or payload, and a malformed anchor or
  bundle frame with no CloudKit access; a deadline, cancel, or lost response
  yields `UnknownOutcome` with no lingering process; the ad-hoc package still
  launches and reports `unavailable`.
- `AC-06` — On the supported Mac with the Apple Development package, the
  maintainer's account, and a private database without the zone, one
  controlled run performs zone save and confirm, anchor create and identical
  re-create, bundle save and identical re-save, change fetch of that bundle,
  and different-bytes rejection, then deletes the exact zone and verifies its
  absence, leaving the database as found.
- `AC-07` — No workspace identifier, bundle bytes, cursor, binding, record
  ID, or CloudKit error text appears in arguments, environment, logs,
  `toString()`, diagnostics, or fixtures; `./gradlew quality` passes,
  including the companion format, lint, and Swift tests.

## Verification

- Swift tests over an injectable CloudKit seam for record encoding and
  validation, zone and anchor outcome mapping including the duplicate and
  lost-response paths, bundle size and name gating, change paging, cursor
  bounds, capability and operation allowlist, and preflight, postflight, and
  account-change handling.
- JVM tests over the scripted fake companion and an in-process fake for
  every port and mailbox outcome, cursor round trip, timeout, cancellation,
  malformed and wrong-identity responses, and buffer clearing; the
  enumerated redaction test for new carriers; `./gradlew quality`;
  `git diff --check`; suppression and private-data scans.
- Physical Mac checklist for `AC-06` with evidence under
  `build/verification/` and no record bytes or identifiers in tracked files;
  the run aborts before any write if the zone already exists. Cross-device
  exchange with iOS and delayed delivery are `SYNC-009` and `SYNC-010`
  evidence.
- Independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

- Recommended: raise the companion frame payload limit from 65,536 to
  65,584 bytes on both sides so a full-size bundle travels with its 32-byte
  binding and 16-byte identifier; the limit is not part of the wire format,
  so the major version stays 1.
- Recommended: no `CKSyncEngine` in the one-shot companion; explicit
  record-zone-changes operations with the server change token as the opaque
  cursor satisfy the ADR 0007 preflight, postflight, and invalidation rules
  without persisting engine state across processes. `SYNC-010` decides
  whether the desktop ever needs an engine.
- Recommended: the mailbox primitive interface lives in `jvmMain` until
  `SYNC-010` defines the common port; the wire layouts in this brief are the
  contract `SYNC-007` mirrors so both platforms return identical outcomes.
- Recommended: `deleteZoneAndVerifyAbsent` ships now because the physical
  proof must leave the maintainer's database as found and `SYNC-009` needs
  the same primitive for explicit removal; it deletes only the exact zone
  under the established binding and verifies absence independently.
- Recommended: the JVM cloud adapter uses a 30-second default deadline,
  longer than the 15-second Keychain default, since each CloudKit call is a
  network round trip within the same budget.
- Manual gate: the Apple Development package with the untracked companion
  profile, the maintainer's iCloud account signed in on the Mac, network,
  and a private database whose `PosatoSyncV1` zone is absent; Apple
  Development builds use the CloudKit Development environment, so the
  schema is created on first save and production deployment stays with
  `RELEASE-001`.
- Decided (`user-confirmed`, 2026-09-04): the anchor create postflight does
  not re-read the anchor. `SYNC-004` already reconciles `UnknownOutcome` and
  `Conflict` through the exact read, and a second read inside the companion
  would hide the race from the coordinator that owns it.
