# Execution: `SYNC-015`

- **Brief:** [Record-based removal](../specifications/sync-015-record-removal.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** implementation complete in-session (2026-09-11)
- **Reviewer:** plan review done; completed-change review `one-more-pass` (2026-09-11)
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

- `MailboxPort` exposes `deleteWorkspaceRecords` and
  `sweepBundlesIfAnchorMissing`; both native stores enumerate by type and
  name through the looped changes traversal, delete bundles before the
  anchor in bounded idempotent batches, and no zone delete remains.
- `ANCHOR_MISSING` maps to action required with local-only peer removal;
  foreign-context bundles skip as progress; the fresh-attempt sweep
  re-reads the anchor and adopts on race.
- ADR 0007, `T-14`, the recipe, and the wiki topic carry the outcome; the
  settle rule stays until `AC-04`.

## Completed-change review

- **Verdict:** `one-more-pass`, do not merge yet (independent reviewer, 2026-09-11)
- **Critical:** none; **Required (1):** the wiki `observed` gate claim
  outran the record's evidence column — reconcile with real gate output.
- **Resolution:** re-ran every gate in-session and entered the observed
  counts below; no code-behavior rework found.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Removal over fakes: success, partial, remaining, retryable, unknown, account, idempotent | done 2026-09-11 | `jvmTest` 544/544, `iosSimulatorArm64Test` 540/540, 0 failures |
| Gates re-run after rebase onto `e40deed` | done 2026-09-12 | `quality` exit 0, companion `swift test` 131/131 pass |
| `ANCHOR_MISSING` peer cases and removal path | done 2026-09-11 | same suites green, new cases included in totals above |
| Deletion-entry page, foreign skip, fresh establish, sweep race | done 2026-09-11 | companion `swift test` 131/131 pass; fakes covered in `jvmTest` |
| Adapter tests, both targets | done 2026-09-11 | `iosSwiftTest` 115 run, 6 skipped, 0 failures; `iosSimulatorArm64Test` green |
| `./gradlew quality`, lint, diff scan | done 2026-09-11 | `quality` exit 0, `git diff --check` clean, no new suppression |
| Physical timed re-link, both shapes, both directions | done 2026-09-12 | full matrix green: S1, S2, R1, R2 each with 11-14 min settle; Mac 1 row, accepted 4 stable, pending 0; fixture arrived over sync in every shape; cleanup left both linked to D with 2 websites |
| AC-04 interim, Mac-first | recorded | S1: removal 08:32:42 local-only in 13 s; press +32 s completed, 1 row, accepted 4; iPhone waited for key B, then removed (B intact), linked, fixture arrived; settle 11 min green. S2: both removed, Mac re-established, iPhone linked, deleted fixture re-arrived; settle 13 min green |
| AC-04 interim, iPhone-first | recorded | R1: iPhone removed, established C, completed; Mac attempt vs C ended action-required; Mac removed (C intact, tombstone 5), deleted fixture locally, linked, fixture re-arrived; settle 14 min green. R2: both removed (tombstone 6), iPhone established D, Mac linked, fixture re-arrived; settle 14 min green; Mac fixture deletion propagated to iPhone within a minute |
| Threat-model closeout and `T-14` update | done 2026-09-11 | file updated |

## Blockers and accepted risks

- Maintainer decisions `D1` to `D5` accepted as recommended
  (`user-confirmed`, 2026-09-11); implementation may start.
- The purge-window mechanism remains a hypothesis; the design removes zone
  deletion regardless, and `AC-04` is the proof.
- If `AC-04` fails, the fallback is a new zone identity per establish, a
  separate decision.

## Final

- **Status:** done (review reconciled, `AC-04` passed 2026-09-12)
- **Outcome:** record-based removal proven over fakes, adapters, and the
  physical matrix; purge-window class closed by construction, settle retired

## Review correction P1/P2 (2026-09-12, PR #50, no hosted review)

- **Outcome:** bounded resumable deletion with progress guarantee (P1) and
  non-atomic mixed-result delete mapping with whole-operation tests (P2).
- **Boundaries:** one new outcome token `INCOMPLETE=18`; Kotlin adapter
  resume loops (cap 10); iOS provider-held resume tokens; no `AC-04` rerun.
- **Design point:** the per-set anchor re-read applies to the anchorless
  sweep only (`D5` scope). Full removal runs on `READY` with the own anchor
  present, so its drain deletes unconditionally and the anchor-delete tail
  plus the emptiness re-scan decide the outcome; a presence gate in the
  drain would abort every production removal.
- **Independent review:** Standard-tier completed-change review, verdict
  `one-more-pass` with 1 Critical (own-anchor abort risk in the first iOS
  drain draft). Fixed by removing the gate from the iOS delete pass and
  pinning the contract with `testDeleteProceedsWhileOwnAnchorPresent`
  (iOS) and `givenPresentAnchorDuringDrainWhenDeletingThenBundlesStillGoFirst`
  (companion). The reviewer's remaining text was truncated past item 4;
  items 5-6 (factory use, mapper mix) self-verified against the diff.
- **Checks:** companion `swift test` 138/138; `jvmTest` 553/553;
  `iosSwiftTest` 126 run, 6 skipped (pre-existing), 0 failures;
  `./gradlew quality` exit 0. UI driving out of scope per `verify-posato`
  (native sync-adapter work, no user action).

## Follow-up correction (2026-09-12, PR #50 comments 3996258047, 3996258371)

- **Outcome:** cross-retry resume continuations (mac adapter slots, iOS
  provider-held tokens), wall-clock checkpoints with 2 s reserve,
  verify-phase token byte, `ANCHOR_MISSING` routed to the gated sweep.
- **Defect found in review of the WIP:** the companion banked the fetched
  token before running that page's deletes, so a reserve-triggered
  checkpoint skipped live records. Fixed to delete-then-bank (iOS already
  did); pinned by a slow-delete deadline test proven to fail on the old
  order. Lint splits: `DeleteResumeToken.swift`, `RecordSweep.swift`,
  `RecordDeletionDeadlineTests.swift`; no suppressions.
- **Independent review:** Standard, verdict `fix-first` (1 Required: a
  keep-cursor test asserted the in-call payload index). Fixed the index,
  added the sweep-side keep test, proved both by mutation.
- **Checks:** companion `swift test` 142/142, SwiftLint 0, `jvmTest`
  561/561, `iosSimulatorArm64Test` 547/547, `iosSwiftTest` 128 run,
  6 skipped (pre-existing), 0 failures, `./gradlew quality` exit 0.
