# Execution: `SYNC-011`

- **Brief:** [Policy convergence](../specifications/sync-011-policy-convergence.md)
- **Status:** `done`; implementation, correction review, and the attended physical gate complete 2026-09-10; merge pending maintainer decision
- **Review tier:** `high-risk`; PR correction: `Standard`
- **Implementer:** implementing agent; PR correction by reviewing agent
- **Reviewer:** independent plan and completed-change agents; PR #47 reviewers
- **Branch:** `feature/sync-011-policy-sync`
- **Updated:** 2026-09-10

## Plan

1. Confirm exchange/projection, volatile queue, store transaction, and refresh boundaries.
2. Add migration `7.sqm`, ordered durable intents, and an applied-base singleton;
   record intent with linked saves and clear both with workspace removal.
3. Drain intents, publish, consume, decide the name, republish, then apply under
   the shared write gate. On no-base passes, author only after consume (`D11`).
4. Cover merge, skip, capacity, corruption, lost removal, and in-flight saves;
   exercise two-harness convergence, restart durability, and frozen sessions.
5. Forward reconciler-only policy changes to both view models; preserve editor
   state. Derive onboarding counts from policy, with receipt-keyed focus.
6. Update copy, design, recipes, threat-model owners, and wiki; run quality,
   native fixtures, attended physical matrix, and independent completed review.
7. PR correction: apply pending intents in order; observe policy changes only
   while Summary is visible, subscribe before the first read, and cancel on exit.

## Independent plan review

- Initial verdict: `changes-required`, 2026-09-10; resolved before implementation.
  Critical: advance the base only in the successful policy transaction, never
  before apply or on refusal. Required: physical group-presence claim, same-pass
  backfill, authored-bundle counting, frozen-session scope, cleanup consequences,
  truthful capacity copy, threat-model owners, and complete write surface.
- Maintainer review required durable intent for lost removals, default-name
  precedence after consume, separate capacity reasons and exits, no immediate
  enforcement promise, and pre-link consequence copy (`D1`–`D8`).
- Delta plan review: `changes-required`, resolved before implementation. Protect
  pending saves during apply (`D10`); learn remote HLC before first-link authoring
  (`D11`); qualify shared-capacity attention to current at-cap outcomes (`D5`).
  Align capacity acceptance criteria, HLC ordering, and receipt-keyed focus.
- `user-confirmed`, 2026-09-10: the maintainer accepted `D9`–`D11` and the `D5`
  amendment when approving the correction plan. `D9` now includes live Summary
  refresh; website-entry reads remain entry/post-save only. No extra plan file.

## Result

- Linked policy saves atomically persist ordered intents. The drainer retains
  failed work and deletes only accepted/skipped intent. Base and intents clear
  with the replica; migration preserves existing policy rows.
- The reconciler shares the raw store's write gate with saves, holds it only
  for read/merge/apply, and retries one revision conflict. Base advancement is
  transactional with apply. Capacity is classified before bounded conversion;
  refusal keeps policy/base. No writes bypass the reducer into `sync_*` tables.
- Established merges apply reduced winners; first-link seeding holds the gate
  and skips pending removals. The post-consume name is re-read before apply.
  Distinct fake crypto streams prevent accidental cross-harness identifier clashes.
- Policy change signals refresh Targets and Session silently, preserving drafts
  and frozen-session summaries. D5 reasons use existing status surfaces; D8
  linking copy uses existing onboarding and Session controls, with no new button.
- PR review corrections cover stale capacity reasons, same-database lost-removal
  recovery, losing-edit convergence, seed races, and capacity classification.
  Seven earlier P1 threads were resolved after re-review at `127b1a9`.
- Latest correction preserves pending-intent order during apply. Suspended-fetch
  tests inspect the local policy between passes for remove/re-add and add/remove;
  the former failed before the fix. Summary now refreshes from the existing
  signal while visible, reads serially, retains its last count on read failure,
  and cancels on exit. Receipt-keyed website-entry focus is unchanged.
- Independent correction review found an initial-read subscription gap. A gated
  read test reproduced it; subscribing before the initial read fixed it.
  Re-review found no remaining actionable P1/P2 in the correction.
- Design, brief, recipes, and wiki reflect accepted behavior. No additional
  wiki-log entry was added for the correction; the PR keeps its single entry.

## Completed-change review

- PR #47 reviews identified actionable defects; accepted fixes and regression
  evidence are recorded in the threads. Final correction review: no remaining
  P1/P2, independent reviewer, 2026-09-10; no tests run by that reviewer.
- This covers code corrections, not completion of the attended physical gate.
  Group-rename advisory requires a control outside this task and does not expand scope.

## Verification

| Check | Result / evidence |
| --- | --- |
| Pending-order regression before correction | 1 failure / 2 tests, `build/verification/pr47-rereview/pending-order-before.log` |
| Summary initial-read race before correction | Reproduced, `build/verification/pr47-rereview/summary-race-before.log` |
| Corrected focused tests, Detekt, ktlint | Pass, `build/verification/pr47-rereview/correction-focused.log` |
| Final aggregate quality and migration | Pass: 515 JVM / 513 shared iOS tests, Swift tests (110, six skipped), migration, host builds; `build/verification/pr47-rereview/quality-correction.log` |
| Prior Simulator website/first-install fixtures | Pass: skip, add, edit, remove, full flow; fixtures unchanged |
| Corrected native onboarding | Pass: `build/verification/runs/pr47-correction-mac-onboarding/` and `build/verification/runs/pr47-correction-sim-onboarding/`; saved row read back, screenshots inspected; Mac databases restored byte-identical |
| Physical bidirectional website convergence | Pass: `-11` Mac→iPhone (`find` plus shot `build/verification/runs/20260910-211738-1bdf/`), `-12` iPhone→Mac (canonical read-back of both), Session summary showed 2 with no active session (`build/verification/runs/20260910-211855-8020/`), removals converged both ways, accepted 5→7 then stable across 3 repeat exchanges |
| Physical group convergence, selections local | Pass mirrored (both devices already held the default group, so the brief order was adapted with maintainer agreement): the fresh Mac join received `Applications` with no selections and its review showed `0 applications, On this Mac only`; the iPhone `1 application, On this iPhone only` was unchanged by every exchange; Mac `application_policy` stayed 1 |
| Physical pre-link backfill after removal/re-link | Pass after one recovery cycle: the first re-link joined a stale zone silently (no `-13` after 3 syncs plus relaunch; a later zone-gone attention state confirmed the divergence); after both-local-only plus ~8 min settle, the Mac established newest, the iPhone joined, `-13` arrived (`build/verification/runs/20260910-220323-f8c3/`), and both kept their websites |
| Physical offline retry | Pass: the airplane-mode add of `-14` stayed local with `Sync didn't finish. Choose Sync now to try again.` (`build/verification/runs/20260910-220831-013d/`); reconnect auto-completed the retry; Mac accepted rose 3→5 (fresh author registration plus `-14`), `-14` arrived exactly once canonically, repeats stayed stable; cleanup converged to empty on both sides and the Mac databases were restored byte-identical |

## Closeout and remaining limits

- Threat-model closeout: convergence adds no transport, key, or diagnostic
  surface. Base/intents stay app-private, bundles encrypted, and status copy
  contains no domain values. Owner rows are updated in the same PR.
- Linking unions websites, including re-adding a peer's pre-link removal;
  cleanup removes fixture domains only and cannot restore per-device originals.
- Rejected bundles still pin the cursor. Historical capacity outcomes below
  the cap are silent; the person may need to compare devices and re-add websites.
- iOS reapply during a session remains limited to poll loss, retry, or relaunch.
  Workspace removal keeps local choices without tracking their remote origin.
- Native onboarding used local-only fixtures; late remote arrival is covered by
  gated common tests, not by those captures. The four attended physical rows
  passed 2026-09-10; merge is the maintainer's decision.
- Re-linking within minutes of removal plus establish can adopt a stale key
  and report completed inside a ghost zone (observed once: no `-13` after 3
  syncs plus relaunch; a zone-gone attention state after the peer's removal
  confirmed the divergence). Settling (~8 min here) before re-linking healed
  it. Proposed follow-up: a recipe settle rule and a diagnostics comparison
  of the two workspaces.
- The iPhone 13 mini was left linked to the newest workspace, empty, its
  selection intact; the Mac databases were restored byte-identical after the
  run (hashes verified against the pre-run backup).
