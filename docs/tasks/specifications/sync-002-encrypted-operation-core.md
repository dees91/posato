# `SYNC-002`: Implement the encrypted operation core

- **Review tier:** `high-risk`
- **Tier reason:** The task implements cryptography, untrusted-input parsing,
  replay protection, durable synchronization state, and a Swift trust boundary.
- **Dependencies:** `SYNC-001`
- **Integration group:** `PR-SYNC-CORE`
- **Authority:** [MVP roadmap revision 3](../mvp-roadmap.md),
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md),
  and maintainer activation on 2026-08-31

## Outcome

The shared application can create, persist, retry, validate, accept, and
deterministically reduce compatible format-1 encrypted operations through the
JDK and iOS CryptoKit provider boundaries.

## Boundaries

- Implement the complete ADR 0006 operation, envelope, author, HLC, validation,
  persistence, retry, and convergence contract in the existing shared module.
- Use the feasibility checkout only as read-only behavioral evidence at revision
  `bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c`; do not copy its format, runtime
  dependencies, runners, identities, signing state, or platform configuration.
- Keep CloudKit, Keychain, workspace bootstrap, mailbox orchestration, UI,
  enforcement, batching, compaction, and portable membership outside this task.
- Provide only an opaque transport-progress mutation that can join the remote
  acceptance transaction; concrete CloudKit cursor and engine state remain with
  `SYNC-010`.
- Preserve existing local target-policy behavior and its independent 1,024-domain
  limit while the synchronized projection supports the format-1 limit of 2,048.
- Persist a caller-driven terminal-expiry marker primitive without deciding when
  a session has expired; that product rule remains owned by `SESSION-001`.

## Acceptance

- `AC-01` — JVM and iOS implementations produce and consume the accepted
  canonical bytes with compatible HKDF, AES-GCM, and Ed25519 behavior.
- `AC-02` — Local writer creation, immutable retry, HLC allocation, checkpoint
  comparison, ambiguous commit reconciliation, and author retirement fail closed
  without reusing an accepted author sequence or replacing prepared bytes.
- `AC-03` — Remote validation, bounded unknown-author staging, gap handling,
  replay detection, and deterministic reduction preserve the last valid state.
- `AC-04` — SQLDelight commits accepted history, pending bundles, staged input,
  HLC state, projection facts, local expiry markers, and opaque transport
  progress atomically. Duplicate success advances progress idempotently, while
  rejection and capacity outcomes do not advance it without exact-refetch proof.
  Existing target-policy data survives migration unchanged.
- `AC-05` — Cross-target vectors, failure matrices, convergence tests, platform
  builds, physical-iPhone evidence, and required independent reviews pass.

## Verification

- Focused codec, reducer, writer, cryptographic provider, and real-database
  contract tests on JVM and iOS Simulator, followed by `./gradlew quality`.
- Credential-free iOS host build, CryptoKit XCTest vectors on Simulator and a
  physical iPhone, dependency/source scans, and scoped diff inspection.

## Decisions or blockers

- The maintainer accepts Kotlin `2.4.20-Beta2` only if SwiftPM import materially
  helps the required CryptoKit boundary. Reverse import on Kotlin `2.4.10` is the
  default and a concrete incompatibility must be recorded before changing it.
- Completion requires a maintainer-connected physical iPhone; no device or
  signing identifier may be recorded in the repository.
