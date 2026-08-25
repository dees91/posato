# MVP Scope

## Status

- **Status:** Accepted
- **Accepted:** 2026-08-25
- **Provenance:** `user-confirmed`

This document is the product authority for the first MVP scope. Feasibility
results remain bounded evidence; they do not expand this contract.

## Product outcome

The MVP helps the owner of their own Apple devices interrupt automatic or
habitual use of selected websites and applications. It adds deliberate friction
without claiming administrator-resistant control or making the product
impossible to remove.

The complete MVP outcome is a time-bounded blocking session that a person can
start on one Apple device and synchronize to a second device using the same
Apple Account after choosing **Sync with iCloud** on each installation. A
person can intentionally end the session early. Stronger UX friction and a
more deeply hidden route for early termination are later work.

## Capability classification

| Capability | Classification | MVP contract |
| --- | --- | --- |
| Website blocking | MVP | Block user-selected domains on supported macOS and iOS versions. Domain policy synchronizes exactly between participating devices. |
| Application blocking | MVP | Block user-selected applications on supported macOS and iOS versions. A shared policy synchronizes, while each device owns its platform-specific application selection or mapping. |
| Manual sessions | MVP | Start a bounded session with an explicit end. The session may expire normally or be ended early through a simple, deliberate user flow. |
| Schedules | Later | Automatic one-time and recurring schedules are not required for the MVP outcome. |
| Apple synchronization | MVP | Synchronize domain policy, semantic application policy, and active-session intent between one Mac and one iPhone through CloudKit Private Database. Use synchronizable Keychain for workspace-key delivery and Apple Account/iCloud Keychain trust for membership. Delivery is best-effort and retryable; the product does not promise device wake-up, eventual delivery, or a delivery time. |
| Apple sync onboarding | MVP | Each installation exposes one **Sync with iCloud** action. Blocker requires no QR, invitation, or approval from another Blocker installation; Apple may still require its own system-level device approval. |
| Total-key-loss recovery | Later | The MVP has no recovery flow after all workspace keys are lost. Ordinary error handling and safe state repair are not deferred by this classification. |

Opaque platform application selections remain local. The product must present
that limitation honestly rather than imply that an iOS selection is a portable
application identifier or an automatic macOS match.

## Platform order and support baseline

macOS and iOS are the first supported platforms and are both required for the
complete MVP outcome. Android and Linux follow later and do not gate the MVP.

At release, the product supports the current and immediately preceding major
versions of both macOS and iOS. The architecture baseline must translate this
product policy into explicit deployment targets and verify them again against
the release toolchain and required Apple APIs.

## Primary user flow

1. The person chooses **Sync with iCloud** on a Mac or iPhone that is signed in
   to their Apple Account.
2. They choose the same action on a second installation on the other supported
   Apple platform. Apple may require its own iCloud Keychain approval outside
   Blocker, but Blocker does not require a QR or cross-device approval.
3. If CloudKit already contains the workspace while its synchronizable key is
   still unavailable, Blocker waits and reports that state. It does not create
   an empty or parallel workspace.
4. They choose shared domains and assign local application selections on each
   device.
5. They start a bounded blocking session on either device.
6. The policy and active-session intent synchronize to the other participating
   device.
7. Both devices enforce their effective local policy until the selected end or
   until the person intentionally ends the session early.

## Measurable MVP outcome

The MVP passes when a controlled physical-device acceptance test on one
supported Mac and one supported iPhone completes the primary user flow without
manual state repair:

- each installation enables **Sync with iCloud** under the same Apple Account
  without a Blocker QR, invitation, or cross-device approval;
- delayed synchronizable-Keychain delivery produces a waiting state and never
  an empty or parallel workspace;
- an exact domain policy created on one device arrives unchanged on the other;
- a semantic application policy synchronizes between the devices while each
  platform-specific application selection remains local;
- a session started on one device reaches the other through synchronization;
- both devices block the selected domain and their locally mapped application
  for that session;
- an early termination initiated on either device synchronizes and removes the
  session's restrictions from both devices; and
- normal expiry removes the session's restrictions correctly on both devices.

This is a pass/fail product-flow measure, not a cloud delivery service-level
agreement. It records no browsing history, allowed navigation events, or usage
counters.

## Explicit non-goals

The MVP does not include or promise:

- one-time or recurring schedules;
- recovery after every workspace key is lost;
- strongly hiding the early-termination route or adding further UX friction;
- Android or Linux implementation or feature parity;
- a product account, product-operated synchronization relay, or
  product-operated shared user-data backend;
- Blocker-managed QR enrollment or cross-device approval in the Apple
  workspace;
- browsing history, allowed-navigation tracking, usage counters, or behavioral
  analytics;
- guaranteed cloud delivery, eventual delivery, delivery latency, or waking a
  sleeping device; or
- resistance to a device administrator, an unremovable control, or absolute
  prevention of deliberate bypass.

## Decisions left to later gates

This scope does not select the product identity, visual design, production
module graph, exact Apple deployment-target numbers, enforcement mechanisms,
production cryptographic primitives and encoding, entitlement path,
distribution model, or pull-request decomposition. Those decisions remain
with their named preparation gates and must not silently change this product
contract. The accepted synchronization trust and workspace-mode boundary is
recorded in
[ADR 0002](../decisions/0002-synchronization-trust-and-workspace-modes.md).
