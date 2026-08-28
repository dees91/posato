# `MACOS-001`: Accept the macOS helper contract

- **Review tier:** `high-risk`
- **Tier reason:** The task selects a privileged process, exposed local trust
  boundaries, authorization, recovery, and system-network mutation behavior.
- **Dependencies:** `SECURITY-001`
- **Integration group:** `PR-MAC-HELPER-CONTRACT`
- **Authority:** [MVP roadmap revision 2](../mvp-roadmap.md) and maintainer
  activation on 2026-08-26

## Outcome

The maintainer can accept one architecture decision that makes macOS helper
ownership, privilege, installation, update, IPC security, lifecycle, recovery,
and removal implementable without inheriting the feasibility spike runtime.

## Boundaries

- Cover only the accepted arm64 macOS 15-or-later MVP helper boundary.
- Select the minimum native process and privilege split needed for safe proxy
  ownership, application enforcement, and recovery.
- Preserve the accepted JVM application, signed native-helper, Kotlin-first
  policy, diagnostics, threat-model, and deliberate-removal boundaries.
- Use `.research/blocker` only for exact mechanism, failure, cleanup, and test
  evidence; do not copy its protocol, temporary grant, scripts, or identifiers.
- Leave browser support, exact-domain and proxy coexistence policy, selected-
  application identity, concrete IPC schemas, implementation, packaging
  automation, notarization, and distribution to their named roadmap tasks.
- Do not add code, dependencies, a general installer or updater, a custom
  watchdog, a shell surface, telemetry, or a production-readiness claim.

## Acceptance

- `AC-01` — The proposed decision assigns every native responsibility to an
  explicitly privileged or unprivileged owner and selects the implementation
  language and supported Apple process-management mechanism.
- `AC-02` — Installation, approval, compatibility, update, disablement, repair,
  and removal have truthful states and never apply enforcement across an
  unknown helper or contract version.
- `AC-03` — Each IPC boundary defines peer authentication, per-operation
  authorization, bounded input, freshness or idempotency, timeouts, cancellation,
  structured redacted outcomes, and protocol compatibility behavior.
- `AC-04` — The ownership state machine preserves unrelated system settings and
  restores unrestricted networking after normal end, early end, process or IPC
  failure, crash, logout, reboot, and supported in-app update, disablement, or
  removal. Out-of-band daemon disablement or a missing application bundle is
  represented truthfully as `recovery-required` when automatic reconciliation
  cannot run.
- `AC-05` — The maintainer explicitly accepts the reviewed proposal before it
  becomes an accepted authority or its conclusions enter maintained synthesis.

## Verification

- Traceability review against the accepted architecture, threat model,
  diagnostics policy, roadmap, relevant wiki topics, current official Apple
  guidance, and exact final enforcement-spike evidence.
- Documentation links, `git diff --check`, scoped sensitive-data scan, and
  independent high-risk plan and completed-change reviews.

## Decisions or blockers

- Maintainer acceptance of the reviewed proposal is required to complete the
  task.
