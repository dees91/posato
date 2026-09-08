# Execution: `SYNC-009`

- **Brief:** [Integrate Keychain and CloudKit bootstrap on both apps without creating a parallel workspace](../specifications/sync-009-apple-bootstrap.md)
- **Status:** `blocked`
- **Review tier:** `high-risk`
- **Implementer:** implementation agent (2026-09-08)
- **Reviewer:** independent plan review complete (changes-required, resolved); completed-change review pending
- **Branch:** `feature/sync-009-apple-bootstrap`
- **Worktree:** `~/Projects/Polyglot/posato-sync-009`
- **Updated:** 2026-09-08

## Plan

1. Blocked on `D1` (how the explicit **Sync with iCloud** action is
   represented) and `D2` (destructive removal deferral). Implementation does
   not start until the maintainer answers both.
2. Add the narrow `commonMain` composition entry point: process-scoped
   single-flight, background dispatcher, established-context read, and no
   provider access before the action; `commonTest` over fakes.
3. Desktop graph: lazy non-fatal companion client, both macOS adapters,
   `JdkSyncCryptoProvider`, `SqlBootstrapStore`; the composition root in
   `Main.kt`.
4. iOS graph: both iOS adapters and `IosSyncCryptoProvider`; adopt the deferred
   `SYNC-007` production provider construction and off-main-thread rule.
5. Physical Mac and iPhone rows in both device orders under the maintainer's
   iCloud account, `./gradlew quality`, independent completed-change review,
   closeout and PR.

## High-risk plan review

- **Verdict:** `changes-required` (independent review, 2026-09-08; 2 Critical,
  6 Required, 4 Recommended), corrections applied to the brief before
  implementation.
- **Critical or Required findings:** `C-1` both candidate bootstrap triggers
  bypassed the explicit **Sync with iCloud** action that ADR 0007 and ADR 0002
  require, silently amending an accepted decision; `C-2` the ADR 0007
  destructive-removal evidence was unbuildable inside the stated boundaries,
  because `BootstrapCloudPort` has no zone delete; `R-1` the write surface
  omitted the platform sync adapter directories the deferred items touch;
  `R-2` graph-scoped single-flight cannot satisfy the "never twice
  concurrently" rule when a second iOS runtime is built; `R-3` eager
  `MacOsSyncCompanionClient.verified` construction would crash every plain
  Gradle run and driver launch; `R-4` the convergence criterion named no
  observable; `R-5` shared documentation and `Main.kt` surfaces were not
  declared against the other two wave tasks; `R-6` the plan added replica-store
  wiring that the brief did not carry and that has no consumer until
  `SYNC-010`.
- **Resolution:** `C-1` and `C-2` became blocking maintainer decisions `D1` and
  `D2` in the brief, with recommendations, and the outcome no longer assumes a
  per-launch bootstrap. `R-1` declared the adapter directories and dropped the
  `SYNC-008` helper refactor, which stays deferred. `R-2` made single-flight
  process-scoped with a test. `R-3` made companion construction lazy and
  non-fatal and promoted it to `AC-05`. `R-4` named the four convergence
  observables including the winner's item still being readable. `R-5` declared
  that this task does not amend the roadmap and that `QUALITY-005` merges first
  for the disjoint `Main.kt` region. `R-6` removed the replica store. Accepted
  Recommended items: `AC-06` narrowed to values that are actually secret, both
  device orders added to `AC-02`, the real graph's dispatcher asserted, and the
  visibility trap recorded in `D3`.

## Result

- Pending implementation.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | `pending` | |
| `commonTest` composition entry point | `pending` | |
| `jvmTest` and `iosTest` graph composition | `pending` | |
| Physical Mac and iPhone rows, both device orders | `pending` | maintainer iCloud account, sequential with other physical work |

## Blockers and accepted risks

- `D1` and `D2` block implementation. `D1` decides how the explicit consent
  action exists at all before `ONBOARDING-001`; `D2` decides whether the
  ADR 0007 destructive-removal evidence is built here or deferred to
  `SYNC-010`.
- The physical rows need the maintainer's iCloud account on both devices and a
  CloudKit private database without the custom zone. Whichever way `D2` is
  answered, the run leaves that database as found.
- Wave 7 runs in parallel with `QUALITY-005` (verification driver) and
  `SESSION-003` (session schema). Write surfaces are disjoint by design; this
  task must not touch `feature/session/**`, `feature/enforcement/**`, or
  `tools/posato-control/**`.

## Final

- **Status:** `blocked`
- **Outcome:** pending `D1` and `D2`
