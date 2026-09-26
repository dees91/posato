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

  The daemon appends the byte only to a successful reply. Without the flag,
  the reply stays exactly 5 bytes. An older daemon rejects the flag with
  `invalidInput`, which the application reads as "not supported". That is
  the capability probe. The helper decodes the first 5 bytes of a forwarded
  reply and passes a sixth byte through; local helper answers stay 5 bytes.
- The Apply right's rule definition is unchanged. **Grant** passes a
  `kAuthorizationEnvironmentPrompt` naming the permission, so its dialog
  differs from a session start.
- `WireOperation.isApply` is true for apply and apply with grant. It is used
  by `effectiveOperation`, `ownsAppliedMutation`, `failClosedApplyResponse`,
  the helper's `applyNeedsRestore` (effective-chain check and restore),
  `activeRequest`, and `LeaseRenewer`, and by the Kotlin client. Kotlin keeps
  a pending-unknown Apply with grant under the Apply operation code, because
  `WireReconcilePayload` rejects code 16.
- Apply with grant uses the digest `canonicalInputDigest(.apply, port)` in
  the daemon, the helper, and Kotlin. An unknown outcome reconciles with
  original operation `apply`. `WireReconcilePayload.supportedOperations` is
  unchanged.

### Service core and daemon

- **Rights.** `AuthorizationPolicy` takes the right name as a parameter for
  install, verify, repair, remove, acquire, and validate. It adds
  `standingApplyRight` with the Apply definition. An `AuthorizationRules`
  protocol wraps it and reports each right as absent, exact, or mismatched.
  The system implementation is the existing code, and a fake serves the
  tests.
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
    it is absent, deleting any record first. A mismatch fails as
    `ruleRepair`.
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
  - **Enable** and **Repair**, direct or reconciled, use one shared
    `convergeRights` function:
    - Enable deletes the record first when the Apply right is absent, then
      installs both rights. When the Apply right is exact, it installs the
      standing right if absent and fails closed on a mismatch.
    - Repair deletes the record first when it replaces either right.

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
  `unavailable`, including in `setupDaemonUnavailableResponse`, so an
  unreachable daemon never makes the helper exit on them.

### Application (Kotlin)

- **Protocol.** `MacOsHelperProtocol` and `HelperResult` gain the
  operations, `Failure.StandingGrantUnavailable` right after `Cancelled`
  (positional mapping), and a 5-or-6-byte decode for the flagged Status only.
- **Client.** `MacOsHelperClient` gains:
  - `grantState()`, returning `Unsupported`, `Off`, `On`, or `Unknown`.
    A 5-byte `invalidInput` reply means `Unsupported`; any other 5-byte reply
    or failure means `Unknown`. A flagged Status never becomes pending-unknown,
    and an existing pending-unknown request means `Unknown`;
  - `prepareGrant()`, `grant()`, and `revokeGrant()`. None of them joins the
    pending-unknown reconcile bookkeeping; an unknown outcome is settled by a
    flagged Status;
  - `applyWithGrant(port)`, whose pending-unknown reconcile uses the Apply
    digest.
- **Enforcer.** Inside the person-initiated `start`,
  `MacOsBrowserDomainEnforcer` sends a flagged Status first and uses
  `applyWithGrant` only when bit 1 is set. It never relies on the cached
  switch state, which a relaunch or login launch leaves stale. On
  `StandingGrantUnavailable` it falls back to the prompted `apply` once, in
  the same call.
- **Resume rule.** `reapplyRequiresPrompt` stays `true`. Resume after a
  relaunch, wake, login launch, or adoption still waits for the person's
  **Resume restrictions** (ADR 0009). The grant only removes the prompt when
  the person presses it.
- **Switch state.** `MacStandingGrant` follows `MacLoginItem`: a
  `state: StateFlow`, `setEnabled`, and `refresh`. The implementation lives
  in `DesktopMacHelperState` or next to it. Turning it on runs Prepare, then
  Grant, then a flagged Status; turning it off runs Revoke, then a flagged
  Status. It shows only what Status confirms, and `Unknown` when the daemon
  cannot be reached. The switch is disabled while a session is active,
  starting, or changing enforcement on this Mac, with the existing "available
  after the session ends" pattern. The helper client and process are shared
  with enforcement: a Grant prompt would block End early, and a lost reply
  would kill the helper and drop restrictions.
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
| F1 | Grant record parsing: unknown schema, more than 8 entries, undecodable data. The file checks keep their one owner, `DurableOwnershipStoreTests`, through the shared `ProtectedPlistFile` | `StandingGrantStoreTests` |
| F2 | Binding: wrong user ID, account UUID, or platform UUID; no console user, `loginwindow`, or another console user; missing standing right | `StandingGrantPolicyTests` |
| F3 | Revocation: Revoke deletes only its own entry; a full record fails closed; stale entries drop | `StandingGrantPolicyTests` |
| F4 | Daemon grant lifecycle: Disable deletes and Restore does not; Remove deletes before the rights; Enable with an absent Apply right and a replacing Repair delete, direct and reconciled; Prepare deletes when it installs; a rejected apply with grant leaves no durable claim; a missing peer user ID fails | `RequestProcessingGrantTests` with fakes |
| F5 | Apply with grant counts as Apply for the fail-closed response, `applyNeedsRestore`, and the reconcile digest (the lease is shown E2E by a session held past 15 s) | `LifecyclePolicyTests`, `MacOsHelperClientTest` |
| F6 | The helper rejects a port other than its listener's | `ApplyPortCheckTests` (pure function) |
| F7 | The launch-environment parser finds each variable and treats unreadable input as unclean | `ParentLaunchEnvironmentTests` |
| F8 | A lost Revoke reply leaves the switch on until Status confirms; a 5-byte `invalidInput` reply to a flagged Status means unsupported; a flagged Status never becomes pending-unknown; the helper passes a sixth byte through | `DesktopMacHelperStateTest`, `MacOsHelperClientTest`, `WireProtocolTests` |
| F9 | Version skew toward an older helper: the daemon answers an unflagged Status with exactly 5 bytes | `RequestProcessingGrantTests` |

The fallback to the prompted Apply is proven by E2E step 6, not by an
isolated test.

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
3. Quit and relaunch. Before pressing anything, `observe --expect allowed`
   and "Restrictions not active on this Mac" prove that nothing applied
   silently. Then press **Resume restrictions**: no prompt, blocked. Repeat
   with the login item on and a loginwindow restart (`«event aevtrrst»`):
   the same silent-apply check, then Resume through the menu and window.
   Hold the session past 15 s to show the lease renewing. After End early:
   `--expect allowed`. Wake cannot be driven, because a Tart guest cannot
   sleep; it uses the same Resume path as a relaunch, and the limit is
   recorded.
4. Turn the switch off. The next start prompts.
5. Turn it on again, then **Remove from this Mac** and enable again. The
   next start prompts, and the switch reads off.
6. `AC-05`:
   - As the Posato user, resolve `getconf DARWIN_USER_TEMP_DIR`, create the
     `.attach_pid<pid>` trigger file there, and send `SIGQUIT` to the Posato
     process. No `.java_pid<pid>` socket appears.
   - As a negative control, a build from `main` does create the socket.
   - Launch with `posato-control launch --env JAVA_TOOL_OPTIONS=-Dposato.probe=1`.
     A session start falls back to the prompt, and turning the switch on is
     refused.
7. With the switch on, a session start during an active session shows the
   switch disabled.

Then the complete `./gradlew quality`, preceded by
`:desktopApp:verifyMacOsDevelopmentPackaging --rerun-tasks`.

Closeout updates the wiki `macos-enforcement` topic and the log, the
verify-posato feature map, and the posato-control README for `vm exec`. The
amendment gains one residual: code running as the same user can copy the
bundle, edit its `Posato.cfg`, and launch the copy. The copy then runs with a
Java agent or with attach enabled, and the helper's static check still
passes. The impact is applying Posato's own proxy without a prompt.

## High-risk plan review

- **Verdict:** `approved` after one `changes-required` pass.
- **Required findings:**
  1. The flagged Status reply length breaks the helper and client decode.
  2. `applyNeedsRestore` is missing from the `isApply` sites.
  3. Reconciled Enable and Repair do not delete the grant record.
  4. The cached grant state is stale at Resume.
  5. A Grant prompt or a lost reply during a session can drop restrictions
     through the shared helper.
  6. Version skew toward an older helper is untested.
  7. The E2E has no silent-apply check.
- **Resolution:** all seven folded into the plan above, with the recommended
  test-list, Kotlin, probe, prompt, wake, residual, and closeout points and
  both optional points. The second pass approved the plan.

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
