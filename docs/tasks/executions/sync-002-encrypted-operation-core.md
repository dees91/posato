# Execution: `SYNC-002`

- **Brief:** [Implement the encrypted operation core](../specifications/sync-002-encrypted-operation-core.md)
- **Status:** `complete`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent Codex agent `/root/sync002_plan_review`
- **Branch:** `feature/sync-002-encrypted-operation-core`
- **Updated:** 2026-09-02

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
  The later bounded-progress and plaintext-cleanup correction also received an
  independent high-risk plan review with no Critical or Required findings.
  The invalid-singleton correction received the same plan approval with no
  findings at any severity; its simplicity review selected one broader state
  query and one existing-boundary invariant check.
  The cancelled local-commit reconciliation correction also received plan
  approval with no findings at any severity; its simplicity review selected
  the existing exact cancellation-reconciliation boundary.
  The sequence-gap expiry correction received independent plan approval after
  preserving the authenticated-restore order and adding an explicit positive
  regression for conflicted applicable starts. Its simplicity review selected
  one shared non-gap helper and no broader abstraction.
  The native signing-key correction received independent plan approval with
  exact invalid-output and ownership-transfer cases. Its simplicity review
  selected validation before wrapper construction and no broader exception
  handling or platform changes.
  The follow-up pre-copy correction received independent plan approval with a
  native-data sentinel that makes any payload read fail. Its simplicity review
  selected the existing exact-length guard pattern and no new helper or seam.
  The persisted-integer and native-result correction received independent plan
  approval with a real-SQLite storage-class matrix and one shared exact-size
  native conversion boundary. Its simplicity review found the plan lean.
  The writer-identity redaction correction received independent plan approval
  with one exact common regression. Its simplicity review selected one root
  `SyncWriter` representation and no duplicate wrapper override.
  The singleton storage-class and signing-key wrapper correction received an
  independent plan review. Its Required finding moved the tampered-table
  regression to a fresh driver, database, and store so prepared-statement cache
  behavior cannot obscure the restored-file boundary. The corrected plan uses
  one SQL preflight branch and two platform-specific redacted strings without a
  schema attestation layer or shared wrapper abstraction.
  The duplicate persisted-bundle correction received independent plan approval
  with no Critical or Required findings. Its simplicity review selected two
  native SQL grouping checks in the existing preflight and no Kotlin helper,
  schema attestation, or broader duplicate policy.
  The bundle-parts redaction correction received independent plan approval with
  no Critical or Required findings. Its simplicity review selected one fixed
  private-carrier representation and no visibility change, reflection test,
  test seam, generic sanitizer, or static scan.
  The persisted-staging capacity correction received independent plan approval
  with no Critical or Required findings. Its simplicity review selected one
  bounded SQL preflight branch before staged payload inspection and one
  real-database boundary regression, without a count query or broader policy.
  The duplicate persisted-state correction received independent plan approval
  with no Critical or Required findings. Its simplicity review selected the
  same bounded SQL preflight pattern at the exact one-row boundary and one
  real-database regression, without a count query or schema change.
  The initial-HLC reachability correction received independent plan approval
  with no Critical or Required findings. Its simplicity review selected one
  authenticated-snapshot guard and one common regression, without duplicating
  the semantic invariant in SQL or adding a helper or schema change.
  The pre-lock writer-shutdown correction received independent plan approval
  with no Critical or Required findings. Its simplicity review selected one
  wider `NonCancellable` boundary and the existing store suspension seam,
  without adding a scope, timeout, retry, helper, or production test hook.
  The conservative-HLC reachability correction received independent plan
  approval with no Critical or Required findings. The reviewer confirmed that
  exact historical replay is unavailable without retained acceptance order and
  local-or-remote provenance, and approved one bounded validation helper using
  the existing clock-transition rules without a schema or replay engine.
  The pending-duplicate and terminal-rewrite correction also received
  independent plan approval with no findings. Its simplicity review selected
  one additional SQL grouping arm, one guard at the existing exhaustion
  boundary, and extensions of two existing regressions without a schema change,
  helper, or broader policy.

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
  review concluded that the correction was already lean. The later pre-lock
  writer-shutdown correction received the same focused approval with no
  findings at any severity; its simplicity review found the wider existing
  cleanup boundary already lean. The local-commit cancellation correction
  received the same focused approval with no findings.
  The authoring-metadata redaction correction was also independently approved
  with no findings. The replica-snapshot redaction and rejected-key lifetime
  corrections received the same focused approval with no findings; the
  simplicity review found the centralized ownership boundary already lean.
  The cancelled remote-commit reconciliation correction received focused
  approval after its required exceptional-read fix; no findings remain. The
  simplicity review found the single guarded cleanup read already lean.
  The orphaned-row and local-mutation-result corrections received focused
  approval with no findings; the simplicity review found them already lean.
  The bounded-progress and plaintext-cleanup corrections received focused
  approval with no Critical, Required, Recommended, or Optional findings; the
  simplicity review found the final change already lean.
  The invalid-singleton correction received the same completed-change approval
  with no findings at any severity; the simplicity review found the broader
  state query and existing-boundary invariant check already lean.
  The cancelled local-commit reconciliation correction also received approval
  with no findings at any severity; the simplicity review found its reuse of
  the existing exact reconciliation boundary already lean.
  The secure-random and persisted-clock corrections received an independent
  plan review with no remaining findings after both physical-clock boundaries
  were included in the regression plan. Independent completed-change review
  then approved the implementation with no Critical or Required findings; its
  simplicity review found the change already lean.
  The persisted-state and decoded-operation redaction correction received an
  independent plan review and completed-change review with no findings at any
  severity; its simplicity review found the change already lean.
  The sequence-gap expiry correction received focused completed-change approval
  with no findings at any severity; its simplicity review found the shared
  non-gap helper already lean.
  The native signing-key correction received focused completed-change approval
  with no findings at any severity; its simplicity review found pre-wrapper
  validation and explicit ownership transfer already lean.
  The pre-copy native signing-key correction received the same approval with no
  findings at any severity; its simplicity review found the exact-length guard
  and iOS-only sentinel already lean.
  The persisted-integer and native-result correction received focused approval
  with no findings at any severity; its simplicity review found the single
  storage preflight and exact-size conversion helper already lean.
  The writer-identity redaction correction received focused approval with no
  findings at any severity. Its simplicity review found the single root
  representation and read-only pending-bundle property already lean.
  The singleton storage-class and signing-key wrapper correction received
  focused completed-change approval with no Critical or Required findings. The
  reviewer recommended adding its verification evidence to this record; that
  bookkeeping correction is included below. Its simplicity review found the
  implementation already lean. The duplicate persisted-bundle correction
  received focused completed-change approval with no Critical or Required
  findings. The reviewer recommended recording its verification evidence;
  that bookkeeping correction is included below. Its simplicity review found
  the two preflight branches and one table-driven regression already lean. The
  bundle-parts redaction correction received focused completed-change approval
  with no Critical or Required findings. The reviewer recommended recording its
  verification evidence; that bookkeeping correction is included below. Its
  simplicity review found the fixed private-carrier representation already
  lean. The persisted-staging capacity correction received focused
  completed-change approval with no findings at any severity; its simplicity
  review found the bounded SQL preflight and one boundary regression already
  lean. The duplicate persisted-state correction received focused
  completed-change approval with no findings at any severity; its simplicity
  review found the exact one-row SQL preflight and one boundary regression
  already lean. The initial-HLC reachability correction received focused
  completed-change approval with no findings at any severity; its simplicity
  review found the single authenticated-snapshot guard and regression already
  lean.

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
  the core mutex. The next required pass found that cancellation during a local
  commit discarded prepared bytes without retiring the authoring incarnation
  and could abandon a newly created signing key.
  A further required pass found that authoring state carriers exposed the exact
  next author sequence, prepared-bundle cardinality, and durable state shape
  through their generated default string representations. The latest required
  finding found that the durable replica snapshot still exposed its revision,
  collection cardinalities, and state shape through its generated default
  string. The maintainer also accepted the accompanying advisory that a failed
  or cancelled writer open could leave its supplied transport key uncleared.
  The next required finding found that cancellation after a remote transaction
  committed could leave the in-memory checkpoint stale. The maintainer accepted
  the related advisory that the same handoff could omit a committed terminal-
  expiry marker and permit same-process session revival after wall-clock
  rollback. The latest pass found that a missing replica-state row could be
  recreated around retained accepted, pending, staged, or expiry records, and
  that successful local-mutation results exposed pending-bundle cardinality
  through their generated default string. Two further required findings found
  that opaque transport progress was copied and stored without an explicit
  size limit, and that an HKDF failure left the already encoded operation
  plaintext outside the existing cleanup block. The invalid-singleton finding
  found that a state row retained under an invalid singleton key was invisible
  to both state restore and residue detection, allowing fresh initialization
  to reset revision, HLC, and transport progress. The latest required finding
  found that cancellation after a durable local commit could leave the writer's
  checkpoint, projection, and pending bundles at their pre-commit values.
  The latest required finding found that the private persisted-state carrier
  exposed the exact replica revision through its generated default string.
  The latest required finding found that reopen accepted a terminal-expiry fact
  referencing only a session start behind an author-sequence gap, even though
  live marker creation rejected the same impossible state.
  The latest required finding found that SQLite could coerce malformed text in
  eight selected integer columns before restore validation. The maintainer also
  accepted the advisory that native cryptographic results other than public
  keys were copied before their exact result lengths were validated.
  The latest required finding found that the default `SyncWriter` identity hash
  supplied a prohibited per-instance correlation identifier, including through
  the generated successful-open result representation. The latest two required
  findings found that a replaced untrusted state table could let SQLDelight
  coerce a text singleton before validation, and that the platform signing-key
  wrappers still exposed dynamic per-instance object identities. The latest
  required finding found that replaced accepted or staged tables could retain
  duplicate bundle identifiers that map construction silently collapsed after
  restore validation. The latest required finding found that the private
  bundle-parts carrier exposed the complete header, ciphertext and tag, and
  signature through its generated default string. The latest required finding
  found that reopen selected and decoded every persisted staged bundle before
  rejecting a staging set above the accepted 128-bundle limit. The latest
  required finding found that reopen materialized every row from a replaced
  replica-state table, including bounded transport-progress bytes, before
  rejecting a second row. The newest required finding found that authenticated
  reopen accepted nonempty history while the durable HLC remained at the fresh
  `(0, 0)` baseline, even though no legal local or remote acceptance path can
  produce that snapshot.
  The subsequent required finding found that cancellation while `close()`
  waited for the writer mutex could prevent state closure, key retirement, and
  owner release, leaving the core unable to open a replacement writer.
  The latest required finding found that reopen accepted durable HLC values
  above the state conservatively explainable by authenticated retained history,
  including a fabricated terminal exhaustion that permanently blocked local
  authoring.
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
  enters a non-cancellable cleanup context before acquiring its mutex, so its
  serialized state transition, key retirement, and owner release complete even
  when cancellation occurs while another operation owns the writer. Common
  regressions cover cancellation both while mutex acquisition and owner release
  are suspended on the JVM and iOS Simulator. A
  prepared local mutation now transfers its incarnation to the writer before
  the commit can suspend; cancellation freezes the writer, closes the key, and
  propagates unchanged instead of permitting replacement bytes for the same
  author sequence. Authoring incarnations and prepared local mutations now use
  fixed redacted default strings without changing equality, persistence, or
  canonical serialization. The replica snapshot now has the same fixed
  redacted representation. Writer open retains ownership of its supplied
  transport key until an active writer is installed; one centralized cleanup
  path clears it for every failure, exception, and cancellation before that
  transfer. Cancelled remote-style commits now perform one non-cancellable
  durable read before rethrowing cancellation: an exact committed or exact
  pre-commit snapshot updates or preserves the checkpoint, while every other
  outcome, including a failed reconciliation read, freezes the writer without
  replacing the original cancellation. The same boundary covers remote acceptance,
  transport progress, staging, terminal expiry, and state-only HLC exhaustion.
  Local commits now use that exact cancellation-reconciliation boundary before
  propagating cancellation, while the existing outer freeze still retires the
  authoring incarnation and refuses later mutation.
  Replica initialization now proceeds only when every child sync table is
  empty; otherwise missing singleton state reports corruption without changing
  retained rows. Successful local-mutation results now use one fixed redacted
  default string without changing their fields or domain behavior. Opaque
  transport progress now rejects more than 64 KiB before copying, the current
  schema and migration reject oversized persistence, and restore reports
  invalid retained bytes as corruption. Bundle sealing now runs nullable
  prerequisites inside its cleanup block, so an HKDF failure still clears the
  owned plaintext buffer. State restore now reads every replica-state row and
  validates its singleton key before fresh initialization can run, so an
  invalid or additional hidden state row reports corruption.
  The maintainer accepted two later advisory findings. JDK random generation
  now maps provider exceptions through the existing nullable cryptographic
  boundary, preserving the writer's established cleanup path. Replica restore
  now rejects physical HLC values outside the format range as corruption before
  constructing the domain clock. The persisted-state carrier now uses a fixed
  redacted representation. The same correction covers the directly observed
  decoded-operation carrier, whose generated representation exposed exact
  author sequence and HLC fields. Replica restore now preflights the type and
  byte length of every selected BLOB in the same transaction before reading
  any contents, so invalid retained state reports corruption without crossing
  the SQLDelight driver boundary. The iOS cryptographic adapter now rejects
  negative random-byte requests before calling native code and wrong-sized
  native output before allocating a Kotlin array, preserving the established
  nullable failure and signing-key cleanup path. Reopen now validates terminal-
  expiry facts against retained session starts whose derived audit outcome is
  not `SEQUENCE_GAP`, using the same rule as live marker creation. This rejects
  impossible gapped markers without invalidating markers retained through a
  derived session conflict. The maintainer accepted the later advisory that an
  invalid native signing public key could throw across the iOS adapter and
  leave its native handle without explicit closure. The adapter now validates
  the public key before wrapper construction: invalid output closes the handle
  and returns nullable failure, while valid output transfers the handle and
  validated key to the wrapper. The maintainer accepted the follow-up advisory
  that this validation occurred only after copying the native payload. The
  adapter now checks the exact public-key length before reading its bytes, so
  malformed output cannot trigger a size-derived Kotlin allocation. Replica
  restore now preflights the SQLite storage class of every selected integer in
  the same transaction before SQLDelight materializes it. The iOS adapter now
  validates the exact expected size of
  random, SHA-256, HMAC-SHA256, AES-GCM, public-key, and signature results before
  allocating or reading their native payloads.
  `SyncWriter` now returns one fixed redacted default string, which also makes
  the generated successful-open result representation stable and non-
  correlating. The existing pending-bundle accessor is now a property so this
  required representation does not weaken the class-complexity gate. Replica
  restore now also rejects a non-integer singleton before typed materialization,
  including after an untrusted table replacement. JDK and iOS signing-key
  wrappers now return fixed redacted strings while retaining their existing
  ownership and close behavior. Replica restore now rejects duplicate
  accepted, pending, or staged bundle identifiers in the same SQL preflight
  before typed rows or bundle payloads are materialized, so map construction
  cannot discard a retained row. The private bundle-parts carrier now returns
  one fixed redacted default string instead of rendering its header, ciphertext
  and tag, and signature byte arrays. Replica restore now rejects a 129th
  staged row in the SQL preflight before inspecting staged payload columns.
  Replica restore now also rejects a second state row in the SQL preflight before inspecting state
  BLOB columns. Authenticated reopen now rejects accepted history at the fresh
  `(0, 0)` HLC baseline while preserving legal equality between a non-initial
  durable clock and an accepted local operation. It now also requires empty
  history to retain the exact fresh clock and bounds nonempty history between
  its greatest operation clock and the most remote advancement retained
  operations could explain. Terminal exhaustion additionally requires a
  terminal upper bound or the state-only first-author exhaustion reachable from
  a preterminal clock. This rejects unexplained physical or logical advances
  without adding unavailable acceptance provenance. A local mutation against
  an already exhausted checkpoint now returns `HLC_EXHAUSTED` without
  rewriting the identical terminal state or advancing its revision; an active
  clock that cannot reserve the required batch still persists exhaustion once.
  No further hosted review is needed.

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
| Writer-release cancellation regression | `pass` | The new focused test first exposed a retained transport key when close was cancelled while waiting for an in-flight mutation. After correction it asserts transport-key zeroization, one signing-key close, and successful replacement open; the JVM and iOS Simulator common suites also retain the deregistration-suspension case. |
| Local-commit cancellation regression | `pass` | The focused test failed before the correction, then JVM and iOS Simulator common suites proved unchanged cancellation propagation, no synthetic durable mutation, prepared-key closure, and frozen refusal of later authoring. |
| Post-cancellation aggregate quality | `pass` | All 113 aggregate tasks passed, including JVM and iOS Simulator tests, migration verification, Detekt, ktlint, approved-exception verification, and target compilation. |
| Authoring metadata redaction correction | `pass` | The focused JVM test failed before the correction. JVM and iOS Simulator common suites then passed, and all 113 aggregate quality tasks passed with the fixed representations. |
| Replica redaction and rejected-key correction | `pass` | Three focused JVM regressions failed before the correction and passed after it. The complete JVM and iOS Simulator suites passed in 32 executed tasks, followed by the aggregate quality gate with every task rerun from a clean build. |
| Cancelled remote-commit reconciliation | `pass` | Three focused JVM regressions failed before their corresponding corrections, including an exceptional reconciliation read. The complete cancellation class then passed, followed by all 32 JVM and iOS Simulator tasks and all 113 aggregate quality tasks after the final correction. |
| Orphaned-state and result-redaction corrections | `pass` | Both focused JVM regressions failed before implementation and passed afterward. Valid accepted-only, staged-only, and expiry-only residue is rejected when singleton state is missing; successful local-mutation output no longer exposes pending cardinality. All 32 JVM and iOS Simulator tasks passed before the aggregate quality gate. |
| Bounded progress and plaintext cleanup | `pass` | Focused JVM regressions demonstrated the missing application and SQL limits before implementation. The corrected JVM and iOS Simulator suites passed in 32 executed tasks; ktlint and Detekt passed without suppressions; all 113 aggregate quality tasks then passed. Independent completed-change review found no findings at any severity. |
| Invalid singleton rejection | `pass` | The focused real-database JVM regression first returned success after relocating the state row to an invalid singleton key, then passed with fail-closed restore. JVM and iOS Simulator suites, ktlint, Detekt, and all 113 aggregate quality tasks passed without suppressions. Independent completed-change review found no findings at any severity. |
| Cancelled local-commit reconciliation | `pass` | The focused JVM regression first exposed a stale post-commit projection, then passed after the correction. The complete JVM and iOS Simulator suites, ktlint, Detekt, and all 113 aggregate quality tasks passed without suppressions. Independent completed-change review found no findings at any severity. |
| Secure-random and persisted-clock corrections | `pass` | Focused JVM regressions first exposed the escaping provider exception and storage-failure mapping for both physical-clock bounds, then passed after the corrections. JVM and iOS Simulator suites, ktlint, Detekt, and all 113 aggregate quality tasks passed without suppressions. Independent completed-change review found no Critical or Required findings. |
| Persisted-state and decoded-operation redaction | `pass` | JVM bytecode inspection confirmed both generated representations exposed exact prohibited metadata before the correction and returned only fixed redacted literals afterward. Focused codec and SQL tests, JVM and iOS Simulator suites, ktlint, Detekt, and all 113 aggregate quality tasks passed without suppressions. Independent completed-change review found no findings at any severity. |
| Persisted-BLOB and native-random boundaries | `pass` | A real-database matrix covers all 17 BLOB columns selected during restore, a wrong SQLite storage class, exact fixed sizes, and exact 32/64 KiB allocation limits. iOS Simulator coverage accepts only exact native random output and rejects negative, short, and long cases before copying. Focused JVM and iOS tests, ktlint, Detekt, all 116 aggregate quality tasks, and the credential-free iOS host build passed without suppressions. Independent completed-change review found no findings at any severity. Existing physical-device evidence remains applicable because Swift, CryptoKit, and the wire format did not change. |
| Sequence-gap expiry validation | `pass` | The focused JVM regression failed before the correction and passed afterward. Reopen rejects a marker backed only by a gapped start, preserves legal gaps without a marker, and accepts a marker backed by conflicting but applicable starts. All 113 aggregate quality tasks passed, including JVM and iOS Simulator tests, ktlint, Detekt, approved-exception verification, and target compilation without suppressions. |
| Native signing-key output validation | `pass` | The focused iOS Simulator regression failed with `IllegalStateException` before the correction and passed afterward. Null, 31-byte, and 33-byte native public keys close their handles exactly once and return failure; an exact 32-byte key transfers ownership until wrapper closure. Detekt's initial return-count finding was fixed in the control flow without suppression. All 113 aggregate quality tasks and the credential-free iOS host build passed. |
| Pre-copy native signing-key validation | `pass` | An iOS `NSData` sentinel that reports 33 bytes and fails on payload access first reproduced the invalid read, then passed when the adapter rejected its length without touching its bytes and closed the signing handle once. The complete iOS Simulator suite, all 113 quality tasks, and the credential-free iOS host build passed without suppressions. |
| Persisted-integer and native-result validation | `pass` | A real-SQLite matrix first reproduced coercion of malformed text in all eight selected integer fields, then passed with same-transaction storage-class preflight. An unreadable 65-byte `NSData` sentinel first reproduced an attempted copy and then proved that SHA-256, HMAC-SHA256, AES-GCM, and signature output is rejected before payload access. All 113 quality tasks, the credential-free iOS Simulator host build, and all six CryptoKit XTests passed without suppressions. |
| Writer-identity redaction | `pass` | The focused JVM regression first observed the inherited per-instance writer representation, then proved exact fixed strings for both the writer and successful-open wrapper. Complete JVM and iOS Simulator suites and all 113 quality tasks passed without suppressions. Detekt's function-count and cyclomatic-complexity findings were resolved in the source by expressing the pending-bundle accessor as a property while retaining the focused preparation-failure helper. |
| Singleton storage and signing-key redaction | `pass` | Focused JVM and iOS regressions first exposed the missing singleton preflight and dynamic wrapper identities, then passed after the correction. All 32 JVM and iOS Simulator tasks and all 113 quality tasks passed without suppressions. Independent completed-change review found no Critical or Required findings; its Recommended evidence-record correction is included here. |
| Duplicate persisted-bundle rejection | `pass` | The focused real-SQLite JVM regression first showed that duplicate accepted or staged bundle identifiers passed preflight, then proved exact preflight rejection and `CORRUPTION` after reopening with a fresh driver. The focused JVM and iOS Simulator contract tests, all 32 cross-target test tasks, and all 113 quality tasks passed. Diff and suppression scans were clean, and independent completed-change review found no Critical or Required findings. |
| Bundle-parts redaction | `pass` | Baseline JVM bytecode rendered all three byte arrays through `Arrays.toString`; post-correction bytecode returns only the fixed `BundleParts(redacted)` literal without reading a field. The focused JVM cryptographic-provider test, all 32 JVM and iOS Simulator test tasks, and all 113 quality tasks passed. Diff and suppression scans were clean, and independent completed-change review found no Critical or Required findings. |
| Persisted-staging capacity preflight | `pass` | The focused real-SQLite JVM regression first showed that 129 physically valid staged rows passed preflight, then proved rejection and `CORRUPTION` after reopening with a fresh driver. The focused JVM and iOS Simulator contract tests, all 32 cross-target test tasks, and all 113 quality tasks passed. Diff and suppression scans were clean, and independent completed-change review found no findings at any severity. |
| Duplicate persisted-state preflight | `pass` | The focused real-SQLite JVM regression first showed that two physically valid state rows with exact-limit transport progress passed preflight, then proved rejection and `CORRUPTION` after reopening with a fresh driver. A controlled SQLite probe confirmed that the first cardinality arm short-circuits the later branch. The focused JVM and iOS Simulator contract tests, all 32 cross-target test tasks, and all 113 quality tasks passed. Diff and suppression scans were clean, and independent completed-change review found no findings at any severity. |
| Initial-HLC reachability | `pass` | The focused JVM regression first showed that authenticated accepted history at the fresh `(0, 0)` baseline opened successfully, then proved `CORRUPTION` after the correction. All 32 JVM and iOS Simulator test tasks and all 113 quality tasks passed while the existing lower, equal, and greater non-initial HLC cases remained green. Diff and suppression scans were clean, and independent completed-change review found no findings at any severity. |
| Conservative HLC reachability | `pass` | Focused JVM regressions first accepted an empty nonfresh clock and remote-history clocks above the reachable successor, then passed after the correction. The complete JVM and iOS Simulator suites and all 113 quality tasks passed without suppressions. Diff and suppression scans were clean. Independent completed-change review found no code, security, boundary, test, or simplicity defects; its Required verification-record finding was resolved by this row. |
| Pending duplicate and terminal rewrite correction | `pass` | Both focused JVM regressions failed before implementation and passed afterward. The complete JVM and iOS Simulator suites passed in 32 tasks, followed by all 113 quality tasks. Detekt's initial complexity finding was addressed by moving the already-terminal idempotency guard into the existing exhaustion operation without suppression. Diff and suppression scans were clean, and independent completed-change review found no findings at any severity. |

## Blockers and accepted risks

- None. Concrete CloudKit transport, Keychain delivery, and sync-engine state
  remain explicitly deferred to their named tasks.
