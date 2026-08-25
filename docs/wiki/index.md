# Wiki Index

This index routes product and engineering work for Posato. Historical Blocker
references identify the repository's former working name or preserved research
provenance rather than a parallel public brand.

## Start here

- [README.md](README.md) — operating contract, claim labels, source precedence,
  and privacy boundary.
- [log.md](log.md) — append-only maintenance history.
- [sources/feasibility-research-seed.md](sources/feasibility-research-seed.md) —
  provenance and scope of the initial synchronization PoC and enforcement
  spike knowledge seed.
- [sources/apple-design-guidance.md](sources/apple-design-guidance.md) —
  current authoritative Apple guidance used by the accepted Gate 3 brand and
  product design baseline.
- [sources/compose-multiplatform-wizard.md](sources/compose-multiplatform-wizard.md)
  — reviewed generator topology, versions, import risks, and accepted Posato
  configuration for the production skeleton.
- [../../tasks/first-mvp-pr-preparation-plan.md](../../tasks/first-mvp-pr-preparation-plan.md)
  — accepted preparation route to the first MVP code pull request.
- [../../tasks/first-mvp-pr-preparation-todo.md](../../tasks/first-mvp-pr-preparation-todo.md)
  — active state and acceptance criteria for its seven preparation gates.
- [../../DESIGN.md](../../DESIGN.md) — accepted, tool-neutral Posato brand and
  product design system for the Apple MVP.

## Product and architecture

- [../product/mvp-scope.md](../product/mvp-scope.md) — accepted MVP capability
  classification, platform baseline, primary flow, outcome, and non-goals.
- [../product/product-identity.md](../product/product-identity.md) — accepted
  **Posato** public name, controlled `posato.app` canonical domain, stable
  `app.posato` technical namespace, naming pattern, and branding boundary.
- [ADR 0002: Synchronization Trust and Workspace Modes](../decisions/0002-synchronization-trust-and-workspace-modes.md)
  — accepted separation of transport, payload encryption, key delivery, and
  device admission for Apple and portable workspaces.
- [ADR 0003: MVP Application Architecture Baseline](../decisions/0003-mvp-application-architecture-baseline.md)
  — accepted module, source-set, Metro, target, helper, extension, deployment,
  generator-import, and toolchain-selection boundaries.
- [topics/product-framing.md](topics/product-framing.md) — problem, intended
  audience, product principles, platform sequencing, accepted scope, and open
  product measures.
- [../product/design-baseline.md](../product/design-baseline.md) — accepted
  Gate 3 decision record and routing to the canonical design contract.
- [topics/brand-and-design-baseline.md](topics/brand-and-design-baseline.md) —
  accepted Gate 3 synthesis, visual direction, accessibility baseline,
  low-fidelity flows, and proposal provenance.
- [topics/architecture-direction.md](topics/architecture-direction.md) —
  accepted Gate 4 technical synthesis and implementation decisions still
  deferred to named vertical pull requests.
- [topics/kotlin-apple-boundaries.md](topics/kotlin-apple-boundaries.md) —
  Kotlin-first ownership, interfaces, `expect`/`actual`, native leaves, and the
  macOS process boundary.
- [topics/mvp-open-questions.md](topics/mvp-open-questions.md) — prioritized
  decisions, accepted gate order, PR #1 boundary, and later open work.

## Feasibility knowledge

- [topics/feasibility-results-and-limits.md](topics/feasibility-results-and-limits.md)
  — what the synchronization PoC and enforcement spike proved and did not
  prove.
- [topics/cross-device-synchronization.md](topics/cross-device-synchronization.md)
  — local-first model, CloudKit and folder transports, common E2EE, mode-specific
  key delivery and membership, lifecycle, and migration boundary.
- [topics/macos-enforcement.md](topics/macos-enforcement.md) — local proxy,
  browser presentation, application enforcement, recovery, and limitations.
- [topics/ios-enforcement.md](topics/ios-enforcement.md) — Family Controls,
  Managed Settings, opaque selections, cleanup, and entitlement limits.
- [topics/privacy-and-trust-model.md](topics/privacy-and-trust-model.md) — data
  boundary, encryption claims, metadata, diagnostics, deletion, and unresolved
  threat model.
- [topics/poc-reuse-inventory.md](topics/poc-reuse-inventory.md) — what may be
  reused, adapted, rewritten, referenced, or left with the experiments.
