# First MVP PR Preparation Checklist

This checklist is the active handoff. Complete the gates in dependency order
and keep their accepted outputs in the durable `docs/` locations named below.

## Gate 1: Define the MVP scope

**Description:** Decide which user-visible behaviors belong to the MVP and
which are deliberately deferred.

**Acceptance criteria:**

- [x] Website blocking, application blocking, manual sessions, schedules,
  synchronization, onboarding, and recovery are each classified as MVP,
  later, or out of scope.
- [x] Initial platform order, supported OS baseline, primary user flow, and
  measurable MVP outcome are accepted.
- [x] Explicit non-goals prevent the first milestone from expanding silently.

**Verification:**

- [x] The accepted scope and non-goals are recorded under `docs/product/`.
- [x] `docs/wiki/topics/product-framing.md` links to the accepted scope.

**Dependencies:** None.

## Gate 2: Select the minimum product identity

**Description:** Choose enough durable naming to create repositories, targets,
and platform resources without requiring final branding.

**Acceptance criteria:**

- [x] Working product name, display name, and one short fallback are accepted.
- [x] Maintainer control of the canonical public domain is confirmed without
  recording registrar account or payment data.
- [x] A stable reverse-DNS namespace and naming scheme for applications,
  helpers, and extensions are accepted.
- [x] Name, repository, and domain collision checks are recorded with their
  date and scope.

**Verification:**

- [x] The identity contract is recorded under `docs/product/` without account
  credentials or signing data.
- [x] The contract distinguishes changeable public branding from identifiers
  whose migration would be expensive.

**Dependencies:** None. Start alongside Gate 1.

## Gate 3: Accept the minimum product and design baseline

**Description:** Establish enough product language and visual direction to
design coherent application shells without blocking on final branding.

**Acceptance criteria:**

- [x] One-sentence positioning, primary audience, product tone, base colors,
  typography, and placeholder icon direction are accepted.
- [x] Low-fidelity flows cover onboarding, block-list management, manual
  session, active blocking, synchronization, and failure or action-required
  states.
- [x] Accessibility and platform-convention constraints are stated for the
  first application shell.

**Verification:**

- [x] The baseline and flow references are recorded under `docs/product/`.
- [x] PR #1 can implement a minimal screen without inventing product language
  or visual conventions in code review.

**Dependencies:** Gates 1 and 2.

## Gate 4: Accept the MVP architecture baseline

**Description:** Decide only the production boundaries needed to scaffold the
first vertical slices and prevent accidental inheritance from the PoC.

**Acceptance criteria:**

- [x] KMP modules, target applications, and `commonMain`, `iosMain`, and
  desktop/JVM source-set ownership are defined.
- [x] The rule for `expect`/`actual` versus injected interfaces, the macOS
  helper and IPC boundary, and required iOS application or extension targets
  are defined at the minimum useful level.
- [x] Production enforcement and synchronization mechanisms, minimum OS
  versions, and pinned toolchain policy are either selected or explicitly
  deferred to a named vertical pull request.

**Verification:**

- [x] Accepted architecture decisions are recorded under `docs/decisions/`.
- [x] The decisions contain enough module and target detail to review PR #1
  without reopening the overall stack direction.

**Dependencies:** Gate 1. Coordinate naming-sensitive targets with Gate 2.

## Gate 5: Accept the engineering quality contract

**Description:** Define the minimum quality bar that every production pull
request must satisfy from the first line of code.

**Acceptance criteria:**

- [x] Definition of Done, mandatory pre-merge review, pull-request sizing, and
  required test layers are accepted.
- [x] Formatting, linting, static analysis, compiler-warning policy, local
  verification, and the deferred CI contract for JVM, iOS, and macOS are
  defined for the targets introduced by each PR.
- [x] Dependency, license, security, privacy, and PoC-reuse review rules are
  accepted.

**Verification:**

- [x] The quality contract is recorded under `docs/development/`.
- [x] The required PR #1 checks, review protocol, evidence rules, and CI
  deadline are concrete while exact commands and jobs remain reviewed
  implementation-plan details.

**Dependencies:** Gate 4 for the initial target and build matrix.

## Gate 6: Decompose the MVP into pull requests

**Description:** Turn the accepted scope into epics, phases, parallel waves,
short outcome stubs, and coherent pull requests. Expand only the work that is
about to start.

**Accepted authority:** [MVP roadmap revision 2](mvp-roadmap.md)
(`user-confirmed`, 2026-08-25).

**Acceptance criteria:**

- [x] Each pull request delivers one coherent, reviewable increment and has
  acceptance and verification criteria.
- [x] Every future task has a short outcome, dependency, wave, and integration
  stub; a concise brief and execution record are created just in time.
- [x] Foundation work is limited to what a named vertical slice needs; there
  is no broad "implement MVP" pull request.
- [x] PR #1 is fixed to the production skeleton contract in
  `docs/tasks/first-mvp-pr-preparation-plan.md`.

**Verification:**

- [x] The roadmap names dependencies, phases, waves, integration groups,
  required manual gates, and physical-device checks without speculative
  reviewer assignments or repeated process text.
- [x] The roadmap follows `docs/tasks/README.md`, including proportional review,
  concise evidence, and the CI deadline.

**Dependencies:** Gates 1 through 5.

## Gate 7: Complete Apple Task 0

**Description:** Register the Apple resources implied by the accepted product
identity and target graph. This is the only planned manual account and 2FA
stage before autonomous PR #1 implementation.

**Acceptance criteria:**

- [x] Bundle identifiers exist for the macOS and iOS applications, native
  helper, and required activity-monitor extension.
- [x] The App Group and CloudKit container exist; required non-Keychain portal
  capabilities are available and assigned to their owning App IDs, and the
  accepted public Keychain access-group suffix is retained for later target
  configuration.
- [x] Non-secret identifiers and manual results are documented;
  credentials, profiles, signing identities, and account-specific secrets
  remain outside Git.

**Verification:**

- [x] Manual Apple Developer inspection records pass or blocked for every
  registered resource and required portal capability association; CloudKit
  Console shows the container.
- [x] Xcode shows the intended team without copying private account values into
  tracked artifacts.

Target entitlement, signing, and development-profile verification starts only
when the owning target and capability exist. It is not a Gate 7 prerequisite
for the credential-free PR #1 skeleton. `SYNC-005` owns iOS Keychain Sharing
target configuration and verification. `SYNC-006` owns the equivalent macOS
external-build entitlement, provisioning, signing, and verification path.

**Dependencies:** Gates 2, 4, and 6.

## Checkpoint: Ready to open PR #1

- [x] Gates 1 through 7 are complete.
- [x] The maintainer explicitly accepts the scope, identity, design,
  architecture, quality contract, and PR roadmap.
- [x] One concise PR #1 brief and execution record state its acceptance
  criteria and applicable verification commands.
- [x] No unresolved entitlement or identifier blocks the application skeleton.
- [x] Production implementation may begin on a short-lived branch from clean
  `main`.
