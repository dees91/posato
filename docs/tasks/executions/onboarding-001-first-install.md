# Execution: `ONBOARDING-001`

- **Brief:** [Complete first-install privacy, Apple workspace, authorization, and target setup without a product account](../specifications/onboarding-001-first-install.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending assignment (brief prepared 2026-09-09)
- **Reviewer:** independent plan review pending; completed-change review pending
- **Branch:** `feature/onboarding-001-first-install`
- **Worktree:** `~/Projects/Polyglot/posato-onboarding-001`
- **Updated:** 2026-09-09

## Plan

1. Maintainer answers `D1` through `D3`; `D4` through `D6` stand unless
   changed.
2. `LocalSetup.sq` and `6.sqm` with the singleton completion row, its store,
   and migration verification.
3. Narrow `commonMain` ports for helper state and iOS authorization, with the
   desktop actual over `MacOsHelperClient` and the iOS actual over a new
   provider capability that requests authorization without the picker; Swift
   and desktop tests.
4. `feature/onboarding` step holder and screens over `PosatoPrivacyPoint`,
   `PosatoSetupStep`, and the existing `SyncBootstrapUiState`; `commonTest`
   over fakes; redaction test; strings.
5. `PosatoApplication` hosts the flow before the destinations and hides
   navigation until completion; both graphs provide the store and ports
   through one port; composition tests.
6. `DESIGN.md` amendment, the `verify-posato` fresh-launch prelude, the
   `features/onboarding.md` recipe and unattended Simulator fixture, then the
   physical rows, `./gradlew quality`, independent completed-change review,
   wiki topic and log, closeout and PR.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

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

## Final

- **Status:** `active`
- **Outcome:** pending
