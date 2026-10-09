# `SYNC-021`: One press removes a Mac workspace with a long zone history

- **Review tier:** `high-risk`
- **Tier reason:** The removal fix alone would be Standard: it changes how
  long one **Remove workspace** press keeps resuming the existing record
  deletion and what the row shows meanwhile, not what removal enumerates,
  deletes, or verifies. The accepted development-only zone deletion (`D4`),
  and the record seeding that `D1` may need, put destructive CloudKit
  operations into product code. That a release build can never reach them
  is a security property, so a brief independent plan review precedes
  implementation. The fix itself still must not change the ADR 0007
  removal procedure, the resume-token format or phases, key deletion, or
  ADR 0006 operation semantics.
- **Dependencies:** none (release 1.4 wave 1); builds on the completed
  `SYNC-015` record-based removal and `SYNC-014` tombstone.
- **Integration group:** `PR-SYNC-REMOVAL-HISTORY`
- **Authority:** release roadmap revision 21 (PR #159), idea 32;
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  and its record-based removal amendment (the zone is never deleted by the
  app; enumeration and verification use the looped changes traversal from
  an empty token, never a query); the [threat model](../../security/apple-mvp-threat-model.md)
  `T-14`; [`DESIGN.md`](../../../DESIGN.md) removal copy.
- **Proposed authority changes:** a verification-only ADR 0007 amendment
  and a `T-14` update, drafted in the pull request; not accepted until the
  maintainer accepts their wording.
- **Record:** [execution record](../executions/sync-021-removal-long-history.md)

## Outcome

On a Mac, one **Remove workspace** press removes the workspace right after a
link and when the zone carries a long change history. While it works, the
macOS row says honestly that removal is in progress instead of failing at a
fixed deadline. The reproduction can be rebuilt on demand, and the test
Apple Account's zone history is cleaned under recorded controls.

## Boundaries

- Find the cause before changing code (record, step 1).
- Only removal gets the longer press. `AppleWorkspaceRemoval` passes a
  progress-bounded budget to `deleteWorkspaceRecords` and
  `sweepBundlesIfAnchorMissing`; every other caller, including the link's
  fresh-attempt sweep in `BootstrapMissingAnchorPhase`, keeps the cap of 10
  passes, so no ADR 0007 bootstrap step changes.
- Keep removal semantics: bundles first, the anchor last, idempotent
  batches, independent absence verification, the binding gate, and the
  account-changed and unknown-outcome stops. The companion, its pass
  bounds, and the resume-token format and phases do not change.
- The app never deletes the zone. Zone deletion and history seeding exist
  only in verification builds, under the proposed ADR 0007 amendment, with
  the controls listed in the record (step 5).
- iOS is out of scope and its UI does not change. It has the same per-press
  cap (`MAX_REMOVAL_CALLS` = 10); a new idea and backlog row own it.

## Acceptance

- `AC-01`: on the long-history fixture, one press of a pre-fix build
  (`572e341`) fails as recorded on 2026-10-09. One press of the fixed build
  removes the workspace in each of 3 fresh Tart clones, with the time
  recorded and `presses == 1` in the `flow icloud remove` envelope.
- `AC-02`: right after `flow icloud link` returns, which since PR #161 is
  after the first sync settles, the first press at the first moment the row
  allows it removes the workspace.
- `AC-03`: during a long removal the macOS row shows "Removing workspace…",
  the note, and the activity indicator, with both actions disabled, and a
  second press cannot start a second removal. With the guest network off
  the press ends in the existing retryable state, and a retry resumes.
- `AC-04`, in this order:
  1. clean the test account's zone with the verification tool;
  2. rebuild the fixture with the recorded procedure;
  3. a pre-fix build (`572e341`) fails on it;
  4. the fixed build passes (`AC-01`);
  5. clean again only if the account should be left empty, then wait at
     least 15 minutes with recorded timestamps, link once, and pass the
     `SYNC-014` ten-minute survival check before a timed routine removal.

  Each clean runs only when the zone's anchor is absent, `tart list` shows
  only this row's clone, a `posato-control` read of the test iPhone shows
  its iCloud row not linked, and the guest's signed-in Apple Account equals
  the configured test account. Cleaning covers the zone only; each removal
  already deletes its own workspace-key item, and the tool never touches
  Keychain items.

## Verification

- Fresh `primary` Tart clones on the test Apple Account with
  `flow icloud link` and `flow icloud remove`, timed press by press, before
  and after the fix (`AC-01` to `AC-03`) and through the `AC-04` order.
- Kotlin adapter tests with a fake transport, written failing first, only
  for the failure inventory in the record that E2E cannot produce.
- The release packaging and DMG checks shown to refuse a verification-flag
  package and to pass a release-configuration one.

## Decisions or blockers

All `user-confirmed` (2026-10-09):

- `D1`, fixture: step 1 measures first. If real link, publish, and removal
  cycles rebuild the history in about 30 minutes, the fixture uses them;
  otherwise the verification-only seeding operation.
- `D2`, progress: "Removing workspace…" with an activity indicator and a
  note that an older workspace can take a few minutes, on macOS.
- `D3`, after the window closes: removal continues while Posato runs; after
  a quit, the next press starts again idempotently. No cursor is stored.
- `D4`, zone cleanup: a verification-only delete-zone operation that the
  agent runs, under the `AC-04` conditions.

Blocker: implementation waits for the maintainer's acceptance of the
proposed ADR 0007 amendment and `T-14` text, and for the plan re-review.
