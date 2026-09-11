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
3. Consult the tombstone in the fresh attempt before the item read, in the
   candidate reconciliation before the losing-candidate cleanup, and in the
   join-only continuation; script the resurrected anchor and lingering item
   in the fakes and cover every `AC-01` and `AC-02` case.
4. Add the harness removal-then-relink case and keep every existing removal
   and second-install test unchanged.
5. Update ADR 0007, the sync recipe (settle rule, ghost gotcha, quick re-link
   row), the threat-model owner rows, the wiki topic, and the wiki log at
   closeout; run focused tests, `./gradlew quality`, and the timed physical
   sequence; write the threat-model closeout statement; obtain the
   independent completed-change review.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

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

- Maintainer decisions `D1` to `D4` in the brief precede implementation.
- The mechanism is a hypothesis until the timed reproduction; the guard is
  correct for the observed case regardless.
- A fresh install without a tombstone can still join a ghost during the
  purge window; recorded as a limit with the creation-date option.

## Final

- **Status:** pending
- **Outcome:** pending
