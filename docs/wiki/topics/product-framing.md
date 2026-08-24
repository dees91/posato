# Product Framing

## Current status

- `user-confirmed`: the repository is the clean product home for an MVP written
  from scratch, using PoC knowledge and selectively reviewed code
  ideas rather than treating experimental code as the product foundation.
- `user-confirmed`: the intended project is open source and should be useful to
  people beyond the original maintainer.
- `user-confirmed`: Apple platforms are first. Android, Linux, and a portable
  synchronization path remain later directions and are not MVP parity gates.
- `open`: the final product name, visual identity, MVP feature scope,
  distribution path, and license have not been selected.

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
- **Privacy by omission:** policy and membership state may synchronize, but
  browsing history, allowed navigations, and usage counters are outside the
  default data model.
- **No product account:** the preferred Apple path uses the system Apple
  Account and the user's private cloud storage rather than a product-operated
  identity or data service.
- **Honest platform differences:** shared policy intent does not imply identical
  enforcement capabilities on every operating system.

## Candidate MVP capabilities

These are candidate inputs, not an accepted MVP specification:

- select domains to block on macOS and iOS;
- select applications to block on macOS and iOS;
- create bounded blocking sessions with an explicit end;
- define recurring schedules;
- show a clear blocked-state destination or system shield;
- synchronize policies and session intent between a person's Apple devices;
- continue valid local enforcement while cloud synchronization is unavailable;
- show local-only, pending, syncing, retryable, and action-required states;
- enroll another device through explicit approval;
- offer an optional recovery path with an irreversible-loss warning;
- stop and remove restrictions predictably when the user is authorized to do
  so.

Whether every item belongs in the first MVP slice remains open. A smaller
vertical slice may ship domain blocking before application blocking,
scheduling, enrollment, or recovery.

## Platform sequence

### Apple workspace

The preferred first workspace covers macOS and iOS devices using the same
Apple Account. CloudKit Private Database is the candidate mailbox transport;
synchronizable Keychain is the preferred smooth path for shared key material.
The application performs no provider login.

### Portable workspace

A later workspace may use one user-selected synchronized folder across Apple,
Android, and Linux devices. A workspace has one active transport. Moving from
an Apple workspace to a portable workspace requires an explicit migration to a
new transport epoch rather than a live bridge between transports.

This later direction has not passed provider-specific or cross-platform
feasibility testing and must not enlarge the Apple-first MVP.

## Candidate non-goals

- monitoring or uploading browsing history;
- a product-operated synchronization relay or identity service;
- covert controls or claims of protection from the device administrator;
- remote erasure of historical copies already held by another device;
- guaranteed cloud-delivery latency or waking sleeping devices;
- immediate four-platform feature parity;
- treating every browser or network client as supported without an explicit
  compatibility contract.

## Outcome measures still needed

- maximum acceptable time before a blocked page is interrupted;
- number or duration of deliberate steps required to bypass an active session;
- acceptable conflict behavior with VPNs, proxies, development tools, and
  captive portals;
- privacy-safe evaluation of whether interruptions reduce automatic browsing;
- frequency and severity of user-visible false blocks or failure recovery;
- time and actions required for onboarding a second device.
