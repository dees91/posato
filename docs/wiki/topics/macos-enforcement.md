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

The blocker needs only a bounded host decision. It should not retain allowed
requests, browsing history, full URLs, paths, query strings, page content, or
TLS plaintext. Diagnostics should use counts, stable categories, and redacted
host-policy identifiers where possible.

Application launch observations are also behavioral data. Production logging
must minimize them and define retention before any telemetry or support capture
is introduced.

## Open questions

- Which browsers are supported in the MVP?
- Is a fixed browser presentation part of the first slice or is clear network
  denial sufficient?
- How will system proxy use interact with VPNs, other local proxies, PAC files,
  captive portals, development tools, and network-service changes?
- What persistence and bypass-resistance level is required?
- What exact daemon and Mach identifiers, launchd policy, IPC schemas, and
  packaged layout will MACOS-003 implement?
- How are signed installation, update, notarization, supported removal, and
  manual recovery verified for the selected release channel?
