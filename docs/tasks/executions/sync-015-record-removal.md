# Execution: `SYNC-015`

- **Brief:** [Record-based removal](../specifications/sync-015-record-removal.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending handoff to an implementing agent
- **Reviewer:** plan reviewer and completed-change reviewer pending
- **Branch:** `feature/sync-015-record-removal`
- **Updated:** 2026-09-11

## Plan

1. Confirm the removal path, the two native delete implementations, and the
   deletion-entry collectors in both backends.
2. Replace the `deletedNames` integrity-failure guard in both native
   backends with progress-only pages and flip the two Swift deletion tests
   (`D3`), because the removal's absence check depends on it.
3. Replace the zone-deletion operation on the mailbox port with record
   deletion plus looped absence verification; enumerate through the changes
   traversal, never a query; implement it in the companion (protocol code,
   request handler, store, backend) and in the iOS provider and live
   backend, in bounded idempotent batches (`D1`).
4. Add `ANCHOR_MISSING` to the established check, map it in `AppleSync`,
   the coordinator, and removal (skip record deletion, delete own key,
   clear, tombstone, end local-only); keep `DIFFERENT_ANCHOR` unchanged
   (`D2`). Add the foreign-context skip in `acceptPage` and the
   fresh-attempt sweep while no anchor exists (`D5`).
5. Cover `AC-01` to `AC-03` over the fakes and the adapter tests on both
   targets; keep every existing removal and tombstone case unchanged.
6. Update ADR 0007, the threat model, the sync recipe, the wiki topic, and
   the wiki log at closeout; run focused tests, `./gradlew quality`, and the
   timed physical matrix in both shapes and directions; write the closeout
   statement; obtain the independent completed-change review.

## High-risk plan review

- **Verdict:** `changes-required` (independent reviewer, 2026-09-11),
  resolved in the brief before handoff.
- **Critical or Required findings:** both native backends reject a changes
  page carrying a deletion as an integrity failure, so `D3` was a real code
  change and `D1`'s absence check depended on it; a still-linked peer's
  late publish could leave a foreign-context bundle in the kept zone and
  pin the next workspace's cursor forever; bundle enumeration had to name
  the changes traversal (ADR 0007 has no queryable index); the absence
  check had to loop past the one-record pages; the parallel claim needed a
  concrete file freeze against `SYNC-012`.
- **Resolution:** `D3` replaces the guard and flips the Swift tests; new
  `D5` adds the consume-loop skip of foreign-context bundles and the
  fresh-attempt sweep with an ADR 0007 amendment; `D1` names the traversal
  and the loop; the non-goal lists the freeze. Advisory items folded:
  roadmap dependencies aligned with the brief, `ANCHOR_MISSING` removal
  ends local-only, both Swift test files named, the late-publish window
  recorded as closed at closeout.

## Result

- Pending implementation.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Removal over fakes: success, partial, remaining, retryable, unknown, account, idempotent | pending | |
| `ANCHOR_MISSING` peer cases and removal path | pending | |
| Deletion-entry page and fresh establish in the found zone | pending | |
| Adapter tests, both targets | pending | |
| `./gradlew quality` | pending | |
| Physical timed re-link, both shapes, both directions | pending | |
| Threat-model closeout and `T-14` update | pending | |

## Blockers and accepted risks

- Maintainer decisions `D1` to `D5` in the brief precede implementation.
- The purge-window mechanism remains a hypothesis; the design removes zone
  deletion regardless, and `AC-04` is the proof.
- If `AC-04` fails, the fallback is a new zone identity per establish, a
  separate decision.

## Final

- **Status:** pending
- **Outcome:** pending
