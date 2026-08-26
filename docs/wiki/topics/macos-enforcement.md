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
The future TARGETS-001 boundary supplies lowercase ASCII canonical
`ExactDomain` values. The proxy revalidates authority, permits one terminal dot
on the wire, compares equality only, never includes subdomains or IP literals,
and rejects malformed, duplicate, or conflicting authority without falling
back direct. A selected host is denied before the port check. Allowed HTTPS is
an opaque tunnel and the helper uses direct upstream sockets to avoid proxy
recursion.

The complete candidate and effective proxy-resolution chains for each selected
domain and both schemes must contain exactly the Posato loopback route. Any
`DIRECT`, PAC, alternate proxy, or later fallback rejects activation or enters
restoration before an active claim. Existing enabled HTTP, HTTPS, SOCKS,
managed/global proxy, PAC, or autodiscovery state is incompatible. Detected
VPNs, network relays or filters that change routing, and iCloud Private Relay
are also incompatible and are never disabled by Posato.

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

HTTP and HTTPS proxy settings are separate atomic `Enable/Proxy/Port` tuples.
Apply and restore use an exclusive preferences lock, preserve PAC,
autodiscovery, and unrelated keys, and independently verify the complete
result. A concurrently changed tuple is preserved as a unit and leaves a
truthful `recoveryRequired` conflict rather than being overwritten or combined
with baseline fields.

launchd and durable state own supported recovery; no custom watchdog exists.
Supported in-app update, disablement, and removal restore and verify before
unregistering. Out-of-band background-item disablement or a missing bundle can
prevent automatic execution and therefore requires repair or truthful manual
proxy recovery rather than a success claim. The architecture is non-sandboxed;
RELEASE-001 must select a compatible distribution path or revisit the ADR.

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

- What exact daemon and Mach identifiers, launchd policy, IPC schemas, bounds,
  and packaged layout will MACOS-003 implement?
- Does MACOS-004 pass every accepted automated and physical browser,
  coexistence, privacy-canary, transition, failure, and cleanup row on the
  release versions?
- How are signed installation, update, notarization, supported removal, public
  support disclosure, and manual recovery verified for the selected release
  channel?
