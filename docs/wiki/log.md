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
