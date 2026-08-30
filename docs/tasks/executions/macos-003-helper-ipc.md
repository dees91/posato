# Execution: `MACOS-003`

- **Brief:**
  [`../specifications/macos-003-helper-ipc.md`](../specifications/macos-003-helper-ipc.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** independent Codex reviewer
- **Branch:** `macos-003-helper-ipc`
- **Updated:** `2026-08-30`

## Plan

1. Build only the minimal nested helper and inert daemon first. With a local
   Apple Development-signed, non-notarized copy under `/Applications`, verify
   `Bundle.main`, approval-to-enabled registration, fixed `BundleProgram`
   launch, idle exit, crash restart, unregister, and restart/boot visibility.
   Stop as blocked if the nested or non-notarized development artifact is
   rejected; do not add JNI, a package installer, or another privilege path.
2. Compile exact mutual XPC requirements from signed self-identity before
   accepting or resuming connections. Pin the opposite identifier, Apple
   anchor, and same Team ID; reject missing requirements, ad-hoc production
   signatures, wrong identifiers, and other teams without PID, UID, path, or
   `codesign` parsing as an authentication fallback.
3. Fix and test the private protocol and launchd lifetime. Use `KeepAlive` with
   `SuccessfulExit=false`: reconcile after load, remain resident only while
   ownership is non-Idle, exit zero when Idle, and relaunch after crash or
   nonzero exit.
4. Implement the Swift service lifecycle, authorization, durable state,
   SystemConfiguration transitions, and recovery state machine behind testable
   boundaries. Sleep, wake, logout, and primary-service recovery remain
   daemon-owned; helper signals may only accelerate recovery.
5. Add the concrete desktop client and Gradle build, embedding, signing, and
   package verification without adding a shared or UI abstraction.
6. Run automated and signed physical verification, complete independent review,
   resolve blocking findings, and close the durable records.

## Result

- The minimal Swift package, nested helper bundle, inert launch daemon, Gradle
  assembly, and inside-out development signing are implemented in the worktree.
- The signed helper reports the expected `Bundle.main`; outer app, helper, and
  daemon signatures share one nonempty Team ID.
- From the isolated `/Applications/Posato-MACOS-003.app` copy, initial service
  status was not found. Registration created an approval-required service entry,
  proving that Service Management resolved the nested calling bundle and plist.
- The maintainer approved the background item. The service reached enabled,
  repeated Mach-service pings succeeded, a forced nonzero daemon termination
  was relaunched by launchd, and the restarted daemon exited zero after its
  Idle reconciliation window. A retry after the launchd transition succeeded.
- The production helper and daemon now use fixed binary pipe and XPC messages,
  required capability negotiation, strict per-connection sequences, elapsed
  deadlines, exact mutual signing requirements, a fixed Authorization Services
  right, atomic SystemConfiguration tuple ownership, root-only durable
  recovery, a renewable lease, and bounded zero-exit launchd lifetime. The
  desktop client verifies and launches only the embedded signed helper, maps
  typed outcomes, sends bounded cancellation before exact-process termination,
  and reconciles unknown effects under the same request identity and canonical
  input digest. A lost reconciliation response retains that original pending
  intent, Apply preflights that exact durable owner before authorization, and
  daemon connection ownership requires that explicit preflight fact rather than
  a global phase. Lease renewal is accepted only from its durable owner.
- The fixed product layout and inside-out signing checks are part of the Gradle
  package pipeline. No signing identity, provisioning material, machine path,
  temporary privilege mechanism, or research runtime is stored in the
  repository.
- Signed physical verification passed Enable, Repair, administrator-authenticated
  Apply, a lease renewal after six seconds, Restore, Remove, authorization-right
  absence, daemon unregistration, root-state absence, and exact preservation of
  the pre-test disabled HTTP, HTTPS, and PAC configuration.
- A controlled helper termination after Apply restored the exact proxy baseline
  and removed durable ownership through authenticated XPC invalidation and the
  daemon lease. A separate Disable row preserved the verified Apply right while
  unregistering the daemon; the final Enable/Remove run removed both and left
  the machine at its original baseline.
- `observed`: the initially planned zero-second authorization credential timeout
  expired while the external form crossed processes. A five-second retry was
  also shorter than the observed system authentication-return latency. The
  implementation uses a non-shared 30-second maximum transfer window, does not
  permit daemon interaction or right extension, and destroys the one-use form
  after validation.
- Hosted review found three post-effect recovery gaps: cleanup retry stopped
  after a transient error, a verified Applied record could lose connection
  ownership when its final save reported failure, and an unavailable daemon
  could synthesize Idle. The correction reuses the existing lease timer until
  cleanup reaches Idle, treats every verified non-Idle Apply phase as owned, and
  returns recovery-required lifecycle responses when daemon state cannot be
  reconciled.
- Hosted review also found that background renewal discarded the daemon
  response and left the helper pipes open after lease loss or the bounded XPC
  operation limit. Renewal now remains healthy only for an explicit Success and
  Applied response. Every other response, malformed payload, or transport error
  invalidates XPC and terminates the helper nonzero so the desktop client cannot
  retain stale enforcement state.
- Hosted review found that exact duplicate Apply and Apply reconciliation used
  startup cleanup semantics, and that blanket reconciliation ownership could
  attach a connection to an unverified durable record. Exact Applied requests
  now use the existing maintenance path, while every unverified non-Idle Apply
  response fails closed as Conflict for both daemon and helper ownership.
- Hosted review found that successful Enable and Repair cleanup did not release
  helper lease ownership, and that an already-running renewal could execute
  after foreground cleanup. Every direct or reconciled ownership-ending intent
  now retires renewal synchronously before daemon execution. Successful Idle
  Enable and Repair responses clear helper and connection ownership, while a
  failed cleanup leaves daemon recovery active without extending the lease.
- Hosted review found that the daemon had no explicit sleep/wake restoration
  trigger and Repair did not replace the registered daemon. The daemon now
  restores before acknowledging system sleep and retries restoration after
  wake without reapplying. Repair restores ownership, awaits asynchronous
  unregistration, and reconciles the original request through a fresh helper
  process and authenticated daemon connection when Service Management rejects
  immediate same-process registration.

## Blockers and accepted risks

- Physical verification requires maintainer approval in System Settings and a
  foreground administrator authentication prompt.
- The current parallel prototype branch changes only prototype and wiki files.
  MACOS-003 owns desktop Gradle and packaging, desktop native-client sources,
  native helper sources, ADR 0004, and the macOS enforcement topic. Work must
  serialize if another task enters those surfaces; `docs/wiki/log.md` may need
  a routine append-conflict resolution.

## Plan review

- **Verdict:** `approved for feasibility slice after required corrections`
- **Required findings:** prove nested non-notarized development registration
  before privileged behavior; replace unconditional root residency with bounded
  launchd lifetime; gate XPC on compiled exact signing requirements.
- **Resolution:** all three are hard gates in the plan above. Failure stops the
  task rather than introducing an unaccepted architecture.

## Completed-change review

- **Verdict:** `approved; merge-ready`
- **Initial result:** no Critical findings; Required findings covered the XPC
  listener gate, cleanup ownership and unregister ordering, rule lifecycle,
  canonical reconciliation, capabilities, deadlines and cancellation, durable
  validation, complete proxy verification, authorization-material destruction,
  owner-bound renewal, unavailable-daemon recovery, package contract checks,
  and exact per-connection Apply ownership.
- **Resolution:** every Required finding was corrected and affected verification
  was rerun. The final independent pass found no Critical, Required,
  Recommended, or Optional actionable defects and independently passed all 36
  Swift tests.

## Hosted-review correction

- **Required findings:** retain cleanup retries after restoration errors; retain
  connection cleanup ownership when a verified Applied record survives a
  post-save error; and do not claim Idle while the daemon is unavailable.
- **Resolution:** all three paths now fail closed through the existing lifecycle
  policy and daemon timer. No dependency, watchdog, retry configuration, or
  production failure-injection surface was added.
- **Focused completed-change review:** approved with no Critical, Required,
  Recommended, or Optional findings. The reviewer independently passed all 41
  Swift tests, the aggregate quality gate, formatting, SwiftLint, diff hygiene,
  and signed-package verification.

## Hosted-review lease-loss correction

- **Required finding:** surface background lease loss to the desktop client
  instead of leaving the helper process and its inherited pipes alive after a
  failed or rejected renewal.
- **Resolution:** the existing renewal timer decodes and validates every Renew
  acknowledgement. Only Success with Applied ownership remains healthy; all
  other outcomes and failures invalidate XPC and terminate the helper nonzero.
  No callback, unsolicited protocol event, reconnect path, dependency, or new
  timer was added.
- **Focused completed-change review:** approved with no Critical, Required,
  Recommended, or Optional findings. The reviewer confirmed that typed response
  validation, XPC invalidation, process exit, and inherited-pipe closure cover
  every rejected or failed renewal without changing the wire protocol.

## Hosted-review idempotency and ownership-proof correction

- **Required findings:** preserve Applied state for an exact duplicate Apply,
  and require a verified durable match before a reconciliation connection can
  claim cleanup ownership.
- **Resolution:** duplicate Apply and exact Apply reconciliation share one
  existing-record path that validates Applied through maintenance instead of
  startup restoration. Durable preflight now reports whether an exact record
  existed, the daemon passes only that fact to connection state, and any
  unverified non-Idle Apply response becomes Conflict before either process can
  claim it. No wire field, capability, dependency, scheduler, or test target was
  added.
- **Focused completed-change review:** approved with no Critical, Required,
  Recommended, or Optional findings. The reviewer verified every direct and
  reconciled proof path, helper and daemon ownership consumer, exact duplicate,
  actual drift, and post-effect failure case.

## Hosted-review cleanup-retirement correction

- **Required finding:** clear helper lease ownership after successful Idle
  Enable and Repair cleanup.
- **Accepted advisory finding:** prevent an already-running renewal from
  executing after foreground Restore, Disable, Remove, Enable, or Repair.
- **Resolution:** one effective-operation policy covers direct and reconciled
  ownership-ending intents. The helper retires its existing renewal through a
  bounded lock before forwarding those intents, and a failed cleanup does not
  restart renewal or extend enforcement. No wire field, timer, callback,
  reconnect path, runtime dependency, or shared production abstraction was
  added.
- **Focused completed-change review:** the first pass required retirement before
  local unavailable-service responses as well as forwarded daemon calls. The
  correction moved retirement before the service-status branch, reran affected
  and aggregate verification, and the final pass approved with no Critical,
  Required, Recommended, or Optional findings.

## Hosted-review post-unregister-state correction

- **Required finding:** successful direct and reconciled Disable and Remove
  returned the daemon's pre-unregister Ready service state to the desktop
  client after the helper had unregistered the service.
- **Resolution:** the existing successful-Idle unregister path now replaces
  only the outbound service state with the status read after synchronous
  unregistration. Outcome, ownership, required action, failure, wire shape, and
  every non-unregister response remain unchanged. One pure lifecycle projection
  keeps the behavior testable without an SMAppService abstraction or new target.
- **Focused completed-change review:** approved with no Critical, Required,
  Recommended, or Optional findings. The reviewer confirmed the shared direct
  and reconciled path, throwing unregister behavior, response-field
  preservation, focused regression, and prior physical evidence.

## Hosted-review sleep and repair correction

- **Required findings:** restore owned proxy state on sleep and wake, and make
  Repair unregister and re-register the launch daemon instead of forwarding a
  daemon-only cleanup request.
- **Resolution:** a native IOKit power monitor restores synchronously before the
  sleep acknowledgement and retries only restoration after wake. The bounded
  Repair workflow restores current ownership, invalidates stale XPC, awaits
  asynchronous unregistration, registers the current embedded daemon, opens a
  fresh authenticated XPC connection, and verifies final Ready and Idle state.
  No dependency, watchdog, protocol field, or background scheduler was added.
- **Physical correction:** macOS 26 returned `SMAppServiceErrorDomain` code 1
  when the process that completed unregistration immediately attempted
  registration. The existing unknown-outcome reconciliation now performs one
  delayed, same-request handoff to a fresh helper process; pipe cleanup is
  best-effort so EOF cannot suppress reconciliation.
- **Review:** the independent plan review approved the bounded deadline and
  conservative ownership behavior. The first completed-change pass found no
  source defect and required only this record plus signed physical evidence;
  the final completed-change pass approved the change for merge with no
  Critical or Required findings.

## Hosted-review authorization-repair correction

- **Required finding:** a same-request Repair handed to a fresh helper after
  Service Management rejected same-process registration verified the existing
  authorization rule instead of repairing a mismatched definition.
- **Resolution:** reconciliation now dispatches the original Enable intent to
  rule verification and the original Repair intent to the existing exact-rule
  repair operation. No protocol field, retry, dependency, or service workflow
  was added.
- **Physical correction:** a signed run began with the expected authorization
  rule changed to a shorter credential timeout. Repair traversed two helper and
  daemon processes, returned Success, Ready, and Idle, and restored the exact
  expected rule. Remove then deleted the right and unregistered the service.
- **Focused completed-change review:** approved for merge with no Critical,
  Required, Recommended, or Optional findings. The reviewer independently
  passed the focused regression and diff-hygiene checks.

## Hosted-review restoration and proxy-conflict correction

- **Required findings:** Repair could unregister the daemon after an uncertain
  restoration response, and Apply could replace an already-enabled manual HTTP
  or HTTPS proxy instead of rejecting incompatible network configuration.
- **Resolution:** uncertain restoration now invalidates the connection and
  stops before any service transition, leaving the existing one-shot
  same-request reconciliation and registered daemon recovery path intact.
  Apply rejects enabled HTTP, HTTPS, SOCKS, PAC, or autodiscovery state before
  durable ownership is saved or proxy tuples are replaced. No retry, protocol
  field, durable schema, dependency, or generalized coexistence layer was
  added.
- **Plan review:** approved as implementation-ready with no Critical, Required,
  Recommended, or Optional findings. Deterministic pre-effect tests were judged
  sufficient; the broader physical coexistence matrix remains MACOS-004 scope.
- **Focused completed-change review:** approved for merge with no Critical,
  Required, Recommended, or Optional findings. The reviewer independently
  passed all three affected regressions and diff hygiene.

## Hosted-review convergent Repair correction

- **Required finding:** a completed Repair reconciled under the same request
  identity could repeat the full unregister and register cycle instead of
  returning the already-completed result.
- **Accepted contract correction:** Repair is a desired-state operation. A
  Ready service with an authenticated compatible daemon, Idle ownership, and
  the exact authorization rule is already repaired. An absent or incompatible
  service is registered once, while uncertain cleanup, compatibility, or final
  state fails closed without unregistering a working service. The earlier
  process-replacement requirement is superseded by this accepted contract.
- **Resolution:** direct and reconciled Repair now share one convergent service
  workflow and one authorization-rule convergence function. The workflow keeps
  a verified Ready daemon, registers a missing service once, and rewrites the
  authorization rule only after exact verification reports it absent or
  mismatched. The desktop client immediately performs its single same-request
  reconciliation after an unknown outcome. The prior unregister cycle and its
  timing delay were removed without adding a journal, wire field, dependency,
  retry layer, or new target.
- **Plan review:** approved as implementation-ready with no Critical, Required,
  Recommended, or Optional findings. The reviewer confirmed that both direct
  and reconciled paths use the same authorization convergence boundary.
- **Focused completed-change review:** approved for merge with no Critical,
  Required, Recommended, or Optional findings. The reviewer independently
  passed the focused convergence regressions and diff hygiene, and confirmed
  that the existing signed platform evidence plus deterministic no-transition
  tests are proportionate without recreating the disposable physical harness.

## Hosted-review lost unregister response correction

- **Required finding:** a completed Disable or Remove whose post-unregister
  pipe response was lost could reconcile against the absent service as
  recovery-required and permanently clear the client's pending request.
- **Resolution:** only same-request Disable and Remove reconciliation now joins
  Repair's existing bounded service-recovery workflow. A fresh helper registers
  the exact daemon once, forwards the unchanged reconciliation through mutual
  authentication, requires Success and Idle, and then uses the existing
  unregister and response projection. Direct cleanup against an absent service
  is unchanged, and no journal, wire field, retry loop, dependency, or Kotlin
  behavior was added.
- **Plan review:** approved as implementation-ready with no Critical, Required,
  Recommended, or Optional findings. The reviewer confirmed fail-closed
  registration, idempotent Remove-right absence, and the use of existing signed
  platform evidence rather than another disposable physical harness.
- **Focused completed-change review:** approved for merge with no Critical or
  Required findings. The reviewer independently passed the focused routing and
  recovery regressions, confirmed identity and digest preservation, and found
  the prior signed platform evidence proportionate.

## Verification

- `swift test`: 49 tests passed, including protocol capability, ordering,
  deadline, lifecycle, reconciliation, authorization-rule, durable-state, and
  complete proxy-dictionary cases. The added cases cover post-save Applied
  state, cleanup retry decisions, unavailable-daemon responses, and the exact
  successful Applied renewal acknowledgement, duplicate Applied requests, and
  verified versus unverified Apply ownership. Direct and reconciled
  ownership-ending operations now share cleanup classification, successful
  Idle Enable and Repair complete ownership, and renewal retirement waits for
  in-flight work while suppressing later work.
- `:desktopApp:verifyMacOsHelperPackaging`: passed with a runtime-only local
  Apple Development identity, including exact embedded Info.plist and launchd
  contract checks.
- Signed physical lifecycle, controlled process-loss, Disable, and cleanup
  matrix: passed.
- Aggregate `quality`: passed with the Gradle configuration cache reused.
- Hosted-review correction aggregate `quality`: passed twice with the Gradle
  configuration cache reused; SwiftLint reported zero violations.
- Lease-loss correction aggregate `quality`: passed twice with the Gradle
  configuration cache reused, 42 Swift tests, zero SwiftLint violations, and
  signed-package verification.
- Idempotency and proof correction aggregate `quality`: passed twice with the
  Gradle configuration cache reused, 46 Swift tests, zero SwiftLint violations,
  and signed-package verification.
- Cleanup-retirement `:macosHelper:check`: passed with 49 Swift tests, strict
  formatting, and zero SwiftLint violations.
- Cleanup-retirement aggregate `quality`: passed with the Gradle configuration
  cache reused, release Swift compilation, signed nested-helper packaging, and
  all repository verification targets green.
- Post-unregister-state `:macosHelper:check`: passed with 50 Swift tests, strict
  formatting, and zero SwiftLint violations. The regression covers the Ready to
  NotRegistered projection while preserving every other response field; the
  prior signed physical matrix already proves successful daemon unregistration.
- Post-unregister-state aggregate `quality`: passed with the Gradle
  configuration cache reused, release Swift compilation, JVM and iOS tests,
  zero SwiftLint violations, and signed nested-helper packaging.
- Sleep and Repair correction `:macosHelper:check`: passed with 62 Swift tests,
  strict formatting, and zero SwiftLint violations. Desktop client tests passed
  the same-request unknown-outcome Repair handoff; pipe teardown is best-effort
  so a closed stream cannot suppress that reconciliation.
- Apple Development-signed physical Repair passed with Success, Ready, and Idle,
  restored the exact proxy baseline, and replaced the running daemon process.
- Apple Development-signed physical sleep/wake passed: an active Apply restored
  the exact proxy baseline before wake verification, returned Success, Ready,
  and Idle, did not silently reapply, and allowed a later explicit Apply.
- Independent focused completed-change review: approved for merge with no
  Critical or Required findings. Its documentation-precision recommendation
  was applied before commit.
- Authorization-repair correction `:macosHelper:check`: passed with 63 Swift
  tests, strict formatting, and zero SwiftLint violations. Aggregate `quality`,
  signed-package verification, and diff hygiene passed.
- Restoration and proxy-conflict correction `:macosHelper:check`: passed with
  65 Swift tests, strict formatting, and zero SwiftLint violations. Aggregate
  `quality`, explicit signed-package verification, and diff hygiene passed.
- Convergent Repair correction `:macosHelper:check`: passed with 68 Swift tests,
  strict formatting, and zero SwiftLint violations. Focused desktop protocol
  reconciliation tests passed without the former delay.
- Convergent Repair correction aggregate `quality`: passed with the Gradle
  configuration cache reused, release Swift compilation, JVM and iOS tests,
  signed nested-helper packaging, and all repository verification targets
  green.
- The disposable physical-host harness used for earlier signed Repair evidence
  is no longer installed or present. This correction therefore relies on the
  existing signed lifecycle evidence plus the new deterministic service and
  authorization convergence regressions; it does not recreate a one-off runner
  or persist machine-specific test infrastructure.
- Lost unregister response correction `:macosHelper:check`: passed with 68
  Swift tests, strict formatting, and zero SwiftLint violations. The focused
  regression proves that reconciled Disable and Remove recover a missing
  service once while direct Disable and Remove remain unchanged.
- Lost unregister response correction aggregate `quality`: passed with the
  configuration cache reused, release Swift compilation, JVM and iOS tests,
  signed nested-helper packaging, and all repository verification targets
  green.
