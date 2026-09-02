# `TARGETS-004`: Associate opaque device-local iOS application selections

- **Review tier:** `high-risk`
- **Tier reason:** The change requests Family Controls authorization, handles
  opaque platform tokens, adds an entitlement and provisioning surface,
  introduces a Swift-to-Kotlin trust boundary, and persists sensitive local
  state.
- **Dependencies:** completed `TARGETS-002` and `APPLE-001`; `TARGETS-003`
  supplies the shared contract and screen
- **Integration group:** `PR-IOS-MAPPING`
- **Authority:** `TARGETS-004` in the accepted MVP roadmap, ADR 0003 (iOS
  enforcement boundary), the threat model (`A-03`, `TB-01`, `TB-03`, `T-10`),
  the diagnostics policy, `DESIGN.md`, and the MVP scope statement that opaque
  selections remain local

## Outcome

On a physical iPhone a person can grant Family Controls authorization, pick
applications with the system picker, review and remove the resulting
device-local mappings in the shared Targets screen, and keep them across
restart, while the product states honestly that the selection is local and
not a portable application identifier.

## Boundaries

- Implement `LocalApplicationMappings` for iOS in `shared/src/iosMain` behind
  a reverse-import adapter: a Kotlin protocol implemented in Swift and
  injected through `mainViewController(...)`, mirroring the CryptoKit
  provider. FamilyControls, ManagedSettings, and `ApplicationToken` types stay
  in Swift.
- Mapping identity is the SHA-256 hex of the encoded opaque token, the same
  64-hex `LocalApplicationMappingId` shape as macOS. Token bytes stay in
  iOS-protected storage and never reach common models, diagnostics, or
  synchronization.
- Persist selections in app-private, versioned local storage that is never
  synchronized, shaped so `IOS-001` can hand tokens to the Device Activity
  extension through the App Group without changing their meaning.
- Read the current Family Controls status on the main queue, observe later
  changes, and request individual authorization only from the contextual
  not-determined action. Surface approved, denied, restricted, and unavailable
  states without persisting authorization state, retrying silently, or
  resetting prior selections.
- Preserve prior valid mappings on cancel, failure, capacity (64), or an
  invalid batch; duplicate selections are idempotent; removal is exact.
- Add the Family Controls capability to the iOS target for development
  signing only. Record no team, profile, or device identifier. The
  credential-free Simulator build stays green and reports `Unavailable`.
- Non-goals: enforcement (`IOS-001`), expiry (`IOS-002`), the Device Activity
  extension and final App Group schema (`IOS-001`), custom shields,
  synchronization of selections, and application-name lookup beyond what the
  platform exposes.
- Exclusive write surface while `SYNC-013` runs in parallel:
  `shared/src/iosMain/kotlin/app/posato/di/IosApplicationGraph.kt`,
  `shared/src/iosMain/kotlin/app/posato/MainViewController.kt`,
  `shared/src/iosMain/kotlin/app/posato/feature/targets/**`, `iosApp/**`, and
  `shared/src/commonMain/kotlin/app/posato/feature/targets/**`, plus direct
  macOS mapping callers, the credential-free CI build matrix, and affected
  authorities. Do not touch `feature/sync/**` or `SyncReplica.sq`.
- `.research/blocker` stays read-only; the spike proves only that the picker
  and authorization flow work on a device.

## Acceptance

- `AC-01` — Not-determined, denied, and approved authorization states map to
  truthful screen states. A physical test records the cold-restart status; if
  it returns to not-determined, retained mappings remain visible and the
  contextual request restores approved state without another authentication
  prompt.
- `AC-02` — A picker selection commits atomically and survives restart;
  cancel, failure, capacity, and invalid batches preserve the prior snapshot;
  removal is exact and idempotent.
- `AC-03` — No token bytes, token-derived names, or authorization details
  appear in default strings, logs, synchronization, or diagnostics; every
  new carrier is redacted by default.
- `AC-04` — The credential-free Simulator build and XCTest stay green with
  the selection reported as `Unavailable`; the physical-iPhone flow passes
  manually: authorize, pick, cancel, remove, restart, and revoke
  authorization in Settings.
- `AC-05` — Existing exact-domain, application-policy, macOS mapping,
  synchronization, packaging, and migration behavior remains valid.

## Verification

- Shared JVM and iOS Simulator ViewModel tests with a fake iOS mappings
  adapter; iOS unit tests for identifier derivation and store round-trip
  with a fake token source.
- XCTest for the Swift adapter with injected fakes wherever FamilyControls
  permits; credential-free Simulator build; `./gradlew quality`.
- Physical-iPhone checklist recorded in the execution record with pass or
  blocked per step and no device identifier.
- Private-data scan, `git diff --check`, an independent plan review before
  implementation, and an independent completed-change review with evidence.
  Hosted review follows physical verification and the `AGENTS.md` two-pass
  budget and triage rules when requested.

## Decisions or blockers

- `user-confirmed`: the shared screen shows only the iOS mapping count while
  the system Family Controls picker owns native labels and detailed review; UI
  copy stays outside the data adapter.
- `user-confirmed`: Save atomically replaces the complete selected set, and
  authorization starts only from one contextual shared action.
- `user-confirmed`: TARGETS-004 uses an app-private store. IOS-001 owns the App
  Group migration and the change from complete protection to protection that
  remains available after the first unlock.
- `user-confirmed`: load maps the current authorization status and observes
  changes. The physical cold-restart, revoke, and reinstall experiment decides
  the recorded platform result; persistent not-determined uses the AC-01
  fallback above.
- Blocker: a physical iPhone with the maintainer's development team. Family
  Controls (Development) needs no Apple approval; distribution approval
  remains a recorded later blocker per the `APPLE-001` record.
