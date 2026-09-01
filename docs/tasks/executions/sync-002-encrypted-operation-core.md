# Execution: `SYNC-002`

- **Brief:** [Implement the encrypted operation core](../specifications/sync-002-encrypted-operation-core.md)
- **Status:** `complete`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent Codex agent `/root/sync002_plan_review`
- **Branch:** `feature/sync-002-encrypted-operation-core`
- **Updated:** 2026-09-01

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
- Merged the current `main` branch without dropping either SYNC-002 runtime
  wiring or the iOS application-mapping fallback. The merged quality-exception
  gate exposed a stale iOS function-naming suppression, so the Kotlin export
  and Swift caller now use lower camel case and the obsolete approval was
  removed instead of broadened.

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
  Required findings. A focused completed-change review of the writer-release
  cancellation correction also approved it with no findings; its simplicity
  review concluded that the correction was already lean.

## Hosted review follow-up

- **Verdict:** `changes required`
- **Required findings:** A reopened replica did not reject durable HLC state
  below an accepted operation clock, and rejected bundles did not commit
  supplied progress when exact refetch remained available. A later review pass
  also found that invalid local mutations froze the writer and that reopen did
  not revalidate accepted author registration and signing-key history. The
  next pass found that reopened staged state did not enforce the live author,
  sequence, bundle-identity, or capacity invariants. The latest pass found that
  oversized transport input was copied before the 64 KiB limit was enforced. A
  subsequent pass found that four session timing carriers exposed exact values
  through their generated default string representations. The final pass found
  that the hybrid logical clock still exposed its exact physical operation time
  through its generated default string representation and through containing
  data classes. An accepted advisory pass then found that cancellation could
  leave a closed writer registered while its owner-release callback waited for
  the core mutex.
- **Resolution:** Reopen validation now enforces the accepted-history HLC lower
  bound. Rejection and deferred-capacity outcomes share one exact-refetch
  progress path with ambiguous-commit reconciliation. Focused regression tests
  cover lower, equal, and greater reopen clocks plus exact-refetch progress and
  exact and unresolved ambiguous results, including writer freezing. Invalid mutations now return
  `INVALID_MUTATION` before authoring resources are reserved, and reopen rejects
  missing or repeated registration, sequence conflicts, and signing-key changes
  while preserving legal sequence gaps. Reopen also rejects staged sequence-1
  operations, accepted-author and bundle-identity overlap, duplicate author
  sequences, and per-author or global capacity overflow. Remote acceptance now
  checks raw byte size before retaining an immutable copy, while oversized
  rejection keeps the existing exact-refetch progress and reconciliation
  semantics. Local mutation, operation payload, reduced-start, and effective-
  session carriers now use fixed redacted default strings. The hybrid logical
  clock also has one fixed redacted representation, which transitively removes
  exact operation times from containing data-class strings without changing
  clock behavior or serialization. Independent focused re-reviews approved the
  corrections with no remaining Critical or Required findings. Writer close now
  runs only owner deregistration in a non-cancellable cleanup context after
  closing its state and keys, so cancellation cannot leave the closed instance
  active. A deterministic common regression test cancels close while that
  callback is suspended and proves it completes on JVM and iOS Simulator. No
  further hosted review is needed.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Baseline `./gradlew quality` | `pass` | Existing main baseline passed before the task worktree was created. |
| Baseline credential-free iOS host build | `pass` | Existing `iosApp` scheme built for the generic iOS Simulator with signing disabled. |
| Focused JVM codec, JCA, migration, and writer tests | `pass` | Canonical round trips, RFC 5869, Ed25519, format-1 envelope, schema upgrades, first-author batch, and terminal HLC exhaustion passed. |
| Final `./gradlew quality` | `pass` | All 116 aggregate tasks passed after the writer-release correction, including JVM and iOS Simulator tests, migration verification, static analysis, approved-exception verification, formatting, and all existing targets. |
| Credential-free iOS Simulator host build | `pass` | The `iosApp` scheme, injected CryptoKit provider, and lower-camel Kotlin-to-Swift entry point compiled with signing disabled. |
| CryptoKit XCTest on iOS Simulator | `pass` | FIPS SHA-256, RFC 4231 HMAC-SHA256, NIST AES-GCM including altered-tag rejection, Ed25519 lifecycle and RFC 8032 cross-verification, and decoding the fixed JCA-produced format-1 golden bundle through Kotlin and CryptoKit passed. |
| CryptoKit XCTest on physical iPhone | `pass` | All six Simulator-tested CryptoKit cases passed on the connected physical iPhone with the maintainer-provided Development Team supplied only as a local build parameter. No personal signing value was added to the repository. |
| Hosted-review regression tests | `pass` | Invalid local session bounds remain recoverable; reopen rejects invalid accepted and staged author histories while preserving legal gaps and exact staging-capacity boundaries. |
| Bounded remote-ingress regression tests | `pass` | Exactly 64 KiB reaches normal parsing, larger input returns `OVERSIZED` before an immutable copy, and exact-refetch proof remains required before rejection advances transport progress. |
| Synchronization timing redaction regression | `pass` | The hybrid logical clock plus local, payload, reduced-start, and effective-session timing carriers return fixed redacted strings on the JVM and iOS Simulator. |
| Writer-release cancellation regression | `pass` | The focused test failed before the correction, then the JVM and iOS Simulator common suites proved that owner deregistration completes after close cancellation. |

## Blockers and accepted risks

- None. Concrete CloudKit transport, Keychain delivery, and sync-engine state
  remain explicitly deferred to their named tasks.
