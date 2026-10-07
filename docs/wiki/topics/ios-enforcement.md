# iOS Enforcement

## Bounded result

`observed`: a development-signed iOS application obtained individual Family
Controls authorization and used one named Managed Settings store to shield one
exact synthetic web domain and one opaque user-selected application. The
website shield appeared in Safari and Chrome, the selected application was
restricted, and unselected controls remained usable on one physical iPhone.

The final automated matrix required one passed test with no failure or skip and
validated six categorical screen outcomes. Exact cleanup cleared the Managed
Settings store, deleted local active state and the opaque selection, revoked
authorization, removed development applications, and confirmed controls were
usable again.

The result proves development feasibility. It does not prove production
entitlement approval, App Store acceptance, supervised deployment, schedule
execution, reboot persistence, bypass resistance, or broad browser behavior.

## Enforcement mechanism

The successful path used system Screen Time APIs:

- Family Controls owned user authorization and target selection;
- the application stored opaque selection values in app-private protected
  storage;
- Managed Settings applied web-domain and application shields;
- the operating system displayed the blocked state.

The tested path used no browser extension, content blocker, Network Extension,
local VPN, DNS mutation, configuration profile, or TLS interception.

Opaque application selections and any opaque web-domain selection values are
platform capabilities, not shared identifiers. They must remain in the native
platform boundary. Exact domain strings in the accepted product policy are a
separate shared concept. Shared Kotlin may refer to a semantic selection or
policy handle but must not inspect, serialize into public logs, or synchronize
an opaque Apple token unless a future platform contract explicitly and safely
supports it.

## Kotlin/native boundary

Likely shared responsibilities:

- policy intent and session state;
- whether selection, permission, or user action is required;
- activation and deactivation orchestration;
- durable local state that contains no opaque Apple value;
- truthful UI state and error categories;
- synchronization of semantic policy where identifiers are portable.

Likely native responsibilities:

- authorization request and status;
- system target picker and presentation lifecycle;
- opaque selection storage;
- translation into Managed Settings shields;
- extension targets and callbacks if schedules or custom shields require them;
- entitlement, provisioning, scene, and application lifecycle integration;
- exact cleanup and authorization revocation behavior.

Kotlin/Native may own some iOS orchestration. Swift remains appropriate for
pure-Swift or SwiftUI-owned APIs and values that cannot cross a stable
Objective-C-compatible boundary cleanly.

## Selection and synchronization asymmetry

An opaque iOS application selection does not naturally equal a bundle ID used
by macOS, Android, or Linux.

`user-confirmed` (2026-08-25): the MVP synchronizes one semantic application
policy while each device owns its local platform selection or mapping. An iOS
opaque selection remains local and is not treated as a portable application
identifier or automatic macOS match. The onboarding flow requires a local
selection on each platform.

`superseded` (2026-09-09, by `ONBOARDING-001` decision `D2`): the previous
sentence's onboarding clause no longer holds. The first website is offered,
not required, and applications are described as optional device-local choices
made later from Paused items, because websites-only is already valid product
copy, applications are unavailable on the Simulator and on a Mac without a
development-signed package, and the MVP outcome needs a local selection only
on the device that will pause applications.

The same asymmetry may apply to website tokens selected through Screen Time
APIs, but the accepted product contract requires exact domain policy to
synchronize. The production boundary must satisfy that contract without
pretending that opaque platform capabilities are universal identifiers. The
invalid-selection lifecycle remains open.

`user-confirmed` (2026-09-02): the first production mapping slice stores a
versioned, bounded set of opaque application tokens in app-private protected
storage. Shared Kotlin receives only a derived local identifier and stable
opaque presentation. Re-selection matches decoded `ApplicationToken` values
by equality; a new token is a new local mapping. The native picker owns Apple
labels, while shared UI shows only an aggregate selected count and distinct
live authorization states. Persisted and shared slot numbers were rejected as
speculative before merge. The later Device Activity extension migrates this
state to the accepted App Group with protection available after first unlock.

`observed` (2026-09-02): on one development-signed physical iPhone, an ordinary
process restart preserved both Family Controls approval and the app-private
selection. Revoking approval returned the live state to not determined while
the stored selection remained intact; granting approval again reopened the
picker with that selection. Uninstalling and reinstalling removed the local
group and opaque selection, required fresh approval, and opened an empty
picker. This is bounded lifecycle evidence, not a cross-device or restore-from-
backup guarantee.

## Extensions and background behavior

The spike used the application target and default system shielding for bounded
manual activation.

`user-confirmed` (2026-08-25): the production target graph includes one Xcode-
owned Device Activity monitor extension,
`app.posato.ios.activitymonitor`. It supplies the normal session-expiry
callback opportunity when the main application is suspended and clears only
Posato-owned restrictions. The application and extension exchange only the
minimum required local state through an App Group. The exact schema belongs to
Apple Task 0 and the first iOS enforcement pull request.

`source-claim`: Apple documents Device Activity callbacks as occurring when the
device is in use. The product and tests therefore must not promise callback
execution at the exact wall-clock end instant.

Default system shields remain the accepted MVP presentation. Custom shield
configuration, shield action, and Device Activity report extensions are not
part of the MVP target graph. Every later extension proposal requires a
user-visible capability and a separate identifier, entitlement, process,
lifecycle, shared-state, testing, and distribution review.

The accepted boundary and identifier are authoritative in
[ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md).

## Failure and cleanup requirements

- Construction is inert; only explicit activation applies shields.
- Missing permission and missing selection are distinct action-required states.
- Activation must not replace valid stored selection with partial input.
- Deactivation and stale-state cleanup are safe to repeat.
- Application removal and authorization changes must have documented effects.
- A failed or cancelled picker must leave the previous valid policy unchanged.
- Tests must reject skipped automation as success.
- Opaque selections, screenshots, result bundles, and device data stay out of
  tracked evidence and diagnostics.

## Recovery after `TARGETS-005`

`observed` (2026-09-03): a per-request generation on the native choose session
stops a stale authorization continuation from presenting or completing a later
selection. Presentation fails closed when another controller is already
presented or the presenter has no window; a refused or failed present completes
the current generation with picker failure and releases the adapter mutex.
A corrupted selection store remains blocked for choose and remove, and the
corruption notice exposes **Clear selection**, which overwrites only that
unreadable file with an empty valid store. Retry alone still reports
corruption. Simulator XCTest covers the FamilyControls-free session and
presentation types and the store/provider clear path; the Family Controls
picker path is compile-checked in the Debug device build.

`user-confirmed` (2026-09-03): a build without a selection producer states
**Choosing apps is not available in this version of Posato.** Clearing a
corrupted store has no confirmation; `DESIGN.md` reserves confirmation for
ending a session early.

`observed` (2026-09-03): on one development-signed iPhone the TARGETS-004
physical flow still passed after this hardening: authorize, pick, cancel with
the prior count retained, process restart with the selection intact, and
in-app clear back to an empty local list. No device, team, or profile value is
recorded.

## Production implementation (`IOS-001`)

`observed` (2026-09-04, Simulator): one named store `app.posato.session`
applies canonical exact domains as `WebDomain` values and local mapping
identifiers as stored `ApplicationToken` values, verifies the set, and
clears only that store. Validation completes before the first write, a
verify mismatch rolls back to an empty owned store, and clear is
idempotent. The `TARGETS-004` store migrates to `group.app.posato.ios.session`
with copy-verify-delete semantics; a corrupt source is never copied.
Simulator and Release builds without the capability report unavailable.
Kotlin `iosMain` exposes the provider seam and the nine platform-neutral
outcomes with redacted carriers; no `expect`/`actual`, no `status()`.

`observed` (2026-09-04, development-signed iPhone): the device suite
(54 passed, 0 skipped) applies and clears the real set with the stored
selection; Safari shows the system blocked presentation, the selected
application shows the system shield, unselected controls stay usable,
clear restores both, and clear after revoked authorization leaves
nothing behind.

`open`: uninstall/reinstall and restore-from-backup observations are
still pending.

## Production implementation (`IOS-002`, simulator-verified)

`observed` (2026-09-05, Simulator): Xcode-owned Device Activity monitor
extension `app.posato.ios.activitymonitor` clears only the named store
`app.posato.session` on the fixed-activity interval-end callback and writes
one versioned App Group record (schema version, session identifier,
cleared-at) into its own `SuspendedExpiry` directory; a foreign activity
clears nothing. The Swift scheduler starts one non-repeating wall-clock
schedule, validates duration against the 15-minute platform minimum and
authorization before any write, stops idempotently, and strips all Apple
error text at the boundary. Kotlin `iosMain` exposes the scheduler seam with
six platform-neutral outcomes, an expired/unknown reconciliation read that
never reports active, and redacted carriers; no `expect`/`actual`.
Simulator suite 68 passed, 0 failed; `./gradlew quality` and the three
credential-free CI builds pass. Reconciliation reports expiry only for the
exact session identifier and consumes the record on report; scheduling drops
a stale cleared record first, so a previous session's clear never reads as
the current session's expiry.

`observed` (2026-09-07, development-signed iPhone): with the `APPLE-002`
profile installed, the signed device build passes once Xcode may contact the
portal. A 17-minute window scheduled from the app, followed by force-quit,
was clear at verify ~4 minutes after interval end; the foreign store
survived, reconciliation read expired with the session id, and the cancelled
probe schedule cleared nothing. Reboot-inside-interval behavior is still
pending by choice (personal phone).

## Local session integration (`SESSION-002`)

`observed` (worktree verification, `./gradlew quality` green including the
Simulator XCTest suites, Simulator driver evidence): the iOS adapter applies
the Posato-owned restrictions and schedules the suspended-expiry interval, or
reports the platform minimum for a short session while the session stays active
on foreground expiry. A `status` read on the existing provider reports the live
named-store state, so post-relaunch state never comes from the timer alone.
Screen entry and foreground read the reconciliation record for the active
session identifier and consume it on report; a stale record never ends the
current session, and clear stays idempotent. Relaunch and foreground silently
re-apply the current set because no administrator prompt exists on this
platform; failure surfaces action-required with Retry. `superseded` in part by
`IOS-006` (2026-09-26): a relaunch into a session whose expiry is still
scheduled adopts it without clearing or re-applying, so the session keeps the
set it started with (`user-confirmed`); re-apply remains for a lost store, a
session below the 15-minute minimum, and Retry.

`open`: the physical iPhone rows (Screen Time granted, site presentation,
force-quit expiry, reopen reconciliation).

## Distribution build (`IOS-003`)

`user-confirmed` (2026-09-14/15, maintainer's portal and Apple email): the
Family Controls (Distribution) request form has no bundle-identifier field, and
Apple assigned the entitlement to the whole account within a minute.
`observed`: distribution profiles still omitted it until
**Family Controls (Distribution)** was enabled on the app and extension App
IDs. Release now signs both targets with the Debug entitlement set and
compiles enforcement under `POSATO_FAMILY_CONTROLS`.

`observed` on one iPhone with the internal TestFlight build 1.0.0 (1): first
install showed the system Screen Time prompt, the grant was read back, and the
picker opened. `user-confirmed`: a website and an individual application were
blocked during a session and usable again after early end and after natural
expiry. Suspended expiry through the extension, reboot, and CloudKit
Production were not exercised on this build.

## Open questions

- ~~Which Family Controls entitlement and distribution paths are available for
  the intended public product at implementation time?~~ Answered by
  `IOS-003`: account-level distribution assignment plus per-App ID enablement.
- ~~What App Group callback protocol is the minimum safe implementation for
  scheduled expiry?~~ Answered for the MVP by `IOS-002`: pending/cleared
  versioned records in a dedicated `SuspendedExpiry` directory.
- ~~Why does relaunching Posato during a session end it as expired?~~
  Answered by `IOS-006` (2026-09-26). `observed` on the test iPhone (iOS
  26.5): stopping and restarting monitoring inside a running window delivers
  `intervalDidEnd` to the extension within about 2 s, and the old extension
  cleared the store and recorded the running session as expired. `inferred`:
  an interval that has not started yet delivers no end, as the `IOS-002`
  cancel row suggested, though it checked only right after the cancel. The
  fix adopts the session on relaunch, and the extension ignores a callback
  more than 60 s before the pending interval end, resolved from the stored
  date components and the absolute end; it clears nothing without a pending
  record and still clears on an unreadable one. The availability-page limit
  stays until `RELEASE-004` publishes the fix.
- Which iOS browsers are included in the support promise?
- What should happen when a selection becomes invalid or the device restores
  from backup?
- Which native APIs can move into `iosMain` without making the boundary harder
  to build, test, or maintain?

## Web filter capacity (`SCHEDULE-004` measurement)

`observed` (2026-09-30, test iPhone on iOS 26.5, build of `ca08247`, whose
iOS enforcement equals release 1.2): a manual session applies each website
with its `www` counterpart, so one website is two `WebDomain` values in
`webContent.blockedByFilter = .specific(...)`. With 25 websites (50 domains)
Safari shows "Website Not Allowed" for every probed website. With 26
websites (52 domains), and with 60, no probed website is blocked, including
those blocked at 25: past 50 domains the filter is dropped as a whole, not
truncated. Apple documents the same 50-domain bound for `.specific(_:)` and
a 50-token bound for `shield.applications` (`source-claim`); the app shield
bound was not measured, and Posato 1.2 accepts up to 64 app choices.
`observed` (same day, throwaway build splitting one session's domains over
two named stores): 20 + 20 domains block both groups, so named stores
compose; 26 + 26 domains block nothing, so the 50-domain bound counts the
union of the stores, and splitting cannot raise it.

## Composed pauses (`SCHEDULE-004` hosts)

`observed` (2026-10-01, test iPhone, PR #125): one Swift composer decides
what the schedule's named store pauses, called by the app and by the monitor
extension under a `flock` in the App Group, so the two never apply over each
other. It reads the version-2 table (each plan's set, every set once with
deduplicated app tokens), the held record each running occurrence keeps in
the App Group, and the manual store's current items as the share already
taken from the 50 web domains and 50 apps. The manual enforcer applies the
same rule with the schedule store as its share and its own store as what it
holds. The app keeps no SQL record of what an iPhone part holds; the stores
and held records are that record. An unreadable version-2 table clears and
never falls back to version 1, which is read as the first set until the app
writes version 2. A 1.2 schedule kept blocking across an App Store-style
install of 1.3 that was never opened, including the end of an overlapping
occurrence. A session shorter than 15 minutes still ends only when Posato is
open at its end (`limits-and-platforms.md`).

## Safari after a website pause ends (`IOS-007`)

`observed` (2026-10-07, test iPhone 13 mini on iOS 26.5.2, build of
`9c37ef7`): after a pause whose web filter
(`webContent.blockedByFilter = .specific(...)`) was in force for 45 s or more
ends, the first page that an already running Safari opens hangs on a black
page, for plain HTTP and HTTPS alike. The page request often never reaches
the server. The second page opened loads in under a second, and a Safari
started fresh loads at once. Safari was opened by `devicectl` from the host,
without the UI driver, so the driver is not the cause. The hang follows
**End early**, natural expiry with Posato in front, and expiry with Posato
in the background, after a wait of 120 s too, and when Safari opens over
Posato. It did not follow a pause of about 10 s or a pause that shielded
only an app. A throwaway build that set the filter and shields to nil, with
or without `clearAllSettings()` 5 s later, hung the same way.

`inferred`: iOS leaves a running Safari with stale web filter state when a
Managed Settings web filter is removed, so the way Posato clears its store
cannot avoid it. The maintainer accepted it as an iOS limit
(`user-confirmed`, 2026-10-07). `superseded`: idea 31's reading that only
plain HTTP hangs and that Posato in front avoids it. `open`: whether
Screen Time's own content restrictions behave the same, and whether the first
page opened after a filter is applied can hang as well.

`observe-unblocked-ios.json` therefore ends Safari with the driver's
`terminateApp` before opening `http://example.com`; three runs after a 60 s
pause passed, and the earlier scenario failed in the same state.
