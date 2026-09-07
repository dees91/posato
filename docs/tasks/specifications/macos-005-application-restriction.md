# `MACOS-005`: Restrict locally mapped macOS applications without affecting unselected applications

- **Review tier:** `high-risk`
- **Tier reason:** The task gives the normal-user helper the authority to
  terminate processes on the person's Mac from a policy the JVM sends it; a
  wrong match kills an unselected or system application, an unbounded grace
  rule destroys unsaved work, and a leaked identity turns the helper into an
  application-usage record.
- **Dependencies:** completed `MACOS-003` (helper, pipe protocol, helper-only
  operations), `MACOS-004` (helper session lifecycle, presentation adapter
  pattern, gated physical harness), `MACOS-006` (signed package), `SESSION-001`
  (session model, read-only here), `TARGETS-003` (macOS mapping database with
  bounded designated requirements)
- **Integration group:** `PR-MAC-APPS`
- **Authority:** `MACOS-005` in MVP roadmap revision 9,
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  (helper owns application observation and termination; the daemon receives
  no application identity), [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md),
  the threat model (`A-03`, `A-07`, `A-09`, `TB-05`, `T-08`, `T-11`, `T-12`),
  the diagnostics policy, `DESIGN.md` "Blocked presentation", and the
  `TARGETS-003` decision that identity is the exact binary designated
  requirement, never a path or bundle identifier

## Outcome

On the maintainer's development-signed Mac, with mapped applications and an
active enforcement session, the helper terminates a selected application when
it is running at activation or launches later, shows the fixed paused
presentation, leaves every unselected application and the system untouched,
and stops observing the moment the session clears, the parent pipe closes, or
the helper exits.

## Boundaries

- Extend the parent-to-helper pipe with one helper-only operation that
  delivers a bounded set of designated-requirement blobs read from the
  `TARGETS-003` mapping database, plus the optional session end time, under a
  new capability bit; the daemon rejects it like operations `10` and `11` and
  gains no application knowledge. Application enforcement mutates no system
  setting, so it needs no Apply, authorization, or daemon round trip; it is
  alive exactly while the helper holds the configured set.
- In the helper, enumerate process identifiers directly (`libproc`) and
  hydrate each one on demand: `NSWorkspace.runningApplications` does not
  refresh in a process without a run loop, so it sees only applications
  already running at activation. Match each candidate by validating its
  code object against the exact requirement with the Security framework,
  and terminate matches with a graceful request first and a forced
  termination after a bounded grace. Never match by name, path, or bundle
  identifier; refuse a requirement that would match Posato's own
  identifier namespace even if the parent sends one.
- Present the fixed `This app is paused` notice with the end time when the
  payload carries one, once per terminated application within a debounce
  window, from the helper (`DESIGN.md`). The notice carries no application
  name, path, or session-mutation control.
- Kotlin stays in `desktopApp` next to `MacOsBrowserDomainEnforcer`: one
  orchestrator that configures, reports verified-active, and clears; it takes
  `LocalApplicationMappingId` values and resolves requirement bytes through
  the existing mapping store on the injected IO dispatcher. No session, DI,
  UI, or `commonMain` wiring: `SESSION-002` integrates start, early end,
  expiry, and the "save your work" copy.
- Diagnostics, IPC outcomes, and evidence carry only target-free capability
  status and stable failure categories: no requirement bytes, bundle
  identifier, name, process identifier, launch event, or termination count.
- Exclusive write surface while `IOS-002` and `APPLE-002` run in parallel:
  `macosHelper/**`, `desktopApp/src/**`, and
  `docs/wiki/topics/macos-enforcement.md`. Shared by rebase:
  `docs/wiki/log.md`. Do not touch `feature/session/**`,
  `feature/targets/**`, `feature/sync/**`, the DI graphs,
  `PosatoApplication.kt`, `iosApp/**`, `macosSyncCompanion/**`,
  `tools/**`, Gradle files, or the verify-posato skill and its fixtures.
- No verify-posato feature file: enforcement has no drivable user path until
  `SESSION-002`; the physical proof is a gated harness in the `MACOS-004`
  shape, driving a disposable development-signed test application as the
  selected target and a second one as the control.
- `.research/blocker` `MacOSEnforcement.swift` is read-only evidence for
  launch observation and termination; re-derive matching, grace, allowlist,
  and presentation here.

## Acceptance

- `AC-01` — With the set configured, launching a selected application during
  the session ends in its termination within the bounded grace and the fixed
  notice, while a control application launches and keeps running.
- `AC-02` — A selected application already running at activation is handled
  by the accepted grace rule below; a differently signed application with the
  same name and a Posato-owned bundle are never matched.
- `AC-03` — Clear, parent pipe closure, and helper exit each stop observation
  immediately; no termination happens afterwards, and the daemon never
  receives the operation.
- `AC-04` — Presentation failure leaves termination intact and reports only a
  target-free category; the notice cannot end or change a session.
- `AC-05` — A synthetic canary (a test application with a unique display
  name and identifier) never appears in helper or daemon logs, pipe or XPC
  outcomes, durable state, or evidence; `./gradlew quality` and `swift test`
  pass with no new suppression.

## Verification

- Swift tests over injected seams: requirement matcher and Posato-namespace
  refusal, launch-observation dispatch, grace state machine (graceful, forced,
  already-exited), debounce, redacted outcomes; the daemon decoder rejecting
  the new operation.
- JVM tests: the new pipe operation encoding and bounds, orchestrator ordering
  (resolve, configure, verified-active as accepted count equal to set size,
  clear; an unknown outcome kills the helper and reports `Failed` with no
  daemon reconciliation), and the enumerated redaction test for the new
  carriers.
- Gated physical harness on the maintainer's Mac: helper enabled, two
  disposable signed test applications built by the harness, rows for launch
  during the session, running at activation, control untouched, clear, forced
  helper termination, parent exit; evidence under the ignored
  `build/verification/`.
- `./gradlew quality`, `git diff --check`, suppression and private-data
  scans; independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

- Decided (`user-confirmed`, 2026-09-05): the grace rule. An application
  launched during the session receives the graceful termination request and
  is force-terminated after 5 s if still running; an application already
  running at activation receives the same rule, and `SESSION-002` owns the
  "save your work" review copy. The alternative, never forcing, lets one
  save dialog defeat enforcement.
- Decided (`user-confirmed`, 2026-09-05): the notice never shows the
  mapping's display name; the generic notice keeps names out of the helper
  entirely.
- Decided by authority: observation is helper-only and dies with the helper;
  applications are fail-open on helper failure because no system state is
  mutated, unlike the proxy. The record states this as an accepted limit.
- Physical gates: development-signed package with the team identity, helper
  Enable approval, both test applications signed with the maintainer's
  development identity by the harness, none of it tracked.
