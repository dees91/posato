# `MACOS-003`: Build authenticated macOS helper IPC

- **Review tier:** `high-risk`
- **Tier reason:** The change introduces signed process trust boundaries,
  administrator authorization, a root daemon, and recoverable system proxy
  ownership.
- **Dependencies:** `MACOS-001`, `FOUNDATION-001`
- **Integration group:** `PR-MAC-HELPER`
- **Authorities:** ADR 0003, ADR 0004, ADR 0005, the Apple MVP threat model,
  and the diagnostics policy

## Outcome

The packaged macOS application can send bounded enforcement lifecycle and
proxy-ownership commands through a mutually authenticated normal-user Swift
helper and least-privileged Swift launch daemon, with exact authorization,
idempotency, and recovery behavior.

## Boundaries

- Keep product policy in Kotlin and native enforcement mechanics in Swift.
- Add no product UI, proxy listener, domain handling, browser behavior,
  application termination, synchronization, updater, or release signing.
- Accept no caller-selected path, executable, network service, command, proxy
  dictionary, or SystemConfiguration key.
- Treat `.research/blocker` as read-only feasibility evidence and do not import
  its spike runtime or temporary privilege mechanisms wholesale.

## Acceptance

- `AC-01` — Fixed signed peers negotiate a bounded, versioned pipe protocol;
  invalid identity, schema, ordering, size, state, and replay inputs fail before
  effects.
- `AC-02` — The helper and daemon mutually authenticate one fixed NSXPC service,
  and Apply consumes one freshly authenticated administrator authorization for
  one exact request.
- `AC-03` — The daemon atomically applies and conditionally restores only the
  Posato-owned HTTP and HTTPS tuples while preserving unrelated settings.
- `AC-04` — Durable state and reconciliation recover safely after lost replies,
  process failure, lease expiry, sleep, logout, restart, and primary-service
  change without silently reapplying.
- `AC-05` — Enable, repair, disable, update reconciliation, and removal preserve
  the accepted rule, service, recovery, and cleanup ordering.

## Verification

- First pass a hard feasibility gate with a local Apple Development-signed,
  non-notarized artifact installed under `/Applications`: the nested helper is
  its own `Bundle.main`, registration reaches approval and enabled states, and
  launchd starts the daemon from the fixed `BundleProgram`. If development
  signing is insufficient or the nested provider is rejected, stop without
  privileged implementation and revisit ADR 0004 with `RELEASE-001`.
- Run cross-language protocol, trust, authorization, durable-state,
  SystemConfiguration, lifecycle, and packaging tests through the aggregate
  quality gate.
- Complete signed physical verification for approval, authentication, apply,
  exact restoration, failure recovery, repair, disablement, and removal.

## Decisions or blockers

- `SMAppService` registration is owned by the fixed nested helper application so
  its daemon plist and executable remain inside the calling signed bundle. A
  signed physical registration check must pass before expanding the daemon
  implementation.
- Developer signing identity is runtime-only physical-test input. Distribution,
  Developer ID signing, and notarization remain `RELEASE-001` decisions.
- The launchd lifetime policy is `KeepAlive` with `SuccessfulExit=false`:
  reconcile once after load, remain alive while ownership is non-Idle, exit zero
  when Idle, and rely on launchd only for crash or nonzero-exit recovery.
