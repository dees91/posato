# `MACOS-002`: Accept the macOS browser-enforcement contract

- **Review tier:** `high-risk`
- **Tier reason:** The task defines network-denial coverage, browser
  interaction, sensitive navigation-data handling, coexistence with existing
  network configuration, and failure behavior.
- **Dependencies:** `SECURITY-001`
- **Integration group:** `PR-MAC-WEB-CONTRACT`
- **Authority:** [MVP roadmap revision 2](../mvp-roadmap.md) and maintainer
  activation on 2026-08-26

## Outcome

The maintainer can accept one product and platform contract that states which
macOS browsers Posato supports, what exact-domain denial and blocked
presentation mean, when existing network configuration prevents activation,
which navigation data may exist transiently, and how failures and recovery are
reported without overstating the feasibility evidence.

## Boundaries

- Cover only the accepted arm64 macOS 15-or-later MVP and the helper boundary
  accepted by [ADR 0004](../../docs/decisions/0004-macos-helper-ownership-and-lifecycle.md).
- Select the minimum explicit browser matrix and distinguish network denial
  from browser presentation for every support claim.
- Define exact-host matching, ambiguous-input rejection, conflict preflight,
  network-transition behavior, privacy limits, truthful degraded states, and
  release-time evidence needed for the claim.
- Use `.research/blocker` only for exact mechanism, browser, failure, cleanup,
  and test evidence; do not copy its browser profiles, destinations, runtime,
  scripts, identifiers, or machine state.
- Leave shared target validation to `TARGETS-001`, application identity to
  `TARGETS-003`, concrete IPC and native implementation to `MACOS-003` and
  `MACOS-004`, and packaging or release readiness to their named roadmap tasks.
- Do not add production code, dependencies, browser extensions, TLS
  interception, profile mutation, synthetic input, telemetry, a general VPN
  or arbitrary-browser claim, or a production-readiness claim.

## Acceptance

- `AC-01` — The proposal names every supported and explicitly unsupported
  browser class, separates denial from presentation, and ties each positive
  claim to bounded version and release-time physical evidence.
- `AC-02` — Exact-domain semantics cover HTTP and HTTPS authority parsing,
  canonical equality, ports and subdomains, malformed or conflicting input,
  and the absence of TLS interception or path-level policy.
- `AC-03` — Activation, proxy coexistence, network-service changes, sleep and
  wake, captive-network conditions, and unsupported VPN or proxy arrangements
  have conservative preflight and recovery behavior that preserves unrelated
  settings.
- `AC-04` — The data contract permits only the minimum transient host and
  browser state needed for enforcement and presentation, prohibits navigation
  history and target-bearing diagnostics, and defines prompt disposal.
- `AC-05` — Proxy, browser-adapter, helper, network, and presentation failures
  keep policy state truthful, restore unrestricted networking where the
  accepted helper can run, and have a concrete downstream verification matrix.
- `AC-06` — The maintainer explicitly accepts the reviewed proposal before it
  becomes an accepted authority or its conclusions enter maintained synthesis.

## Verification

- Traceability review against the accepted scope, design, architecture, threat
  model, diagnostics policy, roadmap, relevant wiki topics, current primary
  platform and browser sources, and exact final enforcement-spike evidence.
- Documentation links, `git diff --check`, scoped sensitive-data scan, and
  independent high-risk plan and completed-change reviews.

## Decisions or blockers

- Maintainer acceptance of the reviewed proposal is required to complete the
  task.
