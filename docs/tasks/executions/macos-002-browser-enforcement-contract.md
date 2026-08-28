# Execution: `MACOS-002`

- **Brief:** [MACOS-002](../specifications/macos-002-browser-enforcement-contract.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `/root/macos_001_plan_review`
- **Branch:** `main`
- **Updated:** `2026-08-26`

## Plan

1. Reconcile the accepted scope, design, architecture, security, privacy,
   diagnostics, and roadmap boundaries with exact browser, proxy, failure, and
   cleanup evidence from the read-only feasibility checkout and current primary
   platform and browser guidance.
2. Prepare the complete contract candidate for review without placing an
   unaccepted decision in `docs/decisions/`. Select the smallest truthful
   browser matrix, separate denial from presentation, and define exact-domain,
   coexistence, data-lifecycle, failure, recovery, and release-evidence rules.
3. Keep target-model validation, application identity, native implementation,
   concrete IPC, packaging, distribution, and release readiness with their
   existing downstream owners.
4. Verify candidate traceability and repository hygiene, obtain an independent
   proposal review, resolve blocking findings, and request maintainer
   acceptance.
5. Only after acceptance, add the accepted decision, align relevant maintained
   synthesis and its log, obtain the completed-change review, record final
   evidence, and mark the task done.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** The first pass found two Required defects:
  the brief linked a nonexistent ADR 0004 path and the execution used an
  unsupported status value.
- **Resolution:** The link now resolves to the accepted ADR and both statuses
  use `active`. Focused re-review approved the plan with no remaining Critical
  or Required finding.

## Result

- The accepted contract limits the positive browser claim to Safari and Chrome
  Stable using the system proxy on supported macOS versions. Firefox and every
  incidental or overriding configuration remain explicitly unsupported.
- Network denial is separate from friendly same-tab presentation. The candidate
  permits one fixed target-free local presentation route, covers regular and
  private browser contexts only after physical proof, and accepts the narrow
  Apple Events time-of-check/time-of-use residual rather than overstating
  atomic tab replacement.
- Exact-domain comparison consumes the future canonical `ExactDomain` contract,
  matches no subdomains, ignores port for the deny decision, rejects ambiguous
  HTTP or CONNECT authority, and initially supports only HTTP port 80 and HTTPS
  port 443 without TLS interception or direct fallback.
- Activation requires a clean, stable primary Wi-Fi or Ethernet service and no
  compatible-path claim for existing proxies, PAC, WPAD, detected VPNs, network
  relays or filters, iCloud Private Relay, or captive portals. Sleep, wake, and
  primary-service change restore rather than silently transfer enforcement.
- The privacy contract names the bounded HTTP transit buffers and one transient
  current-tab URL that the mechanism actually needs. It prohibits retaining or
  exporting navigation events, URLs, targets, request counts, or browser-use
  history and keeps the root daemon target-free.
- `user-confirmed` (2026-08-26): the maintainer accepted the reviewed contract.
  ADR 0005 now records the decision, ADR 0004 routes its resolved deferrals, and
  maintained security, privacy, macOS-enforcement, and open-question synthesis
  distinguishes the accepted contract from unverified MACOS-004 evidence.

## Proposal review

- **Verdict:** `approved`
- **Critical or Required findings:** The first pass found four Required issues:
  only the first proxy-resolution route was checked; the fixed local page had
  no valid origin-form route; Safari could not prove the proposed regular-only
  window distinction; and Apple Events could not provide the claimed atomic
  no-wrong-tab guarantee.
- **Resolution:** The complete route chain must now contain only Posato with no
  fallback; one exact bodyless loopback `GET /blocked` route is allowed and
  tested; Safari and Chrome presentation cover regular and private contexts
  only after separate physical proof; and the rare tab-replacement TOCTOU is a
  disclosed residual with bounded mitigation. Focused re-review approved the
  candidate with no remaining Critical or Required finding.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** none
- **Resolution:** No correction was required. The independent review confirmed
  that the accepted proposal was faithfully promoted, privacy and threat-model
  amendments were accurate, and every positive platform claim remained gated
  by MACOS-004 evidence.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Independent high-risk plan review | `pass` | Two Required process defects corrected; focused re-review approved with no remaining Critical or Required finding. |
| Independent proposal review | `pass` | Four Required findings corrected; focused re-review approved with no remaining Critical or Required finding. |
| Authority and PoC traceability | `pass` | Accepted product, design, architecture, security, diagnostics, roadmap, maintained synthesis, final enforcement evidence, and current primary platform/browser guidance were reconciled. |
| Repository-local Markdown links | `pass` | All 239 repository-local links across the 109 checked Markdown files resolve. |
| Current primary-source links | `pass` | Apple proxy resolution and settings, browser-vendor proxy behavior, current scripting dictionaries, and HTTP authority guidance were checked on 2026-08-26. |
| Documentation whitespace | `pass` | `git diff --check` reports no defect. |
| Scoped sensitive-data scan | `pass` | The task records contain no personal path, credential marker, token shape, or credential-bearing URL. |
| Maintainer acceptance | `pass` | The maintainer explicitly accepted the reviewed MACOS-002 contract on 2026-08-26. |
| Independent completed-change review | `pass` | Approved with no Critical, Required, or material Recommended finding. |

## Blockers and accepted risks

- No blocker. The accepted Apple Events presentation race, system-proxy and
  browser-coverage limits, lack of content erasure, and downstream MACOS-004
  evidence gate remain explicit.

## Final

- **Status:** `done`
- **Outcome:** The maintainer-accepted macOS browser-enforcement and coexistence
  contract is recorded in ADR 0005, aligned with security and maintained
  synthesis, independently approved, and ready for its named downstream tasks.
