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
execution authority for this milestone. Gates 1 through 6 are complete in the
accepted [MVP scope](../../product/mvp-scope.md) and
[product identity](../../product/product-identity.md), with the accepted brand
and product design authority in [DESIGN.md](../../../DESIGN.md) and the
accepted architecture in
[ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md).
The engineering quality baseline is also accepted in the
[quality contract](../../development/engineering-quality-contract.md) and
[task workflow](../../../tasks/README.md). The
[Gate 6 MVP roadmap](../../../tasks/mvp-roadmap.md) is accepted at revision 2
with concise future task stubs. All seven preparation gates now have their
accepted inputs and evidence. Production scaffolding remains blocked until the
Ready to open PR #1 checkpoint, shared PR #1 brief and execution record, and
explicit maintainer acceptance are complete. The sections below retain the
decisions that later tasks must resolve and the questions that may remain
incremental.

`user-confirmed` (2026-08-25): the maintainer accepted the complete
[Gate 3 baseline](brand-and-design-baseline.md), including the brand
foundation, visual direction, platform and accessibility constraints, six
low-fidelity flows, and exact PR #1 shell content. The canonical tool-neutral
contract is [DESIGN.md](../../../DESIGN.md).

## Gate 2: product identity (complete)

`user-confirmed` (2026-08-25): Gate 2 is complete. **Posato** is the product
and display name, `posato.app` is purchased and controlled by the maintainer,
and `app.posato` is the stable reverse-DNS root. Platform applications use
`app.posato.<platform>`; helpers and extensions use
`app.posato.<platform>.<role>`. Gate 4 accepted the exact application, helper,
and Device Activity monitor extension identifiers; exact Apple resource
registration remains Gate 7 work.

The repository records only the control confirmation. Registrar, account,
payment, and renewal details remain outside it.

`user-confirmed` (2026-08-25): the MVP scope, explicit non-goals, Apple-first
platform order, relative OS support baseline, primary flow, and measurable
outcome are accepted. Exact deployment-target numbers remain part of the
architecture baseline.

The repository now uses the accepted `posato` name. **Blocker** remains only
where it identifies historical research or decision provenance.

### Naming candidate screening

`observed` (2026-08-25): a preliminary collision screen compared normalized
exact names in the US and Polish Apple storefront search results, GitHub
repository-name search, RDAP responses for `.com`, `.app`, `.org`, and `.dev`,
and general web search. An RDAP `404` means that this check found no current
registration record; it is not a purchase guarantee, reservation, or legal
clearance. The screen did not include a professional trademark opinion.

| Candidate | Product fit | Preliminary collision result | Current assessment |
| --- | --- | --- | --- |
| **Kind Friction** | Expresses deliberate, owner-chosen resistance without punishment or surveillance | No normalized exact Apple storefront result or GitHub repository result; `.app`, `.org`, `.dev`, `.io`, and `.pl` returned no registration record; `.com` is registered | `user-confirmed`: finalist; the warm, human, values-led direction |
| **Manual Mode** | Frames the product as leaving autopilot and returning control to the person | No normalized exact Apple storefront result; the phrase and repository stem are already used generically; `.com` and `.dev` are registered | `inferred`: clearest alternative, but weak search and trademark distinctiveness |
| **Rallent** | A coined reference to *rallentando*, or slowing down without stopping | No normalized exact Apple storefront result; `.app`, `.org`, `.dev`, `.io`, and `.pl` returned no registration record; `.com` is registered and an unrelated Indian provider uses the name | `user-confirmed`: finalist; the cool, rhythmic, more ownable direction |
| **Intent Gap** | Names the space between impulse and deliberate action | No normalized exact Apple storefront or GitHub repository result; `.app`, `.org`, and `.dev` returned no RDAP registration record; the phrase is increasingly used for an unrelated AI/software concept | `inferred`: semantically useful but too generic and likely to accumulate unrelated search collisions |
| **Digital Detent** | Uses the mechanical detent as a metaphor for resistance that deliberate force can overcome | No normalized exact Apple storefront or GitHub repository result and no RDAP registration record for the four checked domains; separate Apple fitness and financial products already use **Detent** | `inferred`: precise metaphor but too technical and too close to existing software names |

`inferred`: **Blocker** should remain a historical working name rather than the
public identity. A screen-time application already uses **Blocker**, another
open-source website and application blocking product uses the same name, and
the repository term is highly crowded.

`user-confirmed` (2026-08-25): the maintainer narrowed the decision to **Kind
Friction** and **Rallent**. The other screened names are no longer finalists.
A balanced visual exploration applies both names to the same active-session
state: **Kind Friction** presents a warm, candid, values-led identity, while
**Rallent** presents a cool, rhythmic, composed identity whose meaning is built
through the product.

`user-confirmed` (2026-08-25): ease of pronunciation in Polish and domain
availability are explicit selection criteria.

`observed` (2026-08-25): registry RDAP returned registered domain objects for
`kindfriction.com` and `rallent.com`, and no registration objects for either
name under `.app`, `.org`, `.dev`, or `.pl`. The `.io` registry WHOIS returned
"Domain not found" for both names. These responses are a point-in-time
registration screen, not a registrar purchase, pricing, premium-name,
reservation, trademark, or future-availability guarantee.

`inferred`: **Rallent** is materially easier to pronounce and dictate in
Polish. The intended Polish reading, "RAL-lent", follows familiar
letter-to-sound and stress patterns. **Kind Friction** requires an English
reading approximated as "kajnd frikszyn", including a less transparent vowel,
final consonant cluster, and suffix. Domain registration state is currently a
draw across the checked extensions, although the shorter `rallent` stem is
easier to type and communicate. With these criteria added, the current
recommendation shifts toward **Rallent**; this remains an advisor
recommendation rather than an accepted name.

### Rallent-derived name exploration

`open` (2026-08-25): the maintainer raised a concern that the initial `r` and
double `l` in **Rallent** may impede Polish pronunciation or spoken spelling,
and proposed Italian-sounding derivatives and coherent country-code domain
hacks for exploration. This does not add a third accepted finalist.

`observed` (2026-08-25): a point-in-time screen checked exact Apple application
names in the US and Polish storefronts, exact GitHub repository names,
registry RDAP or WHOIS, and responding `.com` websites. None of the five
derivatives had an exact Apple storefront result. Registration results remain
subject to the same purchase, pricing, reservation, and legal limitations as
the earlier screen.

| Derivative | Polish reading | Meaning and memorability | Preliminary collision and domain result | Assessment |
| --- | --- | --- | --- | --- |
| **Ralento** | "ra-LEN-to" | Smooth, transparent Polish reading and a close echo of *rallentando*; Italian-sounding rather than correct Italian | `ralento.com` is registered and serves an existing safe-driving rewards site; `.app`, `.org`, `.pl`, and `.to` returned no registration object; `ralen.to` also returned no object | Best sound and strongest domain hack, but the live consumer-facing name collision prevents a clean recommendation |
| **Rallento** | "ral-LEN-to" | The attested Italian first-person form of *rallentare*, "I slow down" | `.com` is registered and parked; `.app`, `.org`, `.pl`, and `.to` returned no registration object; `rallen.to` also returned no object | Semantically authentic, but retains the double-`l` spelling concern |
| **Ralent** | "RA-lent" | Compact and close to **Rallent**, with one `l`, but still ends in a consonant cluster | `.com` is registered and offered for development; `.app`, `.org`, `.pl`, and `.to` returned no registration object; two exact GitHub repository names exist | Solves spelling more than pronunciation and loses some softness |
| **Ralenti** | "ra-LEN-ti" | Fluid three-syllable form, but less clearly connected to the intended product story | `.com` is registered; `.app`, `.org`, `.pl`, and `.to` returned no registration object; one exact GitHub repository name exists | Easy to say, but weaker and less distinctive |
| **Ralendo** | "ra-LEN-do" | Fully coined, easy to pronounce and spell, but further from the musical root and more conventionally startup-like | `.com`, `.app`, `.org`, `.pl`, and `.to` returned no registration object; `ralen.do` also returned no object; no exact GitHub repository name was found | Cleanest domain slate, but domain availability alone does not compensate for weaker meaning |

`inferred`: **Ralento** is the most appealing phonetic refinement and
`ralen.to` is the only explored domain hack that feels integral rather than
decorative. The existing `ralento.com` product and the fact that standard
Italian spells *rallento* with two `l` characters materially weaken it.
**Ralendo** has the cleanest preliminary namespace but the weakest semantic
anchor. Neither currently constitutes a clear overall improvement over
**Rallent**. If a derivative is promoted for deeper testing, **Ralento** should
be tested first for confusion with the existing brand and with Italian
spelling; `ralen.to` should be a redirect or campaign domain rather than the
only canonical address.

### Conceptually adjacent name exploration

`open` (2026-08-25): the maintainer requested names that preserve the idea
behind **Rallent** and **Ralento** without preserving their spelling. The search
therefore considered measured pace, calm reflection, voluntary resistance,
and gradual slowing. It retained easy Polish pronunciation and preliminary
namespace availability as screening criteria. This exploration does not alter
the maintainer-confirmed shortlist.

`observed` (2026-08-25): the first musical or tempo-derived pass exposed direct
collisions. **Cadento** is an active audio-pacing product for runners,
**Retempo** has an active application, and **Attento**, **Moderato**, **Atempo**,
**Rubato**, **Tenuto**, and **Calando** have materially crowded exact domain or
repository namespaces. These names should not be recycled merely because
their semantics fit.

| Name | Polish reading | Product rationale | Preliminary collision and domain result | Current assessment |
| --- | --- | --- | --- | --- |
| **Posato** | "po-ZA-to" | An attested Italian adjective for a calm, reflective person who acts without haste and shows balance | No exact US or Polish Apple application or GitHub repository name found; `.app`, `.org`, and `.pl` returned no registration object; `.com` is registered and offered for development; `posa.to` is registered | Strongest new conceptual candidate: short, warm, pronounceable, and close to the desired behavior rather than the blocking mechanism |
| **Misurato** | "mi-su-RA-to" in a Polish reading; Italian approximately "mi-zu-RA-to" | An attested Italian adjective for something measured, bounded, balanced, or well considered | No exact US or Polish Apple application or GitHub repository name found; `.app`, `.org`, `.pl`, and `misura.to` returned no registration object; `.com` is registered but did not present a same-name product | Strongest analytical candidate and best coherent domain hack; longer and less immediately spellable than **Posato** |
| **Pacato** | "pa-KA-to" | An attested Italian adjective for a calm, peaceful, composed manner, especially after agitation | No exact US or Polish Apple application or GitHub repository name found; `.app`, `.org`, and `.pl` returned no registration object; `.com` is offered for sale and `paca.to` is registered | Clean and easy, but risks framing the product as a generic calmness application rather than a tool for deliberate agency |
| **Volento** | "wo-LEN-to" or "vo-LEN-to" | A coinage joining volition or voluntary action with *lento*, directly encoding self-chosen slowing | No exact US or Polish Apple application or GitHub repository name found; `.app`, `.org`, and `volen.to` returned no registration object; `.com` and `.pl` are registered | Ideologically precise, but the `v` pronunciation split and existing exact-name web presence weaken spoken consistency and ownability |
| **Calando** | "ka-LAN-do" | An attested musical direction for gradually becoming softer and slower | No exact US or Polish Apple application name found, but exact repositories exist and `.com`, `.app`, `.org`, and `.pl` are registered; `calan.do` returned no registration object | Closest musical cousin to **Rallent**, but too crowded and too suggestive of fading away to recommend |

`inferred`: **Posato** and **Misurato** are the only names in this pass worth
deeper evaluation. **Posato** has the better spoken and emotional product
identity; **Misurato** has the sharper concept of measured limits and the
stronger `misura.to` hack. **Posato** is the current recommendation among the
new names, while **Misurato** is the more intellectually exact alternative.
Neither becomes a finalist without maintainer selection, and both still need
trademark, broader web, native-speaker, and registrar checks before adoption.

`user-confirmed` (2026-08-25): **Posato** is the only name retained from the
conceptually adjacent exploration. **Misurato**, **Pacato**, **Volento**, and
**Calando** remain research history rather than candidates for the next
comparison.

`user-confirmed` (2026-08-25): the final comparison treats **Kind Friction**,
**Rallent**, **Ralento**, and **Posato** as four separate proposals. **Ralento**
is no longer nested under the **Rallent** direction. The domain `ralen.to`
belongs to **Ralento** because it visually completes that spelling.

`observed` (2026-08-25): a normalized registry recheck found all four `.com`
domains registered. All four names returned no registration object under
`.app`, `.org`, `.dev`, `.pl`, and `.to`. The `.io` registry returned no object
for **Kind Friction**, **Rallent**, or **Ralento**, while `posato.io` is
registered. The `ralen.to` hack returned no registration object. Among the
registered `.com` domains, `ralento.com` serves an active safe-driving rewards
site, `posato.com` is offered for development, and the other two did not expose
an active same-name product in this check.

`inferred`: the advisor comparison weights product meaning and fit at 30%,
Polish pronunciation and spoken spelling at 20%, distinctiveness and collision
risk at 25%, domain practicality at 15%, and brand range at 10%. Scores use a
ten-point scale. The recommendation share is a judgmental allocation of 100
points, not a probability; it additionally treats an active exact-name
consumer collision as a gate-level risk rather than only a small linear score
deduction.

| Candidate | Recommended canonical domain | Fit (30%) | Polish usability (20%) | Ownability (25%) | Domains (15%) | Brand range (10%) | Weighted score | Recommendation share |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **Posato** | `posato.app` | 8.8 | 9.5 | 8.0 | 7.8 | 8.5 | **8.6/10** | **38%** |
| **Rallent** | `rallent.app` | 8.5 | 6.5 | 7.5 | 7.5 | 8.5 | **7.7/10** | **30%** |
| **Kind Friction** | `kindfriction.app` | 9.5 | 5.5 | 6.5 | 7.0 | 8.0 | **7.4/10** | **22%** |
| **Ralento** | `ralento.app` | 7.0 | 9.5 | 3.0 | 6.0 | 7.5 | **6.4/10** | **10%** |

`inferred` (2026-08-25): `.app` is the recommended canonical-domain pattern
for every proposal because it preserves the exact public spelling, clearly
identifies a consumer application, and avoids making a country-code hack the
only durable address. Registry RDAP returned no registration object for
`kindfriction.app`, `rallent.app`, `ralento.app`, or `posato.app` at the time of
the recheck. `ralen.to` remains a strong optional redirect for **Ralento**, not
its recommended canonical domain. Registrar availability, pricing, and
premium or reserved status still require confirmation before acquisition.

`inferred`: **Posato** is the current recommendation because it combines the
desired calm, reflective behavior with the easiest Polish usage and the
cleanest preliminary consumer-product namespace. **Rallent** is the strongest
fallback when coined distinctiveness and rhythmic character matter more than
spoken spelling. **Kind Friction** remains the clearest statement of product
philosophy but behaves more like a method name and is harder in Polish.
**Ralento** has the smoothest pronunciation and best domain hack, but its
active exact-name consumer collision and nonstandard Italian spelling make it
the weakest adoption recommendation pending legal clearance.

`user-confirmed` (2026-08-25): the maintainer selected **Posato** as the product
and display name and `posato.app` as the canonical public domain. **Posato** is
also the short fallback; no abbreviation or alternate spelling is introduced.
The accepted contract is recorded in
[product-identity.md](../../product/product-identity.md). The prior candidate
ranking and comparison directions are `superseded` as selection tools but
remain research history. They are not an accepted Gate 3 visual baseline or
final logo.

`observed`: the earlier registry screening found no `posato.app` registration
object at the time of the check. `user-confirmed` (2026-08-25): the maintainer
subsequently purchased and controls the domain and accepted `app.posato` plus
the component naming pattern, completing Gate 2. Trademark and broader release
clearance remain later readiness work rather than grounds to reopen the
accepted working identity automatically.

## Architecture baseline (complete)

`user-confirmed`: Kotlin Multiplatform and Compose Multiplatform are the product
direction. Shared product logic and orchestration are Kotlin-first. Apple APIs
sit behind small semantic interfaces. Swift is reserved for integration that
is materially less practical in Kotlin/Native. Apple framework types do not
cross into `commonMain`.

`user-confirmed` (2026-08-25):
[ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
accepts the initial `:shared`, `:desktopApp`, and `iosApp` graph, platform Metro
graphs, semantic interface and narrow `expect`/`actual` rules, iOS Device
Activity monitor extension, separate macOS native helper over IPC, target
identifiers, iOS 18.0 and arm64 macOS 15.0 baselines, reviewed wizard-import
boundary, and PR #1 toolchain-selection policy.

The maintainer wants to generate the initial skeleton with Compose
Multiplatform Wizard and apply the named `android-compose-engineering` skill to
agent-authored Compose Multiplatform code when available. The maintained
repository rules and ADR, not the locally installed skill or generator
defaults, remain durable authority.

`user-confirmed` (2026-08-25):
[ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
selects CloudKit Private Database, synchronizable-Keychain workspace-key
delivery, Apple Account/iCloud Keychain membership, common application E2EE,
explicit later portable membership, and one-active-transport migration.

The implementation language and privilege lifecycle of the macOS helper, the
iOS App Group schema and extension lifecycle details, persistence and
migration, concurrency ownership, production serialization, and exact later
dependency versions remain assigned to the named vertical pull requests in the
ADR. A separate broad architecture spike is not required.

## Engineering quality baseline

`user-confirmed` (2026-08-25): Gate 5 is complete. The accepted
[engineering quality contract](../../development/engineering-quality-contract.md)
and [repository task workflow](../../../tasks/README.md) establish:

- ktlint as the formatting and mechanical-style authority;
- Detekt as the static-analysis authority, with
  `io.nlopez.compose.rules` loaded through Detekt rather than duplicated in
  ktlint;
- warning-free repository-owned source, no new-code lint baselines, and narrow
  documented exceptions for unavoidable external or generated warnings;
- applicable unit, contract, integration, Compose UI, platform, simulator,
  physical-device, and manual verification chosen for the change rather than
  copied into an applicability matrix;
- Trivial self-checks, Standard completed-change review, and an additional
  brief plan review only for named High-risk work;
- just-in-time 20–40 line task briefs and concise execution records containing
  only the actual plan, result, review, checks, and blockers;
- five standing Definition of Done principles without per-task duplication;
- small task and pull-request boundaries, explicit dependency waves, isolated
  worktrees, and at most three concurrent implementation tasks initially; and
- proportional dependency, license, security, privacy, provenance, and PoC
  reuse review without claiming release readiness.

Gate 5 defines the CI outcome and credential boundary but does not configure a
pipeline. CI is a separate Gate 6 task due before PR #1 merges or the first
parallel implementation wave starts, whichever occurs first. Until then,
fresh local verification evidence is mandatory.

`observed` (2026-08-26): PR #1 now exposes `./gradlew quality` as the local
aggregate boundary. It pins ktlint Gradle plugin 14.2.0 with ktlint 1.8.0,
Detekt 2.0.0-alpha.6, and Compose Rules 0.6.4; generates ktlint and Detekt
reports; runs shared JVM and desktop tests; compiles both iOS targets with
warnings as errors; and creates the macOS distributable. A controlled invalid
composable was rejected by the Compose `ModifierMissing` rule. Generated
Compose Resources Kotlin is excluded from ktlint and no baseline exists.

`user-confirmed` (2026-08-26): the Detekt prerelease is a narrow build-time
exception because no stable Detekt release supports the accepted Kotlin 2.4.10
compiler metadata. The first compatible stable Detekt 2 release is the removal
trigger. An explicit `.research/blocker` search found only compiler
warnings-as-errors evidence, not a production-quality lint or aggregate gate
configuration to reuse.

## Gate 6 roadmap (complete)

`user-confirmed` (2026-08-25): the accepted
[MVP roadmap](../../../tasks/mvp-roadmap.md) retains 36 outcome stubs: one
manual Gate 7 task, three PR #1 milestones, 31 later tasks through physical MVP
acceptance, and one separate release-readiness task.

The roadmap keeps all work serialized until credential-free CI and the one
completed-change review for PR #1 pass. The three PR #1 milestones share one
brief and execution cycle. Later waves expose only concurrency candidates;
exact plans must still prove disjoint write surfaces, frozen contracts,
isolated worktrees, and integration order when activated. Dedicated decision
tasks own the threat model, diagnostics policy, macOS helper
lifecycle, macOS browser support, encrypted-operation contract, and Apple
workspace bootstrap before implementation consumers start.

The disposable [MVP interaction prototype](../sources/mvp-interaction-prototype.md)
is routed as bounded UX evidence for session and onboarding tasks. Its Free
play behavior, fixtures, geometry, and web implementation do not change the
accepted [DESIGN.md](../../../DESIGN.md) contract or become production
requirements.

Roadmap revision 2 is accepted. Future work remains as stubs until a concise
brief is needed. Gates 6 and 7 are complete, but this does not authorize
application scaffolding before the separate Ready to open PR #1 checkpoint is
explicitly accepted.

## Apple identity and distribution

The accepted architecture supplies the target graph. After Gate 6 acceptance,
Gate 7 task `APPLE-001` registers and verifies the Apple resources required by
the roadmap:

- development team and signing ownership;
- four accepted application, helper, and extension bundle identifiers;
- the App Group and CloudKit container plus the accepted public Keychain
  access-group suffix; and
- development-capability availability, resource associations, and explicit
  blockers for later physical tasks.

The maintainer performs these checks manually in Apple Developer and CloudKit
Console, with Xcode used only to confirm intended-team visibility before target
scaffolding. Work proceeds one resource at a time. Credentials and private
account values stay out of Git; no preflight script, parser, wizard, or local
configuration layer is required.

`user-confirmed` (2026-08-26): the corrected pre-scaffold Gate 7 boundary
registers six external resources: four explicit App IDs, one App Group, and one
CloudKit container. `app.posato.sync` remains the accepted public Keychain
access-group suffix but is configured later in target entitlements rather than
registered as a separate portal resource. APPLE-001 verifies resources and
capability associations in Apple Developer, the container in CloudKit Console,
and only intended-team visibility in Xcode. Target entitlements, signing, and
development profiles are verified by the tasks that configure the owning
application packaging paths; they do not block the credential-free PR #1
skeleton.

The source boundary was rechecked against Apple's current guidance for
[adding target capabilities](https://developer.apple.com/documentation/xcode/adding-capabilities-to-your-app),
[configuring Keychain Sharing](https://developer.apple.com/documentation/xcode/configuring-keychain-sharing),
[the Keychain Access Groups entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/keychain-access-groups),
[enabling App ID capabilities](https://developer.apple.com/help/account/identifiers/enable-app-capabilities/),
[supported iOS capabilities](https://developer.apple.com/help/account/reference/supported-capabilities-ios/),
[supported macOS capabilities](https://developer.apple.com/help/account/reference/supported-capabilities-macos/),
[creating development profiles](https://developer.apple.com/help/account/provisioning-profiles/create-a-development-provisioning-profile/),
[signing externally built macOS code](https://developer.apple.com/documentation/xcode/creating-distribution-signed-code-for-the-mac/),
and [enabling CloudKit](https://developer.apple.com/documentation/cloudkit/enabling-cloudkit-in-your-app).

`user-confirmed` (2026-08-26): the current App ID capability list exposed no
entry whose name contains `Keychain`, and no substitute portal capability was
selected. APPLE-001 therefore enumerates its required portal capabilities as
Family Controls development, App Groups, and iCloud/CloudKit. No Keychain
portal action is part of Gate 7. The exact public `app.posato.sync` suffix is
retained for later application configuration. `SYNC-005` owns iOS Keychain
Sharing target configuration, provisioning authorization, and signed-
entitlement verification. Because the accepted macOS host is Compose
Desktop/JVM rather than an Xcode target, `SYNC-006` owns the equivalent
external-build entitlement, provisioning, signing, and verification path.
Neither later check blocks the credential-free PR #1 skeleton.

Notarization, App Store or direct-distribution strategy, update delivery,
privacy disclosures, and public support readiness belong to the separate
`RELEASE-001` review. They are not inferred from Gate 7 or MVP behavior.

No PoC development identifier should be reused automatically.

## Enforcement product choices

The roadmap assigns these decisions to `MACOS-001`, `MACOS-002`,
`TARGETS-003`, `TARGETS-004`, `MACOS-003` through `MACOS-005`, and `IOS-001`
through `IOS-002`. Their roadmap stubs are accepted, but the product and
architecture questions remain open until just-in-time briefs and decision
tasks record maintainer-approved authorities.

- Which accepted blocking capability belongs in the first enforcement slice?
- Which macOS browsers are supported, and is browser presentation part of the
  promise or an optional enhancement?
- What privilege, persistence, coexistence, recovery, and uninstall behavior is
  acceptable for the macOS helper?
- How should proxy conflicts, VPNs, network-service changes, and captive portals
  be handled?
- Which iOS authorization and entitlement path is viable for public
  distribution?
- `user-confirmed`: opaque iOS application selections remain local and attach
  to a synchronized semantic policy. The invalid-selection and remapping
  lifecycle remains open.
- `user-confirmed`: an MVP session has a simple, deliberate early-termination
  flow and does not claim administrator resistance. The later UX friction and
  exact supported-browser contract remain open.

## Synchronization product choices

The roadmap assigns the Apple MVP questions below to `SYNC-001` through
`SYNC-012` and `ONBOARDING-001` through `ONBOARDING-002`. Later portable-mode
questions remain outside the MVP task graph.

- `user-confirmed`: Apple synchronization is part of the MVP. Exact domain
  policy, semantic application policy, and active-session intent synchronize;
  schedules are later and opaque application selections remain local.
- `user-confirmed` (2026-08-25): each Apple installation exposes one
  **Sync with iCloud** action. Posato uses no QR or cross-device approval;
  CloudKit Private Database transports common encrypted payloads, Keychain
  delivers the workspace key, and Apple trust admits the device.
- How should Apple-mode signed authors register automatically without
  introducing a separate Posato device-approval ceremony?
- How should delayed-Keychain, account-change, reset, partial-loss, and retry
  states behave while preserving the one-workspace invariant?
- `user-confirmed`: the later portable folder is an untrusted mailbox and uses
  independent device keys, explicit membership, QR approval, per-device
  wrapping, signed membership operations, key epochs, revocation, and optional
  recovery.
- What authenticated completeness, rollback, fresh-replica, provider, recovery,
  revocation, export, deletion, and migration contracts does portable mode
  require?
- Which metadata may remain visible to the transport?
- What delivery and offline behavior can the product honestly promise?

## Security, privacy, and public readiness

The roadmap assigns the MVP threat model to `SECURITY-001`, diagnostics policy
to `DIAGNOSTICS-001`, task-local security and privacy evidence to every
applicable consumer, and the separate pre-release audit to `RELEASE-001`.

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
are known. The accepted roadmap makes Gate 7 `APPLE-001` and groups
`FOUNDATION-001`, `QUALITY-001`, and `CI-001` into one PR #1 brief,
execution record, and completed-change review. Everything not required by PR
#1 or its immediate dependants is decided as late as the corresponding small
task requires it.

`user-confirmed`: PR #1 creates fresh production modules, uses accepted target
identifiers, runs one minimal shared Compose screen on macOS and iOS,
introduces small semantic platform contracts with fakes, and establishes
baseline tests and CI. Gate 5 does not configure CI; Gate 6 retains CI as a
milestone in the shared PR #1 cycle that completes before PR #1 merges or
parallel implementation begins. PR #1 implements neither blocking nor
synchronization.

`user-confirmed` (2026-08-26): APPLE-001's registered resources,
non-Keychain portal capabilities, associations, team visibility, and retained
Keychain suffix all pass. Gate 7 is complete without claiming Keychain
functionality, provisioning authorization, or signed entitlements. The next
handoff is the still-blocking Ready to open PR #1 checkpoint.
