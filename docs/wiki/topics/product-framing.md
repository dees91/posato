# Product Framing

## Current status

- `user-confirmed`: the repository is the clean product home for an MVP written
  from scratch, using PoC knowledge and selectively reviewed code
  ideas rather than treating experimental code as the product foundation.
- `user-confirmed`: the intended project is open source and should be useful to
  people beyond the original maintainer.
- `user-confirmed`: Apple platforms are first. Android, Linux, and a portable
  synchronization path remain later directions and are not MVP parity gates.
- `user-confirmed`: the first MVP scope is accepted in the
  [MVP scope contract](../../product/mvp-scope.md).
- `user-confirmed` (2026-08-25): the product and display name is **Posato**,
  with `posato.app` selected as the canonical public domain. Registrar control
  and the stable technical namespace remain open in the
  [product identity contract](../../product/product-identity.md).
- `user-confirmed` (2026-08-25): Apple MVP synchronization uses one
  **Sync with iCloud** action per installation, Apple Account/iCloud Keychain
  trust for membership, and no application-level QR or cross-device approval.
  See
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md).
- `open`: the complete visual identity, distribution path, and license have not
  been selected.

## Problem

The product aims to interrupt habitual or automatic navigation to selected
websites and applications before the content captures attention. It should add
meaningful friction to an impulsive bypass without becoming a surveillance
tool or requiring constant maintenance.

The problem is not equivalent to device security against an administrator. A
useful product may resist short, automatic behavior while remaining removable
by a deliberate device owner. The desired level of bypass resistance must be
specified rather than described as absolute blocking.

## Intended product principles

`user-confirmed` and `inferred` directions:

- **Fast interruption:** a blocked navigation should fail before useful page
  content appears.
- **Clear state:** active restrictions, expiry, synchronization status, and
  required user actions should be visible and truthful.
- **Low maintenance:** ordinary use should not require repeated setup,
  permission prompts, or manual repair.
- **Cross-device intent:** participating devices should converge on the same
  policy while continuing to enforce valid local schedules and session expiry
  offline.
- **Privacy by omission:** policy and mode-specific security state may
  synchronize, but browsing history, allowed navigations, and usage counters
  are outside the default data model.
- **No product account:** the preferred Apple path uses the system Apple
  Account and the user's private cloud storage rather than a product-operated
  identity or data service.
- **Honest platform differences:** shared policy intent does not imply identical
  enforcement capabilities on every operating system.

## Accepted MVP boundary

`user-confirmed` (2026-08-25): the MVP includes website blocking, application
blocking, bounded manual sessions, Apple synchronization, and first- and
second-installation onboarding on macOS and iOS. Each installation uses one
**Sync with iCloud** action; Blocker performs no application-level pairing.
Domain targets synchronize exactly, and application policies synchronize with
device-local platform selections. A person may intentionally end a session
early.

Schedules, total-key-loss recovery, and stronger UX friction for
early termination are later work. The accepted capability table, platform
support policy, primary flow, measurable outcome, and explicit non-goals are
maintained in the [MVP scope contract](../../product/mvp-scope.md).

## Platform sequence

### Apple workspace

The preferred first workspace covers macOS and iOS devices using the same
Apple Account. CloudKit Private Database is the selected transport,
synchronizable Keychain delivers the workspace key, and Apple Account/iCloud
Keychain trust admits devices. The application performs no provider login and
retains Blocker-level E2EE for synchronized payloads.

### Portable workspace

A later workspace may use one user-selected synchronized folder across Apple,
Android, and Linux devices. Folder access does not grant Blocker membership;
portable mode uses explicit approval, QR exchange, per-device wrapping, signed
membership operations, key epochs, revocation, and optional recovery. A
workspace has one active transport. Moving from an Apple workspace to a new
portable workspace requires explicit export/import and a new key epoch rather
than a live bridge or dual-write.

This later direction has not passed provider-specific or cross-platform
feasibility testing and must not enlarge the Apple-first MVP.

## Accepted non-goals

- monitoring or uploading browsing history;
- a product-operated synchronization relay or identity service;
- claims of protection from the device administrator or unremovable control;
- guaranteed cloud delivery, eventual delivery, delivery latency, or waking
  sleeping devices;
- Android or Linux implementation or immediate four-platform feature parity.

The complete accepted list is maintained in the
[MVP scope contract](../../product/mvp-scope.md).

## Candidate product boundaries

These remain useful candidates rather than accepted Gate 1 decisions:

- no promise to erase historical copies already held by another device;
- treating every browser or network client as supported without an explicit
  compatibility contract.

## Outcome measures

`user-confirmed`: the MVP product-flow measure is a controlled pass/fail test
on one supported physical Mac and iPhone covering **Sync with iCloud** on each
installation without Blocker pairing, delayed-Keychain waiting behavior, shared
domain policy, device-local application mappings, cross-device session
activation, enforcement, normal expiry, and intentional early termination
without manual state repair.

Additional measures remain open:

- maximum acceptable time before a blocked page is interrupted;
- number or duration of deliberate steps required to bypass an active session;
- acceptable conflict behavior with VPNs, proxies, development tools, and
  captive portals;
- privacy-safe evaluation of whether interruptions reduce automatic browsing;
- frequency and severity of user-visible false blocks or failure recovery;
- time and actions required for onboarding a second device.
