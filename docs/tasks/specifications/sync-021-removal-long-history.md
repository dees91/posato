# `SYNC-021`: One press removes a Mac workspace with a long zone history

- **Review tier:** `standard`
- **Tier reason:** The expected fix changes how long one **Remove workspace**
  press keeps resuming the existing record deletion and what the row shows
  meanwhile. It does not change what removal enumerates, deletes, or
  verifies. The row becomes `high-risk`, with a plan review before
  implementation, if the fix changes the ADR 0007 removal procedure, the
  resume-token format or phases, key deletion, or ADR 0006 operation
  semantics. A test seam in product code (decision `D1`) needs the
  maintainer's approval first.
- **Dependencies:** none (release 1.4 wave 1); builds on the completed
  `SYNC-015` record-based removal and `SYNC-014` tombstone.
- **Integration group:** `PR-SYNC-REMOVAL-HISTORY`
- **Authority:** release roadmap revision 21 (PR #159), idea 32;
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  and its record-based removal amendment (the zone is never deleted by the
  app; enumeration and verification use the looped changes traversal from
  an empty token, never a query); the [threat model](../../security/apple-mvp-threat-model.md)
  `T-14`; [`DESIGN.md`](../../../DESIGN.md) removal copy.
- **Record:** [execution record](../executions/sync-021-removal-long-history.md)

## Outcome

On a Mac, one **Remove workspace** press removes the workspace right after a
link and when the zone carries a long change history. While it works, the
row says honestly that removal is in progress instead of failing at a fixed
deadline. The reproduction can be rebuilt on demand, and only after that is
the test Apple Account's zone history cleaned.

## Boundaries

- Find the cause before changing code. Candidates: the per-press work cap,
  the companion page budget, and the first sync after the link (execution
  record, hypotheses).
- Keep removal semantics: bundles first, the anchor last, idempotent
  batches, independent absence verification, the binding gate, and the
  account-changed and unknown-outcome stops. A pass that makes no progress
  still ends; continuing is allowed only while the resume cursor advances.
- The zone stays; the app still never deletes it. A from-empty or
  zone-deleting removal is out of scope.
- iPhone removal is out of scope. The record notes whether its path has the
  same per-press cap, and if so a new idea is filed.
- The test-account zone cleanup is an explicit last step. It runs only
  under the safety conditions in `AC-04`.

## Acceptance

- `AC-01`: before the fix, on the long-history fixture, the first press
  fails as recorded on 2026-10-09. After the fix, one press removes the
  workspace in each of 3 fresh Tart clones, with the time recorded.
- `AC-02`: right after `flow icloud link`, one press removes the workspace
  even while the first sync after the link is still running or has just
  ended.
- `AC-03`: during a long removal the row shows the agreed progress state
  (`D2`). A failure with no progress, or an account change, still ends in
  the existing retryable or action-required state, and a retry resumes.
- `AC-04`: the long-history fixture can be rebuilt from a recorded
  procedure and shows the pre-fix failure. Only then is the test account's
  zone history cleaned. Safety conditions: the test Apple Account only; no
  linked device (every clone destroyed, the test iPhone local-only); the
  workspace removed first; no new link within the purge window that
  `SYNC-014` measured. A routine link and removal afterwards is measured
  again.

## Verification

- Fresh `primary` Tart clones on the test Apple Account with
  `flow icloud link` and `flow icloud remove`, timed press by press, before
  and after the fix (`AC-01`, `AC-02`, `AC-03`), on the fixture and on the
  cleaned account (`AC-04`).
- A companion-side check of the continuation, only for a failure E2E cannot
  expose reliably, such as a cursor that stops advancing. It is written
  failing first.

## Decisions or blockers

- `D1` (fixture), `D2` (progress copy), `D3` (removal after the window
  closes), and `D4` (who deletes the zone) are open; options are in the
  pull request.
