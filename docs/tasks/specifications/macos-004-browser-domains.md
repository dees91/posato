# `MACOS-004`: Deny selected exact domains in Safari and Chrome with safe recovery

- **Review tier:** `high-risk`
- **Tier reason:** The task adds the loopback proxy that carries the person's
  browser traffic, mutates the system proxy through the root daemon, drives
  Safari and Chrome through Apple Events, and must restore the person's
  network settings after every failure; a defect can leak browsing data, deny
  unrelated traffic, or leave a Mac without a working network.
- **Dependencies:** completed `MACOS-002` (ADR 0005), `MACOS-003` (helper,
  daemon, Apply/Restore, durable ownership), `MACOS-006` (signed package),
  `SESSION-001` (session model, read-only here), `TARGETS-001` (`ExactDomain`)
- **Integration group:** `PR-MAC-DOMAINS`
- **Authority:** `MACOS-004` in MVP roadmap revision 8,
  [ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md),
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md),
  the threat model (`A-07`, `A-09`, `TB-04`, `TB-05`, `T-01`, `T-08`, `T-09`,
  `T-11`, `T-12`, `R-02`, `R-06`), the diagnostics policy, and `DESIGN.md`
  "Blocked presentation"

## Outcome

On the maintainer's development-signed Mac, a configured set of canonical
exact domains is denied for HTTP and HTTPS in Safari and Chrome (regular and
private windows) through the helper-owned loopback proxy, an unselected control
domain and sibling or subdomain controls stay reachable, a blocked navigation
shows the fixed local page in the same tab, and the proxy baseline is restored
exactly after clear, helper failure, sleep, and reboot.

## Boundaries

- Add the ADR 0005 mechanism to the existing normal-user helper: an
  IPv4-loopback listener with bounded HTTP relay and `CONNECT` tunnelling,
  exact-equality host policy revalidated on the wire, the single local
  `GET /blocked` route, the host-free presentation signal, the Safari and
  Chrome same-tab adapters, and the candidate and effective proxy-chain
  verification before and after Apply. The daemon gains no domain, URL, or
  browser knowledge; it keeps its MACOS-003 operation set unchanged.
- Extend the parent-to-helper pipe with one helper-only operation that
  delivers the bounded domain set and returns the bound listener port; the
  existing `Apply(port)` and `Restore` operations then own the system-proxy
  mutation. The daemon rejects the new operation like operation `10`.
- Kotlin stays in `desktopApp` next to `MacOsHelperClient`: a small
  orchestrator sequences configure, Apply, verified-active, and Restore, and
  accepts canonical domain strings at the module boundary. No session wiring,
  UI, DI, or `commonMain` contract: `SESSION-002` integrates start, early
  end, expiry, Retry, and action-required messaging.
- The blocked page uses the accepted vocabulary (`This site is paused`) and
  shows the end time only when the configure payload carries one; it carries
  no target, query, network asset, or session-mutation endpoint.
- Diagnostics, IPC outcomes, durable state, and evidence carry only
  target-free capability status and stable failure categories; no domain,
  URL, path, header, request count, or browser event ever leaves the helper.
- Exclusive write surface while `IOS-001` and `SYNC-008` run in parallel:
  `macosHelper/**`, `desktopApp/src/**`,
  `shared/src/{commonMain,commonTest,jvmMain,jvmTest}/**/feature/enforcement/**`
  (reserved, expected unused), and `docs/wiki/topics/macos-enforcement.md`.
  Shared by rebase: `docs/wiki/log.md`. Do not touch
  `feature/session/**`, `feature/sync/**`, `feature/targets/**`, the DI
  graphs, `PosatoApplication.kt`, `iosApp/**`, `macosSyncCompanion/**`,
  `desktopApp/build.gradle.kts`, `desktopApp/Config/**`,
  `settings.gradle.kts`, `gradle/libs.versions.toml`, or the verify-posato
  skill, its feature map, and its fixtures.
- No verify-posato feature file or fixture: enforcement has no drivable user
  path until `SESSION-002` wires session start, so the skill's "Out of scope"
  rule applies and the physical proof is the gated harness below.
- `.research/blocker` (`BoundedHTTPProxy.swift`, `BoundedProxyRequestParser.swift`,
  `MacOSEnforcement.swift`) is read-only evidence; re-derive parsing, routing,
  and the Apple Events scripts here.

## Acceptance

- `AC-01` — With the domain set configured and Apply verified, Safari and
  Chrome in regular and private windows receive the fixed page for a selected
  host over HTTP and HTTPS, while the control host, a sibling, and a subdomain
  of a selected host load normally; nonstandard ports are rejected rather
  than routed direct, and IP literals never match a selected host and are
  relayed like any unselected host (ADR 0005), which leaves typing a selected
  site's address as a stated non-resistant residual.
- `AC-02` — Every ADR 0005 negative parsing case (raw Unicode, userinfo,
  missing, duplicate, or conflicting `Host`, invalid port, two terminal dots,
  origin-form other than the exact `/blocked` route, oversized or slow input)
  fails closed for that connection with no direct fallback and no upstream
  connection, and the helper never recurses through its own listener.
- `AC-03` — Activation is refused before mutation when a manual, managed,
  PAC, autodiscovery, VPN, relay, or Private Relay route is detected, and a
  post-Apply chain that contains anything but the loopback route enters
  restoration; the maintainer's proxy baseline is byte-identical after clear,
  forced helper termination, sleep and wake, and reboot.
- `AC-04` — Presentation failure (Automation denied, browser not running,
  captured tab no longer selected) leaves network denial intact and reports
  only a target-free capability status; the page cannot end or change a
  session.
- `AC-05` — A synthetic privacy canary (a selected host with a unique path,
  query, and header marker) never appears in helper or daemon logs, pipe or
  XPC outcomes, durable state, or evidence; `./gradlew quality` passes with no
  new suppression.

## Verification

- Swift tests for the request parser, exact-host policy, `/blocked` route,
  bounds and deadlines, no-direct fallback, chain validation over an injected
  resolver, the presentation adapter's URL reduction and mismatch handling,
  and redacted outcomes; JVM tests for the new pipe operation, the
  orchestrator's ordering and unknown-outcome handling, and the enumerated
  redaction test.
- Physical checklist on the maintainer's Mac driven by an environment-gated
  JVM test in `desktopApp` (precedent: the SYNC-006 device round-trip) that
  enables the helper, configures a synthetic exact domain plus controls, applies,
  holds active while the maintainer performs the browser rows, then clears;
  rows recorded as pass or blocked with categorical evidence under
  `build/verification/`. The two-major-version release matrix stays with
  `RELEASE-001`.
- `./gradlew quality`, `git diff --check`, suppression and private-data scans;
  independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

Every decision below is accepted (`user-confirmed`, 2026-09-04) unless it is
marked open.

- Decided: the proof harness is a test-scope, environment-gated JVM test
  rather than a product command, hidden menu, or Gradle task; `SESSION-002`
  and `RELEASE-001` are the named repeated consumers of the same checklist.
- Decided, answering the `IOS-001` open item: no shared `commonMain`
  enforcement port this wave. Each platform keeps its own adapter with the
  outcomes its brief lists, and `SESSION-002` defines the common contract
  when it wires start, early end, and expiry on both hosts.
- Decided: an empty domain set is rejected as invalid input by both the
  orchestrator and the helper; "enforce nothing" is not an activation.
- Decided: configure-and-listen uses a 10-second deadline, Apply keeps
  the MACOS-003 120-second maximum because it waits for administrator
  authentication, and the presentation debounce is 1 second per browser.
- Decided: presentation is in scope for both browsers, with
  `NSAppleEventsUsageDescription` added to the helper's `Info.plist`; the
  Automation prompt is a physical-gate step, and the release-time persistence
  of that permission stays with `RELEASE-001`.
- Decided: how the checklist treats the
  `app.posato.macos.proxy.apply` authentication. `acquireApplyGrant()` builds
  a fresh `AuthorizationRef` whose `deinit` runs
  `AuthorizationFree(_, [.destroyRights])`, and the rule is `shared: false`,
  so the 30-second window never carries a credential into a second Apply:
  every Apply raises its own SecurityAgent dialog, which no driver may
  script. The boundary stays and the proof is split: the gated harness
  drives everything up to the request and asserts the reported state,
  the helper and daemon tests own the privileged path, and the checklist
  records one maintainer authentication per Apply row. Relaxing the rule on
  the verification machine is refused: the daemon verifies and repairs the
  definition, and a check that passes only because the control was removed
  proves nothing.
- Decided (`user-confirmed`, 2026-09-05): IP literals stay relayed as
  unselected hosts rather than rejected, because rejecting them would deny
  unrelated local-network and developer traffic without a product benefit;
  `AC-01` was amended accordingly. The gated harness may drive the installed
  package through a test-only parent process signed with the maintainer's
  development identity and the application identifier, which is the peer the
  helper already requires.
- Physical gates: development-signed package with the team identity, helper
  Enable approval in System Settings, one administrator authentication per
  Apply, Automation prompts for Safari and Chrome, both browsers installed,
  and the maintainer's real proxy settings mutated and restored during the
  run.
