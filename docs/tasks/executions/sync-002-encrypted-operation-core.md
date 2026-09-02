# Execution: `SYNC-002`

- **Brief:** [Implement the encrypted operation core](../specifications/sync-002-encrypted-operation-core.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex, closeout by Claude Code
- **Reviewer:** independent agents (plan and completed-change reviews)
- **Branch:** `feature/sync-002-encrypted-operation-core`
- **Updated:** 2026-09-02

## Plan

1. Reimplement the format-1 codec, reducer, typed outcomes, and serialized
   writer state machine in common Kotlin with injected time, identity,
   cryptography, and persistence boundaries.
2. Add a SQLDelight migration and store for immutable accepted, staged, and
   pending bytes plus durable HLC, caller-driven local expiry facts, and an
   opaque transport-progress mutation committed with remote acceptance.
3. Implement JDK 21 JCA/JCE and injected Swift CryptoKit provider leaves and
   verify their shared vectors without a runtime cryptography dependency.
4. Exercise failure, rollback, replay, reordering, capacity, convergence,
   transport-progress atomicity, and cross-target behavior.

## High-risk plan review

- **Verdict:** `approved`
- **Required finding:** the initial plan omitted the ADR 0006 atomic coupling
  between remote acceptance or staging and opaque transport progress.
- **Resolution:** the brief and plan require the progress mutation in the same
  SQLDelight transaction, including duplicate, rejection, and deferred-capacity
  semantics, while CloudKit cursor and engine state stay with `SYNC-010`.

## Result

- Format-1 operation and envelope codecs, typed validation outcomes, a
  deterministic reducer, a serialized writer, durable HLC, bounded
  unknown-author staging, retry state, and caller-driven terminal expiry facts
  live in the existing shared module.
- SQLDelight schema version 3 stores accepted, pending, and staged bundles,
  replica checkpoints, expiry facts, and opaque transport progress; the
  version 1 and 2 target-policy migration contracts remain covered.
- JDK JCA/JCE and injected Swift CryptoKit providers use platform cryptography
  only. Reverse import compiled on Kotlin `2.4.10`; SwiftPM import and Kotlin
  `2.4.20-Beta2` were not needed.
- A credential-free iOS XCTest target covers CryptoKit HMAC, AES-GCM, and
  Ed25519 vectors. No feasibility implementation or machine-specific state was
  copied into product sources.
- Hosted-review hardening landed in 36 commits after the initial
  implementation; see the summary below. The branch was rebased onto `main`
  at closeout.
- Closeout corrections: a retired transport key is unusable and rejected by
  `open`, writer checkpoint reads are volatile and single-read, and the Swift
  random-byte provider handles a zero-length request without a trap.

## Completed-change review

- **Verdict:** `approved`
- **Required findings:** late author registration, terminal author-sequence
  reuse, exact ambiguous-commit reconciliation, authenticated reopen
  validation, canonical projection digest, the complete CryptoKit format
  boundary, capacity progress semantics, remote terminal-HLC handling,
  terminal-expiry ambiguity, and the normative writer, SQLDelight, failure,
  and convergence test matrix.
- **Resolution:** all resolved and independently re-reviewed before the pull
  request opened. The closeout correction received one independent
  completed-change review with evidence.

## Hosted review summary

40 hosted passes produced 49 findings (40 P1, 9 P2) and 36 correction commits
between 2026-08-31 and 2026-09-02. All were accepted at the time; the review
budget and the excluded finding classes in `AGENTS.md` now bound this loop.

| Finding class | Count | Decision |
| --- | --- | --- |
| Writer lifecycle, cancellation, and checkpoint reconciliation | 14 | Fixed; real defects. |
| Reopen invariants against a tampered local database | 14 | Fixed; class now declined under accepted limit `R-02`. |
| Redacted `toString()` per type | 9 | Fixed; class now covered by one enumerated test. |
| Bounded copies and native output length | 6 | Fixed. |
| Owned buffer and key zeroing | 5 | Fixed within the `R-05` boundary. |
| SQL BLOB preflight before materialization | 1 | Fixed. |
| Final pass after closeout | pending | Triage table decides; one pass, then merge. |

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` after the rebase and closeout corrections | `pass` | JVM and iOS Simulator suites, migration verification, Detekt, ktlint, approved-exception verification, and macOS packaging verification. |
| Focused codec, reducer, writer, store, and provider tests | `pass` | Canonical round trips, RFC 5869, RFC 8032, format-1 envelope, schema upgrades, first-author batch, terminal HLC exhaustion, convergence permutations. |
| Credential-free iOS Simulator host build and CryptoKit XCTest | `pass` | Seven cases including the JCA-produced golden bundle decoded through Kotlin and CryptoKit and the zero-length random request. |
| CryptoKit XCTest on a physical iPhone | `pass` | Six cases on 2026-08-31 with the Development Team supplied only as a local build parameter; later Swift changes are guards on paths Kotlin does not use. |
| Hosted-review regression classes | `pass` | Reopen validation, cancellation reconciliation, bounded ingress, redaction, storage preflight, native output length, and buffer lifecycle regressions run on the JVM and iOS Simulator. |
| Closeout regressions | `pass` | Closed transport key rejected by `open` and by the codec; single-read checkpoint accessors; zero-length native random output. |
| `git diff --check`, suppression scan, personal-data scan | `pass` | No new `Suppress` token, credentials, device identifiers, or personal paths. |

## Blockers and accepted risks

- `R-02` and `R-05` remain accepted limits; reopen validation and buffer
  clearing do not claim tamper resistance or guaranteed memory erasure.
- `SYNC-013` removes dead queries, unused iOS wiring, the test-only digest
  helper, and duplicated validation layers after merge.
- Concrete CloudKit transport, Keychain delivery, and sync-engine state remain
  with their named tasks.
