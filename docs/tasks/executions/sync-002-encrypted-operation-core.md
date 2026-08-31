# Execution: `SYNC-002`

- **Brief:** [Implement the encrypted operation core](../specifications/sync-002-encrypted-operation-core.md)
- **Status:** `complete`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent Codex agent `/root/sync002_plan_review`
- **Branch:** `feature/sync-002-encrypted-operation-core`
- **Updated:** 2026-08-31

## Plan

1. Reimplement the format-1 codec, reducer, typed outcomes, and serialized writer
   state machine in common Kotlin with injected time, identity, cryptography,
   and persistence boundaries.
2. Add a SQLDelight migration and store for immutable accepted, staged, and
   pending bytes plus durable HLC, caller-driven local expiry facts, and an
   opaque transport-progress mutation committed with remote acceptance. Preserve
   existing target-policy data and leave concrete CloudKit cursor state deferred.
3. Implement JDK 21 JCA/JCE and injected Swift CryptoKit provider leaves, then
   verify their shared vectors without adding a runtime cryptography dependency.
4. Exercise failure, rollback, replay, reordering, capacity, convergence,
   transport-progress atomicity, and cross-target behavior; complete required
   reviews and platform verification.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** The initial plan omitted the ADR 0006
  atomic coupling between remote acceptance or staging and opaque transport
  progress.
- **Resolution:** The brief and implementation plan now require an opaque
  progress mutation in the same SQLDelight transaction, including duplicate,
  rejection, and deferred-capacity semantics, while leaving CloudKit cursor and
  engine-state ownership with `SYNC-010`. Independent re-review approved the
  corrected plan with no unresolved Critical or Required findings.

## Result

- Reimplemented the format-1 operation and encrypted-envelope codecs, typed
  validation outcomes, deterministic reducer, serialized writer, durable HLC,
  bounded unknown-author staging, retry state, and caller-driven terminal
  expiry facts in the existing shared module.
- Added SQLDelight schema version 3 for accepted, pending, and staged bundles,
  replica checkpoints, expiry facts, and opaque transport progress. Existing
  version 1 and version 2 target-policy migration contracts remain covered.
- Added JDK JCA/JCE and injected Swift CryptoKit providers using platform
  cryptography only. Reverse import compiled on Kotlin `2.4.10`; SwiftPM import
  and Kotlin `2.4.20-Beta2` were not needed.
- Added a credential-free iOS XCTest target for CryptoKit HMAC, AES-GCM, and
  Ed25519 vectors. No feasibility implementation or machine-specific state was
  copied into product sources.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** No Critical findings. The reviewer required
  corrections for late author registration, terminal author-sequence reuse,
  exact ambiguous commit reconciliation, authenticated reopen validation,
  canonical projection digest, the complete CryptoKit format boundary, capacity
  progress semantics, remote terminal-HLC handling, terminal-expiry ambiguity,
  and the normative writer, SQLDelight, failure, and convergence test matrix.
- **Resolution:** All Required findings were resolved and independently
  re-reviewed. The final review found no remaining actionable Critical or
  Required findings.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Baseline `./gradlew quality` | `pass` | Existing main baseline passed before the task worktree was created. |
| Baseline credential-free iOS host build | `pass` | Existing `iosApp` scheme built for the generic iOS Simulator with signing disabled. |
| Focused JVM codec, JCA, migration, and writer tests | `pass` | Canonical round trips, RFC 5869, Ed25519, format-1 envelope, schema upgrades, first-author batch, and terminal HLC exhaustion passed. |
| Final `./gradlew quality` | `pass` | JVM and iOS Simulator tests, SQLDelight migration verification, static analysis without SYNC-002 suppressions, formatting, and all existing targets passed after the completed-change corrections. |
| Credential-free iOS Simulator host build | `pass` | The `iosApp` scheme and injected CryptoKit provider compiled with signing disabled. |
| CryptoKit XCTest on iOS Simulator | `pass` | FIPS SHA-256, RFC 4231 HMAC-SHA256, NIST AES-GCM including altered-tag rejection, Ed25519 lifecycle and RFC 8032 cross-verification, and decoding the fixed JCA-produced format-1 golden bundle through Kotlin and CryptoKit passed. |
| CryptoKit XCTest on physical iPhone | `pass` | All six Simulator-tested CryptoKit cases passed on the connected physical iPhone with the maintainer-provided Development Team supplied only as a local build parameter. No personal signing value was added to the repository. |

## Blockers and accepted risks

- None. Concrete CloudKit transport, Keychain delivery, and sync-engine state
  remain explicitly deferred to their named tasks.
