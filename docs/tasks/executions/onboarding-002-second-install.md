# Execution: `ONBOARDING-002`

- **Brief:** [Second install joins and waits](../specifications/onboarding-002-second-install.md)
- **Status:** `complete`; implementation and applicable verification passed
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent revised-plan and completed-change reviews approved
- **Branch:** `feature/onboarding-002-second-install`
- **Updated:** 2026-09-10

## Plan

1. Amend ADR 0007 and DESIGN.md using accepted D1/D2 before behavior changes.
2. Add the process-local fresh-join continuation and bounded outcome table;
   preserve candidate/established recovery and test forbidden effects.
3. Share continuation routing between manual and foreground checks, coalesce
   overlaps, and test adoption, changed context, failures, and cancellation.
4. Apply the waiting UI hierarchy and existing native announcement bridge;
   preserve separate local-save/sync facts and collapsed Session controls.
5. Update recipes/wiki; run focused checks, full quality, Simulator fixtures,
   and attended physical joins in both orders with agreed cleanup.
6. Resolve independent review findings, rerun affected checks, and close out
   the record with one wiki-log entry when verification is complete.

## High-risk plan review

- **Initial verdict:** `changes-required` (independent reviewer, 2026-09-10).
  Required corrections defined the store-None boundary, account/anchor
  referent, missing/transient outcomes, actual waiting-device evidence, and
  the exclusion of static UI tests. Candidate/established recovery stayed
  separate. `d2d7635` records the initial resolution.
- **Accepted refinement:** manual and foreground retry share the same bounded
  continuation; Continue is primary; local-save/sync facts stay separate;
  physical immediate joining and observed waiting remain distinct. Narrow
  wiring to the existing Mac announcement bridge was added to the surface.
- **Revised verdict:** `approve`, zero open Critical/Required. The reviewer
  checked brief outcomes/UI/acceptance, execution plan, wiki topics, and the
  coordinator, anchor/item phases, store mapping, ADR 0007, and DESIGN.md.
  `git diff --check` passed; this documentation-only plan review ran no tests.

## Result

- Implemented a process-local fresh-join continuation shared by manual retry
  and foreground. It retains consented binding/anchor context, reads only
  until verified adoption, and reconciles interrupted persistence before retry.
- Continue remains primary while waiting; Check again is secondary. Session
  keeps its collapsed status row, and the summary separates local saves from
  sync state. Manual checks use the existing Mac announcement bridge.
- Updated ADR 0007, DESIGN.md, verification recipes, and wiki synthesis.
  PoC bootstrap/evidence informed the checks; no experiment code was imported.
- Threat-model review: no new store, key format, transport, or membership
  boundary. TB-07/T-04/R-04 still apply; accepted consent timing is explicit
  in ADR 0007. Both physical device orders joined with immediate key access.

## Completed-change review

- **Verdict:** `approve`; refreshed independent review on 2026-09-10 covers
  the original implementation and PR corrections; zero open Critical/Required.
- **Reviewer:** Codex agent `/root/review_onboarding002_implementation`,
  distinct from implementing agent `/root`.
- **Required resolution:** persisted adoption followed by cancellation now
  has regression coverage for exact/conflicting row reconciliation, one
  commit/exchange, and bounded UI retry after storage failure.
- **Review evidence:** under `shared/src/commonMain/kotlin/app/posato/feature/`,
  reviewed `sync/bootstrap/BootstrapJoinPhase.kt:8–165`, `AppleSync.kt:33–232`,
  `BootstrapCoordinator.kt:25–249`; `sync/ui/SyncBootstrapUiState.kt:17–69`,
  `SyncAnnouncements.kt:16–38`, `SyncBootstrapSection.kt:59–169`;
  `onboarding/OnboardingSteps.kt:124–186,225–286` and `OnboardingScreen.kt:60–146`.
  Also checked `PosatoApplication.kt:65–122` and resource strings `201–204`.
  Under commonTest's corresponding sync paths, checked `AppleSyncJoinTest:18–226`,
  `BootstrapJoinTest:16–233`, `AppleSyncTestHarness:24–81`,
  `FakeBootstrapPorts:168–218`, and `SyncBootstrapUiStateTest:11–62`;
  the brief `1–7,88–200` and changed sync-recipe instructions.
- **Reviewer-run checks:** `:shared:jvmTest` filtered to
  `app.posato.feature.sync.*` and `app.posato.feature.onboarding.*`:
  294 tests in 33 suites passed, no failures/errors/skips.
  `:shared:detekt`, `:shared:ktlintCheck`, and `git diff --check` passed.
- **PR corrections:** Session status gains polite live-region semantics
  without visual restyling; summary override applies only to a pending join;
  brief status points to this record and recipe prose wrapping is restored.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `BootstrapJoinTest` bounded checks and outcomes | passed | JVM regressions |
| Manual/foreground, stale context, no consent, cancellation | passed | `AppleSyncJoinTest`, `AppleSyncTest` |
| Holder routing / native waiting presentation | tests passed / physical wait not observed | Delayed-key tests; no spoken-delivery claim |
| `./gradlew quality` | passed after PR corrections | JVM/iOS tests, lint, packaging |
| Simulator full / skip first-install fixtures | passed | UI captures; website/bootstrap/completion counts 1/0/1 and 0/0/1 |
| Simulator degraded attempt after PR corrections | passed | Summary retains local scope; Session reports retryable; bootstrap rows 0 |
| Signed Mac launch and collapsed/expanded Session | passed after PR corrections | Driver snapshot and screenshots |
| iPhone build/install/launch and UI smoke | passed on retry | Initial automation-mode timeout; unlocked-device retry passed |
| iPhone joins existing workspace from Session and relaunches | passed | Completed attempt after consent and relaunch; wait not observed |
| Physical A: Mac establishes, fresh iPhone joins | passed | Onboarding status and summary; immediate key availability |
| Physical B: iPhone establishes, fresh Mac joins | passed | Onboarding status/summary; bootstrap rows 0 → 1 |
| Post-join permission and local selection | passed on both devices, including relaunch | One local selection each; Mac pending/accepted remain 0/0 |
| Threat-model closeout statement | reviewed | Existing TB-07/T-04/R-04; ADR 0007 amendment |

## Blockers and accepted risks

- Maintainer approved clearing disposable dev data without restoration. Both
  devices remain linked with one local app selection each; no session is active.
- Automatic checks do not repeat unchanged waiting announcements. Manual
  progress/completion must remain observable even with the same outcome.
- Waiting is not persisted; relaunch requires explicit consent again. This
  iteration accepts that weaker UX without dismissing durable continuation.
- Waiting was not observed in either physical direction; delayed-key behavior
  and speech remain unit/code evidence only. No Keychain settings were changed.
- Synced domains, policies, and applications stay invisible on the joining
  device until `SYNC-011`; this task claims nothing about them.

## Final

- **Status:** complete; ready for PR review
- **Outcome:** both fresh joins and local setup passed; waiting remains an explicit physical evidence limit.
