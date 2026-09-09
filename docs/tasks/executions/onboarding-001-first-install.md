# Execution: `ONBOARDING-001`

- **Brief:** [Complete first-install privacy, Apple workspace, authorization, and target setup without a product account](../specifications/onboarding-001-first-install.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Muse Code (assigned 2026-09-09, plan approved by maintainer)
- **Reviewer:** independent plan review complete (changes-required, resolved in the brief); completed-change review pending
- **Branch:** `feature/onboarding-001-first-install`
- **Worktree:** `~/Projects/Polyglot/posato-onboarding-001`
- **Updated:** 2026-09-09

## Plan

1. `D1` through `D6` accepted by the maintainer on 2026-09-09 as
   recommended, after the plan-review corrections.
2. `LocalSetup.sq` and `6.sqm` with the singleton completion row and the
   upgrade seed, its store with the tri-state read, and migration
   verification for the empty and the seeded case.
3. Two narrow `commonMain` ports for helper state and iOS authorization; the
   desktop actual over `MacOsHelperClient` with the signing pre-check,
   `Dispatchers.IO`, and the System Settings route; the iOS actual over a new
   `requestAuthorization` provider capability executed by
   `IosLocalApplicationMappings`; Swift and desktop tests.
4. `feature/onboarding` step holder and screens over `PosatoPrivacyPoint`,
   `PosatoSetupStep`, and the existing `SyncBootstrapUiState`; the summary
   step reading its sentences from the services; `commonTest` over fakes;
   strings.
5. `PosatoApplication` hosts the flow outside the navigation scaffold before
   the destinations; `Main.kt` and both graphs provide the store and ports;
   composition tests.
6. `DESIGN.md` amendment, the `first-install-skip.json` prelude after every
   fresh launch in the skill, the `features/onboarding.md` recipe and the
   unattended `first-install.json` fixture, then the physical rows including
   the upgrade row, `./gradlew quality`, independent completed-change review,
   the `ios-enforcement.md` supersession, the threat-model closeout statement,
   wiki log, closeout and PR.

## High-risk plan review

- **Verdict:** `changes-required` (independent review, 2026-09-09; 7
  Required, 6 Recommended, 2 Optional), corrections applied to the brief
  before implementation.
- **Critical or Required findings:** `R-1` `6.sqm` had no upgrade seed, so
  every existing install would re-enter the flow and a linked device would
  get an untruthful iCloud step; `R-2` the Mac permission step was not
  buildable: a doomed `enable()` on an unverifiable helper leaves the client
  with a pending unknown request, the helper never opens System Settings,
  `enable()` is blocking and never moves approval required to ready, and
  `Main.kt` was outside the write surface; `R-3` the privacy and iCloud copy
  could promise cross-device arrival that `SYNC-011` and `SYNC-012` have not
  delivered; `R-4` the "done" step was undefined and would become onboarding
  success; `R-5` `D2` superseded a `user-confirmed` iOS synthesis sentence
  without citing it; `R-6` `AC-02` was unobservable as worded, since reading
  the authorization status is itself a Screen Time call and the helper client
  is constructed at launch; `R-7` the threat-model rule had no landing
  artifact.
- **Resolution:** `R-1` the migration seeds the row when product state
  exists, with an acceptance clause and an upgrade evidence row. `R-2` the
  desktop actual runs the signing pre-check first, runs the client on
  `Dispatchers.IO`, opens the Login Items settings pane from the application
  with **Check again**, and `Main.kt` plus the graph factory joined the write
  surface. `R-3` a no-cross-device-arrival rule in the privacy boundary and
  `AC-03`. `R-4` the last step is a service-read summary with one **Open
  Session** action. `R-5` `D2` cites the sentence and the brand diagram step
  and the closeout supersedes them; `ios-enforcement.md` joined the write
  surface. `R-6` `AC-02` names prompts, `requestAuthorization`, the helper
  process, CloudKit and Keychain access, and the bootstrap row. `R-7` the
  closeout statement is listed under verification. Accepted Recommended
  items: two narrow ports with Detekt deciding the provider shape, hosting
  outside the scaffold with a tri-state completion read, reuse of the single
  `SyncBootstrapUiState`, the iOS capability under the existing mutex and
  generation guard with a FamilyControls-free mapping type, no redaction test
  for enum-only carriers, the named prelude fixture with a deterministic
  decline, and landing on Session after a failed completion write. Accepted
  Optional: the membership sentence now follows `TB-07` wording.

## Result

- Persistence (`LocalSetup.sq`, `6.sqm` with upgrade seed), tri-state
  `SqlLocalSetupStore`, both platform ports with their actuals, the step
  holder and screens hosted outside the scaffold, both graphs, the
  `first-install.json` / `first-install-skip.json` fixtures, the
  `features/onboarding.md` recipe, and the `DESIGN.md` / wiki closeout are
  implemented and verified on Simulator, Mac, and the physical iPhone.
- Detekt decided the provider question: one `OnboardingDependencies` provider
  per graph. Zero new suppressions.
- `./gradlew quality` passes; one transient
  `:desktopApp:verifyMacOsDevelopmentPackaging` failure reproduced once and
  passed standalone and on the next two full runs (stale staged bundle, no
  source change needed).

## Completed-change review

- **Verdict:** `approve` (independent review, 2026-09-09, full
  `main...HEAD` diff against the brief and this record)
- **Critical or Required findings:** none; all seven check areas hold
  (6.sqm upgrade seed, tri-state read with hosting outside the scaffold, one
  provider per graph, Mac `Dispatchers.IO` + Login Items + Check again, no
  cross-device promises, single `SyncBootstrapUiState` reuse, skip prelude).
  Zero new suppressions confirmed.
- **Resolution:** no corrections required

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` (ktlint, Detekt, tests incl. `SqlLocalSetupStoreTest`, `SqlLocalSetupMigrationTest`, `OnboardingUiStateTest`, `DesktopMacHelperStateTest`, `ApplicationAuthorizationAccessTests`) | pass | full runs 2026-09-09, final `BUILD SUCCESSFUL`, 197 tasks |
| Simulator first-install full row (permission request → unavailable → Later → `example.com` → summary → Session) | pass | `build/verification/runs/20260909-103321-5559`, 23/23 steps, screenshots + snapshot |
| Simulator skip row (`first-install-skip.json` prelude to Session) | pass | `build/verification/runs/20260909-103647-c218`, 15/15 steps |
| Mac fresh launch, helper enable, upgrade seed, DB restore byte-identical | pass | `build/verification/runs/20260909-103758-5591`, `20260909-104001-f812`, `20260909-104237-808a` (`desktop-app.log`, `backup/desktop`, screenshots) |
| Physical iPhone Later row (fresh → 6 steps → Session) | pass | `build/verification/runs/20260909-104533-31f6`, 23/23 steps, screenshots + snapshot |
| Physical iPhone attended approval row (Ask → Apple Screen Time sheet → grant → Later → website → summary `Screen Time is allowed.` → Session) | pass | `build/verification/runs/20260909-104630-eb93` (Ask tap + system sheet) and `build/verification/runs/20260909-111713-b8a4` (13/13 steps to Session) |

## Blockers and accepted risks

- The maintainer's Mac already approved the helper, so the approval-required
  branch of the Mac permission step has unit evidence only.
- The handoff between the upgrade seed and the fresh-database rule depends on
  product state being a faithful proxy for "used install"; a database that
  was opened but never received a domain, policy, bootstrap row, or session
  shows the flow once, which is truthful.

## Final

- **Status:** `ready-for-review`
- **Outcome:** implementation, verification, and independent review complete;
  PR outstanding
