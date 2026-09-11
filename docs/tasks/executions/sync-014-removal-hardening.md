# Execution: `SYNC-014`

- **Brief:** [Removal hardening](../specifications/sync-014-removal-hardening.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending handoff to an implementing agent
- **Reviewer:** plan reviewer and completed-change reviewer pending
- **Branch:** `feature/sync-014-removal-hardening`
- **Updated:** 2026-09-11

## Plan

1. Confirm the `observed` facts behind the brief in the adapters and the
   bootstrap phases, and note where each adoption path reads the key item.
2. Add the tombstone to `SyncBootstrap.sq` and `migrations/8.sqm`, write it
   inside `clearEstablished`, bound it, and cover it with store and migration
   tests.
3. Consult the tombstone after the zone step of the fresh attempt and at
   the top of `adoptWinner` before `deleteItemAndVerifyAbsent`, so both
   `reconcileFoundAnchor` and the anchor-create conflict path are covered;
   refuse in the join-only continuation as defense in depth; script the
   resurrected anchor in both fresh-attempt shapes, the conflict shape, and
   the lingering item; cover every `AC-01` and `AC-02` case. Note the
   pre-existing status flip on `confirmOwnAnchor` after a ghost read.
4. Add the harness removal-then-relink case and keep every existing removal
   and second-install test unchanged.
5. Update ADR 0007, the sync recipe (settle rule, ghost gotcha, quick re-link
   row), the threat-model owner rows, the wiki topic, and the wiki log at
   closeout; run focused tests, `./gradlew quality`, and the timed physical
   sequence; write the threat-model closeout statement; obtain the
   independent completed-change review.

## High-risk plan review

- **Verdict:** `changes-required` (independent reviewer, 2026-09-11),
  resolved in the brief before handoff.
- **Critical or Required findings:** `AC-01` demanded zero zone saves
  although ADR 0007 step 2 saves the fixed zone before the anchor read; the
  guard was placed on the candidate attempt while both destructive paths
  meet in `adoptWinner`, including the anchor-create conflict path the
  hypothesis predicts; `AC-04` claimed to observe the refusal, which the
  driver cannot tell from any other retryable outcome; the fresh workspace's
  survival past the purge horizon was unproven; the mechanism named one
  candidate where three must be separated by the runs; the threat-model
  closeout missed the `A-04` owner row and the fresh-install residual.
- **Resolution:** the boundary and `AC-01` allow the fixed zone save and
  script both shapes; `adoptWinner` is the choke point and the conflict shape
  joins `AC-02`; `AC-04` states observable facts, adds the tombstone count
  and a ten-minute survival check, bounds the presses, and mandates the
  both-removed variant; `D4` lists the three mechanisms and the per-press
  capture; `A-04` and the `T-14` residual are in the write surface. Advisory
  items folded: outcome no longer mentions item readability, `D1` states
  the no-expiry rule and the ghost recovery, `D3` enforces the bound inside
  the transaction, the roadmap paragraph is hedged, dependencies match the
  row, and the continuation guard is defense in depth. The reviewer
  confirmed serializing `SYNC-014` before `SYNC-012`.

## Result

- Pending implementation.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Tombstone store and `8.sqm` migration tests | pending | |
| Coordinator, candidate, and continuation refusal cases over fakes | pending | |
| Harness removal-then-relink case | pending | |
| `./gradlew quality` | pending | |
| Physical timed removal and re-link, both devices | pending | |
| Threat-model closeout statement and owner rows | pending | |

## Blockers and accepted risks

- `D1` to `D4` accepted by the maintainer on 2026-09-11; start at step 2.
- The mechanism is a hypothesis until the timed reproduction (purge
  re-attached by the same-identifier zone save, the old key item returning
  through iCloud Keychain, or a stale by-identifier read); the guard is
  correct for the observed case regardless. Removal clears the cursor and
  no sync engine exists, so token state is excluded.
- A fresh install without a tombstone can still join a ghost during the
  purge window; recorded as a limit with the creation-date option.

## Final

- **Status:** pending
- **Outcome:** pending
