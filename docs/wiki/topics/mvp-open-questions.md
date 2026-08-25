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
execution authority for this milestone. Gate 1 is complete in the accepted
[MVP scope](../../product/mvp-scope.md). The immediate next work is product
identity. The sections below retain the decisions each gate must resolve and
the questions that may remain incremental.

## Gate 2: product identity

`open`: accept the product's working identity before registering durable
external identifiers.

- Working product name.
- Display name and one short fallback.
- Stable reverse-DNS namespace and naming scheme for applications, helpers,
  and extensions.
- Dated name, repository, and domain collision checks.
- A distinction between changeable public branding and identifiers whose
  migration would be expensive.

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

`observed`: registry screening found no current `posato.app` registration
object. `open`: registrar purchase and maintainer control have not been
verified, and the stable reverse-DNS namespace and naming pattern remain the
last product-identity decisions required for Gate 2. Trademark and broader
release clearance remain later readiness work rather than grounds to reopen
the accepted working identity automatically.

## Architecture baseline

`user-confirmed`: Kotlin Multiplatform and Compose Multiplatform are the product
direction. Shared product logic and orchestration are Kotlin-first. Apple APIs
sit behind small semantic interfaces. Swift is reserved for integration that
is materially less practical in Kotlin/Native. Apple framework types do not
cross into `commonMain`.

`user-confirmed` (2026-08-25):
[ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
selects CloudKit Private Database, synchronizable-Keychain workspace-key
delivery, Apple Account/iCloud Keychain membership, common application E2EE,
explicit later portable membership, and one-active-transport migration.

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
- CloudKit container and environment strategy for the accepted synchronization
  scope;
- app-group, Keychain access-group, helper, and extension identifiers;
- entitlement request and fallback plan;
- notarization, App Store, direct distribution, and update strategy;
- privacy disclosures and data-handling declarations.

No PoC development identifier should be reused automatically.

## Enforcement product choices

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

- `user-confirmed`: Apple synchronization is part of the MVP. Exact domain
  policy, semantic application policy, and active-session intent synchronize;
  schedules are later and opaque application selections remain local.
- `user-confirmed` (2026-08-25): each Apple installation exposes one
  **Sync with iCloud** action. Blocker uses no QR or cross-device approval;
  CloudKit Private Database transports common encrypted payloads, Keychain
  delivers the workspace key, and Apple trust admits the device.
- How should Apple-mode signed authors register automatically without
  reintroducing Blocker-level device approval?
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
