# Posato Apple MVP Roadmap

## Status and authority

- **Status:** Accepted
- **Revision:** 13
- **Prepared:** 2026-08-25
- **Accepted:** 2026-08-25
- **Last amended:** 2026-09-11
- **Accepted by:** Project maintainer
- **Provenance:** `user-confirmed`
- **Gate 6:** complete

This roadmap retains the MVP outcomes, ordering, direct dependencies, waves,
and integration groups. It does not pre-authorize implementation or require a
full specification for inactive work. Create a concise brief just before a
task or integration group starts, following [the task workflow](README.md).
Revision 4 adds the maintainer-accepted `MACOS-006` development-packaging
correction after a physical TARGETS-003 gate found that the unchanged Gradle
artifact could not launch. It keeps release signing, notarization, and public
distribution in `RELEASE-001`. Revision 5 adds the maintainer-accepted
`SYNC-013` simplification follow-up after hosted review of `SYNC-002` left
dead queries, unused wiring, a test-only digest helper, and duplicated
validation layers in place. Revision 6 adds the maintainer-requested
`QUALITY-002` verification driver so agents can build, drive, inspect, and
reset both applications on the supported Mac, the Simulator, and the
supported iPhone without a maintainer in the loop. Revision 7 adds the
maintainer-accepted `TARGETS-005` hardening follow-up after the independent
review of `TARGETS-004` accepted its blocking findings and deferred four
advisory recoverability and copy items. Revision 8 adds the
maintainer-accepted `APPLE-002` App Store Connect API provisioning task, after
the `SYNC-006` development profile had to be created by hand in the portal,
and the `QUALITY-003` desktop scrolling follow-up for the verification driver,
after `SESSION-001` verification found below-the-fold desktop rows
undrivable. Revision 9 adds the maintainer-requested `QUALITY-004`
unattended-verification task, after a review of the verification driver found
that the two remaining manual steps in the feature map come from the driver
addressing only the application process and from `doctor` reporting no
one-time provisioning state, not from a platform limit. None of these
revisions changes any other task, dependency, wave, or integration group.

Revision 10 adds the maintainer-approved `DESIGN-001` presentation adoption
and includes `QUALITY-003` in the same PR, together with the verification
skill and recipes. It preserves existing ViewModels and native services.

Revision 11 adds the maintainer-accepted `SESSION-003` follow-up after the
`SESSION-002` review recorded that the frozen start set is re-derived from the
live policy once the application is relaunched, so the active summary and its
next-pause copy stop being true for the rest of that session. It changes no
other task, dependency, wave, or integration group.

Revision 12 adds the maintainer-accepted `QUALITY-005` follow-up after
`SESSION-002` verification found the desktop application's accessibility tree
empty while the application rendered, which left a committed unattended
fixture unproven and blocks unattended desktop evidence for later rows. It
changes no other task, dependency, wave, or integration group.

Revision 13 adds the maintainer-accepted `SYNC-014` removal-hardening
follow-up after the `SYNC-011` physical gate observed one re-link, minutes
after **Remove workspace** and a fresh establish, adopt a stale key and report
completed inside a zone still being deleted; it settled only after about
eight minutes. The row precedes `MVP-001` so the acceptance run does not
depend on waiting. It changes no other task, dependency, wave, or integration
group.

The accepted [MVP scope](../product/mvp-scope.md),
[design authority](../../DESIGN.md),
[architecture baseline](../decisions/0003-mvp-application-architecture-baseline.md),
[synchronization trust boundary](../decisions/0002-synchronization-trust-and-workspace-modes.md),
and [quality contract](../development/engineering-quality-contract.md)
remain authoritative for their concerns.

## Planning boundaries

- The roadmap covers one supported arm64 Mac and one supported iPhone, plus a
  separate first-release readiness review.
- PR #1 is the accepted application skeleton. It does not implement blocking,
  synchronization, enrollment, recovery, or a production helper.
- P1 is serialized. P2 starts only after PR #1 has passing local and CI checks,
  one completed-change review, and the readiness checkpoint.
- A wave marks dependency-eligible concurrency, not automatic authorization.
  Exact write surfaces, contract ownership, worktrees, and integration order
  are checked when that wave starts.
- The disposable interaction prototype is bounded UX evidence, not production
  code or a product requirement.
- `DESIGN-001` explicitly adopts its accepted presentation and complete
  reusable component set under `DESIGN.md`, without adopting mock state or
  making the real applications depend on prototype modules.
- `SESSION-001` is the first design-system consolidation checkpoint. It reviews
  repeated production UI patterns against `DESIGN.md` and the prototype while
  keeping implementation inside the session vertical slice; it does not create
  a speculative component-library task.
- Schedules, portable workspaces, Android, Linux, product accounts, analytics,
  stronger early-end friction, and total-key-loss recovery remain outside this
  MVP graph.

## Task stubs, dependencies, waves, and integration groups

The 43 rows below are the complete amended Gate 6 task set. Future rows stay as
stubs.
[`APPLE-001`](specifications/apple-001-register-apple-resources.md) is the
completed Gate 7 brief. The Ready to open PR #1 checkpoint is complete and the
PR #1 implementation cycle is active. Direct dependencies preserve revision 1;
wave barriers add the phase ordering stated above.

| Task | Outcome | Epic | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- |
| `APPLE-001` | Verify the accepted public Apple identifiers and development resources before scaffolding. | Preparation | P1/W1.1 | None | Manual Gate 7 |
| `FOUNDATION-001` | Run a minimal Kotlin-first Compose shell on macOS and build it for the iOS Simulator. | Preparation | P1/W1.2 | `APPLE-001` | PR #1 |
| `QUALITY-001` | Provide one aggregate local gate for formatting, analysis, warnings, tests, and reports. | Preparation | P1/W1.3 | `FOUNDATION-001` | PR #1 |
| `CI-001` | Run the credential-free aggregate quality boundary in CI. | Preparation | P1/W1.4 | `FOUNDATION-001`, `QUALITY-001` | PR #1 |
| `SECURITY-001` | Accept the MVP assets, trust boundaries, threats, mitigations, residual risks, and owners. | Trust and local data | P2/W2.1 | None | PR-SECURITY |
| `DIAGNOSTICS-001` | Accept what MVP diagnostics may record, expose, retain, and export. | Trust and local data | P2/W2.2a | `SECURITY-001` | PR-DIAGNOSTICS |
| `MACOS-001` | Accept the macOS helper ownership, privilege, lifecycle, security, recovery, and removal contract. | Sessions and enforcement | P2/W2.2b | `SECURITY-001` | PR-MAC-HELPER-CONTRACT |
| `MACOS-002` | Accept the macOS browser-support, exact-domain, coexistence, privacy, failure, and recovery contract. | Sessions and enforcement | P2/W2.2c | `SECURITY-001` | PR-MAC-WEB-CONTRACT |
| `MODEL-001` | Persist an atomic local exact-domain policy slice without forbidden platform data. | Trust and local data | P2/W2.3 | `FOUNDATION-001`, `SECURITY-001`, `DIAGNOSTICS-001` | PR-LOCAL-REPLICA |
| `TARGETS-001` | Add, edit, remove, review, validate, and persist exact website domains. | Target management | P2/W2.4 | `MODEL-001` | PR-DOMAINS |
| `SYNC-001` | Accept the bounded signed, encrypted, immutable-operation and convergence contract. | Apple synchronization | P2/W2.4 | `SECURITY-001` | PR-SYNC-FORMAT |
| `MACOS-003` | Send bounded enforcement commands through authenticated, authorized, recoverable helper IPC. | Sessions and enforcement | P2/W2.5 | `MACOS-001`, `FOUNDATION-001` | PR-MAC-HELPER |
| `TARGETS-002` | Manage semantic application policies and truthful local-mapping availability. | Target management | P2/W2.5 | `MODEL-001` | PR-APP-POLICY |
| `SYNC-003` | Accept the one-workspace CloudKit and synchronizable-Keychain bootstrap contract. | Apple synchronization | P2/W2.5 | `SYNC-001`, `APPLE-001` | PR-APPLE-SYNC-CONTRACT |
| `TARGETS-003` | Associate and remove device-local macOS application selections. | Target management | P2/W2.6 | `TARGETS-002` | PR-MAC-MAPPING |
| `MACOS-006` | Produce an unchanged development-signed macOS package whose nested JVM and helper runtime passes strict verification and launches on the supported Mac. | Preparation | P2/W2.6a | `MACOS-003` | PR-MAC-DEV-PACKAGING |
| `TARGETS-004` | Authorize and associate an opaque device-local iOS application selection. | Target management | P2/W2.6 | `TARGETS-002`, `APPLE-001` | PR-IOS-MAPPING |
| `SYNC-002` | Create and process compatible encrypted operations with deterministic rejection and convergence. | Apple synchronization | P2/W2.6 | `SYNC-001` | PR-SYNC-CORE |
| `SYNC-013` | Remove dead sync queries, unused iOS wiring, the test-only digest helper, and duplicated validation layers without changing format, schema, or behavior. | Apple synchronization | P2/W2.6b | `SYNC-002` | PR-SYNC-SIMPLIFY |
| `QUALITY-002` | Build, launch, drive, inspect, screenshot, and reset the macOS and iOS applications on the supported Mac, the Simulator, and the supported iPhone from one agent-facing CLI. | Preparation | P2/W2.6c | `FOUNDATION-001`, `MACOS-006` | PR-VERIFICATION-DRIVER |
| `TARGETS-005` | Recover from a stale authorization request, a refused picker presentation, and a corrupted selection store on iOS, and state unavailability in product terms. | Target management | P2/W2.6d | `TARGETS-004` | PR-IOS-MAPPING-HARDENING |
| `SESSION-001` | Provide shared setup, review, start, early-end, and expiry behavior for one manual session, and consolidate only repeated production UI contracts. | Sessions and enforcement | P2/W2.7 | `TARGETS-001`, `TARGETS-002` | PR-SESSION-CORE |
| `SYNC-004` | Preserve the one-workspace invariant through bootstrap delay, conflict, failure, and restart. | Apple synchronization | P2/W2.8 | `SYNC-003` | PR-BOOTSTRAP-CORE |
| `MACOS-004` | Deny selected exact domains on the accepted macOS browser matrix with safe recovery. | Sessions and enforcement | P3/W3.1 | `MACOS-002`, `MACOS-006`, `SESSION-001`, `TARGETS-001` | PR-MAC-DOMAINS |
| `IOS-001` | Apply and clear only Posato-owned iOS website and application restrictions. | Sessions and enforcement | P3/W3.1 | `SESSION-001`, `TARGETS-004`, `APPLE-001` | PR-IOS-ENFORCEMENT |
| `QUALITY-004` | Address the macOS helper's own window from the verification driver, report every one-time provisioning condition of both targets from `doctor`, and settle whether a captured iOS selection can be restored, so a run on a provisioned machine needs no human step. | Preparation | P3/W3.1a | `QUALITY-002`, `TARGETS-003`, `TARGETS-005` | PR-VERIFICATION-PROVISIONING |
| `QUALITY-005` | Make the desktop accessibility tree readable by the verification driver and prove the unattended session fixture. | Preparation | P3/W3.1b | `QUALITY-004`, `DESIGN-001`, `SESSION-002` | PR-VERIFICATION-ACCESSIBILITY |
| `MACOS-005` | Restrict locally mapped macOS applications without affecting unselected applications. | Sessions and enforcement | P3/W3.2 | `MACOS-006`, `SESSION-001`, `TARGETS-003` | PR-MAC-APPS |
| `IOS-002` | Clear Posato-owned restrictions after normal expiry while the iOS app is suspended. | Sessions and enforcement | P3/W3.2 | `IOS-001` | PR-IOS-EXPIRY |
| `SYNC-005` | Implement the iOS synchronizable-Keychain adapter and truthful service outcomes. | Apple synchronization | P3/W3.3 | `SYNC-003` | PR-IOS-KEYCHAIN |
| `SYNC-006` | Implement the macOS synchronizable-Keychain native boundary and truthful outcomes. | Apple synchronization | P3/W3.3 | `SYNC-003` | PR-MAC-KEYCHAIN |
| `APPLE-002` | Create and renew development profiles and certificates for every Posato App ID through the App Store Connect API with a maintainer-created team key kept outside Git, so no portal step blocks an agent. | Preparation | P3/W3.3a | `APPLE-001`, `SYNC-006` | PR-ASC-PROVISIONING |
| `DESIGN-001` | Adopt the accepted native prototype design and reusable Compose components in the real MVP, preserving existing ViewModels and updating verification tooling. [Brief](specifications/design-001-mvp-design-adoption.md). | Preparation | P3/W3.3b | `SESSION-001`, `TARGETS-001`–`TARGETS-005`, `MACOS-006`, `QUALITY-002` | PR-MVP-DESIGN |
| `QUALITY-003` | Make bounded, scoped `scrollTo` drive below-the-fold lazy rows in both directions; included in the design adoption PR. | Preparation | P3/W3.3b | `QUALITY-002`, `SESSION-001` | PR-MVP-DESIGN |
| `SYNC-007` | Exchange bounded encrypted mailbox bundles through iOS private CloudKit. | Apple synchronization | P3/W3.4 | `SYNC-003` | PR-IOS-CLOUDKIT |
| `SYNC-008` | Exchange bounded encrypted mailbox bundles through the macOS CloudKit native boundary. | Apple synchronization | P3/W3.4 | `SYNC-003` | PR-MAC-CLOUDKIT |
| `SESSION-002` | Integrate safe local start, enforcement, early end, expiry, failure, and recovery. | Sessions and enforcement | P3/W3.5 | `MACOS-004`, `MACOS-005`, `IOS-002`, `SESSION-001` | PR-LOCAL-SESSION |
| `SESSION-003` | Keep the active session's frozen start set truthful across a relaunch. | Sessions and enforcement | P3/W3.5a | `SESSION-002` | PR-SESSION-FROZEN-SET |
| `SYNC-009` | Integrate Keychain and CloudKit bootstrap on both apps without creating a parallel workspace. | Apple synchronization | P3/W3.6 | `SYNC-002`, `SYNC-004`, `SYNC-005`, `SYNC-006`, `SYNC-007`, `SYNC-008` | PR-APPLE-BOOTSTRAP |
| `SYNC-010` | Publish and consume pending encrypted bundles with truthful sync status and retry. | Apple synchronization | P3/W3.7 | `SYNC-009`, `MODEL-001` | PR-CLOUDKIT-SYNC |
| `ONBOARDING-001` | Complete first-install privacy, Apple workspace, authorization, and target setup without a product account. | Apple synchronization | P4/W4.1 | `SYNC-010`, `TARGETS-001`, `TARGETS-003`, `TARGETS-004` | PR-FIRST-INSTALL |
| `ONBOARDING-002` | Join the existing Apple workspace, wait for delayed key delivery, and finish local mappings. | Apple synchronization | P4/W4.2 | `ONBOARDING-001` | PR-SECOND-INSTALL |
| `SYNC-011` | Converge exact domains and semantic policies while opaque selections stay local. | Apple synchronization | P4/W4.3 | `ONBOARDING-002`, `SYNC-010`, `TARGETS-001`, `TARGETS-002` | PR-POLICY-SYNC |
| `SYNC-014` | Refuse to re-adopt a workspace this device removed, so a re-link soon after removal cannot land in a zone under deletion. | Apple synchronization | P4/W4.3b | `SYNC-009`, `SYNC-010`, `SYNC-011` | PR-REMOVAL-HARDENING |
| `SYNC-012` | Converge session start, early termination, and expiry without unsafe delivery promises. | Apple synchronization | P4/W4.4 | `SYNC-011`, `SESSION-002` | PR-SESSION-SYNC |
| `MVP-001` | Pass the accepted MVP flow on one supported Mac and iPhone without manual repair. | Completion | P5/W5.1 | `SYNC-012` | PR-MVP-ACCEPTANCE |
| `RELEASE-001` | Pass or explicitly block every first-release readiness obligation. | Release readiness | Release/R1 | `MVP-001` | PR-RELEASE-READINESS |

## PR #1 shared cycle

`FOUNDATION-001`, `QUALITY-001`, and `CI-001` are milestones within one
coherent pull request. Immediately before PR #1, create one shared brief and
one execution record named `pr-1-production-skeleton.md` covering the shell,
quality gate, and CI outcome. Use one completed-change review after the
integrated increment, not three task cycles plus another holistic review.

## Coverage matrix

| Accepted outcome | Owning tasks | Terminal evidence |
| --- | --- | --- |
| Production shell, target graph, quality gate, and credential-free CI | `APPLE-001`, `FOUNDATION-001`, `QUALITY-001`, `CI-001` | Manual resource results, macOS run, iOS Simulator build, aggregate gate, CI, one review |
| Local domains and semantic application mappings | `MODEL-001`, `TARGETS-001`–`TARGETS-005` | Persistence and contract tests, UI/accessibility checks, physical native selection |
| Bounded local sessions and platform enforcement | `SESSION-001`, `SESSION-002`, `MACOS-001`–`MACOS-006`, `IOS-001`, `IOS-002` | IPC, launchable development packaging, browser/app matrices, physical start/end/expiry/failure cleanup |
| Common encrypted operations and one Apple workspace | `SYNC-001`–`SYNC-010` | Security decisions, vectors, tamper/replay rejection, physical Keychain/CloudKit delay and account isolation |
| First and second installation | `ONBOARDING-001`, `ONBOARDING-002` | Physical flows without a product account or parallel workspace |
| Policy and session convergence | `SYNC-011`, `SYNC-012` | Bidirectional physical convergence, offline/retry, early end, and expiry |
| Removal and re-link hardening | `SYNC-014` | Fake-port resurrection cases and physical removal, quick re-link, and settle evidence |
| Complete measurable MVP outcome | `MVP-001` | One controlled Mac-and-iPhone pass without manual repair |
| Public-release obligations | `RELEASE-001` | Separate pass or blocked readiness verdict |

## Manual and physical gates

| Gate | Owner | Completion rule |
| --- | --- | --- |
| Apple team, four App IDs, App Group, CloudKit container, Keychain suffix, and Family Controls, App Groups, and iCloud/CloudKit portal capabilities | `APPLE-001` | Apple Developer and CloudKit Console resource rows plus Xcode team visibility pass without tracked private values; Keychain target configuration, provisioning authorization, and signed-entitlement verification remain with `SYNC-005` and `SYNC-006`. |
| macOS synchronization-companion App ID and existing CloudKit-container association | `SYNC-003` | `app.posato.macos.sync` exists with iCloud/CloudKit enabled and is associated only with `iCloud.app.posato.sync`; target entitlements, provisioning, signing, and signed-artifact verification remain with `SYNC-006` and `SYNC-008`. |
| Family Controls distribution availability | `TARGETS-004` / `IOS-001` | Later distribution work records pass or a clearing condition. |
| iOS suspended expiry opportunity | `IOS-002` | A physical callback clears owned restrictions without promising exact wake time. |
| macOS helper signing and privilege path | `MACOS-003` | Physical authentication, authorization, failure, and removal/recovery evidence passes. |
| macOS development package launch | `MACOS-006` | The unchanged Gradle-produced application passes strict nested-signature verification and launches on the supported physical Mac without manual re-signing. |
| App Store Connect API team key | `APPLE-002` | The maintainer creates one App Store Connect team key with the Admin role and keeps the `.p8`, key id, and issuer id outside Git; the task records pass or blocked without key values. |
| Browser support and proxy coexistence | `MACOS-002` / `MACOS-004` | Accepted support contract and physical browser matrix pass. |
| CloudKit and Keychain environments | `SYNC-005`–`SYNC-010` | Physical account, delay, restart, error, and cleanup evidence passes. |
| Complete product flow | `MVP-001` | The accepted Mac-and-iPhone matrix passes without manual repair. |
| Public distribution | `RELEASE-001` | Separate readiness verdict; never inferred from MVP behavior. |

No credential, signing identity, provisioning profile, private device ID, raw
capture, opaque application token, real-person domain, or account-specific
value enters tracked evidence.

## Activation rule

Gate 6 acceptance makes these rows planning authority, not implementation
authorization. Complete and explicitly accept the Ready to open PR #1
checkpoint before production scaffolding. For later work, create the brief
only when its dependencies and wave barrier are clear, then apply the
proportional review tier and evidence rules in `docs/tasks/README.md`.
