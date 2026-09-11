# Execution: `SYNC-014`

- **Brief:** [Removal hardening](../specifications/sync-014-removal-hardening.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** implementing agent
- **Reviewer:** a second agent, distinct from the implementing agent, 2026-09-11
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

- `clearEstablished` writes the removed workspace identifier into
  `sync_removed_workspace` in the same transaction, evicts beyond 32, and
  `containsRemoved` is consulted after the fresh-attempt zone step and at
  `adoptWinner` before any key delete. The join continuation returns
  `UNCHANGED`. Refusal is retryable with the existing unlinked copy.
- `8.sqm` adds the table. Existing downgrade helpers drop it before reopen.
- Pre-existing `confirmOwnAnchor` after a ghost read is unchanged:
  `Missing` is retryable; a different `Found` is action-required.
- Recommended recipe fix from completed-change review: tombstone count is
  at least one and must not increase during refused presses, not exactly one.
- Physical AC-04: two timed re-link sequences on the signed Mac and iPhone
  13 mini. Both first presses established directly (accepted fallback). The
  ghost was not reproduced; refusal remains the fake-port cases. iOS
  `scrollTo` now screen-swipes when Compose's wrapper fills the window, which
  was required to reach compact-Session iCloud actions.

## Completed-change review

- **Verdict:** `approved` (independent reviewer, 2026-09-11)
- **Critical or Required findings:** none
- **Resolution:** none required
- **Advisory findings:** recipe `sync_removed_workspace` count of exactly
  one fails after a second removal; accepted and corrected in the recipe.
- **Review evidence:** under `shared/src/commonMain/`, examined
  `kotlin/app/posato/feature/sync/bootstrap/SqlBootstrapStore.kt:47–109`,
  `BootstrapGates.kt:33–54`, `BootstrapCoordinator.kt:159–176`,
  `BootstrapAnchorPhase.kt:149–163`, `BootstrapJoinPhase.kt:107–127`,
  `sqldelight/app/posato/core/database/SyncBootstrap.sq:1–121`, and
  `sqldelight/migrations/8.sqm:1–8`. Under commonTest, examined
  `SqlBootstrapStoreTest.kt:157–274`, `BootstrapCoordinatorTest.kt:709–871`,
  `BootstrapJoinTest.kt:38–50`, `AppleSyncTest.kt:106–137`,
  `FakeBootstrapPorts.kt:168–218`, `SqlRemovedWorkspaceMigrationTest.kt:1–79`,
  and the four downgrade helpers. Also the brief, ADR 0007 amendment, threat-
  model `A-04`/`T-04`/`T-14`, the sync recipe, wiki topic, and this record.
- **Reviewer-run checks:** focused `:shared:jvmTest` for the tombstone,
  coordinator, join, harness, and migration suites plus existing removal and
  join harnesses, all passed. The later PR review independently re-ran
  `:shared:jvmTest` for `app.posato.feature.sync.*` (300 tests in 35 suites),
  the `targets`, `onboarding`, and `session` data suites, `:shared:detekt`,
  and `:shared:verifySqlDelightMigration`; `git diff --check` clean.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Tombstone store and `8.sqm` migration tests | pass | `SqlBootstrapStoreTest`, `SqlRemovedWorkspaceMigrationTest` |
| Coordinator, candidate, and continuation refusal cases over fakes | pass | `BootstrapCoordinatorTest`, `BootstrapJoinTest` |
| Harness removal-then-relink case | pass | `AppleSyncTest` |
| `./gradlew quality` | pass | 2026-09-11, no new suppression |
| Physical timed removal and re-link, both devices | pass (fallback) | two direct establishes; ghost not reproduced |
| Threat-model closeout statement and owner rows | pass | `A-04`, `T-04`, `T-14`, closeout paragraph |

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

- **Status:** `done`
- **Outcome:** met. Fake-port cases prove refusal. Physical runs recorded two
  direct establishes (peer still linked, then both removed) without a
  reproduced ghost; `D4` accepts that fallback. Both devices ended linked to
  the newest workspace; the synthetic fixture domain was removed.

Physical notes (categorical; no identifiers):

- Run 1, 2026-09-11: Mac **Remove workspace** 08:38:56Z (`sync_bootstrap_state`
  0, `sync_removed_workspace` 1). First **Sync with iCloud** 08:41:23Z while
  the iPhone was still linked: completed, bootstrap 1, tombstone 1, accepted 4.
  iPhone later joined that workspace (completed). The unlinked Mac fixture
  domain did not appear on the iPhone. After a further wait Mac reported
  action required with bootstrap still 1; iPhone stayed completed.
- Run 2, same day: Mac remove 09:42:07Z (bootstrap 0, tombstones 2); iPhone
  remove 09:42:23Z (unlinked). Mac press 1 at 09:43:10Z with the peer unlinked:
  bootstrap 1, pending then completed, tombstones 2. iPhone joined completed;
  Mac remained one established row and completed. Fixture domain removed on
  Mac; `wp.pl` and `x.com` retained.
