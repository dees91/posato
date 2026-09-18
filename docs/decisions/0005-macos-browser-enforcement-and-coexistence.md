# ADR 0005: Bound macOS Browser Enforcement and Coexistence

## Status

- **Status:** Accepted
- **Date:** 2026-08-26
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

## TARGETS-006 www-equivalence clarification

`user-confirmed` (2026-09-18): one stored exact host is what the person typed.
Matching treats `www.<host>` and `<host>` as one paused website. No other
subdomain, suffix, or wildcard coverage is added. The sync format is
unchanged. This PR is the acceptance vehicle for the clarification.

## Context

The macOS MVP needs to deny selected exact website domains without TLS
interception, record no browsing history, preserve unrelated network settings,
and explain a blocked navigation without claiming arbitrary-browser or bypass-
resistant enforcement.

[ADR 0004](0004-macos-helper-ownership-and-lifecycle.md) assigns the loopback
proxy and browser presentation to a normal-user Swift session helper and
assigns only atomic system-proxy ownership and recovery to a minimal root
daemon. It deliberately deferred browser coverage, proxy coexistence, exact-
domain request behavior, presentation, and network-transition policy.

`observed`: the final feasibility spike at revision
`bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c` denied one exact synthetic host in
Safari and Chrome on one physical Mac, relayed an unselected control, presented
a fixed browser destination, restored the exact proxy baseline, and performed
no TLS interception. A later Firefox follow-up passed only in an isolated
disposable profile that was forced to use system proxy settings and changed
external-link behavior. A browser-independent presentation attempt could not
reliably replace the same visible tab, and generic focus or keyboard automation
was rejected as unsafe. Those results establish bounded mechanism feasibility,
not production support.

The product therefore needs a deliberately small contract whose positive
claims can be proved by [MACOS-004](../tasks/mvp-roadmap.md), whose privacy
costs are explicit, and whose unsupported environments fail truthfully.

## Decision

### Browser support and evidence

The positive macOS browser matrix is:

| Browser surface | Network-denial claim | Same-tab presentation claim |
| --- | --- | --- |
| Safari bundled with a supported macOS release | HTTP and HTTPS in regular and Private Browsing windows | Frontmost top-level tab in regular and Private Browsing windows |
| Google Chrome Stable | HTTP and HTTPS in regular and Incognito windows | Frontmost top-level tab in regular and Incognito windows |

Both claims require the browser to use the active network service's system
HTTP and HTTPS proxy settings. Same-tab presentation additionally requires the
browser to be running, Apple Automation permission to be available, and the
exact context to pass the physical matrix. Posato does not classify a Safari
window as regular or private because Safari exposes no accepted mode property;
both modes must pass separately before the Safari presentation claim ships.

Firefox is unsupported. Posato does not inspect or mutate an ordinary Firefox
profile to select system-proxy mode or change external-link behavior. Safari
Technology Preview, Chrome channels other than Stable, Chromium, Edge, Brave,
Arc, other browsers, WebViews, and in-app browsers are also unsupported.
Incidental compatibility is not a support claim.

Managed browser proxy policy, proxy-controlling extensions, browser launch
flags, or any client behavior that ignores or bypasses system proxy settings
is outside the claim. The MVP is not administrator-resistant and does not try
to discover or override every such path.

A release claim requires physical evidence on the latest available patch of
the current and immediately preceding supported macOS major versions, their
bundled Safari versions, and current Chrome Stable. The evidence records exact
operating-system and browser versions plus its date. A new macOS or Chrome
major version is `notYetVerified` until that matrix passes; a release or
support update cannot infer coverage from the feasibility spike or an older
major line.

### Network denial and its limits

Network denial is the enforcement guarantee. Friendly presentation is a
separate bounded operation and can never turn a denied route into an allowed
route.

The initial supported web-port surface is:

- cleartext HTTP on port 80; and
- HTTPS through `CONNECT` on port 443.

A selected exact host is denied before the supported-port check, so changing
the port cannot create a direct route. An unselected destination on any other
port is rejected rather than sent direct. The nonstandard-port limitation must
be disclosed before activation and in release support material.

Enforcement begins with the first request routed through the proxy after
activation. Posato does not erase an already rendered page, browser back-
forward cache, service-worker or offline response, completed download, or
media already resident in the browser. Product language describes network
denial rather than content erasure.

### Exact-domain and request boundary

[TARGETS-001](../tasks/mvp-roadmap.md) owns user-input validation and must
produce one canonical DNS `ExactDomain`: lowercase ASCII IDNA A-labels with no
terminal dot, wildcard, IP literal, single-label or local name, scheme,
userinfo, port, path, query, or fragment.

The proxy revalidates the untrusted network representation. DNS names compare
case-insensitively after accepting at most one terminal dot. A selected
`example.com` matches `www.example.com` and the reverse; it does not match
`badexample.com`, `mail.example.com`, or another suffix. The port does not
change a selected-host match. IPv4 and IPv6 literals can never match an
`ExactDomain`.

For proxied HTTP, the request target must be an absolute-form `http` URI and
there must be exactly one valid `Host` field. Canonical authority and effective
port must agree. For `CONNECT`, the request target must be one valid `host:port`
authority, there must be exactly one valid `Host` field, and the authorities
must agree.

Raw Unicode, userinfo, missing or duplicate `Host`, conflicting authority,
invalid ports, invalid or ambiguous labels, malformed framing, unexpected
request forms, and more than one terminal dot are rejected for that connection
with no direct fallback. Exact input, framing, header, body-chunk, connection,
and deadline bounds are selected by MACOS-004 and tested at their boundaries.

Allowed HTTPS is an opaque tunnel after `CONNECT`; Posato does not inspect SNI,
certificates, TLS plaintext, paths, or content. Allowed HTTP is relayed using
bounded parsing and streaming. Direct upstream sockets bypass system proxy
resolution so the helper cannot recurse through its own listener.

### Proxy resolution and coexistence

Supported activation begins on one stable primary Wi-Fi or Ethernet service.
The listener and daemon reconciler are ready first, and repeated reads must
identify the same primary service before mutation.

The following are incompatible with activation:

- any enabled manual HTTP, HTTPS, or SOCKS proxy;
- a managed or global proxy;
- PAC or automatic proxy discovery; and
- a detected active VPN, network relay or filter that changes routing, or
  iCloud Private Relay.

Posato neither changes nor disables those mechanisms. An override that the
accepted public boundaries cannot detect remains an explicit non-resistant
residual, not a positive protection claim.

Disabled proxy tuple values and every unrelated proxy dictionary key are
preserved under ADR 0004. Before Apply, the helper resolves the complete
candidate proxy chain for both supported schemes and every selected
`ExactDomain` without making a target request. The ordered result must contain
exactly the Posato loopback route and no `DIRECT`, PAC, alternate proxy, or
later fallback entry. After Apply, the helper repeats the check against the
effective system settings before reporting active enforcement. Any additional
route fails; a post-Apply failure immediately enters ADR 0004 restoration. An
environment whose unavoidable chain contains another route is unsupported.

Captive-portal coexistence is unsupported. The person completes network sign-
in before Apply. Posato adds no portal probe, exception, automatic sign-in, or
browser-history collection. A system-reported unsatisfied path or detected
portal condition fails before mutation; an undetected portal remains a stated
limitation and can require deliberate early end followed by Retry.

A transient upstream outage on an otherwise unchanged service fails only the
affected allowed connection. It does not broaden policy or claim that the
session ended.

### Fixed presentation

A blocked cleartext HTTP request receives the fixed local presentation
directly. A blocked HTTPS `CONNECT` is rejected before TLS and emits one host-
free, in-memory presentation signal.

The same loopback listener has one internal exception before general proxy
routing: a bounded, bodyless origin-form `GET /blocked` with no query or
fragment and exact `Host: 127.0.0.1:<current-listener-port>`, received on the
IPv4 loopback-bound listener. It serves the page locally and is never policy-
matched or forwarded. Every other origin-form request, loopback authority,
path, method, port, recursion attempt, or body is rejected.

The self-contained page contains no selected or attempted target, query,
fragment, third-party or network asset, or session-mutation endpoint. It uses
the accepted design language: the site is paused, the session end is shown
when available, and the person is routed to Posato. MACOS-004 owns the safe
fixed app-activation interaction; the page cannot end or change a session.

For Safari and Chrome, the adapter captures the narrowest browser, window, and
tab reference exposed by that browser, reads only that tab's current URL,
reduces it to scheme and canonical host, checks membership in the current
selected-domain set as close to mutation as the API permits, writes only the
fixed local destination, and verifies the result. A detectable mismatch,
permission failure, or race causes no further navigation.

Apple Events provides no compare-and-swap between URL validation and mutation.
A rare undetected change can therefore cause the captured tab to be replaced
after its URL is no longer selected. This time-of-check/time-of-use outcome is
an accepted presentation residual. Posato rate-limits attempts, never treats
presentation as enforcement, and never stores or restores the displaced URL.

There is no browser extension, profile mutation, TLS interception, certificate
installation, DNS or VPN enforcement, JavaScript injection, synthetic input,
generic browser automation, or arbitrary `NSWorkspace.open` fallback.

### Privacy and data lifetime

Kotlin owns policy. The normal-user session helper holds the canonical selected-
domain set only while enforcement needs it. The root daemon and its durable
ownership state never receive domains, browser events, URLs, or application
identities.

The mechanism has two explicit transient-processing boundaries:

- HTTP relay necessarily holds a bounded request line and headers plus bounded
  body chunks, including path, query, cookies, or other headers supplied by the
  browser, solely long enough to validate framing and authority and relay the
  request; and
- after a host-free blocked signal, the Safari or Chrome adapter necessarily
  holds one current top-level URL solely long enough to avoid replacing an
  unrelated tab and to write the fixed destination.

HTTPS handling holds the CONNECT authority and opaque tunnel chunks but never
TLS plaintext. Request, tunnel, and URL buffers are released promptly after
routing or presentation. Owned mutable sensitive buffers are cleared where the
runtime supports it; Posato does not claim complete erasure of browser,
framework, or managed-runtime copies.

These values are transit data, not product records. Posato retains no browsing-
history model, per-request record, allowed or blocked navigation event, request
or target counter, page title or content, cookie, header, path or query,
endpoint IP, or tab timeline. None enters OSLog, diagnostics, crash metadata,
support export, process arguments or environment, IPC outcomes, or durable
state. Only target-free current capability status and accepted stable failure
categories may leave the helper.

The host-free signal and a bounded monotonic presentation debounce exist only
in memory and are discarded at session end. Browser-owned history or cache may
contain the person's attempted URL and the fixed page; Posato neither reads
historical entries nor clears, reconstructs, or restores browser state.

### Failure, network transitions, and recovery

Settings are never applied until the listener is bound and self-tested and the
daemon reconciler is ready. Active enforcement is not reported until the
complete applied tuples, effective no-fallback route, and a target-free
synthetic listener route are independently verified.

Malformed, oversized, timed-out, unsafe-port, recursion, unexpected-method or
framing, and resource-exhaustion input fails closed for that client, never
`DIRECT`. An upstream failure affects only that allowed connection.
Presentation failure records only target-free current capability status and
keeps network denial intact.

Listener failure, lease or IPC invalidation, an owned-tuple mismatch, sleep,
wake, or primary-service change ends the active-enforcement claim and invokes
ADR 0004 reconciliation. Sleep begins restoration. Posato never silently
transfers or reapplies settings after wake or a service change. Restoration
remains tied to the recorded service; if session intent remains active, an
explicit Retry performs fresh preflight and a newly authorized Apply. A service
that cannot be reconciled uses ADR 0004 `recoveryRequired` behavior.

The loopback listener remains available until restoration is independently
verified. Normal expiry and early end restore before stopping it and cancel
presentation work and debounce state. Recovery never navigates back, restores
an attempted URL, closes a tab or window, modifies a profile, or clears browser
data. Persistent Apple Automation permission and release removal guidance stay
with [RELEASE-001](../tasks/mvp-roadmap.md).

## Required implementation evidence

MACOS-004 must automate the important parsing, policy, privacy, and recovery
boundaries, including:

- canonical equality, one-terminal-dot equivalence, and sibling, subdomain,
  IP-literal, raw-Unicode, authority, `Host`, port, framing, and origin-form
  negative cases;
- the one positive local `GET /blocked` route and negative method, body, host,
  path, query, port, and recursion cases;
- bounded request and connection resources, no-direct fallback, direct
  upstream no-recursion behavior, host-free events, redacted outcomes, and
  cleanup of transient state; and
- complete candidate and effective proxy-chain validation, including listener
  failure with a cache-busted selected target that never falls back direct.

The physical release matrix must cover, on every claimed exact version:

- Safari and Chrome regular plus private/incognito HTTP and HTTPS exact denial,
  fixed same-tab presentation, an allowed control, and sibling/subdomain
  controls;
- missing or denied Automation permission, a browser not running, and an
  adapter mismatch or failed verification without weakening denial;
- conflicting HTTP, HTTPS, SOCKS, managed proxy, PAC, and autodiscovery state;
- detected VPN, Private Relay, and not-ready network conditions;
- sleep, wake, primary-service change, listener failure, helper/daemon failure,
  complete no-fallback behavior, and exact cleanup; and
- synthetic privacy canaries proving that no target, full URL, path, query,
  header or body marker, browser event, or request count enters logs,
  diagnostics, IPC outcomes, or durable state.

## Consequences

- Safari and Chrome Stable are the only intended positive browser claims, and
  MACOS-004 physical evidence gates each exact release claim.
- Network denial remains truthful when presentation is unavailable, but
  friendly presentation requires Automation permission and has an accepted
  rare tab-replacement race.
- Only ports 80 and 443 are supported; nonstandard web traffic can fail during
  a session rather than bypass enforcement.
- Existing rendered, cached, offline, downloaded, or resident content is not
  erased.
- Cleartext HTTP transit bytes and one current-tab URL exist briefly in the
  normal-user helper, but never become product, diagnostic, or durable data.
- Existing proxy automation, detected routing overlays, captive portals, and
  undetectable browser overrides limit coexistence and bypass claims.
- Sleep and network changes favor restoration and explicit Retry over seamless
  background continuation.
- This decision adds no production code, dependency, browser component,
  telemetry, or production-readiness claim.

## Alternatives considered

### Claim every system-proxy-compatible browser

Rejected. Browser proxy adoption and presentation differ, and the feasibility
result did not establish arbitrary-browser support.

### Support Firefox by configuring its profile

Rejected for the MVP. The successful follow-up changed an isolated disposable
profile. Inspecting or mutating an ordinary profile would add private data,
compatibility, recovery, and ownership surfaces with no current requirement.

### Add a browser extension or intercept TLS

Rejected. Either choice adds distribution, permission, security, update, and
privacy surfaces that the loopback-denial contract does not need.

### Use generic URL opening or synthetic focus and keyboard input

Rejected. The spike could not guarantee the same visible tab, and synthetic
input can target the wrong application.

### Permit a direct fallback or silently resume on another network service

Rejected. Either behavior can make an active claim false. The contract instead
rejects every fallback route and restores before an explicit Retry.

### Treat denial and browser presentation as one success

Rejected. A presentation adapter can fail after the network route is already
denied. Combining them would either weaken enforcement or report its state
incorrectly.

## Downstream ownership

- TARGETS-001 implements the canonical `ExactDomain` input contract.
- MACOS-003 fixes the concrete helper/daemon protocol, identifiers, limits,
  build wiring, and recovery implementation required by ADR 0004.
- MACOS-004 implements this proxy and browser contract and supplies the
  automated and physical evidence above.
- SESSION-001 owns product session state, early end, action-required messaging,
  and Retry orchestration.
- RELEASE-001 rechecks exact supported versions, Automation and network
  disclosures, signing, notarization, distribution, removal, manual recovery,
  privacy notice, and public support language.

## Sources checked on 2026-08-26

- [Apple proxy settings](https://support.apple.com/en-gb/guide/mac-help/mchlp2591/mac)
- [Apple proxy resolution](https://developer.apple.com/documentation/cfnetwork/cfnetworkcopyproxiesforurl%28_%3A_%3A%29)
- [Apple system proxy settings](https://developer.apple.com/documentation/cfnetwork/cfnetworkcopysystemproxysettings%28%29)
- [Apple Private Relay compatibility](https://support.apple.com/en-ie/102022)
- [Apple network-service order](https://support.apple.com/en-ae/guide/mac-help/mchlp2711/mac)
- [Chromium proxy behavior](https://chromium.googlesource.com/chromium/src/+/main/net/docs/proxy.md)
- [Mozilla Firefox connection settings](https://support.mozilla.org/en-US/kb/connection-settings-firefox)
- [RFC 9110: HTTP semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
- [RFC 9112: HTTP/1.1](https://www.rfc-editor.org/rfc/rfc9112.html)
- [RFC 3986: URI generic syntax](https://www.rfc-editor.org/rfc/rfc3986.html)
- [RFC 5890: IDNA definitions](https://www.rfc-editor.org/rfc/rfc5890.html)

## Related records

- [MVP scope](../product/mvp-scope.md)
- [Design contract](../../DESIGN.md)
- [Apple MVP threat model](../security/apple-mvp-threat-model.md)
- [Diagnostics and support-data policy](../security/diagnostics-and-support-data.md)
- [macOS helper ownership and lifecycle](0004-macos-helper-ownership-and-lifecycle.md)
- [macOS enforcement synthesis](../wiki/topics/macos-enforcement.md)
- [MACOS-002 brief](../tasks/specifications/macos-002-browser-enforcement-contract.md)
- [MACOS-002 execution](../tasks/executions/macos-002-browser-enforcement-contract.md)
