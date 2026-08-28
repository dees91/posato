# Execution: `SYNC-001`

- **Brief:** [SYNC-001](../specifications/sync-001-encrypted-operation-contract.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** `/root/sync_001_plan_review`
- **Branch:** `feature/sync-001-encrypted-operation-contract`
- **Updated:** 2026-08-28

## Plan

1. Reconcile the accepted Apple workspace and threat boundaries with the exact
   PoC format, operation model, reducer, rejection, and cross-target evidence.
2. Review current primary documentation, target support, release and maintenance
   status, licenses, and security posture only for the minimum viable Apple MVP
   primitive and provider candidates.
3. Write one concise proposed ADR that selects only the cryptographic and
   convergence contract needed by the Apple MVP and explicitly rejects
   prototype-only and portable-mode complexity.
4. Verify traceability, internal consistency, and repository hygiene, then
   obtain an independent proposal review before requesting maintainer acceptance.
5. After acceptance, mark the ADR accepted, update the synchronization wiki and
   log, record final evidence, obtain a focused independent review of the final
   versioned change, and only then mark the task done.
6. Address accepted hosted-review corrections in the same contract without a
   wire-format change, rerun affected verification and focused independent
   review, then commit, push, and reply with evidence.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** The plan did not require current primary
  evidence for primitive and provider selection, and it placed completed-change
  review before the post-acceptance closeout delta.
- **Resolution:** The corrected plan adds a focused current provider and
  primitive evidence step, preserves a pre-acceptance proposal review, and
  requires focused independent review after the final closeout before done.
  Independent re-review approved the corrected plan with no remaining Critical
  or Required finding.
- The third hosted-review correction plan initially required exact
  ambiguous-commit reconciliation, whole-batch HLC reservation, atomic terminal
  exhaustion, and a recoverable invalid-wall-clock outcome. Focused re-review
  approved the corrected plan with no remaining Critical or Required finding.

## Result

- Added proposed ADR 0006 with a closed Apple MVP operation vocabulary,
  canonical byte grammar, platform-provider cryptography, automatic author
  registration, bounded reorder staging, and deterministic convergence.
- The maintainer accepted ADR 0006 on 2026-08-28. Promoted it to an accepted
  authority and reconciled the architecture decisions, threat model, maintained
  synchronization, architecture, privacy synthesis, routing, and open questions.
- Corrected the accepted format after hosted review: author metadata is now
  encrypted, each immutable bundle has a context-bound single-use key, local
  identity loss fails closed or rotates identity, and the synchronized domain
  projection has a deterministic 2,048-item capacity rule.
- Corrected reordered capacity outcomes and clock-rollback expiry after the
  maintainer-requested second hosted pass: capacity is recomputed projection
  state, while observed expiry is a terminal local marker bound to its
  encrypted session identifier; competing starts quarantine the session.
- The maintainer requested a third hosted pass. Its Required coordinated-
  rollback finding replaces persistent Apple signing state with a fresh
  process-memory authoring incarnation per local writer open. Its advisory HLC
  overflow finding was explicitly accepted into scope and now has terminal
  fail-closed local authoring behavior while inbound projection continues.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** The pre-acceptance proposal review required
  an exact byte grammar, nonce uniqueness construction, safe reordered
  first-contact handling, and complete time-dependent session projection. The
  completed-change review found that the execution record still described
  already-obtained maintainer acceptance as pending.
- **Resolution:** The initial accepted revision added a 144-byte normative
  grammar, per-author HKDF key and sequence nonce, bounded authenticated
  staging, and deterministic projection at the same evaluation instant. The
  hosted-review correction below supersedes its header, key, and nonce design.
  The stale acceptance blocker was also removed; focused re-review approved the
  initial correction with no remaining Critical or Required finding.
- Hosted review on PR #6 reported two Required findings: deterministic nonce
  safety depended on identity/sequence durability, and author metadata was
  unnecessarily exposed. It also reported an advisory domain-capacity gap,
  which the maintainer explicitly accepted into scope. The correction removes
  author metadata from the header, derives a single-use key per bundle, assigns
  identity reconciliation ownership, and defines deterministic capacity
  rejection. The focused independent review found the correction entry inserted
  ahead of existing wiki history and the review state closed prematurely. The
  entry was appended at the end, the record remained active through re-review,
  and focused re-review approved that result with no remaining Critical or
  Required finding.
- The maintainer later requested a second hosted pass. It found two Required
  defects: persisted capacity outcomes depended on delivery order, and a
  wall-clock rollback could revive an observed-expired session. The correction
  recomputes capacity outcomes from the complete applicable set and records the
  maintainer-selected terminal local expiry marker without a timestamp or new
  synchronized operation. Focused review found that a provisionally canonical
  start could displace the marker under reordered delivery. Requiring the
  marker to cover the session identifier and deriving a `session-conflict`
  quarantine from every distinct start removes that displacement without
  exposing the identifier as routing metadata. Focused re-review approved the
  complete correction with no remaining Critical or Required finding.
- The third hosted pass found that coordinated database and secure-record
  rollback could reuse an author sequence. It also reported advisory HLC
  overflow at the accepted maximum, which the maintainer accepted into scope.
  Independent correction-plan review required exact ambiguous-commit
  reconciliation, whole-batch HLC reservation for registration plus the first
  business operation, atomic exhaustion at the terminal tuple, and a distinct
  recoverable invalid-wall-clock outcome. The corrected plan passed focused
  re-review with no remaining Critical or Required finding.
- The first focused completed-change review of that correction found one
  remaining Required defect: the in-memory sequence did not prevent a
  same-open rollback from deleting an unpublished committed operation, creating
  an unfillable gap, lowering HLC, or clearing exhaustion. The correction now
  compares the retained author/HLC footprint with the last committed in-memory
  checkpoint and freezes the writer before any later sealing on regression.
  Focused re-review then found that legitimate remote acceptance also advances
  HLC during the same open. The state machine now advances the checkpoint with
  every exact serialized local or remote transaction and rejects only regressed
  or unexplained state. Focused re-review approved the complete correction with
  no remaining Critical, Required, Recommended, or Optional finding.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Initial authority and feasibility routing | `pass` | Read the required repository contracts, accepted authorities, maintained synchronization synthesis, and final feasibility sources before drafting. |
| Current cryptography evidence | `pass` | Reviewed current Apple CryptoKit and Keychain accessibility, JDK 21, NIST, RFC 5869, RFC 8032, and cryptography-kotlin primary sources for the minimum Apple MVP candidates and rollback boundary. |
| Pre-acceptance proposal review | `pass` | Independent corrected-proposal review found no remaining Critical or Required defect. |
| Maintainer acceptance | `pass` | The maintainer explicitly selected acceptance of ADR 0006 on 2026-08-28. |
| Repository-local Markdown links | `pass` | All repository-local Markdown links resolve. |
| Diff and sensitive-data hygiene | `pass` | `git diff --check` passed and the scoped scan found no personal path, private-key marker, or common credential shape. |
| Third-correction completed-change review | `pass` | Focused review required same-open regression checks; re-review required verified remote transactions to advance the same checkpoint. Final focused re-review approved the complete correction with no remaining findings. |

## Blockers and accepted risks

- No blocker. Production implementation and cross-target evidence remain with
  `SYNC-002`, and Apple bootstrap remains with `SYNC-003`.

## Final

- **Status:** `done`
- **Outcome:** accepted encrypted-operation and convergence contract includes
  rollback-safe local authoring and fail-closed HLC exhaustion
