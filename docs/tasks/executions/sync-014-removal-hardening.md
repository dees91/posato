# Execution: `SYNC-014`

- **Brief:** [Removal hardening](../specifications/sync-014-removal-hardening.md)
- **Status:** `done`
- **Review tier:** `high-risk`; PR correction: `standard`
- **Implementer:** implementing agent; PR correction by the reviewing agent
- **Reviewer:** a second agent, distinct from the implementing agent, 2026-09-11
- **Branch:** `feature/sync-014-removal-hardening`
- **Updated:** 2026-09-11

## Plan

1. Confirm the `observed` facts behind the brief and where each adoption
   path reads the key item.
2. Add the tombstone to `SyncBootstrap.sq` and `migrations/8.sqm`, write it
   inside `clearEstablished`, bound it, and cover it with store and migration
   tests.
3. Consult the tombstone after the zone step of the fresh attempt and at
   the top of `adoptWinner` before `deleteItemAndVerifyAbsent`, so both
   `reconcileFoundAnchor` and the anchor-create conflict path are covered;
   refuse in the join-only continuation as defense in depth; script the
   resurrected anchor in both fresh-attempt shapes, the conflict shape, and
   the lingering item; cover every `AC-01` and `AC-02` case.
4. Add the harness removal-then-relink case and keep every existing removal
   and second-install test unchanged.
5. Update ADR 0007, the sync recipe, the threat-model rows, the wiki topic
   and log; run focused tests, `./gradlew quality`, and the timed physical
   sequence; write the closeout statement; obtain the independent review.

## High-risk plan review

- **Verdict:** `changes-required` (independent reviewer, 2026-09-11),
  resolved in the brief before handoff.
- **Critical or Required findings and resolution:** the fixed zone save
  before the anchor read is allowed and both fresh-attempt shapes are
  scripted; `adoptWinner` is the choke point and the anchor-create conflict
  shape joins `AC-02`; `AC-04` states observable facts, adds the tombstone
  count, a ten-minute survival check, bounded presses, and the both-removed
  variant; `D4` lists three candidate mechanisms; `A-04` and the `T-14`
  residual joined the write surface. `SYNC-014` serializes before `SYNC-012`.

## Result

- `clearEstablished` writes the removed workspace identifier into
  `sync_removed_workspace` in the same transaction, evicts beyond 32, and
  `containsRemoved` is consulted after the fresh-attempt zone step and at
  `adoptWinner` before any key delete. The join continuation returns
  `UNCHANGED`. Refusal is retryable with the existing unlinked copy.
- Physical `AC-04`, signed Mac and iPhone 13 mini, 2026-09-11: Run 2 (both
  removed first, press about one minute after removal) established, joined,
  and kept one established row through the wait. Run 1 (Mac re-linked about
  two and a half minutes after removal while the iPhone was still linked)
  established a fresh identifier and reported completed, yet the website
  added while unlinked never reached the iPhone and, after a further wait,
  the Mac reported action required while keeping its established row. That
  is the `AC-04` survival check failing and the `SYNC-011` ghost outcome
  recurring in the purge-window variant; `inferred`: the tombstone never
  fired, because a completed establish cannot have matched a tombstoned
  anchor and every establish mints a fresh identifier.
- PR correction (Standard): record, wiki, recipe, and threat model state
  Run 1 truthfully and carry the residual; the iOS driver's `scrollTo`
  reports reached only when the element's centre is on screen; the Simulator
  fixtures were rerun with the rebuilt driver.

## Completed-change review

- **Verdict:** `approved` (independent reviewer, 2026-09-11); one advisory
  recipe count correction accepted.
- **Review evidence:** in `feature/sync/bootstrap/`:
  `SqlBootstrapStore.kt:47–109`, `BootstrapGates.kt:33–54`,
  `BootstrapCoordinator.kt:159–176`, `BootstrapAnchorPhase.kt:149–163`,
  `BootstrapJoinPhase.kt:107–127`, `SyncBootstrap.sq:1–121`, `8.sqm`; tests
  `SqlBootstrapStoreTest.kt:157–274`, `BootstrapCoordinatorTest.kt:709–871`,
  `BootstrapJoinTest.kt:38–50`,
  `AppleSyncTest.kt:106–137`, `FakeBootstrapPorts.kt:168–218`, the migration
  test, the four downgrade helpers; the brief, ADR 0007, the threat model,
  the sync recipe, the wiki topic, and this record.
- **Reviewer-run checks:** focused `:shared:jvmTest` for the tombstone,
  coordinator, join, harness, and migration suites; the PR review re-ran
  `:shared:jvmTest` for `app.posato.feature.sync.*` (300 tests in 35 suites),
  the `targets`, `onboarding`, and `session` data suites, `:shared:detekt`,
  and `:shared:verifySqlDelightMigration`; `git diff --check` clean.
- **PR correction review:** `approved` (a second agent, 2026-09-11) over
  the correction diff, the fixture log, and the driver; two advisory items
  (provenance tag, iPhone-side facts) folded.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Tombstone store and `8.sqm` migration tests | pass | `SqlBootstrapStoreTest`, `SqlRemovedWorkspaceMigrationTest` |
| Coordinator, candidate, and continuation refusal cases over fakes | pass | `BootstrapCoordinatorTest`, `BootstrapJoinTest` |
| Harness removal-then-relink case | pass | `AppleSyncTest` |
| `./gradlew quality` | pass | 2026-09-11, no new suppression |
| Physical Run 1: Mac removed 08:38:56Z, re-linked 08:41:23Z, iPhone still linked | fail (survival) | completed with one established row and accepted 4; iPhone joined and stayed completed; website never reached the iPhone; later the Mac reported action required with the row kept |
| Physical Run 2: Mac removed 09:42:07Z, iPhone removed 09:42:23Z, Mac re-linked 09:43:10Z | pass | pending then completed; iPhone joined; one established row through the wait; fixture domain removed, other websites retained |
| Threat-model closeout statement and owner rows | pass | `A-04`, `T-04`, `T-14`, closeout paragraph with the purge-window residual |
| Simulator fixtures with the rebuilt driver (centre-on-screen rule) | pass | `first-install-skip`, `add-website`, `remove-website`, `website-edit`, `session-start`, `first-install`, every step `ok`; runs `20260911-120213-15fe` to `20260911-120320-f0dd` under `build/verification/runs/` |

## Blockers and accepted risks

- `D1` to `D4` accepted by the maintainer on 2026-09-11. Accepted MVP limit
  (`user-confirmed`, 2026-09-11): a fresh workspace established within minutes
  of **Remove workspace** can be lost when the provider purges the same-name
  zone; the recipe's settle rule (wait at least ten minutes) is the mitigation.
  The fix direction is `open` for a later roadmap revision: unique zone identity
  per establish (an ADR 0007 arbiter change), a removal that deletes records and
  keeps the zone, or the settle rule kept as the MVP limit.
- The mechanism remains a hypothesis (purge re-attached by the same-name zone
  save fits Run 1 best); a fresh install without a tombstone can still join a
  ghost during the purge window.

## Final

- **Status:** `done`
- **Outcome:** `AC-01` to `AC-03` met; `AC-04` Run 2 met and Run 1 failed the
  survival check, reproducing the purge-window variant that this guard does
  not cover; recorded as an accepted limit with the settle rule and an open
  fix decision. Both devices ended linked to the newest workspace; the
  synthetic fixture domain was removed.
