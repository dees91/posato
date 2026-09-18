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
- [sources/mvp-interaction-prototype.md](sources/mvp-interaction-prototype.md)
  — pinned provenance, observed flows, and evidence limits for the disposable
  interactive MVP UX prototype.
- [../tasks/first-mvp-pr-preparation-plan.md](../tasks/first-mvp-pr-preparation-plan.md)
  — accepted preparation route to the first MVP code pull request.
- [../tasks/first-mvp-pr-preparation-todo.md](../tasks/first-mvp-pr-preparation-todo.md)
  — active state and acceptance criteria for its seven preparation gates.
- [../development/engineering-quality-contract.md](../development/engineering-quality-contract.md)
  — accepted formatting, static-analysis, testing, review, CI, and Definition
  of Done authority.
- [../tasks/README.md](../tasks/README.md) — accepted proportional review,
  just-in-time task brief, concise execution record, wave, and parallel-work
  process.
- [../tasks/mvp-roadmap.md](../tasks/mvp-roadmap.md) — accepted Gate 6
  MVP task-stub, dependency, wave, evidence, and integration-group authority;
  complete and retained as history.
- [../tasks/release-roadmap.md](../tasks/release-roadmap.md) — planned
  releases after 1.0.0 with their rows, waves, backlog, and the idea intake
  rule.
- [../../DESIGN.md](../../DESIGN.md) — accepted, tool-neutral Posato brand and
  product design system for the Apple MVP.
- [Prototype design reference](../../prototypes/mvp-interaction-flow/DESIGN.md)
  — implemented mock tokens, components, native layouts, and interactions;
  evidence for consolidation, not production acceptance.

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
- [ADR 0004: macOS Helper Ownership and Lifecycle](../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  — accepted macOS process, privilege, IPC, authorization, atomic proxy
  ownership, recovery, update, and removal boundaries.
- [ADR 0005: macOS Browser Enforcement and Coexistence](../decisions/0005-macos-browser-enforcement-and-coexistence.md)
  — accepted Safari and Chrome support, exact-domain network denial, fixed
  presentation, proxy coexistence, transient-data, failure, and recovery
  boundaries.
- [ADR 0006: Apple MVP Encrypted Operation and Convergence Contract](../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  — accepted format-1 operation vocabulary, canonical encrypted bundle,
  cryptographic provider, automatic author-registration, validation, and
  deterministic convergence boundaries.
- [ADR 0007: Apple Workspace Bootstrap and Native Sync Boundary](../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  — accepted CloudKit zone, anchor, mailbox, synchronizable workspace-key,
  account-isolation, deterministic bootstrap, and macOS native-sync boundaries.
- [Apple MVP threat model](../security/apple-mvp-threat-model.md) — accepted
  assets and data classification, trust boundaries, threats, required controls,
  downstream owners, and residual risks.
- [Apple MVP diagnostics and support-data policy](../security/diagnostics-and-support-data.md)
  — accepted diagnostic purposes, field and value boundaries, local expiry and
  cleanup, user-controlled export, and no-remote-collection boundary.
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
- [topics/first-release-readiness.md](topics/first-release-readiness.md) —
  accepted release channels, license, reporting routes, privacy policy, the
  blocked readiness verdict, and the roadmap owners of each blocker.

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
  boundary, accepted MVP threat-model synthesis, metadata, diagnostics,
  deletion, and remaining privacy questions.
- [topics/poc-reuse-inventory.md](topics/poc-reuse-inventory.md) — what may be
  reused, adapted, rewritten, referenced, or left with the experiments.
