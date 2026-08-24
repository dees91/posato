# First MVP PR Preparation Checklist

This checklist is the active handoff. Complete the gates in dependency order
and keep their accepted outputs in the durable `docs/` locations named below.

## Gate 1: Define the MVP scope

**Description:** Decide which user-visible behaviors belong to the MVP and
which are deliberately deferred.

**Acceptance criteria:**

- [ ] Website blocking, application blocking, manual sessions, schedules,
  synchronization, onboarding, and recovery are each classified as MVP,
  later, or out of scope.
- [ ] Initial platform order, supported OS baseline, primary user flow, and
  measurable MVP outcome are accepted.
- [ ] Explicit non-goals prevent the first milestone from expanding silently.

**Verification:**

- [ ] The accepted scope and non-goals are recorded under `docs/product/`.
- [ ] `docs/wiki/topics/product-framing.md` links to the accepted scope.

**Dependencies:** None.

## Gate 2: Select the minimum product identity

**Description:** Choose enough durable naming to create repositories, targets,
and platform resources without requiring final branding.

**Acceptance criteria:**

- [ ] Working product name, display name, and one short fallback are accepted.
- [ ] A stable reverse-DNS namespace and naming scheme for applications,
  helpers, and extensions are accepted.
- [ ] Name, repository, and domain collision checks are recorded with their
  date and scope.

**Verification:**

- [ ] The identity contract is recorded under `docs/product/` without account
  credentials or signing data.
- [ ] The contract distinguishes changeable public branding from identifiers
  whose migration would be expensive.

**Dependencies:** None. Start alongside Gate 1.

## Gate 3: Accept the minimum product and design baseline

**Description:** Establish enough product language and visual direction to
design coherent application shells without blocking on final branding.

**Acceptance criteria:**

- [ ] One-sentence positioning, primary audience, product tone, base colors,
  typography, and placeholder icon direction are accepted.
- [ ] Low-fidelity flows cover onboarding, block-list management, manual
  session, active blocking, synchronization, and failure or action-required
  states.
- [ ] Accessibility and platform-convention constraints are stated for the
  first application shell.

**Verification:**

- [ ] The baseline and flow references are recorded under `docs/product/`.
- [ ] PR #1 can implement a minimal screen without inventing product language
  or visual conventions in code review.

**Dependencies:** Gates 1 and 2.

## Gate 4: Accept the MVP architecture baseline

**Description:** Decide only the production boundaries needed to scaffold the
first vertical slices and prevent accidental inheritance from the PoC.

**Acceptance criteria:**

- [ ] KMP modules, target applications, and `commonMain`, `iosMain`, and
  desktop/JVM source-set ownership are defined.
- [ ] The rule for `expect`/`actual` versus injected interfaces, the macOS
  helper and IPC boundary, and required iOS application or extension targets
  are defined at the minimum useful level.
- [ ] Production enforcement and synchronization mechanisms, minimum OS
  versions, and pinned toolchain policy are either selected or explicitly
  deferred to a named vertical pull request.

**Verification:**

- [ ] Accepted architecture decisions are recorded under `docs/decisions/`.
- [ ] The decisions contain enough module and target detail to review PR #1
  without reopening the overall stack direction.

**Dependencies:** Gate 1. Coordinate naming-sensitive targets with Gate 2.

## Gate 5: Accept the engineering quality contract

**Description:** Define the minimum quality bar that every production pull
request must satisfy from the first line of code.

**Acceptance criteria:**

- [ ] Definition of Done, mandatory pre-merge review, pull-request sizing, and
  required test layers are accepted.
- [ ] Formatting, linting, static analysis, compiler-warning policy, and CI for
  JVM, iOS, and macOS are defined for the targets introduced by each PR.
- [ ] Dependency, license, security, privacy, and PoC-reuse review rules are
  accepted.

**Verification:**

- [ ] The quality contract is recorded under `docs/development/`.
- [ ] PR #1 has concrete automated checks and a review checklist rather than a
  promise to add quality controls later.

**Dependencies:** Gate 4 for the initial target and build matrix.

## Gate 6: Decompose the MVP into pull requests

**Description:** Turn the accepted scope into a sequence of small vertical
changes with explicit dependencies and acceptance criteria.

**Acceptance criteria:**

- [ ] Each pull request delivers one coherent, reviewable increment and has
  acceptance and verification criteria.
- [ ] Foundation work is limited to what a named vertical slice needs; there
  is no broad "implement MVP" pull request.
- [ ] PR #1 is fixed to the production skeleton contract in
  `tasks/first-mvp-pr-preparation-plan.md`.

**Verification:**

- [ ] The ordered PR roadmap names dependencies, required manual gates, and
  physical-device checks.
- [ ] The roadmap has an explicit review checkpoint before implementation.

**Dependencies:** Gates 1 through 5.

## Gate 7: Complete Apple Task 0

**Description:** Register the Apple resources implied by the accepted product
identity and target graph. This is the only planned manual account and 2FA
stage before autonomous PR #1 implementation.

**Acceptance criteria:**

- [ ] Bundle identifiers exist for the macOS and iOS applications, native
  helper, and required Screen Time extensions.
- [ ] The App Group, Keychain access group, CloudKit container, entitlements,
  and provisioning profiles required by the accepted first slices exist.
- [ ] Non-secret identifiers and clean-checkout configuration are documented;
  credentials, profiles, signing identities, and account-specific secrets
  remain outside Git.

**Verification:**

- [ ] A read-only preflight confirms every required identifier, entitlement,
  and profile without printing sensitive values into tracked artifacts.
- [ ] A clean checkout can discover the documented local configuration path
  and fail clearly when it is absent.

**Dependencies:** Gates 2, 4, and 6.

## Checkpoint: Ready to open PR #1

- [ ] Gates 1 through 7 are complete.
- [ ] The maintainer explicitly accepts the scope, identity, design,
  architecture, quality contract, and PR roadmap.
- [ ] The exact PR #1 acceptance criteria and verification commands are copied
  into its branch plan or pull-request description.
- [ ] No unresolved entitlement or identifier blocks the application skeleton.
- [ ] Production implementation may begin on a short-lived branch from clean
  `main`.
