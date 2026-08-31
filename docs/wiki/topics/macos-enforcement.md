# macOS Enforcement

## Bounded result

`observed`: the development-only macOS spike passed named domain rows in
Safari and Chrome, a later supported-browser follow-up in an isolated Firefox
profile, and repeated termination of one disposable application while an
unlisted control application remained usable.

The experiment used synthetic targets on one physical Mac. It completed exact
cleanup and left no project-owned proxy setting, listener, process, temporary
privilege, browser permission, or disposable profile active.

This proves mechanism feasibility. It does not define supported production
browsers, installation, persistence, update behavior, bypass resistance, or
compatibility with every network environment.

## Domain enforcement mechanism

The successful path used an application-owned loopback HTTP/HTTPS proxy and a
separate narrow mechanism to apply and restore the active network service's
system proxy settings.

For allowed traffic:

- ordinary HTTP requests were relayed upstream;
- HTTPS `CONNECT` requests became opaque tunnels;
- the proxy did not decrypt, inspect, or rewrite TLS content.

For an exact blocked host:

- HTTP forwarding was denied;
- HTTPS `CONNECT` was rejected before the destination TLS session formed;
- an in-memory bounded event could request a fixed local presentation action.

This separation matters: denial happens at the network boundary, while a
friendly blocked destination is browser presentation. The proxy can block a
hostname without possessing a certificate authority or observing page content,
but it cannot issue an HTTP redirect inside an opaque HTTPS tunnel.

## Browser presentation

The successful supported-browser follow-up used explicit integrations for
Safari, Chrome, and Firefox. Presentation was restricted to a fixed destination
after an exact blocked-host event and was not a general browser-automation API.

`observed`: a browser-independent attempt could not guarantee replacement of
the same visible tab across arbitrary browsers. Browser activation, tab
selection, URL-opening behavior, and proxy adoption differ. Generic keyboard
or focus automation was rejected because it could target the wrong
application.

Product consequences:

- browser support must be explicit and tested;
- network denial and visible presentation must have separate contracts;
- a client that ignores the system proxy is not covered by this mechanism;
- same-tab behavior is browser-specific and may change with browser releases;
- presentation failure must not silently convert a denied host into allowed
  traffic.

`user-confirmed` (2026-08-26):
[ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md)
accepts Safari and Google Chrome Stable as the only positive macOS browser
claims. Network denial and fixed same-tab presentation cover regular and
private/incognito contexts only after MACOS-004 passes their separate physical
rows on the exact release versions. Firefox remains unsupported because its
follow-up required a modified disposable profile; Posato does not inspect or
change an ordinary profile. Other browsers and any client override that ignores
system proxy settings remain outside the claim.

The accepted presentation is one self-contained, target-free local page served
through an exact loopback-only `GET /blocked` route. A host-free signal triggers
the browser adapter. The adapter uses only the frontmost captured tab, checks
its current canonical host against in-memory policy, and writes the fixed page.
Presentation is not enforcement, requires Automation permission, and retains a
rare accepted Apple Events race because URL validation and mutation are not
atomic. Detectable mismatch or failure causes no further navigation and never
permits the denied route.

## Accepted exact-domain and coexistence contract

ADR 0005 supports cleartext HTTP on port 80 and HTTPS `CONNECT` on port 443.
`observed` (2026-08-28): the TARGETS-001 boundary now supplies lowercase ASCII
canonical `ExactDomain` values after bounded UTS-46 processing and strict DNS
and A-label round-tripping. Product input accepts a domain rather than a URL,
requires at least two labels, excludes IP literals through the WHATWG
[ends-in-a-number](https://url.spec.whatwg.org/#ends-in-a-number) rule, and
permits only one terminal DNS dot before canonicalization. The proxy revalidates
authority, permits one terminal dot on the wire, compares equality only, never
includes subdomains or IP literals, and rejects malformed, duplicate, or
conflicting authority without falling back direct. A selected host is denied
before the port check. Allowed HTTPS is an opaque tunnel and the helper uses
direct upstream sockets to avoid proxy recursion.

The complete candidate and effective proxy-resolution chains for each selected
domain and both schemes must contain exactly the Posato loopback route. Any
`DIRECT`, PAC, alternate proxy, or later fallback rejects activation or enters
restoration before an active claim. Existing enabled HTTP, HTTPS, SOCKS,
managed/global proxy, PAC, or autodiscovery state is incompatible. Detected
VPNs, network relays or filters that change routing, and iCloud Private Relay
are also incompatible and are never disabled by Posato.

`observed` (2026-08-30): MACOS-003 rejects enabled HTTP, HTTPS, SOCKS, PAC, and
autodiscovery state before saving durable ownership or replacing proxy tuples.
Managed/global proxy, effective-chain, VPN, relay, filter, and Private Relay
verification remains part of the MACOS-004 activation and physical matrix.

Supported activation starts on one stable primary Wi-Fi or Ethernet service.
Captive-portal coexistence is unsupported and adds no probe or automatic sign-
in. Sleep, wake, or primary-service change ends the active claim, restores the
recorded service, and requires an explicit fresh Retry rather than silently
transferring enforcement. Existing rendered, cached, service-worker, offline,
downloaded, or resident content is not erased; denial starts at the first
routed request after activation.

## Application enforcement

The spike observed launch state for one exact disposable application identity
and terminated matching processes. Repeated launches were stopped while a
different control application continued to run.

The production design must define:

- identity selection and validation;
- behavior for already-running applications;
- process relaunch and child-process handling;
- race behavior during activation and deactivation;
- user-visible explanation;
- accessibility and data-loss concerns when terminating applications;
- allowlists for system-critical and product-owned processes.

A generic process killer is not an acceptable shared API. Shared Kotlin should
express application policy intent; the native leaf should own platform process
identity and lifecycle details.

## Desktop application packaging

`observed` (2026-08-27): the SQLDelight desktop host uses its SQLite JDBC
driver, so the runtime image inside the Compose native distribution must include
the JDK `java.sql` module. TARGETS-001 reproduced a packaged-application launch
failure at `java/sql/DriverManager` even though JVM tests and distribution
creation had passed. Adding only `modules("java.sql")` to the desktop package
restored normal launch and maintainer-verified exact-domain interaction; image
inspection also confirmed that the corrected runtime contains `java.sql`.

This is a packaging requirement, not a new persistence layer or a claim about
release signing, notarization, or distribution readiness. The preserved PoC
independently carried the same module requirement for its JDBC-backed desktop
package.

## Privilege, installation, and recovery

The spike used a temporary, narrowly scoped privilege path to modify and restore
system proxy settings. That setup was deliberately not a daemon or production
installer.

`user-confirmed` (2026-08-26):
[ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
accepts two narrow Swift executables behind the Kotlin-owned policy boundary.
The short-lived normal-user helper `app.posato.macos.helper` owns the loopback
proxy, application observation, and native presentation. A root launch daemon
owns only the exact Authorization Services rule, SystemConfiguration proxy
mutation, minimum durable ownership state, and recovery. It receives no
domains, application identities, browser events, arbitrary paths, commands, or
business policy.

The JVM-helper boundary uses private inherited pipes and fixed signed peers.
The helper-daemon boundary uses a fixed NSXPC Mach service with mutual compiled
code-signing requirements. Both boundaries validate bounded versioned input,
request and session identity, state, deadlines, replay, and authorization for
each operation. A timeout has an unknown outcome that must be reconciled under
the same request identity.

The only privileged apply right is `app.posato.macos.proxy.apply`. It requires
fresh administrator authentication, is non-shared and one-use, and is bound to
one authenticated connection, request, and session. Restore uses no bearer
right and can act only on exact durable Posato ownership.

`observed` (2026-08-28): MACOS-003 implements the nested provider at
`Posato.app/Contents/Helpers/PosatoMacOSHelper.app`, with helper identity
`app.posato.macos.helper`. Its root daemon identifier, launchd label, and Mach
service are all `app.posato.macos.proxy-settings`; the fixed daemon executable
is `Contents/Resources/PosatoProxySettingsDaemon` inside the provider. launchd
uses `BundleProgram`, background process type, umask `0077`, and
`KeepAlive/SuccessfulExit=false`. The root-only ownership record is
`/Library/Application Support/Posato/ProxySettings/ownership-v1.plist`.

The fixed binary protocol is major version 1. It bounds pipe frames at 512 KiB,
XPC messages at 64 KiB, request deadlines at 120 seconds, identities at 16
bytes, and each connection at 256 operations. It requires its fixed capability
bit, strictly increasing sequences, elapsed deadline propagation, and bounded
cancellation before exact helper-process termination. Unknown outcomes bind the
original operation and SHA-256 canonical input digest to the same request and
session identities. Exact mutual code-signing requirements pin the Apple
anchor, opposite identifier, and same Team ID; the daemon installs its listener
requirement before a delegate can accept an XPC connection.

Lease renewal requires the exact durable session and request owner. Enable may
install an absent exact rule but only explicit Repair may replace a mismatched
one. That Repair intent is preserved when an unknown outcome moves to a fresh
helper process, so reconciliation restores the exact rule instead of merely
verifying the mismatched definition. Apply verifies the exact durable session,
request, and canonical digest
before authorization so a rejected request cannot acquire another operation's
cleanup ownership. Per-connection cleanup ownership requires that explicit
verified fact rather than a global ownership phase. If the daemon is
unavailable, registration state or rule absence alone is not treated as proof
that durable ownership and proxy cleanup completed. A verified Apply keeps
cleanup ownership for every non-Idle durable phase, including an Applied record
whose final persistence step reports an error after replacement. Exact
duplicate Apply and exact Apply reconciliation validate an Applied record
through maintenance instead of startup restoration. An effective Apply that
has not completed durable preflight can expose a non-Idle phase only as
Conflict, so neither the daemon connection nor helper can claim another
session's ownership. Startup reconciliation, connection invalidation, and an
expired lease retry cleanup on the existing daemon timer until durable
ownership reaches Idle; healthy Applied maintenance preserves its lease. A
background renewal keeps the helper alive only when the daemon explicitly
returns Success with Applied ownership. Renewal uses a separate authenticated,
non-owning XPC connection and rotates it before the 257th operation, while the
original Apply connection remains open as the cleanup owner. Any other outcome,
malformed acknowledgement, transport failure, or timeout invalidates both
connections and terminates the helper nonzero, closing its inherited pipes so
the desktop client cannot retain stale enforcement state. A helper that cannot
reach an enabled daemon reports recovery required rather than synthesizing an
Idle result. Before direct or reconciled Enable, Repair, Restore, Disable, or
Remove cleanup, the helper synchronously retires renewal so an already-running
timer cannot execute after cleanup. Successful Idle Enable and Repair responses
clear helper and daemon-connection cleanup ownership; a failed cleanup does not
restart renewal, leaving the daemon's existing deadline and retry path to
restore ownership without extending enforcement.

`user-confirmed` (2026-08-30): Repair converges to the accepted service state
instead of unconditionally replacing a healthy daemon process. An already
enabled daemon is retained only after the fixed authenticated connection accepts
the current protocol, durable ownership reaches Idle, and the exact
authorization rule verifies or is repaired. A not-registered or not-found
service registers the current embedded daemon once and performs the same final
checks. Direct and same-request reconciled Repair share this behavior, so a
completed duplicate neither cycles Service Management nor rewrites an exact
rule. Unconfirmed cleanup, compatibility, transport, or final state remains
fail-closed. The earlier physical daemon-PID replacement proved the former
workflow but is superseded as a product requirement.

`user-confirmed` (2026-08-30): same-request Disable and Remove reconciliation
does not infer successful cleanup from an absent service. If a successful
unregister response was lost, a fresh helper registers the exact embedded
daemon once, verifies the original request through authenticated reconciliation
to Idle, and unregisters it again. Remove also verifies absence of the exact
authorization right. Direct cleanup against an absent service and every
uncertain registration, connection, cleanup, or final state remain fail-closed.

`observed`: a zero-second custom-right credential timeout expired before an
external authorization form could be validated in the daemon. The implemented
rule uses a 30-second maximum transfer window. It remains non-shared and fresh;
the daemon cannot show UI or extend the right, accepts it for one Apply only,
and destroys and zeroes the material immediately after validation. A signed,
non-notarized development artifact under `/Applications` then passed approval,
registration, exact mutual XPC authentication, administrator-authenticated
Apply, lease renewal, exact restore, right removal, daemon unregistration, and
clean idle-state verification on one physical Apple silicon Mac. Controlled
helper termination after Apply also restored the exact proxy baseline and
removed durable ownership, while a separate Disable preserved the right and
unregistered the daemon before final Remove cleaned both. This is development
evidence, not release-signing or notarization evidence.

The Gradle packaging gate also parses the assembled helper Info.plist and
launchd plist and rejects changes to their identifiers, executable, deployment
minimum, UI role, `BundleProgram`, Mach service, bounded KeepAlive policy,
process type, or umask before signature verification.

HTTP and HTTPS proxy settings are separate atomic `Enable/Proxy/Port` tuples.
Apply and restore use an exclusive preferences lock, preserve PAC,
autodiscovery, and unrelated keys, and independently verify the complete
result. A concurrently changed tuple is preserved as a unit and leaves a
truthful `recoveryRequired` conflict rather than being overwritten or combined
with baseline fields. Durable records are rejected unless their phase, identity
bounds, service, baselines, applied tuples, and digest are semantically valid;
mutation verifies the complete resulting proxy dictionary.

launchd and durable state own supported recovery; no custom watchdog exists.
Supported in-app update, disablement, and removal restore and verify before
unregistering. Out-of-band background-item disablement or a missing bundle can
prevent automatic execution and therefore requires repair or truthful manual
proxy recovery rather than a success claim. The architecture is non-sandboxed;
RELEASE-001 must select a compatible distribution path or revisit the ADR.

`observed` (2026-08-30): on one Apple silicon Mac running macOS 26, the signed
daemon restored an active Apply to the exact proxy baseline during physical
sleep/wake, reached Idle after wake, and did not silently reapply. A later
explicit Apply remained possible. A signed Repair also restored ownership and
replaced the running daemon process. Service Management rejected immediate
registration by the same process that had completed asynchronous
unregistration with error code 1; one bounded same-request reconciliation in a
fresh helper process registered the current embedded daemon and returned Ready
and Idle. A later controlled run began with an existing rule whose credential
timeout was deliberately reduced. Repair used two helper and daemon processes,
restored the expected rule, and returned Ready and Idle before Remove deleted
the right and service. If restoration loses its daemon response, Repair now
invalidates that uncertain connection and leaves the service registered; only
an explicit successful Idle response permits unregistration. This is
one-machine development evidence plus deterministic lifecycle verification,
not a platform-wide timing guarantee or release-readiness claim.

Durable safety properties from the spike:

- snapshot exact state before mutation;
- verify the applied state independently;
- restore only the owned change rather than overwriting unrelated user edits;
- restore unrestricted networking before stopping the listener;
- make cleanup safe to repeat;
- fail without leaving a half-applied proxy;
- bind the listener to loopback;
- never put sensitive values in process arguments or logs.

## Privacy boundary

`user-confirmed` (2026-08-26): the normal-user proxy must transiently hold a
bounded cleartext HTTP request line and headers plus body chunks to relay HTTP.
The Safari or Chrome presentation adapter must transiently hold one current
top-level URL after a host-free blocked signal to avoid replacing an unrelated
tab. HTTPS exposes only CONNECT authority plus opaque tunnel chunks; there is
no TLS plaintext inspection.

Those values are transit buffers, not product records. They are released
promptly and never enter the root daemon, durable state, IPC outcomes, OSLog,
diagnostics, crash metadata, support export, or request/target counters. Posato
retains no allowed or blocked navigation event, history, URL, path, query,
header, cookie, endpoint, or tab timeline. Browser-owned history and runtime
copies remain outside Posato control, and no complete memory-erasure claim is
made.

Application launch observations are also behavioral data. Production logging
must minimize them and define retention before any telemetry or support capture
is introduced.

## Open questions

- Does MACOS-004 pass every accepted automated and physical browser,
  coexistence, privacy-canary, transition, failure, and cleanup row on the
  release versions?
- How are signed installation, update, notarization, supported removal, public
  support disclosure, and manual recovery verified for the selected release
  channel?
