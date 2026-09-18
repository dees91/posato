# Execution: `TARGETS-006`

- **Brief:**
  [`../specifications/targets-006-www-coverage.md`](../specifications/targets-006-www-coverage.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Grok
- **Reviewer:** pending
- **Branch:** `feature/targets-006-www-coverage`
- **Updated:** 2026-09-18

## Plan

1. Derive a `www` counterpart on `ExactDomain` and expand new hosts in
   `createWebsiteBatchSubmission`, keeping edit as a single-row replace.
2. Expand already-saved domains once on first `SyncTargetPolicyStore` read
   after upgrade, recording sync intents for any new rows.
3. Update website recipes, `DESIGN.md`, the wiki proposal, and the roadmap
   outcome sentence so they describe automatic persistence rather than an
   opt-in.

## Result

- Entering a host now also stores its valid `www` counterpart as a second
  exact-domain row. Matching stays equality-only. Edit still replaces one
  row. A one-shot flag expands already-saved lists on first policy read
  after upgrade and does not restore a later removal.
- Independent completed-change review is still required. AC-04 (Safari and
  Chrome on a synthetic pair) remains a maintainer-attended physical check.

## Completed-change review

- **Verdict:** pending
- **Critical or Required findings:** none yet
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | worktree, 2026-09-18 |
| Simulator add `example.com` | pass | both rows in SQL; run `20260918-143714-023f` |
| Simulator remove one of the pair | pass | apex gone, `www.example.com` remained; then cleaned |
| Simulator first-install | pass | summary `2 websites saved`; run `20260918-143748-d1e5` |
| Desktop add/remove `targets006-proof.example` | pass | both rows, independent remove; run `20260918-143900-456b` |
| Physical Safari/Chrome pair | pending | AC-04, maintainer-attended |

## Blockers and accepted risks

- AC-04 needs the maintainer at the Mac.
- First launch of this build on the supported Mac expanded the local
  `example.com` / `example.net` list with their `www` counterparts. That is
  the intended upgrade behavior.

## Final

- **Status:** `active`
- **Outcome:** implementation complete pending review and AC-04

