# Wiki Log

## [2026-08-24] ingest | Seed MVP knowledge from feasibility work

- Created the embedded wiki and mandatory repository routing.
- Synthesized product, architecture, synchronization, enforcement, privacy,
  reuse, and open-decision knowledge from the recorded research revision.
- Kept experiment code, execution artifacts, and machine-local configuration
  with the feasibility work instead of treating them as an MVP baseline.
- Preserved every feasibility result as bounded evidence rather than a
  production or MVP claim.

## [2026-08-24] correction | Focus the repository narrative on product evidence

- Reframed the repository around a fresh MVP implementation informed by the
  completed synchronization PoC and enforcement spike.
- Retained experiment provenance, production limits, and the optional read-only
  feasibility reference without making research history part of the product
  introduction.

## [2026-08-24] decision | Set the route to the first MVP code pull request

- Defined the next milestone as the first pull request containing production
  application code rather than another PoC or architecture spike.
- Accepted seven preceding gates: MVP scope, product identity, product and
  design baseline, architecture baseline, quality contract, vertical PR
  decomposition, and manual Apple resource setup.
- Fixed PR #1 to a fresh KMP application skeleton with accepted identifiers, a
  minimal shared Compose screen on macOS and iOS, semantic platform contracts
  with fakes, baseline tests, and CI.
- Excluded blocking and synchronization implementation from PR #1 and routed
  current work to MVP scope and minimum product identity.

## [2026-08-25] decision | Accept the first MVP scope

- Included website and application blocking, bounded manual sessions, Apple
  synchronization, and first- and second-device onboarding on macOS and iOS.
- Required exact domain synchronization and semantic application-policy
  synchronization with device-local platform selections.
- Allowed intentional early session termination while deferring stronger UX
  friction, schedules, and total-key-loss recovery.
- Accepted a current-plus-previous-major OS support policy and a physical Mac
  and iPhone end-to-end product-flow measure.
- Excluded product accounts and relays, behavioral tracking, Android and Linux
  implementation, cloud-delivery guarantees, and administrator-resistant
  claims.

## [2026-08-25] decision | Separate synchronization trust by workspace mode

- Accepted CloudKit Private Database transport, synchronizable-Keychain
  workspace-key delivery, and Apple Account/iCloud Keychain membership for the
  Apple MVP.
- Replaced Blocker QR and cross-device approval in Apple onboarding with one
  **Sync with iCloud** action per installation and a required missing-key
  waiting state.
- Retained one common application-encrypted, authenticated, and signed
  operation format for CloudKit and portable-folder transports.
- Retained explicit device identity, membership, QR approval, per-device
  wrapping, key epochs, revocation, and optional recovery for later portable
  mode.
- Required a new portable workspace and key epoch for migration, with no live
  bridge, dual-write, or parallel CloudKit-folder authority.
- Recorded the production decision in ADR 0002 while preserving feasibility
  ADR 0001 and PoC results as historical evidence.

## [2026-08-25] research | Screen product identity naming candidates

- Compared five naming directions against the accepted self-directed,
  privacy-preserving friction model and English/Polish usability.
- Performed preliminary Apple storefront, GitHub repository-name, RDAP domain,
  and general-web collision checks without treating them as trademark or
  purchase clearance.
- Recorded **Kind Friction** as the strongest current recommendation and
  **Manual Mode** as the clearest alternative, both still awaiting maintainer
  acceptance.
- Retained **Blocker** only as a historical working name because direct product
  and open-source collisions make it unsuitable as the recommended public
  identity.

## [2026-08-25] correction | Narrow product name finalists

- Recorded the maintainer's shortlist of **Kind Friction** and **Rallent**;
  the other screened names are no longer finalists.
- Compared both names in the same active-session context with equal visual
  weight: warm, human, and values-led for **Kind Friction**; cool, rhythmic,
  and more ownable for **Rallent**.
- Kept the visual concepts as decision aids rather than accepted Gate 3 design
  direction or final logo work.

## [2026-08-25] research | Add pronunciation and domain criteria

- Added Polish pronunciation ease and domain availability as maintainer-set
  criteria for choosing between **Kind Friction** and **Rallent**.
- Rechecked `.com`, `.app`, `.org`, `.dev`, `.io`, and `.pl`: both `.com`
  domains are registered, while the remaining checked names returned no
  registration record at the time of the check.
- Recorded **Rallent** as the current recommendation because its Polish
  pronunciation and spoken spelling are substantially easier; retained the
  final choice as open and the domain result as a point-in-time screen.

## [2026-08-25] research | Explore Rallent-derived names and domain hacks

- Explored **Ralento**, **Rallento**, **Ralent**, **Ralenti**, and **Ralendo**
  for Polish pronunciation, semantic fit, preliminary collisions, and current
  registration state.
- Found no registration object for `ralen.to` or `rallen.to`; recorded
  `ralen.to` as the strongest coherent hack, but only as a possible redirect
  rather than a canonical-domain recommendation.
- Identified **Ralento** as the strongest phonetic refinement but recorded its
  active safe-driving `.com` collision and nonstandard Italian spelling.
- Kept **Kind Friction** and **Rallent** as the only maintainer-confirmed
  finalists; no derivative was promoted by the research alone.

## [2026-08-25] research | Explore names adjacent to the Rallent idea

- Expanded the search from the **Rallent** spelling family to measured pace,
  calm reflection, voluntary resistance, and gradual slowing.
- Screened direct musical terms and rejected names with active adjacent
  products or crowded namespaces, including **Cadento**, **Retempo**, and
  **Calando**.
- Identified **Posato** as the strongest new spoken and emotional candidate and
  **Misurato** as the strongest measured-limits concept; found no registration
  object for their `.app` domains or for `misura.to` at the time of checking.
- Preserved the existing maintainer-confirmed shortlist and recorded both new
  names as advisor recommendations awaiting selection, not accepted identity.

## [2026-08-25] correction | Retain Posato from the adjacent-name pass

- Retained **Posato** as the only name from the conceptually adjacent
  exploration for continued comparison.
- Left **Misurato**, **Pacato**, **Volento**, and **Calando** as research
  history rather than live candidates.
- Recommended a three-direction comparison of **Kind Friction**, **Rallent**,
  and **Posato**, with no fourth name added merely for symmetry.
- Clarified that `ralen.to` spells **Ralento** and therefore belongs to a
  nested variant experiment rather than serving as a matching **Rallent**
  domain.

## [2026-08-25] correction | Set the four-name final comparison

- Confirmed **Kind Friction**, **Rallent**, **Ralento**, and **Posato** as four
  separate proposals for the final comparison.
- Reclassified **Ralento** from a nested spelling experiment to an independent
  proposal and associated `ralen.to` with that proposal.
- Rechecked a common domain set and recorded the active `ralento.com` consumer
  collision, the `posato.io` registration, and the no-object result for
  `ralen.to`.
- Recorded the weighted advisor assessment and recommendation allocation:
  **Posato** 38%, **Rallent** 30%, **Kind Friction** 22%, and **Ralento** 10%,
  with the final product-name decision still open.

## [2026-08-25] research | Recommend canonical domains for the finalists

- Rechecked `kindfriction.app`, `rallent.app`, `ralento.app`, and `posato.app`;
  registry RDAP returned no registration object for all four at the time of
  checking.
- Recommended the matching `.app` domain as the canonical address for each
  proposal to preserve exact public spelling and one consistent product-domain
  pattern.
- Retained `ralen.to` only as a possible **Ralento** redirect and kept registrar
  purchase, pricing, premium-name, reservation, and legal checks open.

## [2026-08-25] decision | Select Posato product identity

- Accepted **Posato** as the product name, display name, and unchanged short
  fallback; retired **Blocker** to historical working-name provenance.
- Selected `posato.app` as the canonical public domain without claiming that
  registrar purchase or maintainer control has completed.
- Promoted the accepted public identity, dated collision evidence, and the
  branding-versus-durable-identifier boundary to
  `docs/product/product-identity.md`.
- Updated current repository and wiki handoffs to **Posato** while preserving
  **Blocker** where it identifies the repository path, research history, or
  dated decision provenance.
- Left Gate 2 open for domain-control confirmation and acceptance of the stable
  reverse-DNS namespace and component naming pattern.

## [2026-08-25] correction | Align repository path references with Posato

- Removed stale statements that the repository still used the historical
  `blocker-mvp` directory name.
- Retained **Blocker** only where it identifies research history or dated
  decision provenance.

## [2026-08-25] decision | Complete the minimum product identity

- Recorded the maintainer's confirmation that `posato.app` was purchased and
  is under maintainer control without storing registrar, account, payment, or
  renewal details.
- Accepted `app.posato` as the stable reverse-DNS root, with
  `app.posato.<platform>` for applications and
  `app.posato.<platform>.<role>` for helpers and extensions.
- Kept exact target roles in Gate 4 and exact Apple resource registration in
  Gate 7 rather than inferring either from the naming decision.
- Marked Gate 2 complete and advanced the active handoff to the minimum product
  and design baseline.

## [2026-08-25] proposal | Define the Gate 3 brand and design candidate

- Proposed a brand foundation centered on calm self-directed agency, truthful
  state, privacy by omission, and a quiet interval between impulse and action.
- Proposed a restrained warm-neutral, Moss, and Clay palette; system
  typography; and an open-interval placeholder icon direction without
  promoting any of them into an accepted product contract.
- Added platform-fit and accessibility constraints derived from current Apple
  Human Interface Guidelines and recorded the reviewed primary sources.
- Added low-fidelity flows for onboarding, paused-item management, manual
  sessions, active blocking, synchronization, and action-required recovery.
- Kept Gate 3 open pending explicit maintainer acceptance or correction.

## [2026-08-25] decision | Accept the minimum brand and product design

- Recorded the maintainer's acceptance of the rendered Gate 3 direction and
  promoted it into the tool-neutral root `DESIGN.md` contract.
- Accepted the positioning, audience, promise, language, palette, system
  typography, open-interval placeholder mark, interface hierarchy, platform
  constraints, accessibility baseline, six low-fidelity flows, and exact PR #1
  shell content.
- Preserved unknown spacing, radius, shadow, navigation, responsive, final-logo,
  and implemented-accessibility details instead of deriving false precision
  from one brand-board rendering.
- Added product and wiki routing, marked Gate 3 complete, and advanced the
  active handoff to the MVP architecture baseline.

## [2026-08-25] correction | Make design authority routing explicit

- Added a direct `AGENTS.md` requirement to read the root `DESIGN.md` before
  brand, product-design, UI, or application-shell work.
- Clarified that the related wiki topic retains synthesis and provenance rather
  than competing with the accepted design contract.
- Replaced the stale Gate 3 `candidate` label in the wiki index with the
  accepted-baseline status.

## [2026-08-25] proposal | Add Gate 4 scaffolding and Metro inputs

- Recorded the maintainer's intent to bootstrap the production skeleton with
  Compose Multiplatform Wizard and use the `android-compose-engineering` skill
  for agent-authored Compose Multiplatform code when available.
- Recorded the maintainer's Metro selection as the only dependency-injection
  framework for the greenfield graph.
- Audited wizard revision `e639d668a45f04c2ec11b382c1ccb745b185271f`,
  including its module topology, displayed versions, generated identifiers,
  sample content, and safe-import boundary.
- Added a proposed minimal module, source-set, Metro, Apple target, deployment,
  dependency, and toolchain baseline without marking Gate 4 complete.

## [2026-08-25] decision | Accept the MVP architecture baseline

- Accepted the initial `:shared`, `:desktopApp`, and `iosApp` graph with one
  common Metro contract and compile-time platform graphs in `iosMain` and
  `jvmMain`.
- Accepted `app.posato.ios`, `app.posato.macos`, the deferred
  `app.posato.ios.activitymonitor` extension, and the deferred
  `app.posato.macos.helper` native process over authenticated, versioned IPC.
- Accepted iOS 18.0 and arm64-only macOS 15.0 deployment baselines, with a
  release-time support recheck and PR #1 responsibility for compatible stable
  toolchain pins.
- Accepted the isolated Compose Multiplatform Wizard import and sanitation
  boundary while keeping the generator and local coding skill subordinate to
  repository authority.
- Promoted the decision to ADR 0003, completed Gate 4, and advanced the active
  preparation handoff to the Gate 5 engineering quality contract.

## [2026-08-25] decision | Accept the engineering quality and task workflow

- Accepted ktlint for formatting, Detekt for static analysis, Compose Rules
  through Detekt, warning-free owned source, and a reproducible aggregate local
  quality gate whose exact implementation is selected in PR #1 task planning.
- Accepted applicable test layers, independent plan and implementation review,
  bounded correction loops, task-local acceptance and Definition of Done
  evidence, and proportional dependency, security, privacy, and PoC-reuse
  review.
- Made repository Markdown the durable task source of truth, with outcome-only
  task specifications separated from technical plans and execution records.
- Added reusable specification and execution templates plus the accepted
  bootstrap governance task and its actual review record.
- Recorded the maintainer-accepted one-time GOVERNANCE-001 transition
  exception: its
  initial documentation drafts preceded corrected-plan approval, the chronology
  remains explicit, and no later task inherits the exception.
- Accepted epics, phases, dependency waves, worktree-isolated parallel work,
  and an initial limit of three concurrent implementation tasks.
- Deferred CI configuration from Gate 5 into a separate Gate 6 task while
  retaining it as a PR #1 outcome due before PR #1 merges or the first parallel
  implementation wave begins, whichever happens first.
- Completed Gate 5 and advanced the active preparation handoff to Gate 6 MVP
  decomposition.

## [2026-08-25] experiment | Group macOS prototype tasks in large windows

- Recorded fullscreen prototype evidence that a flexible mobile-style spacer
  detached sparse macOS task content from its primary action and allowed
  supporting elements to stretch across the window.
- Added a macOS-only layout hypothesis that groups task content and actions,
  constrains notices and lists, and preserves the iPhone action placement.
- Kept exact geometry open pending maintainer review and did not promote the
  prototype values into the accepted `DESIGN.md` contract.

## [2026-08-25] experiment | Separate Free play from strict product flows

- Recorded the maintainer's confirmation that Free play should allow every
  action in any order and return attention to the Current product surface.
- Added deterministic prerequisite preparation only to Free play while keeping
  Product surface actions and Guided walkthroughs on the strict reducer.
- Verified all 30 Free play actions individually from reset and retained the
  intentional blocked start attempt in the Action required walkthrough.
- Kept the workbench convenience outside the accepted product interaction
  contract and left `DESIGN.md` unchanged.

## [2026-08-25] experiment | Exercise configurable paused items

- Recorded the maintainer's correction that the prototype must not imply a
  hardcoded website-and-application limit.
- Added custom website entry, local validation and normalization, editing,
  removal, and a synthetic multi-application picker.
- Exposed independent Mac and iPhone application mappings while keeping exact
  website domains shared across devices.
- Verified invalid and duplicate input, previous-value preservation, keyboard
  submission, responsive overflow, all 33 Free play actions, and automated
  browser accessibility checks.
- Kept synthetic application names, exact domain rules, and native picker
  behavior outside the accepted production contract; `DESIGN.md` is unchanged.

## [2026-08-25] proposal | Prepare the Gate 6 MVP roadmap

- Recorded the disposable interaction prototype as pinned UX evidence without
  promoting its workbench, fixtures, geometry, or web implementation into the
  accepted product or design contract.
- Prepared a draft 36-item Apple MVP roadmap with explicit epics, dependencies,
  serialized decision gates, concurrency-candidate waves, conflict classes,
  PR groups, physical evidence, and a separate release-readiness track.
- Added one outcome-only draft specification per work item with acceptance
  criteria, evidence categories, review applicability, and decision gates.
- Kept Gate 6 incomplete and production implementation blocked pending
  maintainer acceptance of the roadmap and linked specifications.

## [2026-08-25] decision | Accept the Gate 6 MVP roadmap

- Recorded the maintainer's acceptance of roadmap revision 1 and all 36 linked
  outcome-only task specifications.
- Completed Gate 6 and made `APPLE-001` the first incomplete Gate 7 task.
- Preserved task-local plan, independent review, dependency, evidence, and
  readiness-checkpoint requirements; acceptance does not authorize production
  scaffolding.

## [2026-08-25] correction | Streamline the engineering workflow

- Replaced universal plan review and exhaustive evidence matrices with
  Trivial, Standard, and High-risk review tiers and five Definition of Done
  principles.
- Reduced inactive Gate 6 work to 36 roadmap stubs and made task briefs
  just-in-time; the three PR #1 milestones now share one execution and review
  cycle.
- Reframed APPLE-001 as a manual Apple Developer and Xcode checklist without
  scripts, parsers, wizards, or local configuration layers.

## [2026-08-26] correction | Correct the APPLE-001 pre-scaffold boundary

- Recorded the maintainer's acceptance that Gate 7 registers four explicit App
  IDs, one App Group, and one CloudKit container while retaining
  `app.posato.sync` as a public entitlement suffix rather than a portal
  resource.
- Limited pre-scaffold Xcode verification to intended-team visibility and
  routed resource, capability, and container checks to Apple Developer and
  CloudKit Console.
- Moved target entitlement, signing, and development-profile verification to
  the tasks that configure the owning targets and capabilities, preserving a
  credential-free PR #1 skeleton.

## [2026-08-26] correction | Route Keychain Sharing to application packaging

- Recorded the maintainer's current portal observation and acceptance that
  APPLE-001 requires no Keychain action in Certificates, Identifiers &
  Profiles.
- Retained `app.posato.sync` as the public suffix and assigned iOS target
  configuration to `SYNC-005` and the macOS external-build entitlement,
  provisioning, signing, and verification path to `SYNC-006`.
- Completed the Gate 7 resource and non-Keychain portal-capability evidence
  while preserving the separate Ready to open PR #1 checkpoint.

## [2026-08-26] event | Import the Foundation application skeleton

- Recorded the supplied Wizard archive digest and selectively retained the
  Apple host and wrapper structure while excluding Android, Web, sample,
  optional dependency, icon, and local configuration surfaces.
- Added the fresh accepted `:shared`, `:desktopApp`, and `iosApp` topology with
  Metro platform graphs and the exact four-line shared shell.
- Selected Compose Multiplatform 1.10.3 after controlled inspection found that
  the 1.11.1 and 1.12.0 Skiko ICU objects exceeded the accepted iOS 18.0
  deployment target.
- Passed shared tests, platform graph compilation, credential-free Xcode 26.6
  Simulator build and launch, and macOS application-image build and launch.

## [2026-08-26] correction | Pin the Gradle daemon to Temurin 21

- Supplemented the runtime JDK guard with a checked-in daemon JVM criterion
  that takes precedence over Android Studio's JBR 25 launcher.
- Added the Gradle-owned Foojay resolver convention plugin 1.0.0 to generate
  portable Eclipse Temurin 21 provisioning URLs.
- Retained Compose Desktop's JDK vendor safeguard after it rejected Homebrew's
  JDK distribution, then passed the Foundation build from a JBR 25 launcher
  with a Temurin 21 daemon.

## [2026-08-26] decision | Accept the temporary Detekt prerelease exception

- Added `./gradlew quality` as the aggregate local formatting, analysis,
  warning, test, build, packaging, and report boundary for PR #1.
- Pinned ktlint Gradle plugin 14.2.0, ktlint 1.8.0, Detekt 2.0.0-alpha.6, and
  Compose Rules 0.6.4 after compatibility and license review.
- Accepted Detekt's prerelease only as build-time quality tooling because no
  stable Detekt release supports Kotlin 2.4.10 metadata; the first compatible
  stable Detekt 2 release is the removal trigger.
- Confirmed Compose Rules enforcement with a controlled `ModifierMissing`
  failure and retained no lint baseline or failure-tolerant mode.

## [2026-08-26] event | Configure credential-free CI

- Added one GitHub Actions job that runs `./gradlew quality` and a separate
  signing-disabled iOS Simulator host build on the arm64 `macos-15` runner.
- Selected Temurin 21 and Xcode 26.3 explicitly, pinned GitHub-maintained
  actions by immutable release commit, disabled checkout credential
  persistence, and limited repository permissions to read access.
- Recorded that no PoC workflow existed to reuse and kept `CI-001` open until
  the first hosted workflow run can be observed from a connected GitHub
  repository.

## [2026-08-26] decision | Limit automated tests to important logic

- Removed static application-shell and platform-theme UI tests that protected
  framework wiring and fixed copy rather than important product behavior.
- Limited pre-stabilization automated coverage to business, state, policy,
  validation, parsing, and boundary logic with regression value.
- Deferred golden UI testing, with Paparazzi named as a possible approach, to a
  separate post-MVP decision; platform builds and proportionate manual
  inspection remain the current UI verification boundary.

## [2026-08-26] correction | Align the shell with platform appearance

- Replaced fixed Material typography and black, white, and opacity styling
  with a narrow platform theme boundary using semantic system backgrounds,
  labels, and text sizes.
- Corrected UIKit dynamic-color conversion after manual Simulator inspection
  exposed a launch crash for an extended monochrome color space.
- Confirmed the corrected iOS launch and both desktop appearance branches; one
  opaque label color now preserves contrast while type and spacing carry the
  four-line hierarchy.

## [2026-08-26] decision | Adopt a human-owned pull request review loop

- Recorded the first successful hosted PR #1 quality run and closed `CI-001`
  without adding signing material or application credentials.
- Kept human inline comments as the primary feedback channel and added a
  manually requested `@codex review` as an independent, read-only pass.
- Assigned corrections and verification to the local implementation agent,
  thread resolution to the reviewer, and the final merge decision to the
  maintainer; automatic AI reviews remain disabled pending useful evidence.
- Observed the first manual hosted Codex review complete without a major issue
  or inline finding while the reviewed commit's hosted `Quality` job passed.

## [2026-08-26] correction | Stop hosted review loops for documentation

- Reserved hosted `@codex review` for the latest substantive code or
  configuration change.
- Excluded documentation-only status, evidence, wiki-log, and review
  bookkeeping from hosted review without weakening proportional local or human
  review for meaningful documentation.
- Made documentation-only closeout non-invalidating so recording a review
  result cannot recursively trigger another review request.

## [2026-08-26] decision | Accept the Apple MVP threat model

- Accepted ten asset and data classes, eight trust boundaries, and fifteen
  threat-to-control mappings with existing roadmap owners.
- Accepted the Apple membership, administrator and equivalent-process,
  metadata and availability, remote-wipe and total-key-loss, and memory-erasure
  residual boundaries for the MVP.
- Kept cryptographic design, diagnostics, retention and deletion, helper and
  browser contracts, iOS distribution details, and release readiness with
  their named downstream tasks.
- Retained the synchronization PoC and enforcement spike as bounded abuse-case
  and verification evidence without importing their implementation or
  production status.

## [2026-08-26] decision | Accept MVP diagnostics and support-data policy

- Accepted four bounded support questions, a closed field allowlist, explicit
  prohibited data, and allowlist-first redaction for every future producer.
- Accepted local capture as off by default, unavailable for diagnostic use
  after at most 24 hours, and bounded to 500 records and 512 KiB in protected
  no-backup storage with lifecycle-truthful physical cleanup.
- Required previewed user-controlled export and retained no automatic
  telemetry, analytics, crash upload, support store, processor, or system-
  console diagnostic surface.
- Used final PoC and enforcement-spike privacy evidence only for synthetic
  canary, categorical result, redaction, and cleanup test ideas without
  importing implementation or production status; no diagnostic producer was
  implemented by this decision.

## [2026-08-26] decision | Accept macOS helper ownership and lifecycle

- Selected a short-lived normal-user Swift session helper and a minimal Swift
  root launch daemon while retaining Kotlin ownership of product policy and
  session meaning.
- Accepted Service Management lifecycle, fixed signed IPC peers, the one-use
  `app.posato.macos.proxy.apply` right, atomic HTTP and HTTPS proxy ownership,
  and durable repeatable recovery without a custom watchdog or shell surface.
- Limited automatic restoration claims to paths where the exact daemon can run
  and routed browser and proxy coexistence, concrete IPC and packaging,
  application identity, and distribution to their existing downstream tasks.

## [2026-08-26] decision | Accept macOS browser enforcement and coexistence

- Accepted Safari and Chrome Stable as the only positive macOS browser claims,
  with exact release-version evidence required for network denial and fixed
  same-tab presentation in regular and private contexts.
- Bounded exact-domain enforcement to HTTP port 80 and HTTPS port 443, required
  a complete no-fallback proxy chain, and rejected conflicting proxy automation
  or detected routing overlays without modifying them.
- Accepted the two necessary volatile data boundaries, the Apple Events
  presentation race, content-erasure limit, restoration on network transitions,
  and downstream MACOS-004 physical and privacy-canary evidence gate.

## [2026-08-27] implementation | Persist policy with standard SQLDelight lifecycle

- Classified exact-domain rows as active app-private configuration with no TTL;
  an empty replacement removes every domain row atomically.
- Selected SQLDelight's standard schema initialization, versioning, migrations,
  and platform drivers without a custom database-management layer.
- Bound the standard platform driver, generated database, and ready store
  directly in each app-scoped Metro graph, with suspending store work on the
  platform database dispatcher and replacement in one SQLDelight transaction.
- Reduced v1 to revision metadata and canonical domains, committed the generated
  schema baseline, linked system SQLite in the iOS host, and retained focused
  cross-runtime policy, rollback, restart, invalid-file, redaction, bounds,
  malformed A-label, and dispatcher coverage.

## [2026-08-27] correction | Align Kotlin formatting across IDE and quality gates

- Set one 150-character Kotlin limit in ktlint, Detekt, and Android Studio.
- Kept expression bodies, call assignments, and `when` expressions on their
  declaration or assignment line when they fit the limit.
- Retained blank lines before terminal returns and aligned multiline chains as
  authored style without adding a custom lint plugin solely for those choices.

## [2026-08-27] correction | Require integer revision storage

- Required the v1 policy revision to use SQLite's physical integer storage
  class instead of relying on column affinity and numeric comparison alone.
- Added one real-SQLite cross-runtime contract proving that a non-integer write
  is rejected and leaves the committed policy unchanged.

## [2026-08-27] correction | Preserve policy storage types and read snapshots

- Required canonical domains to use SQLite's physical text storage class so a
  BLOB cannot be coerced through the generated String accessor.
- Read revision metadata and canonical domains in one standard SQLDelight
  transaction so callers receive one committed logical state.

## [2026-08-27] correction | Bound canonical-domain storage bytes

- Applied the existing 253-character ASCII domain budget to the complete stored
  byte sequence so an embedded NUL cannot hide an oversized suffix.
- Added one real-SQLite cross-runtime contract for rejection and preservation of
  the previously committed policy without runtime or file-level validation.

## [2026-08-27] correction | Preserve corruption during policy replacement

- Validated the previous logical state inside the replacement transaction after
  the revision compare-and-set and before deleting domain rows.
- Kept a corrupt replica intact and returned typed corruption instead of
  silently replacing it, without adding schema attestation or recovery logic.

## [2026-08-27] correction | Bound hosted review and macOS CI repetition

- Limited hosted `@codex review` to one final manual pass per pull request by
  default; another pass now requires an explicit maintainer request.
- Kept hosted P2 and lower findings advisory and moved routine post-review
  bookkeeping to the pull-request conversation instead of another commit.
- Made draft pull requests allocate no runner and added whole-pull-request
  classification so review-ready Markdown-only changes skip macOS while
  substantive changes, `main` pushes, and classification failures retain the
  full quality gate.

## [2026-08-27] implementation | Add exact-domain management

- Added bounded Unicode-to-ASCII exact-domain canonicalization with strict DNS
  and A-label restoration checks and redacted domain value rendering.
- Added one shared screen whose aggregate immutable state combines private
  flows and starts policy reads only while the UI state is consumed.
- Kept persistence directly on the existing atomic policy store without a new
  use case, navigation layer, custom scope, or availability check.

## [2026-08-27] correction | Establish the Material 3 UI foundation

- Kept mutable text input in one composable-owned `rememberTextFieldState`
  instead of duplicating it in aggregate immutable UI state or a ViewModel
  flow.
- Assigned the first reviewed screen to establish one root `PosatoTheme` with
  platform semantic adaptation and Material 3 components only.
- Required Compose Rules through Detekt to reject Material 2 source use while
  leaving shared components and geometry tokens evidence-driven.
- Narrowed the redacted-default-string rule to repository-owned carriers and
  diagnostics because final framework `TextFieldState` exposes live text;
  prohibited logging, diagnosing, persisting, or forwarding that state to the
  ViewModel.

## [2026-08-27] correction | Include SQL in the desktop runtime image

- Reproduced the packaged macOS launch failure at `java/sql/DriverManager`
  despite passing JVM and distribution-build checks.
- Added only the JDK `java.sql` module required by SQLDelight's desktop JDBC
  driver, then verified the corrected runtime image, packaged launch, and
  maintainer interaction flow.
- Confirmed that the preserved PoC carried the same package requirement without
  importing its broader signing or synchronization machinery.

## [2026-08-27] correction | Enforce assignment-line formatting

- Added one repository-owned ktlint rule after repeated authored drift showed a
  real consumer for the accepted right-hand-side layout.
- Required declarations, assignments, named arguments, default values, and
  expression bodies to start their value after `=` when the first line fits.
- Kept comments, over-limit values, and standard-formatted multiline raw
  strings valid and omitted autocorrect or a general formatting framework.

## [2026-08-27] decision | Add tooling-only shared Compose previews

- `user-confirmed`: common Compose previews use a narrow Android KMP library
  target because the Compose preview tooling requires one.
- The target has no Android application, host, identifier, distribution
  artifact, or MVP platform claim; Android product work remains deferred.
- Product screens use deterministic named `PreviewParameterProvider` cases via
  their state-and-callback render surface, with review rather than a brittle
  naming-based static rule enforcing the convention.

## [2026-08-27] correction | Align screen preview ownership

- Kept exactly two preview functions in the product screen file and moved the
  deterministic synthetic state provider into a separate adjacent file.
- Required phone and desktop previews to consume the same complete provider
  sequence rather than maintaining a reduced desktop-only case.

## [2026-08-28] correction | Enforce when-entry arrow placement

- Added a repository-owned ktlint rule for the maintainer-confirmed layout that
  keeps a fitting `when` entry arrow on its final condition line.
- Kept comments and over-limit condition lines valid, omitted autocorrect, and
  disabled the conflicting standard declaration-site trailing-comma rule.

## [2026-08-28] decision | Adopt feature-first shared packages

- `user-confirmed`: organize product code under
  `app.posato.feature.<feature>` and add layers only inside a feature when the
  responsibilities exist.
- Assigned TARGETS domain, data, and UI to `app.posato.feature.targets` and
  assigned shared database and design-system ownership to named
  `app.posato.core` capabilities.
- Retained one `:shared` module and direct ViewModel-to-store injection; package
  organization does not create pass-through use cases or speculative modules.

## [2026-08-28] correction | Reject ambiguous numeric hosts

- Applied WHATWG's number-ending rule to the final canonical domain label so
  alternate IPv4 spellings cannot enter exact-domain policy.
- Reconciled conflict reloads with active edits, clearing an editor whose target
  disappeared while preserving one whose target remains in persisted policy.

## [2026-08-28] decision | Pause automatic hosted CI

- `user-confirmed`: pause automatic GitHub Actions triggers through 2026-09-05
  after the account exhausted its included runner minutes.
- Retained manual workflow dispatch and made a fresh local aggregate quality
  pass the temporary merge gate without weakening review requirements.

## [2026-08-28] decision | Accept Apple MVP synchronization contract

- `user-confirmed`: accepted ADR 0006's bounded format-1 encrypted operation,
  automatic Apple author-registration, validation, and deterministic
  convergence contract.
- Selected platform system providers without importing the PoC format,
  experimental cryptography dependency, portable membership, schedules, or
  batching into the Apple MVP.
- Kept production implementation and cross-target vectors with `SYNC-002` and
  deterministic CloudKit and Keychain bootstrap with `SYNC-003`.

## [2026-08-28] correction | Harden encrypted operation contract

- Encrypted author identity, public key, and sequence; replaced author/sequence
  nonce derivation with a context-bound single-use bundle key; defined
  fail-closed author lifecycle recovery; and capped the format-1 synchronized
  domain projection at 2,048 with deterministic rejection.

## [2026-08-28] correction | Make capacity and expiry order-safe

- Made capacity outcomes recomputable from the complete applicable operation
  set, quarantined conflicting starts deterministically, and made observed
  expiry a terminal local fact bound to the encrypted session identifier,
  preventing delivery order or clock rollback from reviving invalid state.

## [2026-08-28] correction | Make local authoring rollback-safe

- Replaced the persistent Apple signing identity and secure sequence high-water
  with a process-memory authoring incarnation scoped to one local writer open.
- Required exact reconciliation of ambiguous local commits, serialized
  local/remote committed-footprint checks, and atomic HLC exhaustion without
  rejecting later valid remote input.
- Kept invalid wall-clock recovery distinct from terminal format-1 exhaustion
  and added no hardware anchor, online preflight, wire operation, or dependency.

## [2026-08-28] correction | Define local HLC allocation

- Defined one bounded HLC successor, one wall-clock sample per local batch, and
  the exact wall-ahead reset and equal-or-regressed advancement rules.
- Kept remote advancement wall-independent and retained the existing atomic
  batch reservation, terminal exhaustion, wire format, and operation model.

## [2026-08-28] decision | Separate work records from review depth

- `user-confirmed`: choose the lightweight or recorded-task path separately
  from Trivial, Standard, or High-risk review.
- Let changed sources, focused checks, affected authorities, and one concise
  wiki-log entry close lightweight work without a brief or execution record.
- Retained PREVIEW-001 and CI-002 records as history; equivalent future work
  uses the lightweight path while dependency, build-target, and CI risk may
  still require independent completed-change review.

## [2026-08-28] tooling | Add Android Studio Apple run configurations

- Added shared Android Studio configurations for the existing `iosApp` Xcode
  application and `:desktopApp:run` macOS application task. The iOS entry uses
  Android Studio's bundled Shell Script runner and needs no KMP IDE plugin.
- Kept simulator and device selection machine-local and recorded no signing,
  account, device, or personal-path value.

## [2026-08-28] decision | Define one-workspace Apple bootstrap

- `user-confirmed`: accepted one create-only CloudKit anchor, one exact
  synchronizable-Keychain item contract, and automatic convergence for
  concurrent first runs without replacement keys or parallel workspaces.
- Added the dedicated `app.posato.macos.sync` companion so CloudKit, Keychain,
  and the workspace key stay outside the macOS enforcement helper.
- `user-confirmed`: the new App ID, iCloud/CloudKit capability, and association
  with the existing `iCloud.app.posato.sync` container passed manual inspection.
- Kept implementation, entitlements, schema deployment, retry policy, and
  physical Mac-and-iPhone evidence with `SYNC-004` through `SYNC-009`.

## [2026-08-28] correction | Bind Apple bootstrap to its account

- Added one local-only opaque account binding to each candidate and established
  workspace binding, and required it before and after every bootstrap provider
  access.
- Made account mismatch fail closed without treating another private database
  as empty or changing the existing CloudKit and Keychain formats.

## [2026-08-28] correction | Bind ongoing mailbox access to its account

- Reused the established local account binding around every private-CloudKit
  mailbox operation instead of adding another token or provider field.
- Required account mismatch to preserve pending work and the last accepted
  cursor and engine state without accepting fetched or sent results, invalidate
  the affected sync-engine instance, and recreate it only under the original
  binding.

## [2026-08-28] correction | Establish the exact CloudKit zone before bootstrap

- Required a binding-checked fetch, save-if-absent, and read confirmation of the
  fixed `PosatoSyncV1` zone before treating the workspace anchor as absent.
- Reused the existing provider outcomes and account binding without adding a
  zone manager or local zone token; established-workspace zone loss remains
  action-required and preserves local and pending work.

## [2026-08-28] correction | Bind the workspace-key lifecycle to its account

- Reused the candidate or established account binding around every
  synchronizable workspace-key read and deletion, including destructive
  removal, without changing item selectors, bytes, or device-local Keychain.
- Failed account checks expose no key bytes or cleanup success, preserve the
  established state for exact reconciliation under the original account, and
  the wiki index now routes future work through ADR 0007.

## [2026-08-28] implementation | Add semantic application groups

- Added one validated, revisioned application-group name to the local target
  aggregate and migrated the SQLDelight schema from v1 to v2 without shared
  platform application identifiers.
- Extended the shared paused-items screen with truthful device-local selection
  status while retaining app pickers and opaque mappings for TARGETS-003 and
  TARGETS-004.

## [2026-08-29] experiment | Verify authenticated macOS helper IPC

- Fixed the nested helper, daemon, Mach service, launchd policy, protocol bounds,
  durable-state path, and exact mutual signed-peer requirements for MACOS-003.
- A signed physical lifecycle passed authenticated Apply, lease renewal, exact
  restore, controlled helper-loss recovery, Disable, right removal, daemon
  unregistration, and clean idle verification.
- Replaced the infeasible zero-second cross-process authorization timeout with
  a non-shared 30-second transfer bound and immediate one-use destruction.
- Required protocol capabilities, monotonic sequences and elapsed deadlines;
  bound unknown reconciliation to canonical input; and made failed cleanup keep
  ownership and registration fail-closed.
- Bound renewal to the durable owner, kept unavailable-daemon cleanup
  action-required, and made the packaging gate assert the exact helper and
  launchd plist contracts.
- Preflighted durable Apply ownership before authorization so a rejected request
  cannot inherit another operation's cleanup responsibility.
- Made daemon connection ownership require the explicit preflight result so a
  malformed Apply cannot inherit a foreign global phase.

## [2026-08-29] tooling | Add SwiftLint to the macOS quality gate

- Pinned SwiftLint 0.65.1 through its Swift package command plugin, enabled
  strict linting alongside `swift format`, and connected it to the Gradle
  `check` and root `quality` lifecycle.
- Split oversized native files by existing model, transport, coordination, and
  request-processing responsibilities; no lint rules were disabled.

## [2026-08-29] correction | Retain macOS cleanup ownership until Idle

- Kept transient startup, invalidation, and expired-lease cleanup failures on
  the existing daemon timer until durable ownership reaches Idle.
- Retained verified Apply ownership after a post-effect error and made an
  unavailable daemon report recovery required instead of synthetic Idle.

## [2026-08-29] correction | Surface background macOS lease loss

- Required every background renewal to receive an explicit Success and Applied
  acknowledgement from the daemon.
- Made every rejected, malformed, timed-out, or exhausted renewal invalidate
  XPC and terminate the helper nonzero so inherited desktop pipes cannot retain
  stale enforcement state.

## [2026-08-29] correction | Preserve exact macOS Apply ownership

- Kept exact duplicate Apply and exact Apply reconciliation in Applied state by
  validating the existing record through maintenance instead of startup
  restoration.
- Made every unverified non-Idle Apply response conflict before either the
  daemon connection or helper can claim another session's cleanup ownership.

## [2026-08-29] correction | Retire macOS renewal before cleanup

- Made successful Idle Enable and Repair clear helper and daemon-connection
  cleanup ownership alongside Restore, Disable, and Remove.
- Synchronously retired renewal before every direct or reconciled cleanup so an
  in-flight timer cannot run afterward; failed cleanup no longer extends the
  lease while the daemon retries restoration.

## [2026-08-30] correction | Restore on sleep and replace the macOS daemon

- Added native sleep/wake restoration without a watchdog or silent reapply.
- Made Repair restore ownership, await daemon unregistration, and reconcile the
  same request through a fresh helper when immediate same-process registration
  is rejected by Service Management.
- Signed physical checks passed exact sleep/wake restoration, explicit later
  Apply, and a Ready and Idle Repair with a replaced daemon process.

## [2026-08-30] correction | Preserve authorization repair across helper handoff

- Made same-request Repair reconciliation restore the exact Authorization
  Services rule, while Enable reconciliation continues to verify it only.
- A signed physical run replaced a deliberately mismatched rule through a fresh
  helper and daemon, then Remove deleted the right and service.

## [2026-08-30] correction | Stop unsafe Repair and proxy replacement

- Prevented Repair from unregistering its recovery daemon when restoration is
  not explicitly confirmed Idle.
- Made Apply reject existing HTTP, HTTPS, SOCKS, PAC, and autodiscovery proxy
  state before durable ownership or SystemConfiguration mutation.

## [2026-08-30] decision | Converge macOS Repair without daemon churn

- Accepted Ready, authenticated, compatible, Idle, exact-rule state as a
  completed Repair without mandatory daemon replacement.
- Kept registration for absent services and made direct and reconciled Repair
  share one effect-free exact-state path.

## [2026-08-30] correction | Reconcile cleanup after daemon unregistration

- Made same-request Disable and Remove reconciliation temporarily restore
  authenticated daemon access instead of treating an absent service as proof
  of cleanup.

## [2026-08-31] correction | Rotate the macOS renewal connection

- Moved periodic lease renewal to a separate non-owning authenticated XPC
  connection and rotated it before its bounded operation sequence is exhausted.
- Kept the original Apply connection as cleanup owner and preserved fail-closed
  termination for actual renewal failures.

## [2026-08-31] decision | Consolidate the design system in SESSION-001

- Made `SESSION-001` the first explicit checkpoint for comparing repeated
  production UI patterns with `DESIGN.md` and the disposable prototype.
- Kept reusable components, exact tokens, and responsive rules evidence-driven
  within the session slice instead of adding a speculative design-system task.
- Accepted roadmap revision 3 without changing its task count, dependencies,
  waves, or integration groups.

## [2026-08-31] decision | Require approval for quality suppressions

- Made root-cause correction the default and required explicit prior maintainer
  approval for every narrowly justified quality-tool exception.
- Added an aggregate quality check that rejects Kotlin suppression annotations
  outside the exact allowlist of approved pre-existing exceptions.

## [2026-08-31] experiment | Add device-local macOS application mappings

- Added strict signed-bundle selection in the normal-user helper and an
  owner-only local mapping database outside synchronization and diagnostics.
- Kept picker capability and its human-interaction deadline off daemon XPC;
  lifecycle transport remains unchanged.

## [2026-08-31] correction | Retire the AppKit picker helper after selection

- Made the dedicated application-picker helper client one-shot after physical
  testing found that returning to a blocking pipe read left AppKit unresponsive.
- Added bounded shutdown cleanup that cancels an active AppKit panel before its
  helper exits, preventing an orphaned system picker when Posato quits.
- Kept enforcement helper lifecycle and daemon ownership unchanged.

## [2026-08-31] decision | Add a development-packaging correction gate

- Added `MACOS-006` before macOS domain and application enforcement after the
  unchanged Gradle package failed to launch during a physical gate.
- Required strict nested-signature verification and launch without manual
  re-signing while retaining release signing and notarization in `RELEASE-001`.

## [2026-08-31] correction | Remove TARGETS-003 lint suppressions

- Removed every detekt suppression introduced by TARGETS-003 and split mapping
  UI, state conversion, and ViewModel operations along their existing concerns.
- Centralized the unchanged corrupt-mapping failure without weakening its
  validation or exception contract.

## [2026-08-31] correction | Preserve truthful mapping startup states

- Deferred packaged helper discovery until first use so the documented Gradle
  desktop shell starts outside an application bundle.
- Kept mapping load failures distinct from a confirmed empty snapshot and
  rendered only their retryable failure state.

## [2026-08-31] correction | Reject Posato-owned application bundles

- Extended application self-selection rejection to the complete macOS Posato
  identifier namespace, including the signed helper.
- Kept third-party signature validation and atomic batch rejection unchanged.

## [2026-08-31] correction | Align application mapping status and IPC evidence

- Hid device mapping status when neither an application group nor retained
  application choices exist.
- Corrected the TARGETS-003 record: capability negotiation ends at the
  parent/helper pipe, while daemon XPC rejects selection without a handshake.

## [2026-09-01] correction | Make development macOS packages launchable

- Signed and verified the complete arm64 JVM, SQLite, helper, and daemon package
  surface inside-out for credential-free and Apple Development artifacts.
- Kept development entitlements on the application launcher only and packaged
  the unchanged verified bundle without JPackage rewriting nested signatures.

## [2026-09-02] experiment | Verify and harden the encrypted operation core

- Implemented and verified the format-1 codec, deterministic reducer, serialized
  writer, durable SQLDelight replica state, and JCA and CryptoKit provider
  boundary without importing feasibility code wholesale; the JVM, iOS
  Simulator, and physical-iPhone quality matrix passed.
- Fixed writer lifecycle, cancellation, checkpoint-reconciliation, and reopen
  validation defects found by hosted review, and gave sensitive sync carriers
  fixed redacted string representations.
- Bounded native cryptographic output and persisted-state restore, cleared owned
  key and plaintext buffers, made a retired transport key unusable, and kept
  the active writer's key when a rejected open passes the same instance.

## [2026-09-02] correction | Remove dead synchronization code

- Removed four unused SQLDelight queries and moved the projection digest into
  test sources without changing schema, format, or runtime behavior.

## [2026-09-02] tooling | Add the agent verification driver

- Added `posato-control`, a Kotlin CLI with a Swift accessibility bridge and
  an XCUITest driver, so agents can build, launch, drive, inspect, screenshot,
  and reset the macOS and iOS applications on the Simulator and the supported
  iPhone with JSON evidence kept under the ignored build directory.
- Recorded that Compose Desktop exposes controls through macOS accessibility
  without `testTag`, while iOS maps `testTag` to the accessibility identifier.

## [2026-09-02] implementation | Add device-local iOS application mappings

- Added a fresh Family Controls picker boundary, live authorization state,
  bounded app-private token persistence, an aggregate shared mapping count, and
  simulator-safe unavailable behavior without copying feasibility code.
- Verified save, cancel, clear, restart, authorization revocation and recovery,
  and reinstall lifecycle behavior on a development-signed physical iPhone;
  kept the Device Activity App Group migration explicit for its named task.
- Recorded that losing Screen Time access while the picker is open is an access
  change, not a failure: the selection is retained and the action becomes
  `Allow and review applications`. The Screen Time consent alert and the picker
  list stay outside the driver's accessibility tree, so granting access,
  selecting applications, and revoking access remain maintainer steps.

## [2026-09-03] tooling | Add the verify-posato skill

- Added the project-local `verify-posato` skill under `.agents/skills/` with a
  feature map (websites, application group, macOS application mappings) that
  tells an agent how to launch, check, drive, prove, and clean up Posato through
  `posato-control` on the desktop, the Simulator, and the iPhone.
- The websites and application-group recipes were executed end to end on the
  Simulator; the macOS application picker is documented as a manual step
  because its panel belongs to the helper process.
- Added a root `CLAUDE.md` that imports `AGENTS.md`, so Claude Code loads the
  shared agent instructions automatically instead of relying on a manual read.
- The proof runs corrected the iOS driver: `near` now weighs vertical distance
  so a row's own button wins, a `near` target clipped at the screen edge is
  scrolled into view, and clearing a field sends a few extra deletes because
  the first keystrokes can be lost while the keyboard appears.

## [2026-09-03] planning | Retain the iOS mapping hardening follow-up

- Added the `TARGETS-005` roadmap stub after the independent review of
  `TARGETS-004` accepted its blocking findings and deferred four advisory
  items, so the follow-up survives without pre-expanding a brief.
- Recorded the deferred items on the iOS enforcement page: a stale
  authorization request driving a later picker, a refused presentation
  stranding the selection and holding the adapter mutex, a corrupted store
  that no in-app action can clear, and unavailability stated in build terms.

## [2026-09-03] implementation | SYNC-004 one-workspace bootstrap

- Implemented the ADR 0007 ten-step bootstrap protocol with
  deterministic fake ports, a `3.sqm` singleton table, and a
  37-test coordinator contract matrix covering the full evidence
  list.
- Independent completed-change review required two corrections,
  both applied: the established path re-reads its anchor, and
  AC-02 is demonstrated by two coordinators over one shared fake
  provider. `./gradlew quality` passes on JVM and iOS Simulator;
  a verify-posato simulator pass confirmed the app still launches
  and website add/remove works with the new migration.

## [2026-09-03] experiment | Recover iOS mapping stale, refused, and corrupted states

- Closed the four deferred TARGETS-004 hardening items: a per-request choose
  generation, fail-closed picker presentation, in-app clear of a corrupted
  selection store, and product-terms unavailability copy.
- Recorded the maintainer-accepted sentence "Choosing apps is not available in
  this version of Posato" and that corrupted-store clear has no confirmation.

## [2026-09-04] implementation | Close SYNC-005 iOS synchronizable-Keychain adapter

- Implemented the Swift provider with binding preflight and postflight plus an
  account-change observation window, the Kotlin `iosMain` adapter over the
  frozen bootstrap ports, and the iOS target entitlements with the team prefix
  read at runtime from `Info.plist`, so no team value is tracked.
- Proved the full create, exact-read, duplicate, conflict, and
  delete-and-verify-absent cycle on the physical iPhone with a random
  workspace id and teardown cleanup; Simulator, Kotlin, and quality checks
  pass, and the independent review closed with two fixes and one evidenced
  decline.

## [2026-09-04] implementation | SYNC-006 macOS Keychain companion

- Added the nested `app.posato.macos.sync` companion, one-shot pipe protocol,
  entitlement guard, and JVM `BootstrapAccountPort`/`BootstrapKeyPort` adapters.
- Ad-hoc packaging, Swift and JVM tests, and a posato-control desktop launch
  passed. Keychain Sharing is not an App ID capability; the untracked
  development profile is the packaging gate. The companion stamps
  `com.apple.application-identifier`. A synthetic-id create / identical /
  read / delete-and-verify-absent round trip passed and left no item.

## [2026-09-04] implementation | SESSION-001 one bounded manual session

- Shipped the session core on both shells: 5-minute to 24-hour setup with
  review of the resolved end time and effective local items, atomic start,
  confirmed early end, and terminal expiry with the marker committed before
  the session is exposed as ended; shared Paused-items patterns moved into
  `core/designsystem` with no behavior change.
- Independent completed-change review required two corrections, both
  applied with regression tests: start/end failures stay visible with retry
  in review instead of being wiped by the refresh, and `startSession()`
  re-checks the blocking review reasons instead of trusting the button
  state. `./gradlew quality` and 51 session tests pass; verify-posato
  start and early-end re-run green on the Simulator, and the desktop
  re-run gap is documented tooling flakiness in the execution record.

## [2026-09-04] tooling | Wave 3 closeout: verify-posato session shell, roadmap revision 8

- Aligned the `verify-posato` skill, feature map, and canonical fixtures with
  the session shell: launch readiness is the `Paused items` button, every
  Websites or Applications recipe switches destination first, and a new
  `features/sessions.md` documents setup, review, start, early end, expiry,
  and persistence. Documented what the driver does not prove (native
  Keychain, companion, CloudKit paths) and two desktop traps with their
  workarounds: rows below the window are reached through Tab focus
  traversal, and a `type` right after a `tap` on the same field fails.
- Fixtures re-proven on the Simulator and the desktop (add, remove, session
  start, early end) with database read-backs; the desktop database was
  restored afterwards.
- Removed the companion verifier test that depended on a staged package in
  `desktopApp/build`, and added `APPLE-002` (App Store Connect API
  provisioning) and `QUALITY-003` (real desktop `scrollTo`) to the roadmap
  as revision 8.

## [2026-09-04] tooling | QUALITY-004 unattended verification

- The macOS application picker is now driven end to end. `posato-control`
  element commands take `--process <name|pid>`, resolved only to a process
  whose executable lives inside the staged `Posato.app`; the panel is reached
  with `⌘⇧G`, a typed absolute path, and `Return`, and the mapping count went
  0 to 1 to 0 with no human step. Superseded the earlier conclusion that the
  picker cannot be scripted.
- `observed`: the helper presents its panel with `NSOpenPanel.runModal()` and
  never runs an `NSApplication` event loop, so it owns a real window while
  exposing no accessibility server. `snapshot` on it reports
  `PROCESS_NOT_INSPECTABLE` instead of an empty tree, and key events go to the
  session tap after the driver brings that process forward, refusing rather
  than typing blindly when it cannot.
- `doctor` became the provisioning gate: every one-time condition is a named
  check with a state of `ok`, `missing`, or `unknown` and one remedy. An
  unobservable condition (the helper's background approval, Screen Time
  authorization) is `unknown` and never blocks; `error` is reserved for a
  configuration packaging genuinely rejects, so an ad-hoc checkout stays
  `ok: true`. `observed`: `./gradlew quality` restages an ad-hoc package and
  silently removes the picker, which `desktop.staged` now names; the first such
  run after a development-signed stage also fails packaging verification on the
  mixed state and passes on a rerun.
- `observed` (iOS 26.5.2): a captured iOS selection store can be seeded back
  with `devicectl` and survives a reinstall as a count read-back, so the
  picker is not needed on a rerun; the Screen Time consent alert still is, and
  whether the restored tokens still enforce stays `open` for `IOS-001`.
- Task workflow revision 6, maintainer-requested: a new worktree must copy the
  whole ignored `local.properties` from the main checkout, which is the
  canonical copy of every `posato.` key, and run `:posato-control:installDist`
  before it is usable. `git worktree add` carries neither, and a partial copy
  fails deep inside a feature recipe instead of at setup.

## [2026-09-04] implementation | IOS-001 Posato-owned iOS restrictions

- Enforce exact domains and the opaque application selection through one
  named Managed Settings store (`app.posato.session`) with
  validate-before-write, verify, rollback to empty, and idempotent clear;
  the selection store migrates to `group.app.posato.ios.session` with
  copy-verify-delete semantics. Independent plan and completed-change
  reviews passed after brief and code corrections.
- `./gradlew quality` green, Kotlin enforcement tests 4/4, Simulator
  suite 50 passed with 1 expected device-only skip. The physical iPhone
  run cleared the gate the same day: signed build with the ephemeral
  team parameter, 54 device tests passed with 0 skips, and the
  `AC-01`/`AC-02`/`AC-04` manual steps (Safari block, app shield,
  unselected controls, clear restores, revoke-then-clear) all pass.

## [2026-09-05] implementation | SYNC-008 macOS CloudKit boundary

- Added macOS CloudKit operations 5-11 to the nested `app.posato.macos.sync`
  companion (zone, anchor, bundle, bounded change fetch) with binding
  preflight/postflight and JVM `BootstrapCloudPort`/mailbox adapters.
- AC-06 physical round trip passed on one Mac with the untracked development
  profile: zone save and confirm, anchor create plus conflict on identical
  re-create, bundle save plus identical re-save, one-bundle change fetch,
  different-bytes rejection, exact zone delete verified absent.
- Review follow-ons fixed: anchor-request slice copy, dropped zeroing
  theater, `PageCursor` buffer copy, direction-keyed fetch frame cap,
  `uuidTextBytes` constant. Proves the macOS leg only; cross-device exchange
  stays with SYNC-009.

## [2026-09-05] verification | MACOS-004 physical matrix and harness corrections

- The gated JVM harness now drives the installed development package through
  a parent process signed with the maintainer's development identity, and the
  physical matrix passed on macOS 26.5.2 with Safari 26.5.2 and Chrome 152:
  denial and same-tab presentation in Safari regular and Chrome regular and
  Incognito windows, controls reachable, conflict preflight, listener stall,
  helper kill, sleep/wake, reboot, and privacy canary; the Safari Private
  Browsing rows passed after the Screen Time passcode was lifted.
- The rows exposed and the closeout fixed three defects: Safari tabs have no
  AppleScript `id`, a failed post-Apply chain check answered Apply with the
  Restore response, and an Apply reconciled to `Idle` was reported as active.
  The effective-chain check now waits for configd propagation. IP literals
  stay relayed as unselected hosts and `AC-01` was amended to say so.

## [2026-09-05] tooling | Align verify-posato and doctor with the wave-4 merges

- Corrected the one recipe the merges made wrong: the iOS selection store now
  lives in the App Group container `group.app.posato.ios.session` at
  `ApplicationMappings/mappings-v1.json`, so capture and seeding use
  `--domain-type appGroupDataContainer`. The private container is only a
  migration source, adopted while the group file is absent or empty and
  deleted afterwards, so a capture taken from it after any migrated launch
  reads nothing.
- `doctor` observes the three nested components of the staged package
  separately (`desktop.helperBundle`, `desktop.proxyDaemon`,
  `desktop.syncCompanion`). Packaging verified them only at build time, so an
  incomplete stage previously reported `ok: true` and failed later inside a
  recipe; presence is now a named check with one remedy.
- The skill records what the driver must not fake: macOS browser-domain denial
  (`MACOS-004`) and iOS restrictions (`IOS-001`) have no user path until
  `SESSION-002`, their proof is the gated JVM harness and the device XCTest
  run, and `PosatoMacOSHelper` is no longer picker-only. `observed`: a harness
  or companion run leaves the root-owned proxy ownership record and the
  workspace key in the synchronizable Keychain, an iCloud item rather than a
  local one, which `reset -t desktop` does not clear and `doctor` does not
  report.

## [2026-09-05] implementation | APPLE-002 App Store Connect provisioning

- `posato-provisioning` obtains Apple development provisioning for the five
  Posato App IDs through the App Store Connect API, so the portal step that
  `SYNC-006` had to perform by hand no longer blocks an agent. It is JDK-only:
  the ES256 token is signed with `java.security` and requests go through
  `java.net.http`, adding no dependency.
- Three provisioning facts are worth keeping because each one fails silently.
  A Mac's Provisioning UDID is not its Platform UUID, and on Apple silicon the
  two differ in value and length; an iPhone's is `hardwareProperties.udid`,
  not devicectl's `identifier`. App Store Connect's `filter[identifier]` is a
  contains match, so `app.posato.ios` also selects the activity-monitor
  extension and App IDs must be matched exactly on the client. `ES256`
  requires a raw `r||s` signature while the JDK emits DER, and the padding
  difference only shows up in a fraction of signatures.
- A local signing identity is correlated to the account by exact certificate
  bytes before a profile is built around it, because a profile naming a
  certificate this Mac holds no private key for installs and reports success
  while signing nothing.
- Only `GET` is retried. A retried `POST` that had already been applied would
  create a duplicate certificate against Apple's per-team cap, a conflicting
  device, or a second profile claiming a name Apple keeps unique per team.
- "Connected iPhone" has to mean wired. `devicectl` reports a phone paired
  over the local network as connected as well, and its `tunnelState` changed
  between two consecutive listings on the same Mac, so selecting on that would
  register a different set of devices depending on when the command ran.
- The two `doctor` reports are deliberately separate: the verification driver
  asks whether this checkout can build, sign, and drive right now, while this
  one asks whether the Mac can obtain Apple resources and whether the account
  holds them. Their check identifiers do not overlap.

## [2026-09-06] experiment | Move the interaction study to native Compose hosts

- Refined the warm paper and moss visual direction and extracted a reusable
  Compose component library, desktop catalog, and deterministic previews.
- Replaced the maintained HTML target with an isolated mock iPhone and macOS
  prototype using the existing Compose component library. Preserved strict
  flows, configurable items, guided scenarios, independent Free play, and the
  manual clock; added hidden native inspection controls and common regression
  coverage. Recorded [native evidence and limits](sources/mvp-interaction-prototype.md#native-interaction-prototype)
  without changing production design, platform services, or architecture.
- A 50-website native probe exposed excessive scrolling. Following maintainer
  acceptance, added bounded session summaries, read-only details, separate
  website/app lists, and search. Recorded native keyboard, editing, filtering,
  and state-retention evidence without promoting prototype geometry to authority.
- Further maintainer feedback replaced bare custom-time input with hour/minute
  wheels and step arrows, and prioritized inline/batch website entry over search.
  Refined segmented categories, app-chooser priority, metadata spacing, and row
  menus. Following further acceptance, moved main navigation to an iPhone bottom
  bar and Mac sidebar with a readable minimum window width. Kept these
  interactions and their validation in the isolated mock prototype.
- At the maintainer's request, documented current Compose tokens, components,
  layouts, behavior, and controls. Review clarified the authority split: root
  `DESIGN.md` keeps accepted production requirements, while the full
  [prototype reference](../../prototypes/mvp-interaction-flow/DESIGN.md) records
  mock implementation evidence without adopting its geometry in production.
- Preserved the retired HTML and Node suite under the maintainer-approved
  non-release tag `archive/mvp-interaction-flow-html`, independently of a squash
  merge or work-branch deletion; confirmed recovery with a tag-only fetch.
- Integrated the Mac title area with the app background and added an explicit
  20 pt native frame mask, disabled in fullscreen. Kept system window controls
  and verified native resizing, dragging, appearance, and draft retention;
  the AppKit integration remains prototype-only and runtime-specific evidence.
- Follow-up review removed the blocking AWT-to-AppKit dispatch, retaining the
  window through queued configuration. Separated fast prototype verification
  from explicit host builds; native application resources and declared package
  inputs keep JVM model tests independent while tracking native-only changes.

## [2026-09-07] verification | MACOS-005 physical matrix and harness corrections

- The gated JVM harness drove the installed development package through all 8
  rows: launch-during and running-at-activation termination, the paused notice
  after a maintainer-allowed notification prompt, control survival, clear,
  forced helper termination, and parent exit; the privacy canary is absent
  from the evidence.
- The rows exposed and the closeout fixed three defects: `NSWorkspace`
  snapshots never refresh without a run loop (replaced with `libproc` pid
  enumeration plus per-pid hydration), held `NSRunningApplication` snapshots
  go stale so the notice never fired (each poll re-resolves tracked entries
  against a fresh listing, vanished pid counts as terminated), and the
  harness `pkill -f` matched its own launcher command line and SIGKILLed the
  run (the client now kills only the launcher's child via `ProcessHandle`).
  Physical packaging must use the Apple Development identity; the ad-hoc
  fallback fails the client team check.

## [2026-09-07] verification | IOS-002 suspended-expiry Simulator and device runs

- The Device Activity monitor extension, Swift scheduler seam, and Kotlin
  adapter are implemented and verified: 68 Xcode tests pass with no failures,
  8 Kotlin enforcement tests pass, `./gradlew quality` and the three
  credential-free CI builds pass, and plan, completed-change, device-test,
  and hosted P1/P2 reviews are recorded as approved with no open findings.
- The `APPLE-002` profile cleared the physical gate: the signed device build
  passes with portal updates allowed, and two device windows passed on a wired
  iPhone (force-quit clear, foreign store intact, cancel verified,
  session-matched reconciliation expired; clear observed within minutes after
  interval end, never promised). The review's fixes (session attribution,
  absolute one-shot schedule, synchronous extension clear) were proven live in
  the second run. Reboot-inside-interval stays an open observation.

## [2026-09-07] implementation | Adopt the native prototype design in the MVP

- Adopted the accepted native prototype's palette, complete reusable Compose
  components, iPhone bottom navigation, and native Mac sidebar/window into the
  real Session and Paused items screens, retaining existing ViewModels and
  service boundaries. The prototype remains frozen reference evidence.
- Added bounded batch entry, retained drafts, searchable lazy lists, compact
  selection details, duration wheels, and automatic application-group activation.
  DESIGN.md and the maintained verification driver/skill now describe these
  real user paths without implying connected synchronization or enforcement.
- Native keyboard verification corrected double IME subtraction in the root;
  the design topic records why the modal dialog does not share that correction.
  Physical picker and timer checks retain their platform and accessibility limits.

## [2026-09-07] implementation | Add focused design-system component previews

- Added colocated Compose previews for visual component families, using small
  synthetic examples without providers, ViewModels, or live services. Selected
  variants cover dark appearance, larger text, disabled/error states, and reflow.
- Theme, token definitions, and full application scaffolds remain outside the
  standalone preview set. Runtime implementations and prototype sources are unchanged.

## [2026-09-07] maintenance | Run native Swift tests in the local quality gate

- Added simulator-compatible Swift XCTest to the aggregate quality gate with
  a shared Xcode scheme, prebuilt Kotlin framework/resources, isolated temporary
  Simulator ownership, and retained test reports. Device-only cases remain
  separate physical checks rather than implied Simulator coverage.
- Preserved unsigned Debug device and Release Simulator host compilation in
  local quality, including Swift branches excluded from Simulator tests.
- The maintainer's latest decision disables GitHub CI and removes its workflow
  source and required status check, superseding the earlier manual-CI policy.
  Local quality and review remain mandatory; `main` retains PR and administrator
  enforcement without force pushes, deletion, or bypass allowances.
- Refreshed onboarding to describe the real MVP-in-progress applications and
  tests while retaining the unconnected synchronization/enforcement boundary.
- Isolated the macOS CI proxy timeouts to blocking native test orchestration
  starving queued work. Same-runner serialized controls passed without timeout
  changes. Replaced blocking test-task waits with an awaited dedicated-thread
  boundary, retaining assertions, deadlines, and parallel execution. Temporary
  diagnostics were removed, with no product behavior change.

## [2026-09-07] implementation | Exchange mailbox bundles through iOS private CloudKit

- Implemented `SYNC-007`: the six platform-neutral mailbox result types moved
  from `jvmMain` to `commonMain` (the `ByteBuffer` page parser stays in
  `jvmMain`); a Swift CloudKit provider behind the `iosMain` cloud and mailbox
  adapters mirrors the macOS companion error table, explicit record-zone-changes
  paging, and create-only reconciliation behind binding preflight/postflight and
  an account-change window, with cancellable calls and redacted carriers.
- An expired server change token maps to `unknown-outcome` with no recovery
  path on both platforms; that retry story stays an open `SYNC-010` question.
- Proof is 28/28 Swift unit tests, 16/16 JVM and 18/18 iOS contract tests, a
  clean aggregate quality gate, and one controlled physical-iPhone run covering
  zone save and confirm, anchor create with conflict, bundle save with
  identical re-save, change fetch, different-bytes rejection, and verified zone
  deletion, leaving the private database as found.

## [2026-09-07] implementation | SESSION-002 local session enforcement

- Wired session start, early end, and observed expiry to the accepted local
  enforcement on both platforms: commit, apply or clear, report, with a frozen
  effective set shown in the active surface and Retry or Resume on every failed
  or refused path. macOS applies browser denial first and restores it when the
  application configure fails; iOS reads live restriction state and consumes
  the suspended-expiry reconciliation once. `./gradlew quality` and the
  Simulator driver rows pass; the physical Mac and iPhone rows stay pending
  with the maintainer, as does the desktop driver accessibility-tree finding.

## [2026-09-08] implementation | Refuse system-critical applications in the macOS picker

- The picker now refuses the same system-critical set the enforcement helper guards (8 bundle identifiers plus `/System/Library/CoreServices/`), before signature inspection with whole-batch rejection; both sides share one source in the `PosatoMacOSHelper` target, and the stale picker-follow-up comment and wiki sentence are corrected.
- Outcome byte `7` flows into shared rejection `SYSTEM` with the copy "System components such as Finder cannot be added to this group."; existing `SELF`, `INVALID_OR_UNSIGNED`, and iOS-owned `UNSUPPORTED` paths are unchanged.
- Verified by focused Swift (155/155) and JVM suites, aggregate `./gradlew quality` (197 tasks), a driver run proving the Finder refusal row and the Safari control row with restart persistence, and two independent completed-change reviews with no findings.

## [2026-09-08] implementation | SESSION-003 frozen start set persists across relaunch

- The active session now persists its frozen start set (exact domains plus the
  application count, no opaque identifiers) with the session row and shows it
  in the summary and Selected items after a relaunch, while Resume and the
  silent re-converge keep applying the current Paused items; the active copy
  states both halves. Migration `5.sqm` preserves existing rows with a live
  fallback for pre-upgrade sessions. `./gradlew quality`, the session suites,
  and a desktop driver relaunch row pass with an independent review approval.

## [2026-09-08] implementation | SYNC-009 Apple bootstrap composition and consent control

- Both apps compose the accepted bootstrap behind the narrow `AppleBootstrap`
  facade (process-scoped single-flight, background dispatcher,
  established-context read); the desktop companion client resolves lazily and
  degrades to a truthful non-Ready outcome, and the iOS CloudKit backend defers
  container resolution past startup after eager construction trapped the Swift
  test host.
- The Session screen carries one explicit Sync with iCloud control that states
  what it does, runs one attempt per press, and reports only the coordinator
  outcome; `DESIGN.md` names the control and no longer claims synchronization is
  unconnected.
- Verified by `./gradlew quality`, real-graph composition tests on JVM and
  simulator, Swift tests, and the full physical matrix on the maintainer's
  account: Mac-first and iPhone-first joins from an empty account, and a
  simultaneous opt-in with the two presses 0.1 s apart in which the loser held
  `waiting-for-workspace-key`, kept a candidate without establishing anything,
  then adopted the winner's workspace on the next press while the winner kept
  its established status. The maintainer confirmed exactly one zone in the
  CloudKit Console.
- Three limits are recorded rather than claimed: a from-empty rerun needs the
  maintainer to delete the zone by hand until `SYNC-010` adds removal; the
  count of surviving synchronizable Keychain accounts is invisible to both the
  `security` command line and the driver; and no user path reads the workspace
  key at all in this task, since the linked status is a local-state read and a
  press on an established workspace stops at the anchor. The losing candidate's
  cleanup and the winner's key readability therefore stay covered by tests
  instead of physical observation.

## [2026-09-08] result | QUALITY-005 desktop accessibility diagnosis and unattended fixture proof

- The `SESSION-002` empty desktop accessibility tree does not reproduce on the current application in either staging mode; both runs expose a full tree from the first readiness wait.
- The driver reports a tree with no addressable window as named failure `DESKTOP_WINDOW_UNAVAILABLE` (exit 4) instead of an empty success; `waitFor` polling tolerates a transient empty tree and reports the named failure at the deadline.
- The unattended `session-start-action-required-desktop.json` fixture passes end to end; confirmed prompts land in the attended active-claim path instead (distinguished by `authd` evidence), and the fixture's nothing-restricted step matches by `textContains` against the accepted `SESSION-003` copy.

## [2026-09-08] implementation | Established Apple mailbox exchange

Recorded SYNC-010 process ownership, writer-owned publication confirmation,
transactional cursor progress, token-expiry restart, and anchor-gated removal.
Updated the current design and physical verification recipe, retained the
accepted pre-link and exact-refetch limits, and restored the missing SYNC-009
bootstrap observation. A physical iPhone probe exposed and fixed an empty-cursor
NSData conversion failure before native fetch; its adapter regression and
cross-device exchange pass. Signed physical tests also cover offline retry,
iPhone sign-out recovery, removal and foreign-anchor protection in both
directions, and concurrent consent with losing-device key adoption. The
maintainer accepted iPhone sign-out evidence and waived sign-out on the working
Mac; that coverage limit remains explicit in the brief and execution record.
Review corrections make local saves independent of the network flight through
an ordered, workspace-bound handoff and on-demand writer opening. Tests cover
cancellation, failed authoring, and exclusion of old workspace changes; signed
Mac/iPhone edit, relaunch, cleanup and repeated exchange also pass. The volatile
handoff before outbox authoring remains an explicit D1 limit.

## [2026-09-09] implementation | First-install flow and accepted onboarding UI

The six-step first-install flow uses a local completion row and migration seed
for existing product data. The maintainer accepted the reviewed UI: compact
progress, prototype-inspired welcome, icon-led privacy points, contextual
permission copy, responsive actions, and service-state-aware continuation.
DESIGN.md records the accepted presentation; service, persistence, and
cross-device promise boundaries remain unchanged. Native verification covers
Simulator full/skip paths, Mac setup, and the original physical iPhone consent
path. The Mac approval-required branch retains unit-only evidence on hardware
where the helper is already approved.
Review also identified the missing post-onboarding Mac helper-setup entry point;
the brand topic records it as open, and UI copy no longer promises that route.

## [2026-09-09] implementation | Session This Mac helper-setup route

The Session screen gains a macOS-only This Mac section below Sync with iCloud
that closes the deferred Mac setup route from the PR #44 review: Check Mac
setup reads the helper state only on an explicit press, then the section names
the real state with one action (Enable on this Mac, Open System Settings plus
Check again, or the enabled notice) and keeps a quiet Check again for every
known state. One helper readiness holder is shared by the first-install
permission step and Session, a never-registered service maps to a distinct
not-enabled readiness, and both enable and status reads verify the helper
signature before touching the client. A lost helper connection cannot be
rechecked in-process, so the unavailable notice says to quit and reopen
Posato. Nothing reads the helper at launch, foreground, or navigation; the
first-install pgrep evidence keeps holding. Secondary setup actions preserve
the main Session action. Collapsed iCloud and This Mac rows keep controls out
of the overview until requested, and the Mac host forwards progress and
repeated results to AppKit accessibility announcements.

## [2026-09-10] implementation | Bounded second-install workspace joining

Implemented ONBOARDING-002 with process-local, consent-bound manual and
foreground continuation, truthful waiting actions, and existing local setup
routes. Both physical device orders joined successfully and retained local
application selections; key waiting was not physically observed. ADR 0007 and
DESIGN.md record the accepted behavior; the execution record separates unit
coverage, physical results, and remaining evidence limits.

## [2026-09-10] task | SYNC-011 policy convergence (single PR entry, updated in place)

Shared write gate per the brief (`LocalTargetPolicyStore.withWriteGate`, one
`Mutex` in the SQL store, decorator saves and reconciler applies); latent
outcome receiver fixed (`SyncProjection`); three Detekt `TooManyFunctions`
findings resolved without suppressions. `D3` change signal: defaulted empty
on the interface, emitted only by reconciler applies, forwarded by the
decorator, silent re-reads in both view models. `D9` onboarding counts from
the real policy with receipt-keyed focus. Two-harness convergence fixed:
shared deterministic crypto minted colliding identifiers with divergent
contents, correctly reported as `REPLAY_CONFLICT`; fixtures now use
independent streams (`streamSeed`), no product change. `D4` base lag fixed
by re-reading the projection after the group-name republish. `./gradlew
quality` green (501 JVM tests, migration verification, `iosSwiftTest`);
five simulator fixtures green; verify-posato recipes assert convergence;
threat-model rows `A-04`/`T-03` extended. Details in the SYNC-011 execution
record.

## [2026-09-11] task | SYNC-014 removal hardening

Device-local tombstone of removed workspace identifiers refuses re-adoption
on the device that performed **Remove workspace**, at the fresh-attempt and
losing-candidate choke points, with join continuation as defense in depth.
Retryable uses the existing unlinked copy. ADR 0007 records the dated
amendment; the threat model adds `SYNC-014` to `A-04`, `T-04`, and `T-14`
and the fresh-install residual; the sync recipe states the settle rule and
ghost recovery. A fresh install without a tombstone can still join a ghost
during the provider purge window. Physical AC-04: the both-removed run
converged; the peer-still-linked run established a fresh identifier and then
lost it to the provider purge, a variant the tombstone does not cover,
recorded as an accepted limit with the settle rule and an open fix decision.
iOS `scrollTo` screen-swipes on compact Session and reports reached only
when the element's centre is on screen; the Simulator fixtures were rerun.

## [2026-09-11] task | MACOS-007 helper setup recovery

Diagnosed a registered, allowed daemon that launchd cannot resolve, plus a
post-timeout Check again that issued Status into a pending unknown request.
Setup retry now reconciles that original request until a conclusive answer
releases it; only an XPC endpoint that never accepted a Status or Enable
becomes RecoveryRequired, while a deadline that expires after dispatch stays
unknown. In-app copy does not tell
the person to unregister from Login Items without Idle cleanup. Onboarding
shows progress and keeps Not now usable on Mac. After leftover copies were
removed and Background Items reset, `SMAppService.notFound` no longer maps to
the unavailable Check-again-only dead end; Check offers Enable. Physical
Ready/Idle on the current development package survived relaunch; HTTP(S)
proxy stayed off. A registration attempt that fails and leaves the service
unregistered reports that setup could not be completed, each unresolved state
carries its own collapsed summary, a repeated unresolved result offers a Mac
restart, and a non-ready helper is named once above the session action.
Details in the MACOS-007 execution record and
[macOS enforcement](topics/macos-enforcement.md).
