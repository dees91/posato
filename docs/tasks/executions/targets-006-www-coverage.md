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

- **Verdict:** `changes required` on `af6636b`
- **Critical or Required findings:** direction (materialized counterparts);
  write in a read path; batch double-count; accepted documents; tests
- **Resolution:** matching-rule rework in this correction
- **Advisory findings:** none accepted as scope

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | worktree after rework |
| Simulator add/remove `example.com` | pass | one SQL row; caption present; run `20260918-151409-9897` |
| Desktop add/remove `targets006-proof.example` | pass | `1 added`; one SQL row; caption in the row name; run `20260918-151445-ce36` |
| Physical Safari/Chrome | pending | AC-04 after Mac list/`user_version` cleanup |

## Blockers and accepted risks

- Before `AC-04`: in the UI remove `www.example.com` and `www.example.net`
  added by `af6636b`, then
  `sqlite3 ~/Library/Application Support/Posato/posato-policy.db "PRAGMA user_version = 10; DROP TABLE IF EXISTS www_counterpart_expansion;"`.
- Accepted leftover: a 1.0 list that already stored both hosts still works;
  the row caption is then redundant and is not cleaned here.

## Final

- **Status:** `active`
- **Outcome:** rework after review
