# ADR 0004: Define macOS Helper Ownership and Lifecycle

## Status

- **Status:** Accepted
- **Date:** 2026-08-26
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

## MACOS-014 standing Apply grant amendment (proposed)

`proposed` (2026-09-26). The maintainer chose the mechanism and the opt-in
(`user-confirmed`, recorded in the
[`MACOS-014` brief](../tasks/specifications/macos-014-authorization.md)).
This text takes effect only after an independent security review passes and
the maintainer accepts it. Until then the one-use authorization in
[Authorization of Apply and cleanup](#authorization-of-apply-and-cleanup)
remains the only way to authorize Apply.

### What changes

A person can opt in once, with a fresh administrator authentication, so that
later Apply requests from their own Posato need no administrator prompt. The
daemon records that decision as a root-only standing grant. A grant
authorizes Apply with the fixed proxy host and the port of the helper's own
running listener only.

It changes nothing else:

- the daemon's operation allowlist and fixed proxy values;
- durable ownership, the lease, reconciliation, and Restore;
- Enable, Repair, Disable, and Remove, except for the grant duties below;
- the rule that Restore and cleanup accept no bearer material.

### Opt-in right

The opt-in uses a second custom right:

```text
app.posato.macos.proxy.standing-apply
```

Its compiled definition equals the Apply right: class `user`, group `admin`,
`authenticate-user`, not `session-owner`, not `shared`, no `allow-root`, the
30-second transfer bound, and version 1. A separate name keeps one prompted
Apply from ever becoming a standing grant, and lets the prompt name what the
administrator allows.

The daemon owns this definition as it owns the Apply right:

- Enable and Repair install or converge both rights. Status verifies the
  Apply right as today and reports separately whether the standing right is
  present and exact.
- A daemon enabled by an earlier version has no standing right and stays
  `ready`. The opt-in first sends **Prepare grant**, which installs the
  standing right only when it is absent. A mismatched rule fails closed;
  only Repair replaces it.
- Remove removes and verifies the absence of both rights. An older daemon
  after a downgrade removes only the Apply right and leaves the standing
  right in the authorization database. The rule alone authorizes nothing,
  and a later Enable deletes any record because the Apply right is absent.

### Operation support

**Prepare grant**, **Grant**, **Revoke grant**, and **Apply with grant** are
four new operations in the fixed operation set. Protocol major version 1
stays. Status reports a capability flag for them. The application and helper
send them only when Status from the running daemon shows that flag. An older
daemon is never sent an operation it cannot decode.

### Opt-in operation

1. The helper sends **Prepare grant** (no administrator).
2. The helper obtains the standing right with interaction allowed and sends
   only its external form with **Grant**, over the authenticated connection.
3. The daemon validates and destroys the form under the same one-use rules as
   the Apply form. The form is bound to the connection, request, and session;
   it is never persisted, logged, or replayed, and every owned copy is zeroed.
4. The daemon takes the grantee from the connection, never from the payload:
   - the effective user ID of the XPC peer;
   - that account's generated UID, resolved with `mbr_uid_to_uuid`;
   - the Mac's platform UUID.

   It requires the peer to be the current console user, then writes the
   grant entry atomically. When all 8 entries are in use and none is stale,
   **Grant** fails closed and writes nothing.

The daemon reads the effective user ID from the connection that delivered
the current message (`NSXPCConnection.current()`). It never caches the value
per connection and never resolves a user ID through a process ID. The code
signing requirement is already checked on every message against the sender's
audit token. There is no console user, the console user is `loginwindow`, or
the console user has another user ID: each fails closed. The console check
keeps other accounts out, including SSH sessions and accounts switched to the
background. It does not prove that the screen is unlocked.

**Grant** neither reads nor changes proxy ownership.

**Prepare grant**, **Grant**, and **Revoke grant** are idempotent by value
and are not part of `reconcile`. The helper settles an unknown outcome by
reading Status back. The switch shows the grant as on or off only as Status
confirms. When the daemon cannot be reached, the switch shows that the state
is unknown.

### Grant record

The record is `/Library/Application
Support/Posato/ProxySettings/apply-grant-v1.plist`. It is kept apart from
`ownership-v1.plist` so that the ownership schema stays unchanged. It uses
the same file protections:

- root owner, mode `0600`, and one link;
- no symbolic-link following;
- atomic replacement with `fsync`;
- exclusion from backup.

It contains only:

- a schema version;
- at most 8 entries, each holding a user ID, the account's generated UID, the
  platform UUID, and the grant time.

It holds no names, no authorization material, and no secret. Nothing in it
works as a bearer token.

### Grant-authorized Apply

**Apply with grant** carries only the port and no authorization material.
It is an Apply everywhere except in how it is authorized:

- its canonical input digest is Apply's digest over the port, not a digest
  of its own operation code, so one intent keeps one identity however it was
  authorized;
- the connection that sent it owns cleanup;
- the lease starts, and the helper renews it as for Apply;
- the fail-closed Apply response applies;
- an unknown outcome reconciles with the original operation `apply` and
  Apply's digest.

The helper rejects both Apply forms unless the port equals the port of its
own running loopback listener. It checks this before it obtains
authorization or forwards anything. Without that check, code that controls
the application could point the system proxy at a listener of its own for
the moment before the helper's chain validation restores it. With a grant,
that would need no administrator and could repeat.

The daemon checks, in order:

1. the ownership preflight, as for Apply;
2. the exact Apply right;
3. the grant record: it must pass the file and schema checks;
4. an entry for the peer's effective user ID;
5. that the entry's generated UID equals the account's current one;
6. that the entry's platform UUID equals this Mac's;
7. that the peer is the current console user;
8. the exact standing right.

Only then does it mutate, and it starts the lease as today.

Any failed check returns a new stable category, `standingGrantUnavailable`,
before any durable claim or side effect. The application may then retry the
same request identity through the prompted Apply. That is allowed because it
requests grant-authorized Apply only after the person acts (next section).

### Who may request it

The daemon cannot tell whether a person acted. The application therefore
keeps the ADR 0009 rule. It requests grant-authorized Apply only for:

- the person's own start of a session;
- the person's own **Resume restrictions**: after a relaunch, a wake, a login
  launch, or adopting a session from another device.

The menu, a login launch, synchronization, schedules, and automatic retries
never request any Apply. `SCHEDULE-001` needs its own review to change this.

### Revocation

- **Switch off.** **Revoke grant** needs no administrator. It deletes only the
  entry for the peer's own user ID, is idempotent, and leaves other accounts'
  entries in place.
- **Remove from this Mac.** After `Idle`, the daemon deletes the whole grant
  record and verifies it is gone before removing the rights. Reconciling a
  Remove verifies that too.
- **Disable.** Deletes the whole record, so enabling again asks for a new
  opt-in. Only Disable does this, never the Restore that ends a session,
  although the daemon runs both on one path today. Disable verifies that the
  record is gone, and a reconciled Disable repeats the deletion and the
  check. Turning the background item off in System Settings does not run
  Disable, so the record stays.
- **Rule lifecycle.** The daemon deletes the whole record first when either
  of these happens:
  - Enable finds the Apply right absent and installs it;
  - Repair replaces either right.

  A missing or mismatched right means a Remove or tampering took place.
- **Unusable record.** A record that fails the file or schema checks is never
  used. Revoke, Disable, Remove, and Repair delete it.
- **Stale entries.** An entry whose generated UID or platform UUID no longer
  matches is ignored and dropped at the next write.

An update keeps the grant because the signed identity and daemon label stay
the same. That is intended. Moving the application to the Trash without
Remove leaves the record and rules, as it already leaves the Apply rule. A
reinstall of the same signed Posato for the same account then finds the grant
again. Status reports the grant for the peer, so the switch shows the true
state and the person can turn it off.

### Threats

| Threat | Handling | Residual |
| --- | --- | --- |
| Another local user | The grantee comes from the XPC peer's effective user ID, never the payload. Entries are per account and bound to the generated UID. Apply requires the console user. Revoke deletes only the caller's own entry. The record is root-only. | The proxy is system-wide. While the grantee's session is active, fast user switching leaves it in place for other accounts. That already happens with prompted Apply; the grant only removes the administrator step before each session. |
| Compromised non-admin process under another account | It cannot match the peer user ID. Without the helper's signature it cannot open the XPC connection. | None new. |
| Compromised non-admin process under the grantee's account | Only the signed helper passes the XPC requirement. The helper accepts only a signed Posato parent and only the port of its own listener. The daemon applies only the fixed proxy values, and the lease and Restore clean them up. | `T-07`: any code running as the grantee can start a session without an administrator. The Posato application runs on a Java virtual machine, so such code can load itself into the signed application: through the Java attach mechanism, or by relaunching it with `JAVA_TOOL_OPTIONS` or `JDK_JAVA_OPTIONS`. The hardened runtime does not stop that. Optional hardening, which a VM would verify with `jcmd`: disable the attach mechanism, and refuse to start with those variables set. |
| Replay | **Apply with grant** carries no bearer material. The opt-in form keeps the one-use rules. Durable identities reconcile duplicates. | None. |
| Grant theft | The record has no secret and is root-only. Its entries are bound to the account's generated UID and this Mac's platform UUID. Copying or editing it needs root. | `R-02`: an administrator or root can create a grant. |
| Stale grants after removal, reinstallation, or account deletion | The revocation rules above, the generated UID binding, and deletion when a rule goes missing or changes. | Trash without Remove followed by a reinstall keeps the grant. Status shows it, and the switch or Remove clears it. |
| Rule tampering | Only root changes the authorization database. A missing or mismatched right deletes the grants. | `R-02`. |
| Silent apply | The application rule in "Who may request it". A check in the daemon cannot see whether a person acted. | `T-07`: any code running as the grantee, as in the row above. |
| Version skew | The new operations are sent only when Status reports the capability flag; otherwise the application uses the prompted path. An older helper never requests a grant. | After a downgrade, Remove leaves the standing right in place. It authorizes nothing by itself. |
| Lost reply | Grant, Revoke, and Prepare are idempotent. Status settles them, and the switch shows only what Status confirms. | None. |

### Verification

Written failing first, isolated tests cover:

- record parsing, including the schema, size, and file checks;
- binding to the user ID, generated UID, platform UUID, and console user;
- every revocation path, including Revoke deleting only the caller's own
  entry;
- deleting the record when a rule changes, and only on Disable, never on
  Restore;
- that a rejected grant-authorized Apply leaves no durable claim;
- **Apply with grant** counting as Apply for the lease, cleanup ownership,
  the fail-closed response, and reconciliation;
- the helper rejecting a port other than its own listener's;
- a lost Revoke reply leaving the switch on until Status confirms;
- a wrong peer or user, and no console user or `loginwindow`;
- a full record.

End-to-end verification follows the brief.

### On acceptance

- Rewrite
  [Authorization of Apply and cleanup](#authorization-of-apply-and-cleanup) so
  that Apply is authorized by either the one-use form or a standing grant.
- Add the four operations to the fixed operation set in the
  [MACOS-003 implementation amendment](#macos-003-implementation-amendment).
- Extend the threat model rows `TB-04`, `T-07`, and `T-08`. `T-07` states
  that any code running as the grantee can start a session without an
  administrator.
- Check that [`PRIVACY.md`](../../PRIVACY.md) covers the local, root-only
  user ID and generated UID. Neither leaves the Mac.

## MACOS-011 in-app update amendment

`user-confirmed` (2026-09-22, [ADR 0008](0008-macos-update-delivery.md));
applied on 2026-09-25 by `MACOS-011` after the verified delivery recorded in
its execution record.

The in-app update path uses Sparkle as specified by ADR 0008. Before passing
an Install continuation that can begin downloading, extracting, or installing,
Posato atomically acquires maintenance admission with the no-active-session
check. It rejects every new Apply or Resume, including synchronization-driven
attempts and retries. It reconciles unknown helper outcomes, confirms that
both proxy tuples are no longer Posato-owned, reaches Idle, and completes the
old helper and companion shutdown needed for replacement. Best-effort close,
process disappearance, and absent registration alone do not prove cleanup.

Maintenance admission remains closed while bundle replacement is possible,
including after cancellation, ordinary quit, crash, or relaunch. A Sparkle
dismissal, aborted cycle, or relaunch callback is not sufficient evidence to
admit enforcement. Reopening admission requires positive evidence of either
successful replacement, or termination of the exact installer with no pending
replacement, followed by native ownership and compatibility revalidation.
An enabled service must be ready and Idle before another Apply. A previously
disabled or never-enabled service remains disabled; updating does not grant
permission or silently enable it. Any uncertain outcome retains the gate and
an action-required recovery state.

The 2026-09-15 registration exception remains valid for an unchanged daemon
label, BundleProgram, and compatible executable set. Changes to those values
or an incompatible protocol require a separately verified re-registration
path after cleanup; they cannot use that exception. Unknown state schemas,
mismatched signatures, or incomplete restoration never permit replacement or
silent reset. No active session is ended by the updater. A session arriving
while an admitted update is running does not bypass maintenance admission;
after relaunch, existing administrator-approved Resume rules still apply.

MACOS-011 must prove safe installation, cancellation, and restart recovery
before release. If supported Sparkle mechanisms and exact owned-process
observation cannot establish the required facts, delivery is blocked pending
a maintainer decision. This does not authorize private Sparkle APIs, a fork,
a custom installer, or a weaker fallback. Manual quit, replace, and open
remains the route from version 1.0 to the first updater-capable build.

## SYNC-003 amendment

`user-confirmed` (2026-08-28):
[ADR 0007](0007-apple-workspace-bootstrap-and-native-sync-boundary.md) assigns
CloudKit and synchronizable-Keychain access to the distinct
`app.posato.macos.sync` companion. The enforcement helper and root daemon retain
no synchronization entitlement, workspace key, secure store, or mailbox
responsibility.

## MACOS-002 amendment

`user-confirmed` (2026-08-26):
[ADR 0005](0005-macos-browser-enforcement-and-coexistence.md) resolves the
deferred macOS browser matrix, exact-domain proxy behavior, fixed presentation,
proxy and network coexistence, transient-data, failure, and network-transition
contract. This amendment changes no process, privilege, IPC, authorization, or
durable ownership boundary accepted here. MACOS-004 owns implementation and
physical evidence for ADR 0005.

## MACOS-003 implementation amendment

`observed` (2026-08-28): MACOS-003 fixes the launch daemon identifier, launchd
label, and Mach service name to `app.posato.macos.proxy-settings`. The nested
provider is
`Posato.app/Contents/Helpers/PosatoMacOSHelper.app`, and its daemon executable
is `Contents/Resources/PosatoProxySettingsDaemon` relative to that provider.
The daemon uses `BundleProgram`, a background process type, umask `0077`, and
`KeepAlive` only when the previous exit was unsuccessful.

The private pipe and XPC representations share binary protocol major version
1. Pipe frames are limited to 512 KiB, XPC messages to 64 KiB, deadlines to 120
seconds, identifiers to 16 bytes, and one connection to 256 operations. The
fixed operation set is status, enable, repair, apply, restore, disable, remove,
reconcile, and lease renewal. Version 1 requires its fixed capability bit,
strictly increasing sequences, elapsed deadline propagation, and bounded
cancellation followed by exact helper-process termination when the transport
cannot acknowledge it. Unknown outcomes reconcile the original operation and
SHA-256 canonical input digest under the same request and session identities.
The root ownership record is fixed at `/Library/Application
Support/Posato/ProxySettings/ownership-v1.plist`.

The daemon installs or verifies the exact Apply right during Enable before
reporting ready, verifies it during Status, and removes it only after successful
Idle cleanup; only explicit Repair may replace a mismatched existing rule. The
XPC listener installs its exact helper signing requirement before a delegate can
accept a connection. Failed cleanup keeps connection ownership and service
registration intact. Renewal requires the durable session and request owner.
Periodic renewal uses a separate authenticated XPC connection that never owns
cleanup. The helper rotates that connection before its 257th operation while
keeping the original Apply connection open as the cleanup owner. A rejected,
malformed, timed-out, or failed renewal still invalidates the ownership
connection and terminates the helper nonzero.
When the daemon is unavailable, registration or rule absence alone never proves
cleanup. Apply preflights the exact durable session, request, and canonical
input digest before authorization so a rejected peer cannot acquire another
request's cleanup ownership. The daemon passes that verified fact explicitly
to per-connection lifecycle state; a reported global phase alone never grants
ownership. Durable records are validated semantically before use, and proxy
mutation verifies the complete resulting dictionary rather than only the owned
tuples.

`observed`: a zero-second Authorization Services credential timeout could not
carry the freshly granted right across the helper-to-daemon process boundary;
`authd` treated it as expired before the daemon could validate it. The concrete
rule therefore has a 30-second maximum credential-validity window solely for
that transfer. It remains non-shared, the helper obtains it immediately before
one Apply, the daemon cannot present authorization UI or extend the rights,
and both processes release the reference after the reply while the daemon
destroys the right and both processes zero every owned copy of the external
form and encoded request material. This corrects the earlier zero-timeout
implementation detail without changing the one-Apply authorization boundary.

## TARGETS-003 implementation amendment

`observed` (2026-08-31): application selection is operation `10` on the
authenticated parent-to-normal-user-helper pipe only. That pipe requires
protocol capability bits `1|2`; the existing lifecycle capability remains bit
`1`. Selection may use a 30-minute deadline for a person-controlled AppKit
panel. Every lifecycle frame and helper-to-daemon XPC frame retains the
120-second maximum.

The normal-user helper verifies every selected bundle across all architectures,
rejects ad-hoc signatures and Posato itself, and returns bounded display names
plus binary designated requirements as one atomic batch. The JVM stores only
SHA-256 identifiers, display names, and exact requirement bytes in a separate
owner-only local SQLDelight database. These identities never enter semantic
policy, synchronization, diagnostics, or the root daemon. Daemon decoding
rejects operation `10` before payload handling. Helper-to-daemon XPC has no
capability handshake; it retains the existing protocol version, 120-second
deadline maximum, and lifecycle operation allowlist.

## Context

The arm64 macOS 15-or-later MVP runs its product UI, policy, and orchestration
in the Compose Desktop JVM application. Native enforcement still needs Apple
application observation, a loopback proxy, privileged SystemConfiguration
mutation, authenticated local IPC, and recovery after the foreground
application or one of its native processes fails.

[ADR 0003](0003-mvp-application-architecture-baseline.md) accepted a separate
signed native-helper boundary but deliberately deferred its language,
privilege split, installation, update, recovery, and removal lifecycle. The
[Apple MVP threat model](../security/apple-mvp-threat-model.md) additionally
requires least privilege, authorization for every privileged operation,
bounded authenticated IPC, exact ownership of system mutations, repeatable
cleanup, and redacted outcomes. The accepted
[diagnostics policy](../security/diagnostics-and-support-data.md) excludes
browsing history, domains, application identities, process identifiers,
authorization material, paths, and raw native errors from routine records.

`observed`: the final feasibility spike at enforcement revision
`bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c` demonstrated a Swift child helper,
a loopback proxy, SystemConfiguration apply and exact cleanup, code-signing
relationship checks, bounded request identity, repeated application
termination, and crash-oriented lease recovery on one physical Mac. It also
used a temporary root command and narrow `sudoers` grant that were explicitly
not production installation mechanisms. The current read-only research
checkout does not change those enforcement files after that revision.

The product needs the smallest production boundary that preserves those safety
properties without copying the spike runtime or placing parsing and product
policy in a root process.

## Decision

### Process and language ownership

macOS enforcement uses the existing JVM application and two narrow Swift
executables:

```text
Compose Desktop JVM application
    product policy and session intent
        |
        | private inherited pipes
        v
app.posato.macos.helper
    short-lived, normal-user session helper
    loopback proxy, application observation, native presentation
        |
        | authenticated NSXPC Mach service
        v
Posato proxy-settings launch daemon
    root, Service Management owned
    SystemConfiguration ownership and recovery only
```

The JVM application and shared Kotlin remain the only owners of product policy,
selected targets, session meaning, and user-visible orchestration.

The signed session helper `app.posato.macos.helper` runs with normal user
privilege only while an enforcement session or its required cleanup is active.
It owns the loopback proxy, native application observation and termination, and
later browser-presentation mechanisms. It has no durable security store and no
authority to mutate system proxy settings directly.

The signed launch daemon runs as root and owns only these operations:

- install, verify, and remove the fixed Posato Authorization Services rule;
- prepare, apply, inspect, reconcile, and restore Posato-owned proxy settings;
- read and write the minimum durable proxy-ownership state; and
- report bounded status and redacted outcome categories.

It does not receive domains, application identities, browser events, arbitrary
paths, executables, commands, or shell text. It has no network listener and
owns no synchronization, product policy, proxy forwarding, application
observation, or presentation behavior.

Swift is selected for both executables because Service Management, XPC peer
requirements, Authorization Services, SystemConfiguration, and AppKit are
native Apple boundaries with direct Swift support, and the spike verified the
relevant mechanisms in Swift. A Kotlin/Native implementation would add a
second Kotlin runtime and build, interop, packaging, and debugging surface
without moving shared product policy out of Kotlin. This is a Kotlin-first
ownership decision, not a language-count goal.

### Installation and service states

The application embeds the signed launch daemon and manages it with
`SMAppService`. Registration happens only after an explicit foreground
**Enable macOS enforcement** action. Enforcement does not start until the
service is registered, any required System Settings approval is complete, the
exact signed peers and supported protocol are verified, and the authorization
rule is ready.

The product exposes at least these stable service states:

- `notRegistered`;
- `approvalRequired`;
- `ready`;
- `unavailableOrIncompatible`; and
- `recoveryRequired`.

Unknown helper identity, protocol major version, capability set, durable-state
schema, registration status, or authorization-rule definition fails before a
new mutation. Registration is not repeated for each session. This decision
adds no `sudoers` rule, `SMJobBless` path, shell command, custom installer,
custom updater, or custom watchdog.

Authorization Services plus a root launch daemon select a non-sandboxed macOS
architecture. [RELEASE-001](../tasks/mvp-roadmap.md) owns the eventual
distribution verdict, but an incompatible distribution channel cannot be
selected without revisiting this decision.

The exact daemon bundle identifier, Mach service name, launchd property-list
policy, build wiring, and embedded path are fixed and reviewed by
[MACOS-003](../tasks/mvp-roadmap.md) before implementation. Each must remain
inside the `app.posato.macos` namespace and must not be caller-selectable.

### JVM-to-session-helper boundary

For enforcement, the application launches only `app.posato.macos.helper` from
its signed bundle at a fixed embedded path. The application verifies the
expected helper identity
before use; the helper verifies the expected signed parent identity and package
relationship before accepting a frame.

The processes communicate over private inherited pipes rather than a named
socket or network port. The contract is bounded and versioned and includes:

- an exact protocol major version and capability negotiation;
- one stable session identity and one stable request identity per intent;
- bounded frame, collection, string, and operation sizes;
- explicit operation allowlists and state preconditions;
- ordering or freshness checks, deadlines, cancellation, and timeouts; and
- structured success, conflict, action-required, unknown-outcome, and redacted
  failure categories.

Malformed, oversized, stale, unauthorized, unknown-version, out-of-state, or
non-identical replay input is rejected before a side effect. An exact duplicate
of `(session identity, request identity, canonical input)` is idempotent: it is
never executed again and instead reconciles or returns the already recorded
state and outcome. Reusing one session and request identity with different
canonical input is a conflict.

A timeout means the effect is unknown, not failed. The caller reconciles the
same request identity and canonical input before issuing another intent. It
never creates a new identity merely because the earlier result was lost.

No custom transport encryption is added to inherited pipes. The security
boundary is the fixed signed process relationship, private process-owned file
descriptors, bounded validation, and minimum data exposure. Sensitive values
remain absent from arguments, environment, logs, and structured outcomes.
The concrete frame schema belongs to MACOS-003.

### Session-helper-to-daemon boundary

The session helper connects to one fixed NSXPC Mach service. Both sides enforce
compiled code-signing requirements for the exact Posato identities and signing
relationship. An authenticated connection is not blanket authorization: the
daemon validates the operation, bounded schema, protocol compatibility,
request and session identity, current durable state, and operation-specific
authorization on every call.

XPC inputs are allowlisted data. The daemon accepts no caller-selected path,
executable, process, shell text, service label, proxy dictionary, or arbitrary
SystemConfiguration key. Native errors are mapped to the accepted stable
redacted categories before crossing either IPC boundary.

The daemon durably claims the session identity, request identity, and canonical
intent discriminator before mutation. An exact duplicate, including one on a
new authenticated connection after an unknown outcome, reconciles that durable
operation and does not execute it again. A duplicate Apply does not reuse or
accept the prior external authorization form. The same identities with
different input conflict; stale, unauthorized, or non-identical replay is
rejected.

### Authorization of Apply and cleanup

The only custom privileged apply right is:

```text
app.posato.macos.proxy.apply
```

Its compiled accepted definition requires a freshly authenticated
administrator, is non-shared, has the 30-second transfer bound recorded in the
MACOS-003 amendment, and represents one Apply attempt. The root daemon is the
only component that installs, reads back, verifies, explicitly repairs, and
removes that definition.

Installation occurs only during an explicit in-app Enable operation over the
authenticated connection while proxy ownership is idle. Repair occurs only
during an explicit in-app Repair operation. Direct and reconciled Repair first
verify the exact rule and replace it only when it is absent or semantically
mismatched. Apply never installs or silently repairs the rule: absence or
semantic mismatch fails closed as `recoveryRequired`. Supported removal deletes
only this exact right and verifies its absence before unregistering the daemon.

For one foreground Apply intent, the user-session helper obtains the right
immediately before the operation. Only its external form crosses the already
authenticated XPC connection. The daemon binds the external form to that exact
connection, request identity, and session identity; accepts it once; checks the
right immediately before mutation without presenting UI; and destroys and
zeroes the material after the attempt. It is never persisted, logged, replayed,
or accepted on another connection.

Restore and reconciliation accept no Apply bearer material. They are internally
authorized only for the exact Posato-owned mutation described by durable state.
This permits cleanup after the foreground authorization or application is gone
without granting a general privileged operation.

### Durable ownership and atomic proxy mutation

Absent durable state means `Idle`. The root-only, restrictive, no-backup local
state may contain only:

- a state-schema version;
- the stable request and session identities plus the minimum canonical intent
  discriminator needed to detect identity reuse;
- the exact SystemConfiguration network-service identity;
- the current ownership phase;
- the baseline presence and values of the HTTP and HTTPS proxy tuples; and
- the values Posato applied for those tuples.

It never contains domains, URLs, browser events, selected applications,
process identifiers, authorization material, raw errors, browsing history, or
an unrelated proxy dictionary.

The phases are:

```text
Idle -> Prepared -> Applied -> RestorePending -> Idle
                              \-> RecoveryRequired -> RestorePending
```

`Prepared` is written atomically and durably before system mutation. Every
later transition is written atomically. State is deleted only after independent
verification that no Posato-owned tuple remains. Unknown or corrupt schemas are
`recoveryRequired`; they are never treated as empty or deleted to make progress.

Posato owns two atomic groups rather than six independent fields:

```text
(HTTPEnable,  HTTPProxy,  HTTPPort)
(HTTPSEnable, HTTPSProxy, HTTPSPort)
```

Apply and restore obtain the exclusive `SCPreferences` lock, re-read the full
proxy configuration, compare the owned tuples, construct one resulting
dictionary, commit and apply it, and then verify the complete result. PAC,
autodiscovery, and every unrelated key are preserved. Whether their presence
is supported or requires a fail-before-mutation outcome is decided by
[MACOS-002](../tasks/mvp-roadmap.md).

During restore, a tuple that exactly equals the Posato-applied tuple is restored
atomically to its baseline presence and values. If any tuple member differs,
the complete current tuple is preserved and verified, the conflict remains
durably `recoveryRequired`, and the other tuple is restored only if it remains
exactly Posato-owned. Posato never creates a hybrid tuple or overwrites an
out-of-band tuple change. A normal UI keeps cleanup active and asks the person
to resolve or retry rather than claiming completion.

### Lifecycle and recovery

launchd owns daemon lifetime; Posato adds no watchdog process. Before Apply,
the session helper's loopback listener and the daemon's reconciler are ready.
The daemon combines authenticated-connection invalidation with a short,
monotonic, renewable ownership lease. The loopback listener is not
intentionally stopped until restoration is independently verified.

The daemon reconciles durable state after:

- normal expiry or deliberate early end;
- application-helper or helper-daemon IPC invalidation;
- lease expiry;
- application, session-helper, or daemon crash and restart;
- logout or reboot;
- supported in-app disable, repair, update, or removal; and
- sleep, wake, or primary-network-service change.

The desktop application may remain resident with no window (ADR 0009,
applied by `MACOS-013`). Window visibility is not a lifecycle event for the
helper or daemon. A login launch or any relaunch never re-applies ownership
without a new foreground Apply authorized as above; an active session found at
launch waits in the Resume state.

Sleep, wake, and network-service changes trigger revalidation. Posato never
silently transfers an applied mutation to another network service. Cleanup
remains tied to the recorded service; the support and coexistence behavior for
service changes belongs to MACOS-002.

Automatic restoration is guaranteed only while the exact registered daemon
can run. System Settings disablement, **Stop running in background**, forced
termination that prevents launchd restart, a damaged or missing application
bundle, or other out-of-band removal can prevent the daemon from executing.
Those cases are `recoveryRequired`, not successful automatic cleanup. Recovery
re-enables or re-registers the exact signed compatible daemon and reconciles
its durable state before new enforcement. If that path cannot run, the product
provides truthful manual Apple proxy-recovery instructions.

### Update, repair, disablement, and removal

A supported in-app disable, update, or removal performs these steps in order:

1. reject new Apply intents;
2. request restoration and reconcile any unknown outcome;
3. verify that both atomic tuples are no longer Posato-owned;
4. reach `Idle` and delete the exact owned state;
5. for removal, remove and verify absence of the exact custom authorization
   right;
6. unregister the daemon with `SMAppService` and await completion; and
7. for update, replace only the exact owned component, re-register the exact
   signed compatible daemon, and verify `ready` before allowing another Apply.

Repair is desired-state convergence rather than an unconditional daemon
restart. It rejects new Apply intents, reconciles durable proxy ownership to
`Idle`, and then:

- retains an already enabled daemon only when the fixed mutually authenticated
  connection accepts the current protocol and the exact authorization rule is
  present or repaired;
- registers the current exact embedded daemon once when Service Management
  reports it as not registered or not found; and
- fails closed without unregistering when cleanup, compatibility, or the final
  `ready` and `Idle` state cannot be confirmed.

Direct Repair and same-request reconciliation use this one convergent path. An
exact duplicate that finds the daemon `ready`, ownership `Idle`, and the exact
rule performs no Service Management transition and does not rewrite the rule.

Same-request reconciliation for Disable or Remove does not treat an absent
service as proof that cleanup completed. When the service became unregistered
before its successful response reached the application, the fresh helper
registers the exact embedded daemon once, authenticates it, and reconciles the
original request to `Idle`. Remove also verifies that the exact authorization
right is absent. Only then does the helper unregister the daemon again and
return `notRegistered`; any uncertainty remains action-required or an unknown
outcome.

Disable and removal leave the daemon unregistered. Removal also leaves the
custom right absent. Update and repair may resume service only after the
registered executable set, IPC contract, durable state, and authorization rule
all pass their compatibility checks.

An application or native executable is not updated while proxy ownership is
active. A supported protocol major version and required capabilities must match
before enforcement resumes. Unknown state schemas, incompatible executable
sets, signature mismatch, interrupted unregister, or incomplete cleanup remain
action-required or `recoveryRequired`; they are not reset silently.

This decision does not invent an updater or claim an uninstall hook when the
application is dragged to Trash or damaged. Release packaging and update
delivery belong to MACOS-008.

`user-confirmed` clarification (2026-09-15, MACOS-008): a new notarized build
is delivered as a manual download. Updating is to quit Posato, replace the
application bundle, and open it again, with no stop action in the interface.
That path meets the steps above:

- Quitting Posato ends the helper. That covers **Quit Posato**, Cmd-Q, a
  logout or restart, a supported update relaunch, or a crash. Closing the
  main window does not quit Posato and leaves the helper and its lease
  untouched (ADR 0009, applied by `MACOS-013`). The helper restores any Apply
  it owns when its input closes. If that restore fails, the daemon restores on
  disconnect or when the lease expires.
- The daemon exits successfully only at `Idle` with no connection or lease.
  While restore is unconfirmed the old daemon stays running, and Repair applies.
- Replacing the bundle needs no unregister or re-register while the daemon
  label and `BundleProgram` stay unchanged. launchd starts the replaced
  executable on the next helper connection.
- `ready` is still verified before a session's restrictions resume.

A future change to the daemon label, `BundleProgram`, or an incompatible
protocol requires explicit re-registration. In-app Disable and Remove remain
owned by MACOS-009.

### Required implementation verification

MACOS-003 must automate important state, policy, validation, parsing, and
boundary behavior, including:

- every durable-state crash window and repeatable restore;
- wrong peer, wrong path, wrong signature, wrong version, malformed and
  oversized input, stale sequence, rejected non-identical replay, idempotent
  exact duplicate, and same identity with different input;
- one-use authorization-form binding and replay rejection;
- missing, mismatched, repaired, and removed authorization-rule states;
- timeout with unknown Apply or Restore outcome;
- HTTP and HTTPS tuple conflict without a hybrid write;
- daemon or background-service disablement;
- reboot, logout, sleep, wake, and primary-network-service change; and
- supported update, disable, repair, removal, and manual-recovery routing.

Physical verification must additionally prove signed-peer validation,
Service Management approval states, root-only state protection, complete proxy
dictionary preservation, exact restore, and no sensitive value in arguments,
logs, diagnostic output, or public evidence.

## Alternatives considered

### Run all native enforcement as root

Rejected. Proxy parsing, domain decisions, browser presentation, and
application observation do not need root. Moving them into the daemon would
turn a narrow system-settings capability into a broad privileged attack
surface.

### Reuse the spike's root command and temporary sudoers grant

Rejected. It has no production installation, update, peer-lifecycle, or
durable recovery contract and would expose a command-shaped privilege surface.

### Use only an unprivileged helper

Rejected. It cannot own privileged SystemConfiguration changes or reliably
reconcile durable proxy ownership after the user-session process has gone.

### Implement the native executables in Kotlin/Native

Rejected for this boundary. It adds runtime and interop complexity without
sharing product policy and has no demonstrated safety or maintenance advantage
over the narrow Swift leaves.

### Add encryption to the inherited pipes

Rejected. It would add key establishment and recovery state without replacing
signed peer authentication or boundary validation. The private inherited
descriptors do not expose a generally connectable transport.

### Restore six proxy fields independently or replace the whole dictionary

Rejected. Independent fields can create incoherent HTTP or HTTPS tuples, while
whole-dictionary replacement can overwrite PAC, autodiscovery, VPN, or another
actor's settings. Locked tuple-level compare-and-swap is the minimum safe
ownership model.

### Add a custom watchdog or automatic Trash uninstall hook

Rejected. launchd and durable reconciliation own supported recovery. A custom
watchdog adds another privileged lifecycle, and macOS provides no reliable hook
that can make an already removed or disabled service execute cleanup.

## Consequences

- ADR 0003's single native-helper placeholder becomes two independently
  privileged Swift executables behind narrow interfaces; no product policy
  moves out of Kotlin.
- Root code has one small SystemConfiguration and authorization-rule surface.
- The build and signed application bundle must add one launch-daemon target and
  enforce two signed peer relationships.
- A root daemon and Authorization Services exclude an App Sandbox architecture
  unless a later accepted decision replaces this mechanism.
- [ADR 0005](0005-macos-browser-enforcement-and-coexistence.md) now governs
  exact browser coverage, proxy/VPN/PAC coexistence, exact-domain behavior, and
  presentation; selected-application identity remains open.
- Out-of-band daemon disablement or application removal can require repair or
  manual proxy recovery; the product must state that limit before enabling the
  mechanism.
- No source, protocol, script, identifier, signing value, dependency, or
  machine-specific configuration is copied from the research checkout.

## Open implementation decisions

- concrete browser, proxy, captive-portal, network-transition, and presentation
  implementation plus physical verification in MACOS-004 under ADR 0005;
- selected-application identity and termination behavior in TARGETS-003 and
  MACOS-005; and
- signing, notarization, update delivery, distribution eligibility, manual
  recovery documentation, and release audit in RELEASE-001.

## Sources checked on 2026-08-26

- [Apple SMAppService](https://developer.apple.com/documentation/servicemanagement/smappservice)
- [Apple SMAppService registration](https://developer.apple.com/documentation/servicemanagement/smappservice/register%28%29)
- [Apple SMAppService unregistration](https://developer.apple.com/documentation/servicemanagement/smappservice/unregister%28%29)
- [Apple background-process management](https://developer.apple.com/documentation/appkit/managing-ongoing-background-processes-in-your-mac)
- [Apple Authorization Services concepts](https://developer.apple.com/library/archive/documentation/Security/Conceptual/authorization_concepts/02authconcepts/authconcepts.html)
- [Apple AuthorizationExternalForm](https://developer.apple.com/documentation/security/authorizationexternalform)
- [Apple AuthorizationRightSet](https://developer.apple.com/documentation/security/authorizationrightset%28_%3A_%3A_%3A_%3A_%3A_%3A%29)
- [Apple AuthorizationCopyRights](https://developer.apple.com/documentation/security/authorizationcopyrights%28_%3A_%3A_%3A_%3A_%3A%29)
- [Apple XPC connection code-signing requirement](https://developer.apple.com/documentation/foundation/nsxpcconnection/setcodesigningrequirement%28_%3A%29)
- [Apple code-signing requirements](https://developer.apple.com/documentation/technotes/tn3127-inside-code-signing-requirements)
- [Apple SCPreferencesLock](https://developer.apple.com/documentation/systemconfiguration/scpreferenceslock%28_%3A_%3A%29)
- [Apple SCNetworkProtocolSetConfiguration](https://developer.apple.com/documentation/systemconfiguration/scnetworkprotocolsetconfiguration%28_%3A_%3A%29)
