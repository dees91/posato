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
