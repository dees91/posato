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
authority, permits one terminal dot on the wire, compares equality after `www`
equivalence, never includes other subdomains or IP literals, and rejects
malformed, duplicate, or conflicting authority without falling back direct. A selected host is denied
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

`decided` (2026-09-05, MACOS-005): identity is the exact binary designated
requirement validated with the Security framework across all architectures,
strict, no network; ad-hoc signatures are refused and the Posato namespace
never matches even if sent. System-critical processes are refused in the
helper even when selected (fixed bundle-identifier set plus
`/System/Library/CoreServices/`, threat T-08); the picker refuses the same
set at selection time before signature inspection (`observed`, 2026-09-08,
`TARGETS-003` follow-up). Observation enumerates process identifiers
directly and hydrates each one on demand, because
`NSWorkspace.runningApplications` does not refresh in a process without a
run loop and would miss applications launched after activation. Child
processes carry different identities and are not targets; an application
modified on disk after launch loses dynamic
validity and is not matched (fail-open). Already-running applications get
the same grace rule as launched ones (graceful request, force after 5 s);
`SESSION-002` owns the "save your work" copy. Replacing the set starts the
new observer before stopping the old one; an empty set clears; a corrupt
payload keeps the existing session. The notice is the fixed generic "This
app is paused" with the end time, posted once per requirement per debounce
window from the helper bundle; presentation failure never blocks
termination. Transport is helper-only pipe operation `12` with capability
bit `8`; the daemon learns nothing, and an unknown outcome kills the
helper and reports `Failed` with no reconciliation. Unit evidence is Swift
and JVM tests including a redaction canary; a spike verified dynamic
matching and graceful termination on a disposable development-signed
application. The gated physical rows run on the maintainer's Mac before
the pull request.

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

`observed` (2026-09-01): MACOS-006 replaced permissive outer-bundle signing
with an inside-out arm64 package boundary. Both Apple Development and ad-hoc
packaging sign the SQLite JDBC native library inside its JAR, every runtime
Mach-O file and runtime bundle, loose application Mach-O code, daemon, helper,
and outer application. Verification enumerates that same surface rather than
assuming `codesign --deep` can inspect native code stored inside an archive.

The Apple Development artifact requires one Apple Development authority and
one nonempty Team ID throughout. Its application and launcher carry only
`com.apple.security.cs.allow-jit`; nested code carries no entitlement. The
credential-free artifact remains teamless and authority-free, with the three
Compose JVM development entitlements confined to its application and launcher.
Strict verification and isolated SQLite-backed launch passed from both mounted
DMGs on the supported Apple silicon Mac.

`observed`: JPackage changed nested signed executables when it converted the
verified application image into a DMG, invalidating the helper resource seal.
The Gradle package task therefore uses Compose's native macOS DMG task with a
staged, already verified application instead of asking JPackage to transform
that image. Preserved PoC revisions `d48e90b` and `fd3f95c` informed the signing
order and SQLite boundary, but the Posato implementation and result were
independently re-established. This remains one-machine development evidence for
the current arm64 JDK 21 and Compose toolchain, not release-signing,
notarization, x86_64, or public-distribution evidence.

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

`observed` (2026-09-15, MACOS-008): Developer ID distribution keeps this
architecture unchanged.

- **Candidates.** Candidates 3 and 4 used Developer ID signing, a secure
  timestamp, hardened runtime, and the Temurin 21 runtime. Each application and
  each DMG was notarized, stapled, and assessed by Gatekeeper as `Notarized
  Developer ID`.
- **Pairing.** On one Apple silicon Mac, the helper and daemon mutual
  code-signing requirements passed unchanged for Developer ID code.
- **Install.** Each candidate was downloaded through Safari, so the copy was
  quarantined, and installed in `/Applications`.
- **Setup.** First setup exposed a defect. After background approval, Check
  again only read status and never installed the Apply right. The fix: Check
  again now runs Enable, which installs a missing right but never overwrites a
  changed one. The approval path was reproduced by switching the background
  item off, and then passed.
- **Blocking.** Safari and Chrome blocked the selected domains while an
  unselected domain loaded. The selected application was quit while a control
  application kept running.
- **Update.** The user quit Posato during an active session, replaced the
  bundle, and reopened it. Quitting restored the proxy and ended the old daemon
  cleanly. After Resume, a new daemon ran the replaced build's code. launchd
  still reported the earlier parent bundle version in its metadata.
- **Companion.** It did not launch while sync stayed off.

Supported in-app removal remains open and belongs to `MACOS-009`.

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

## Device-local application identity

`observed` (2026-08-31): TARGETS-003 implements macOS selection in the existing
normal-user helper. AppKit returns application bundles; Security framework
validation checks all architectures strictly without network access, rejects
ad-hoc signatures, and extracts each binary designated requirement. The macOS
Posato application and every product-owned bundle in its identifier namespace
are rejected as self-selection before that candidate's requirement is
inspected. The complete batch fails before persistence if any member is invalid.

`observed` (2026-08-31): picker capability negotiation exists only on the
authenticated parent-to-helper pipe. Helper-to-daemon XPC has no capability
handshake; its bounded decoder rejects application selection before payload
handling and keeps the lifecycle deadline maximum unchanged.

`observed` (2026-08-31): the AppKit picker helper must be one-shot. Returning
from `NSOpenPanel.runModal()` directly to a blocking inherited-pipe read left
the activated accessory process unable to service AppKit events and caused a
system beachball around later panels. The mapping adapter owns a dedicated
helper client, closes it after every successful or cancelled selection
response, and starts a freshly authenticated process for the next picker.
Desktop shutdown also closes the mapping adapter from a JVM shutdown hook. The
client sends the active picker helper `SIGTERM` without waiting for its
serialized request, and the helper cancels the AppKit panel before exiting;
forced termination remains a bounded fallback. This lifecycle is separate from
enforcement helper ownership.

`observed` (2026-08-31): the normal-user helper path must be resolved only when
the helper is first requested. Eager bundle discovery prevented the documented
Gradle desktop shell from launching outside an application bundle; lazy
discovery preserves packaged signature verification while keeping shell startup
independent of picker availability.

The JVM hashes each exact requirement with SHA-256 for its redacted mapping key
and keeps the requirement bytes and bounded display name in a separate local
SQLDelight database. Its directory is owner-only and the database plus present
SQLite sidecars are owner read/write only. Selection identity remains absent
from the root daemon, synchronized policy, diagnostics, and logs. Process
matching and termination remain deferred to MACOS-005.

## MACOS-004 physical verification

`observed` (2026-09-05, one Apple silicon Mac on macOS 26.5.2 with Safari
26.5.2 and Chrome Stable 152, development-signed package): exact-domain denial
held for HTTP and HTTPS in Safari regular windows and in Chrome regular and
Incognito windows, with the fixed local page written into the same tab; the
control, subdomain, suffix-sibling, and other-suffix hosts stayed reachable;
nonstandard ports were rejected without a direct route; a stalled listener
returned no bytes and never fell back direct; conflict preflight refused an
enabled manual proxy before Apply; forced helper termination, a real
sleep/wake cycle, and a reboot each restored the recorded baseline
byte-identically and left the daemon `Idle`; a synthetic canary never reached
the unified log, evidence, or client output. Safari Private Browsing rows
passed in a second run after the maintainer lifted the Screen Time passcode,
which disables Private Browsing entirely on such a Mac.

`observed`: Safari tabs expose no AppleScript `id` (error -1700 on macOS
26.5.2), so a Safari adapter must address the current tab by index within its
window; Chrome tabs keep a stable `id`. A tab closed or reordered between the
read and the write therefore falls under the accepted no-compare-and-swap
residual of ADR 0005. Unit tests with a stubbed Apple Events runner cannot
catch this class of defect; only the physical rows did.

`observed`: after `SCPreferencesApplyChanges` the settings that
`CFNetworkCopySystemProxySettings` returns lag behind by a short interval, so
an effective-chain check right after Apply must poll within a bounded settle
window. When that check fails, the helper must answer the Apply request with
its own failure payload; echoing the Restore response produces an operation
mismatch that the parent client can only treat as an unknown outcome, and an
Apply reconciled to `Success`/`Idle` must never count as active enforcement.

`observed`: the helper accepts only a parent process signed with the
application identifier and team, so an out-of-bundle harness needs a parent
process signed with the maintainer's development identity; TCC attributes the
helper's Apple Events to the responsible process (the terminal) rather than to
the helper bundle.

`open` (2026-09-05): the helper refuses a `utun`, `ipsec`, or `ppp` primary
interface and an unsatisfied path, but iCloud Private Relay exposes no public
detection, so activation is not refused while Private Relay is on; ADR 0005
treats such undetectable overrides as a non-resistant residual and
`RELEASE-001` owns the disclosure.

`observed` (2026-09-05, maintainer review): a configure that replaces the
domain session must be refused while an Apply is owned and must start the
replacement listener before it stops the previous one, otherwise the system
proxy can point at a closed port; Apply without a configured session is refused
before the authorization prompt; and browser presentation must leave the
proxy's serial queue, because an Apple Events round trip or the Automation
prompt would otherwise stall all relayed traffic and the restore path.

`user-confirmed` (2026-09-05): IP-literal authorities never match an exact
domain and are relayed like unselected hosts; rejecting them would deny
unrelated local-network and developer traffic, so typing a selected site's
address stays a stated non-resistant residual rather than a rejected route.

## Local session integration (`SESSION-002`)

`observed` (worktree verification, `./gradlew quality` green, Simulator driver
evidence): the shared enforcement port sequences commit, apply, report on
start and commit, clear, report on end and expiry. The JVM adapter applies the
browser denial first and restores it when the application configure fails, so
an active claim always means both; Retry clears before re-applying because the
helper refuses configure while an Apply is owned. A refused or failed Apply
leaves the session active with an action-required state and Retry, never an
active claim. Relaunch during an active session reports action-required with an
explicit Resume action instead of raising the administrator prompt
automatically. The active summary shows the frozen start set; Paused-items
edits apply to the next pause.

`open`: the maintainer-attended physical Mac rows (Safari row, disposable
application row, early end, expiry, byte-identical proxy baseline).

`observed` (2026-09-08, `QUALITY-005`): the `SESSION-002` empty desktop
accessibility tree does not reproduce on the current application in either
staging mode (ad-hoc or development-signed) — both runs expose a full tree
from the first readiness wait. The driver now reports a tree with no
addressable window as named failure `DESKTOP_WINDOW_UNAVAILABLE` instead of an
empty success, and the previously unproven unattended
`session-start-action-required-desktop.json` fixture passes end to end.

## Registered service failing to launch

`observed` (2026-09-11, current development package on one Mac): enabling
helper access stalled until the 120-second client deadline, then reported
unavailable. The system service was registered and allowed, but launchd
repeatedly failed to initialize it with `EX_CONFIG` and
`copy_bundle_path ... Invalid or missing Program/ProgramArguments`. Background
Task Management also reported no container item for that service. Its parent
helper entry referenced an older installed verification bundle, while the
running app used the current development package. Both embedded daemon files
existed and their launchd plists had the same relative `BundleProgram`.

`inferred`: stale or inconsistent Service Management bundle registration is
preventing daemon startup; competing installations with the same helper
identifier are a candidate trigger. The exact trigger and recovery remain
unproven until a controlled registration repair succeeds. This evidence does
not establish that the daemon executable is missing or that administrator
approval was denied. A local app-data reset does not reset service registration.

`superseded` in code (MACOS-007): after a parent-side request timeout,
`MacOsHelperClient` still retains `pendingUnknownRequest`, but Check and
Enable now reconcile that original identity instead of issuing Status or a
new Enable. Only a reply that still says nothing about the original request
keeps it: a lost reply and the registered-but-unlaunchable tuple. Every
conclusive answer, including not-registered and a daemon recovery response
for an apply or restore, releases it, so a later Enable can register and
Apply and Restore are not refused for the rest of the process. A helper XPC
endpoint that never accepted the request on Status or Enable returns
structured RecoveryRequired rather than crashing the helper into a
parent-only unknown; a deadline that expires after the request reached the
daemon stays unknown and keeps the request identity.

`superseded` in the native UI (MACOS-007): onboarding shows Checking/Enabling
immediately and keeps Not now usable on Mac; Session distinguishes
uncertainty from registered-but-unlaunchable and no longer tells the person
that restarting repairs registration. Raw evidence remains in ignored local
verification output. The recovery task is
[MACOS-007](../../tasks/specifications/macos-007-helper-setup-recovery.md).

`superseded` (2026-09-11, MACOS-007 stale BTM parent): BTM still names the parent helper as
`/Applications/Posato-MACOS-004.app/Contents/Helpers/PosatoMacOSHelper.app`
while the running development package is a different bundle. The daemon
remains enabled/allowed with launchd `EX_CONFIG`. In-app Check now shows
progress immediately, does not claim Ready, and after a daemon transport loss
reports registered-but-unlaunchable with Check again. In-app copy does not
tell the person to remove Login Items, because that would unregister without
confirmed Idle cleanup. A later lost reply shows uncertainty and Check again
reconciles instead of issuing a blocked Status. Construction and Session
navigation still start no helper. Protocol or integrity failures on Status or
Enable stay failures; they are not mapped to daemon-loss recovery. ADR 0004
still forbids unregister without confirmed Idle cleanup, so the current
package cannot re-point launchd while that stale BTM parent remains. The
leftover copy and proxy-settings Login Item cleanup were maintainer-approved
and attempted; see the following observation.

`observed` (2026-09-11, MACOS-007 AC-01): leftover development copies and a
Background Items reset were maintainer-approved. Empty BTM made
`SMAppService.status` `notFound` even with the plist in-bundle; Check now maps
that to not-enabled so Enable is offered. After a fresh register and Login
Items allow, launchd submitted `system/app.posato.macos.proxy-settings` for
the current development-package helper URL. This Mac showed Background helper
enabled and the same Ready state survived relaunch. HTTP(S) proxy stayed
disabled; iCloud and local websites were unchanged. The helper BTM parent
item can remain `disabled` while the nested daemon is `enabled, allowed` and
launchd still starts it.

`observed` (2026-09-11, MACOS-007 setup dead ends): three dead ends shared one
shape, an action that cannot change its own answer. A reconcile reporting
not-registered used to retain the request so Enable could never register; a
`register()` that threw was indistinguishable from a service that had never
been asked, so Enable silently did nothing; and a registered-but-unlaunchable
helper offered only a Check again that returns the identical reply. The client
now releases a reconciled request on every conclusive answer, the helper
reports a failed registration as a failure rather than as not-registered, and
the options add one sentence offering a Mac restart the second time the same
answer comes back in a state whose action cannot change it, which excludes an
unfinished request because its retry does reconcile the original one. Restarting Posato is still not a registration
repair. `user-confirmed` (2026-09-11): not enabled shows Enable alone, because
Enable already registers and re-reads the status, and a helper state other than
ready is named once above the session action while This Mac is collapsed, so a
Mac that can enforce nothing is visible before a session starts. Nothing is
named before an explicit read.

`observed` (2026-09-16, MACOS-009 removal): the This Mac row removes the helper,
and the whole removal needs no administrator prompt, because the root daemon
restores the proxy, removes and verifies the Apply right, and the helper then
unregisters. On a notarized package the right disappeared, the daemon left the
system domain, and proxy settings matched the pre-removal baseline. Background
item records keep the `allowed` bit after unregistering, so only `enabled`
separates a removed helper; the records survive a move to the Trash with stale
URLs and disappear once the bundle is deleted. The Login Items pane keeps
showing a removed item until System Settings is quit and reopened, so that pane
is not evidence on its own. Turning the background item off unregisters the
daemon but leaves the right installed, which is why a refused removal must be
retried after approval rather than replaced by anything the app can do alone.
A failure that reached the daemon therefore offers Remove again instead of
Check again, which would reinstall the right through the repair path. A session
started after removal reports that the helper is not enabled rather than
claiming a pause.

## Update delivery

`user-confirmed` (2026-09-22): [ADR 0008](../../decisions/0008-macos-update-delivery.md)
selects Sparkle with opt-in checks and installation after the session ends.
`MACOS-011` must prove maintenance admission across installation, cancellation,
and crash/relaunch before delivering it. The unchanged-registration exception
in ADR 0004 remains applicable to compatible updates; an absent service or
best-effort close is not proof of restored ownership.

`observed` in Sparkle 2.10.0 source: canceling an update cycle does not await
termination of its installer.

`observed` (2026-09-23, `MACOS-011` Stage 1 on notarized builds 8-14): a
persisted maintenance gate held on every tested path. The gate closes with a
no-active-session check before the first Install reply. Every scenario stayed
safe: completed updates, Cmd-Q or a crash at Ready to Install, a crash or
cancel during download, an active session, and a second instance. The gate
reopened only when all of the following held:
- the installer job was absent in the user and system launchd domains;
- the on-disk bundle matched the running signed build;
- the service revalidated.

Findings from the proof:
- The standard Ready to Install window cannot be dismissed. Replacement is
  committed once the installer reaches that stage, so quitting or crashing
  installs.
- Sparkle's default User-Agent carries the app version, so Posato sets a
  fixed one.
- Admission must bound companion draining and show progress. Without that,
  an in-flight sync transaction left Install silently waiting.
- Update transitions must be serialized and tied to their cycle. Otherwise a
  release poll or a late admission from an ended cycle can reopen maintenance
  during a newer installation.
- Ad-hoc helper copies registered in LaunchServices by development worktrees
  can stop the daemon from launching (`EX_CONFIG`).

`observed` (2026-09-25, `MACOS-011` Stage 2, notarized candidates in Tart
clones): consent, request privacy, the release feed, and the ADR 0008 matrix
passed. The [execution record](../../tasks/executions/macos-011-updates.md)
maps each row to evidence. Durable findings:
- No request leaves before consent or after opt-out. Besides standard HTTP
  headers, requests carry the fixed User-Agent `PosatoUpdater` and
  `Accept-Language: en`. With the cookie policy set
  to Never, no `Cookie` is sent, even on the redirect right after GitHub's
  `latest/download` hop sets `_octo`.
- Archive-signature failures happen after admission: the gate closes before
  the download, Sparkle's installer rejects the archive, and the gate reopens
  only after the cycle ends.
- A proxy conflict or an unavailable daemon refuses admission and keeps the
  gate closed until the evidence holds. A root-owned bundle uses Sparkle's
  system-domain installer, and the gate waits for that job too.
- An update keeps websites, application choices, an established iCloud
  workspace, and the consent answer, and leaves a removed helper disabled.
- A blocking modal at launch starves the main-thread work that loads
  application choices, so update alerts are sheets.
- Test harness: an app launched by the Tart guest agent makes the agent
  responsible, and a recorded App Management denial then forces
  administrator authorization for every update. Candidates must launch
  through LaunchServices.

The ADR 0003 and ADR 0004 amendments are applied. `RELEASE-003` publishes the
first updater-capable release, whose build number must exceed every candidate
that embeds the release key (25 or higher; pass previous build 24 so the feed
validation enforces it). Until then, the supported product
path remains manual quit, replace, and open.

## Open questions

- Does the full MACOS-004 matrix pass on the release versions and on the
  immediately preceding macOS major line (`RELEASE-001`)?
- Is there a supported way to detect iCloud Private Relay before Apply, or
  does the release disclose it as an unsupported coexistence?
- How are signed installation, notarization, supported removal, public
  support disclosure, and manual recovery verified for the selected release
  channel? (`MACOS-011` verified the in-app update path.)
- `observed` (2026-09-24, `QUALITY-010` `M5`, macOS 26 guest): after Retry
  applied the proxy to a second network service, deleting that service and
  re-enabling the original one left **Restrictions active** with no proxy and
  no application termination. The ownership record still named the deleted
  service, so every later start reported that restrictions may still apply.
  How should the helper reconcile a recorded service that no longer exists?
  Owner: `MACOS-020`.
