# Execution: `ONBOARDING-001`

- **Brief:** [Complete first-install privacy, Apple workspace, authorization, and target setup without a product account](../specifications/onboarding-001-first-install.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending assignment (brief prepared 2026-09-09)
- **Reviewer:** independent plan review complete (changes-required, resolved in the brief); completed-change review pending
- **Branch:** `feature/onboarding-001-first-install`
- **Worktree:** `~/Projects/Polyglot/posato-onboarding-001`
- **Updated:** 2026-09-09

## Plan

1. Maintainer answers `D1` through `D3`; `D4` through `D6` stand unless
   changed.
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

- Pending.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | | |

## Blockers and accepted risks

- `D1`, `D2`, and `D3` are maintainer decisions; implementation of the
  affected steps waits for them.
- The maintainer's Mac already approved the helper, so the approval-required
  branch of the Mac permission step has unit evidence only.
- The handoff between the upgrade seed and the fresh-database rule depends on
  product state being a faithful proxy for "used install"; a database that
  was opened but never received a domain, policy, bootstrap row, or session
  shows the flow once, which is truthful.

## Final

- **Status:** `active`
- **Outcome:** pending
