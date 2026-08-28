# Execution: `MACOS-001`

- **Brief:** [MACOS-001](../specifications/macos-001-helper-contract.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `/root/macos_001_plan_review`
- **Branch:** `main`
- **Updated:** `2026-08-26`

## Plan

1. Reconcile the accepted architecture, security, privacy, diagnostics, and
   roadmap boundaries with exact helper, proxy, failure, and cleanup evidence
   from the read-only feasibility checkout and current official Apple guidance.
2. Prepare the complete ADR candidate for review without placing an unaccepted
   decision in `docs/decisions/`. Select the smallest safe Swift process split,
   Service Management and IPC boundaries, authorization rules, durable
   ownership state machine, and installation, update, recovery, and removal
   behavior.
3. Keep browser coverage, proxy coexistence policy, selected-application
   identity, concrete protocol schema, implementation, distribution, and
   release readiness with their existing downstream owners.
4. Verify candidate traceability and repository hygiene, obtain an independent
   proposal review, resolve blocking findings, and request maintainer
   acceptance.
5. Only after acceptance, add the accepted ADR, align architecture and macOS-
   enforcement wiki routing plus the log, obtain the completed-change review,
   record final evidence, and mark the task done.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** `none`
- **Resolution:** The reviewer found the task dependency-eligible, scoped to
  one decision, and proportionate. The candidate must explicitly cover sleep,
  wake, primary-network-service changes, and why Swift is the smallest choice
  consistent with Kotlin-first ownership.

## Result

- A complete contract candidate is ready for maintainer acceptance. It selects
  a short-lived unprivileged Swift session helper plus one minimal Swift root
  daemon limited to atomic proxy ownership and recovery. Kotlin remains the
  product and policy owner.
- The initial independent proposal review found three Required issues: out-of-
  band daemon disablement was overclaimed as automatically recoverable, proxy
  fields were restored too independently, and the Authorization Services right
  lacked an exact owner and lifecycle.
- The candidate now limits guaranteed recovery to paths where the exact daemon
  can run, treats HTTP and HTTPS settings as separately atomic tuples under an
  exclusive preferences lock, and defines the exact one-use
  `app.posato.macos.proxy.apply` right and its daemon-owned lifecycle.
- Focused re-review approved all corrections with no remaining Critical or
  Required finding.
- `user-confirmed` (2026-08-26): the maintainer accepted the reviewed contract.
  ADR 0004 and the maintained architecture and macOS-enforcement synthesis now
  record the decision without adding implementation, dependencies, or release
  claims.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** The first pass found four Required issues:
  exact duplicates were described only as rejected replay; Repair and
  unconditional unregister were missing from the supported lifecycle;
  maintained privacy and security synthesis still called the accepted helper
  architecture open; and the new ADR had an extra blank line at end of file.
- **Resolution:** Exact duplicates now reconcile durable state without re-
  execution or bearer reuse; all four supported lifecycle operations restore
  to `Idle` before unregister; privacy, threat-model, and architecture synthesis
  distinguish the accepted ADR from unverified MACOS-003 controls; and the
  whitespace defect was removed. Focused re-review approved the complete
  correction with no remaining Critical or Required finding.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Independent high-risk plan review | `pass` | Approved with no Critical or Required finding. |
| Independent proposal review | `pass` | Three Required findings corrected; focused re-review approved with no remaining Critical or Required finding. |
| Authority and PoC traceability | `pass` | Accepted architecture, threat model, diagnostics policy, roadmap, relevant synthesis, Apple guidance, and final enforcement revision `bcdc8ce9` reconciled; enforcement evidence is unchanged through research checkout `d48bdfb0315e`. |
| Repository-local Markdown links | `pass` | All 264 inline links across 56 tracked or new Markdown files were scanned; every local target resolves. |
| Wiki routing and log lint | `pass` | All 15 topic and source pages are routed and every log heading has the parseable form. |
| Roadmap ownership | `pass` | All eight task IDs referenced by the new ADR and task records exist in the accepted roadmap. |
| Current Apple source links | `pass` | Service Management, background-process, Authorization Services, XPC signing, code-signing, and SystemConfiguration sources resolve on 2026-08-26. |
| Documentation whitespace | `pass` | Tracked `git diff --check`, per-file checks for all three new files, and explicit trailing-whitespace scan reported no defect. |
| Scoped sensitive-data scan | `pass` | Affected documents contain no personal path, private-key marker, common credential shape, or credential-bearing URL. |
| Maintainer acceptance | `pass` | The maintainer explicitly accepted the reviewed MACOS-001 contract on 2026-08-26. |
| Independent completed-change review | `pass` | Four Required findings were corrected; focused re-review approved with no remaining Critical or Required finding. |

## Blockers and accepted risks

- No blocker. MACOS-002, MACOS-003, TARGETS-003, MACOS-004, MACOS-005, and
  RELEASE-001 retain the downstream decisions named by ADR 0004.

## Final

- **Status:** `done`
- **Outcome:** accepted ADR 0004 governs macOS helper ownership and lifecycle
