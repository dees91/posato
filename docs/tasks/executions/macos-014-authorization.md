# Execution: `MACOS-014`

- **Brief:** [Reduce repeated administrator prompts on the Mac](../specifications/macos-014-authorization.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude, on the maintainer's delegation (2026-09-26)
- **Reviewer:** independent agents (amendment security review, plan, and completed change)
- **Branch:** `feature/macos-014-authorization`
- **Updated:** 2026-09-26

## Plan

The authority is the accepted ADR 0004 `MACOS-014` amendment. Paths are
relative to `macosHelper/Sources/` for Swift and to the Kotlin source sets
named below.

### Wire contract

- `WireOperation` gains prepare grant `13`, grant `14`, revoke grant `15`,
  and apply with grant `16`. They are not helper-only.
- `FailureCategory` gains `standingGrantUnavailable = 10`. Only the new
  operations return it, so an older helper never sees it.
- Status takes an optional 1-byte payload `0x01`. With it, the daemon appends
  one byte to its 5-byte reply:
  - bit 0: the standing right is present and exact;
  - bit 1: a valid grant exists for the peer.

  Without the flag, the reply stays 5 bytes. An older daemon rejects the flag
  with `invalidInput`, which the application reads as "not supported". That
  is the capability probe.
- `WireOperation.isApply` is true for apply and apply with grant. It is used
  by `effectiveOperation`, `ownsAppliedMutation`, `failClosedApplyResponse`,
  the helper's `activeRequest` and `LeaseRenewer`, and the Kotlin client.
- Apply with grant uses the digest `canonicalInputDigest(.apply, port)` in
  the daemon, the helper, and Kotlin. An unknown outcome reconciles with
  original operation `apply`. `WireReconcilePayload.supportedOperations` is
  unchanged.

### Service core and daemon

- **Rights.** `AuthorizationPolicy` takes the right name as a parameter for
  install, verify, repair, remove, acquire, and validate. It adds
  `standingApplyRight` with the Apply definition. An `AuthorizationRules`
  protocol wraps it; the system implementation is the existing code, and a
  fake serves the tests.
- **File primitives.** The private file helpers of `DurableOwnershipStore`
  move into a `ProtectedPlistFile` with a prefix parameter:
  - `O_NOFOLLOW`, owner, `nlink`, mode, and size checks;
  - atomic temp file, `fsync`, rename, directory `fsync`, and backup
    exclusion.

  The ownership store keeps its behavior and its tests.
- **Grant store.** `StandingGrantStore` holds `apply-grant-v1.plist`:
  - schema 1 and at most 8 entries of `{userID, accountUUID, platformUUID,
    grantedAt}`;
  - a `StandingGrantPersistence` protocol with a memory fake.
- **Policy.** `StandingGrantPolicy` is pure, with no I/O:
  - `evaluate(record, peerUserID, identity)`;
  - `granting(record, peerUserID, identity)`, which replaces the same user
    ID, drops stale entries, and returns full at 8;
  - `revoking(record, peerUserID)`.

  `SystemIdentity` is a protocol:
  - `accountUUID(uid)` through `mbr_uid_to_uuid`;
  - `platformUUID()` through IOKit `IOPlatformUUID`;
  - `consoleUserID()` through `SCDynamicStoreCopyConsoleUser`, which returns
    nil for no user or for `loginwindow`.
- **Peer user ID.** `ProxySettingsServiceObject.perform` reads
  `NSXPCConnection.current()?.effectiveUserIdentifier` synchronously, before
  the asynchronous hand-off. It passes the value with that one message. A
  missing value fails closed.
- **Coordinator.** `RequestCoordinator` gets an initializer that takes the
  ownership persistence, proxy configuration, grant persistence, identity,
  and authorization rules. Production keeps its defaults.
- **Operations.**
  - **Status** verifies the Apply right as today and, when flagged, appends
    the grant byte.
  - **Prepare grant** (empty payload) installs the standing right only when
    it is absent. A mismatch fails as `ruleRepair`.
  - **Grant** (payload is the external form):
    1. requires both rights to be exact;
    2. validates and destroys the standing form under the one-use rules;
    3. requires `consoleUserID == peer`;
    4. writes the entry. A full record fails as `standingGrantUnavailable`.
  - **Revoke grant** (empty payload) deletes only the peer's own entry. An
    unusable record is deleted whole. It is idempotent.
  - **Apply with grant** (2-byte payload) runs, in order:
    1. the ownership preflight;
    2. the Apply right check;
    3. `evaluate`, which checks the record, entry, account, platform,
       console user, and standing right;
    4. `engine.apply`;
    5. the lease.

    A failed evaluation throws `standingGrantUnavailable` before
    `engine.apply`.
  - **Disable** is separated from Restore. After Restore reaches `Idle`, it
    deletes the grant record and verifies that it is gone. A reconciled
    Disable does the same. Restore never touches grants.
  - **Remove** deletes the grant record and verifies that it is gone, then
    removes both rights. A reconciled Remove does the same.
  - **Enable** deletes the record first when the Apply right is absent, then
    installs both rights. When the Apply right is exact, it installs the
    standing right if absent and fails closed on a mismatch.
  - **Repair** deletes the record first when it replaces either right.

### Helper

- **Launch environment.** At startup, `ParentLaunchEnvironment` reads
  `KERN_PROCARGS2` for `getppid()`. A pure parser returns whether the
  environment has `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, or
  `JDK_JAVA_OPTIONS`. A read failure counts as unclean.
- **Pipe validation.**
  - Status: empty, or `0x01`.
  - Prepare grant, Grant, and Revoke grant: empty. The helper obtains the
    right itself.
  - Apply with grant: 2 bytes.
- **Port check.** Apply and apply with grant need `domainSession`, and a
  payload port equal to `domainSession.port`. Otherwise the helper answers
  locally with `invalidInput` before any authorization or forwarding.
- **Unclean environment.** Grant and apply with grant answer locally with
  `standingGrantUnavailable`.
- **Grant forwarding.** The helper obtains the standing right with
  interaction. A declined prompt answers locally with `cancelled` and does
  not exit. The external form is zeroed as it is for Apply.
- **Service not enabled.** The new operations answer locally with
  `unavailable`.

### Application (Kotlin)

- **Protocol.** `MacOsHelperProtocol` and `HelperResult` gain the operations,
  `Failure.StandingGrantUnavailable`, and a 6-byte decode only for the
  flagged Status.
- **Client.** `MacOsHelperClient` gains:
  - `grantState()`, returning `Unsupported`, `Off`, `On`, or `Unknown`;
  - `prepareGrant()`, `grant()`, and `revokeGrant()`. None of them joins the
    pending-unknown reconcile bookkeeping; an unknown outcome is settled by a
    flagged Status;
  - `applyWithGrant(port)`, whose pending-unknown reconcile uses the Apply
    digest.
- **Enforcer.** `MacOsBrowserDomainEnforcer.start` uses `applyWithGrant`
  while the grant state is `On`. On `StandingGrantUnavailable` it falls back
  to the prompted `apply` once, in the same person-initiated call, and then
  refreshes the grant state.
- **Resume rule.** `reapplyRequiresPrompt` stays `true`. Resume after a
  relaunch, wake, login launch, or adoption still waits for the person's
  **Resume restrictions** (ADR 0009). The grant only removes the prompt when
  the person presses it.
- **Switch state.** `MacStandingGrant` follows `MacLoginItem`: a
  `state: StateFlow`, `setEnabled`, and `refresh`. The implementation lives
  in `DesktopMacHelperState` or next to it. Turning it on runs Prepare, then
  Grant, then a flagged Status; turning it off runs Revoke, then a flagged
  Status. It shows only what Status confirms, and `Unknown` when the daemon
  cannot be reached.
- **UI.** `MacSetupSection` adds the switch below **Open Posato at login**.
  It is shown only while the helper is ready and the grant state is not
  `Unsupported`:
  - label: **Start sessions without the password**;
  - supporting text: "An administrator approves this once. Restrictions
    still apply only when you start or resume a session.";
  - `Unknown`: the switch is disabled, and the text reads "Could not confirm
    this setting. Check again.".

  The Remove confirmation line becomes "proxy settings are restored and the
  administrator rules and this permission are removed". A DESIGN.md
  amendment records the switch, which the maintainer accepted (2026-09-26),
  and its copy.
- **JVM hardening.** `compose.desktop.application.jvmArgs` gets
  `-XX:+DisableAttachMechanism`. The packaging verification tasks read
  `Contents/app/Posato.cfg` and require that option.

### Isolated tests, written failing first (`AC-04`)

| # | Failure | Test |
| --- | --- | --- |
| F1 | Grant record parsing: unknown schema, more than 8 entries, oversize, wrong owner, mode, or link | `StandingGrantStoreTests` |
| F2 | Binding: wrong user ID, account UUID, or platform UUID; no console user, `loginwindow`, or another console user; missing standing right | `StandingGrantPolicyTests` |
| F3 | Revocation: Revoke deletes only its own entry; a full record fails closed; stale entries drop | `StandingGrantPolicyTests` |
| F4 | Daemon grant lifecycle: Disable deletes and Restore does not; Remove deletes before the rights; Enable with an absent Apply right and a replacing Repair delete; a rejected apply with grant leaves no durable claim; a missing peer user ID fails | `RequestProcessingGrantTests` with fakes |
| F5 | Apply with grant counts as Apply: lease ownership, fail-closed response, reconcile digest | `LifecyclePolicyTests`, `WireProtocolTests`, `MacOsHelperClientTest` |
| F6 | The helper rejects a port other than its listener's | `ApplyPortCheckTests` (pure function) |
| F7 | The launch-environment parser finds each variable and treats unreadable input as unclean | `ParentLaunchEnvironmentTests` |
| F8 | A lost Revoke reply leaves the switch on until Status confirms; `invalidInput` on a flagged Status means unsupported | `DesktopMacHelperStateTest`, `MacOsHelperClientTest` |
| F9 | The enforcer falls back to the prompted Apply once on `StandingGrantUnavailable` | `MacOsBrowserDomainEnforcerTest` |

Each test is seen failing before its fix. A negative control is shown by a
mutation check where the test could pass vacuously.

### End to end in Tart (`AC-02`, `AC-03`, `AC-05`)

These steps use a dev-signed build in a primary clone and `posato-control`
only. They add a `vm exec` command for guest shell steps.

1. Onboard and enable the helper (`vm prompt admin`, `vm prompt background`).
   With the switch off, a session start prompts. That is the baseline.
2. Turn the switch on (one prompt). The session starts with no prompt:
   `vm wait-text` sees no password dialog, and `observe --expect blocked`
   passes.
3. Quit and relaunch, then press **Resume restrictions**: no prompt,
   blocked. With the login item on, a login launch followed by Resume
   through the menu and window: no prompt, blocked. After End early:
   `--expect allowed`.
4. Turn the switch off. The next start prompts.
5. Turn it on again, then **Remove from this Mac** and enable again. The
   next start prompts, and the switch reads off.
6. `AC-05`:
   - Create the attach trigger file and send `SIGQUIT` to the Posato
     process. No `.java_pid` socket appears.
   - As a mutation control, a build without the option does create the
     socket.
   - Run `launchctl setenv JAVA_TOOL_OPTIONS -Dposato.probe=1` and relaunch
     Posato. A session start falls back to the prompt, and turning the
     switch on is refused. Then unset the variable.

Then the complete `./gradlew quality`, preceded by
`:desktopApp:verifyMacOsDevelopmentPackaging --rerun-tasks`.

## High-risk plan review

- **Verdict:** `pending`

## Result

- Pending.

## Completed-change review

- **Verdict:** `pending`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- Accepted residuals are listed in the ADR 0004 `MACOS-014` amendment.

## Final

- **Status:** `active`
- **Outcome:** pending
