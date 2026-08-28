# Execution: `DIAGNOSTICS-001`

- **Brief:** [DIAGNOSTICS-001](../specifications/diagnostics-001-diagnostics-and-support-data.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `/root/diagnostics_001_plan_review`
- **Branch:** `main`
- **Updated:** `2026-08-26`

## Plan

1. Reconcile the accepted privacy, security, product, architecture, and roadmap
   boundaries with exact redaction and categorical-evidence lessons from the
   read-only synchronization PoC and enforcement spike.
2. Write `docs/security/diagnostics-and-support-data.md` as one proposed
   authority. Keep the default local-only and minimal: stable user status is
   always available, diagnostic capture is explicit and time-bounded, export
   is previewed and user-initiated, and automatic remote collection is absent.
3. Define field and value boundaries, retention and deletion, producer
   obligations, and release rechecks without implementing diagnostics or
   inventing a generalized logging or support layer.
4. Verify traceability and repository hygiene, obtain an independent
   completed-change review, resolve blocking findings, and request maintainer
   acceptance.
5. Only after acceptance, mark the authority accepted, update security routing
   and maintained privacy synthesis plus the wiki log, record final evidence,
   and mark the task done.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** `none`
- **Resolution:** The independent reviewer found the plan dependency-eligible,
  privacy-safe, and appropriately limited to one proposed authority without an
  implementation or speculative support layer.

## Result

- Added one Apple MVP diagnostic and support-data authority covering four
  bounded support questions, diagnostic surfaces, a closed field allowlist,
  prohibited data, redaction, consent, local retention and deletion, previewed
  export, producer obligations, and release rechecks.
- Kept local capture off by default, available for diagnostic use for at most
  24 hours, and bounded to 500 records and 512 KiB in protected no-backup
  storage. Automatic analytics, telemetry, crash upload, remote processing,
  system-console logging, and support storage remain absent.
- Used the synchronization PoC and enforcement spike only for categorical
  error, synthetic-canary, redaction, ignored-evidence, and exact-cleanup test
  ideas. No schema, logger, event name, dependency, code, private value, or
  production claim was imported.
- `user-confirmed` (2026-08-26): the maintainer accepted the reviewed policy
  and its capture, retention, deletion, export, and no-remote-collection
  boundaries. The accepted authority, threat model, security routing, and wiki
  synthesis now agree; no diagnostic implementation was added.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** The first pass reported four Required
  defects: the 24-hour physical-deletion promise ignored suspended or
  terminated process lifecycle; a monotonically increasing sequence leaked an
  exact event count after eviction; producer verification omitted protection
  and backup-exclusion checks; and this execution record omitted completed-
  change verification.
- **Resolution:** The policy now separates logical expiry from physical cleanup
  at the first available execution opportunity, quarantines cleanup failures,
  applies the same rule to export staging, removes sequence values, requires
  protection and backup-exclusion verification after representative writes,
  and records the checks actually run below. Focused re-review found no
  remaining Critical or Required defect.
- **Advisory findings:** Tightened the diagnostic-surface summary to use the
  policy's precise "unavailable for Posato diagnostic use" lifecycle wording.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Independent high-risk plan review | `pass` | Approved with no Critical or Required finding. |
| Authority and PoC traceability | `pass` | Accepted scope, ADRs, threat model, maintained privacy synthesis, and exact final PoC/spike privacy artifacts reconciled; referenced artifacts are unchanged between final feasibility revision `bcdc8ce9` and current checkout `d48bdfb0`. |
| Local Markdown links | `pass` | Every repository-local link in all affected authority, routing, wiki, and task documents resolves. |
| Documentation whitespace | `pass` | `git diff --check` and explicit trailing-whitespace checks for every affected file reported no defect. |
| Scoped sensitive-data scan | `pass` | Affected documents contain no personal path, private-key marker, AWS access-key shape, GitHub token shape, or Slack token shape. |
| Independent completed-change review | `pass` | Initial four Required findings were corrected; focused re-review approved the proposal with no remaining Critical or Required defect. |
| Maintainer acceptance | `pass` | The maintainer explicitly accepted the reviewed policy and requested the completed change be committed on `main`. |

## Blockers and accepted risks

- None. Diagnostic producers remain future task-local implementations rather
  than accepted or verified behavior from this governance change.

## Final

- **Status:** `done`
- **Outcome:** `met`
