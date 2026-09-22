# Execution: `TARGETS-006`

- **Brief:**
  [`../specifications/targets-006-www-coverage.md`](../specifications/targets-006-www-coverage.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Grok
- **Reviewer:** maintainer completed-change review, third pass on `bf4d1e6`
- **Branch:** `feature/targets-006-www-coverage`
- **Updated:** 2026-09-18

## Plan

1. Remove materialized counterparts, the read-path rewrite, and schema 10.
2. Match `www` and apex in `ExactHostPolicy.matches`; expand iOS `WebDomain`s
   at apply; keep one stored row; say so at entry and on the row.
3. Clarify ADR 0005, restated roadmap revision 2, restore recipes to one row.

## Result

- Matching-rule rework after the completed-change review rejected persistence.
  One stored row; macOS `ExactHostPolicy.matches` and iOS apply-time
  `WebDomain` expansion; entry and row copy name the counterpart.

## Completed-change review

- **Verdict:** third pass on `bf4d1e6` `approved` after AC-04; earlier passes
  on `af6636b` and `ae99685`+`601db43` were `changes required`
- **Critical or Required findings:** pairwise matcher vs key equality;
  counterpart treated as covered at entry; none remaining after `bf4d1e6`
- **Resolution:** `matches` is pairwise with the shared counterpart vector;
  batch and editor count a counterpart as a duplicate
- **Advisory findings:** caption `remember` and hide-when-listed taken;
  helper key precompute declined (lookup stays linear, same as before)

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | after pairwise matcher and covered-at-entry |
| Simulator add/remove `example.com` | pass | one SQL row; run `20260918-154450-bd2d` |
| Desktop add/remove `targets006-proof.example` | pass | one SQL row; run `20260918-154621-613b` |
| `xcodebuild test` `IosEnforcementTests` (iPhone 17 Simulator) | pass | 17 executed, 3 skipped (device-only), 0 failures; `testWebsitesOnlyApplySetsFilterAndLeavesApplicationsUnset` and `testWwwCounterpartVectorExpandsTheShieldSet` passed |
| Physical Safari/Chrome AC-04 | pass | Desktop session via posato-control with stored `example.com` (`Restrictions active.`, no Retry). Safari titles: pause page at `http://example.com/` and `http://www.example.com/`; Chrome HTTP pause page for both; `curl --proxy` pause HTML for both HTTP hosts; CONNECT 403 for both HTTPS hosts; control `example.org` tunneled (HTTP Example Domain, CONNECT 200). Evidence under ignored `build/verification/runs/20260918-155423-b786/ac04/` and session screenshots `20260918-155423-b786`, `20260918-155720-8143`. |

## Blockers and accepted risks

- Mac cleanup after `af6636b` is done: extra `www.example.com` and
  `www.example.net` removed in the UI; `user_version` is 10 and
  `www_counterpart_expansion` is dropped. `example.com` and `example.net`
  remain.
- Accepted leftover: a 1.0 list that already stored both hosts still works;
  the row caption is then redundant and is not cleaned here.

## Final

- **Status:** `done`
- **Outcome:** matching-rule www coverage, AC-04 passed on the supported Mac
