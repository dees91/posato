# Execution: `TARGETS-006`

- **Brief:**
  [`../specifications/targets-006-www-coverage.md`](../specifications/targets-006-www-coverage.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Grok
- **Reviewer:** maintainer completed-change review of `af6636b`
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

- **Verdict:** `changes required` on `af6636b`; second pass on `ae99685`+`601db43`
  also `changes required` (two P1)
- **Critical or Required findings:** pairwise matcher vs key equality; counterpart
  treated as covered at entry
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
| Physical Safari/Chrome | pending | AC-04, maintainer-attended |

## Blockers and accepted risks

- Mac cleanup after `af6636b` is done: extra `www.example.com` and
  `www.example.net` removed in the UI; `user_version` is 10 and
  `www_counterpart_expansion` is dropped. `example.com` and `example.net`
  remain.
- Accepted leftover: a 1.0 list that already stored both hosts still works;
  the row caption is then redundant and is not cleaned here.

## Final

- **Status:** `active`
- **Outcome:** rework after review; Mac schema leftover cleared
