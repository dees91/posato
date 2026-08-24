# MVP Open Questions

This page is the decision queue for starting product code. It separates gates
for the first MVP pull request from questions that can remain incremental.

## Current handoff

`user-confirmed` (2026-08-24): the next milestone is the first pull request
containing production application code. Seven gates precede it: MVP scope,
minimum product identity, minimum product and design baseline, architecture
baseline, engineering quality contract, vertical PR decomposition, and manual
Apple resource setup.

The active [preparation plan](../../../tasks/first-mvp-pr-preparation-plan.md)
and [checklist](../../../tasks/first-mvp-pr-preparation-todo.md) are the
execution authority for this milestone. The immediate next work is MVP scope
and product identity. The sections below retain the decisions each gate must
resolve and the questions that may remain incremental.

## Gate 0: product identity and first slice

`open`: accept the product's working identity before registering durable
external identifiers.

- Product name and a short naming fallback.
- One-sentence promise and primary audience.
- Minimum visual direction: wordmark treatment, base colors, typography, and
  application icon concept.
- First MVP slice and explicit non-goals.
- Initial platform order and supported OS versions.

The repository name `blocker-mvp` is temporary and does not settle the product
name.

## Architecture baseline

`user-confirmed`: Kotlin Multiplatform and Compose Multiplatform are the product
direction. Shared product logic and orchestration are Kotlin-first. Apple APIs
sit behind small semantic interfaces. Swift is reserved for integration that
is materially less practical in Kotlin/Native. Apple framework types do not
cross into `commonMain`.

`open`: the first architecture decision must make the following concrete:

- initial modules and source sets;
- dependency direction and composition root per application;
- interface versus `expect`/`actual` criteria;
- macOS application-to-helper process and IPC boundary;
- state, concurrency, error, and lifecycle conventions;
- persistence, serialization, migration, and test strategy;
- dependency and version-selection policy.

This decision belongs in the first small reviewed slices. A separate broad
architecture spike is not required.

## Engineering quality baseline

- Code style and formatter.
- Static analysis and compiler-warning policy.
- Unit, integration, UI, native-boundary, and physical-device test layers.
- Pull-request size, review checklist, and merge requirements.
- Continuous-integration targets and credential boundary.
- Documentation and architecture-decision rules.
- Versioning, changelog, and release-readiness conventions.

## Apple identity and distribution

Decide only after the product name and ownership model are accepted:

- bundle identifier namespace and individual application identifiers;
- development team and signing ownership;
- CloudKit container and environment strategy if synchronization enters scope;
- app-group, Keychain access-group, helper, and extension identifiers;
- entitlement request and fallback plan;
- notarization, App Store, direct distribution, and update strategy;
- privacy disclosures and data-handling declarations.

No PoC development identifier should be reused automatically.

## Enforcement product choices

- Is the first slice domain blocking, application blocking, scheduling, or a
  narrower vertical path?
- Which macOS browsers are supported, and is browser presentation part of the
  promise or an optional enhancement?
- What privilege, persistence, coexistence, recovery, and uninstall behavior is
  acceptable for the macOS helper?
- How should proxy conflicts, VPNs, network-service changes, and captive portals
  be handled?
- Which iOS authorization and entitlement path is viable for public
  distribution?
- Are opaque iOS application selections local-only, and how do they relate to a
  synchronized semantic policy?
- What bypass resistance is promised, and what is explicitly out of scope?

## Synchronization product choices

- Is synchronization in the first MVP slice or a later milestone?
- Which objects synchronize: policy, schedule, selected targets, membership,
  or user preferences?
- Is CloudKit the initial production transport, and what portability contract
  must remain stable?
- What onboarding, recovery, revocation, deletion, export, and migration
  promises are required?
- Which metadata may remain visible to the transport?
- What delivery and offline behavior can the product honestly promise?

## Security, privacy, and public readiness

- Production threat model and attacker assumptions.
- Key ownership, rotation, backup, recovery, and compromise response.
- Local IPC authentication and privileged-helper attack surface.
- Diagnostic data policy, retention, redaction, and opt-in behavior.
- Product privacy policy and user data export or deletion behavior.
- Dependency, source-code, asset, and third-party license audit.
- Repository license, contribution policy, security contact, and public support
  boundary.

## Later platform questions

Android and Linux remain in the accepted portable-folder direction, but they do
not gate the Apple-first MVP. Before those implementations begin, decide:

- supported enforcement mechanisms and privilege models;
- portable synchronization provider and filesystem semantics;
- target-specific secure storage and cryptographic providers;
- background-execution and scheduling constraints;
- how much UI and lifecycle behavior can remain shared.

## Accepted route to the first code PR

The first code PR waits for the seven gates in the
[preparation checklist](../../../tasks/first-mvp-pr-preparation-todo.md),
including the manual Apple Task 0 after the product identity and target graph
are known. Everything not required by PR #1 or its immediate dependants should
be decided as late as the corresponding small vertical slice requires it.

`user-confirmed`: PR #1 creates fresh production modules, uses accepted target
identifiers, runs one minimal shared Compose screen on macOS and iOS, introduces
small semantic platform contracts with fakes, and establishes baseline tests
and CI. It implements neither blocking nor synchronization.
