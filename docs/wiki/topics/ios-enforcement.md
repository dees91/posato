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

The same asymmetry may apply to website tokens selected through Screen Time
APIs, but the accepted product contract requires exact domain policy to
synchronize. The production boundary must satisfy that contract without
pretending that opaque platform capabilities are universal identifiers. The
storage representation, invalid-selection lifecycle, and remapping UX remain
open.

## Extensions and background behavior

The spike used the application target and default system shielding for bounded
manual activation. A production design may need Screen Time extensions for
scheduled monitoring, custom shield appearance, or shield actions. Every
extension adds an identifier, entitlement, process, lifecycle, shared-state,
testing, and distribution boundary.

The exact target graph must be selected before registering production Apple
identifiers. Do not create extension targets merely because the framework
offers them; tie each target to an accepted user-visible capability.

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

## Open questions

- Which Family Controls entitlement and distribution paths are available for
  the intended public product at implementation time?
- Are custom shield configuration and action extensions part of MVP?
- Are recurring schedules implemented through Device Activity, application
  lifecycle opportunities, or a smaller first-slice contract?
- Which iOS browsers are included in the support promise?
- How are local opaque selections associated with synchronized semantic policy?
- What should happen when authorization is revoked, the selection becomes
  invalid, or the device restores from backup?
- Which native APIs can move into `iosMain` without making the boundary harder
  to build, test, or maintain?
