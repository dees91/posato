# MVP Open Questions

This page is the decision queue for starting product code. It separates gates
for the first MVP pull request from questions that can remain incremental.

## Current handoff

`user-confirmed` (2026-08-24): the next milestone is the first pull request
containing production application code. Seven gates precede it: MVP scope,
minimum product identity, minimum product and design baseline, architecture
baseline, engineering quality contract, vertical PR decomposition, and manual
Apple resource setup.

The active [preparation plan](../../tasks/first-mvp-pr-preparation-plan.md)
and [checklist](../../tasks/first-mvp-pr-preparation-todo.md) are the
execution authority for this milestone. Gates 1 through 6 are complete in the
accepted [MVP scope](../../product/mvp-scope.md) and
[product identity](../../product/product-identity.md), with the accepted brand
and product design authority in [DESIGN.md](../../../DESIGN.md) and the
accepted architecture in
[ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md).
The engineering quality baseline is also accepted in the
[quality contract](../../development/engineering-quality-contract.md) and
[task workflow](../../tasks/README.md). The
[Gate 6 MVP roadmap](../../tasks/mvp-roadmap.md) is accepted at revision 2
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

`user-confirmed` (2026-08-26):
[ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
selects the macOS Swift session-helper and root proxy-settings-daemon split,
Service Management lifecycle, authenticated IPC boundaries, exact privileged
apply right, atomic proxy ownership, and supported recovery and removal
contract. Concrete schemas and packaging remain with MACOS-003. The iOS App
Group schema and extension lifecycle details, persistence and migration,
concurrency ownership, production serialization, and exact later dependency
versions remain assigned to their named vertical pull requests. A separate
broad architecture spike is not required.

## Engineering quality baseline

`user-confirmed` (2026-08-25): Gate 5 is complete. The accepted
[engineering quality contract](../../development/engineering-quality-contract.md)
and [repository task workflow](../../tasks/README.md) establish:

- ktlint as the formatting and mechanical-style authority;
- Detekt as the static-analysis authority, with
  `io.nlopez.compose.rules` loaded through Detekt rather than duplicated in
  ktlint;
- warning-free repository-owned source, no new-code lint baselines, and narrow
  documented exceptions for unavoidable external or generated warnings;
- applicable unit, contract, integration, platform, simulator,
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

Gate 5 defines the CI outcome and credential boundary. `observed` (2026-08-26):
PR #1 now contains a GitHub Actions workflow that runs the aggregate gate and
a signing-disabled iOS Simulator host build on the arm64 `macos-15` runner.
The workflow selects Temurin 21 and Xcode 26.3 explicitly, grants read-only
repository access, disables checkout credential persistence, and pins its
GitHub-maintained actions to immutable commits. Current official runner-image
inventory lists Xcode 26.3 on that image; this stays within Kotlin 2.4.10's
documented Xcode compatibility ceiling.

`observed` (2026-08-26): PR #1 is open in a private GitHub repository and its
first hosted `Quality` job passed in 6 minutes 52 seconds. The run exercised
the aggregate gate, the signing-disabled iOS Simulator host build with Xcode
26.3, and report upload on the selected arm64 `macos-15` runner. `CI-001` is
complete without introducing signing material or an application credential.

`observed` (2026-08-26): PR #1 now exposes `./gradlew quality` as the local
aggregate boundary. It pins ktlint Gradle plugin 14.2.0 with ktlint 1.8.0,
Detekt 2.0.0-alpha.6, and Compose Rules 0.6.4; generates ktlint and Detekt
reports; retains shared JVM and desktop test tasks for future behavior-bearing
logic; compiles both iOS targets with warnings as errors; and creates the macOS
distributable. The current static shell has no automated tests. A controlled
invalid composable was rejected by the Compose `ModifierMissing` rule.
Generated Compose Resources Kotlin is excluded from ktlint and no baseline
exists.

`observed` (2026-08-27): repeated manual drift established a consumer for one
repository-owned ktlint rule. `posato:rhs-on-assignment-line` rejects a value
starting below `=` when its first line fits the configured 150-character
limit. Public-rule-engine tests cover declarations, assignments, named
arguments, defaults, expression bodies, comments, over-limit values, and the
standard-required multiline raw-string layout while still rejecting a fitting
single-line raw string below `=`; a controlled source probe confirmed that the
shared aggregate ktlint task fails on the rejected form.

`user-confirmed` (2026-08-26): pre-stabilization UI tests that assert static
copy, rendering, theme-token mapping, or shell wiring are maintenance burden
without protecting important product logic. They are excluded through the MVP;
UI changes use platform builds and proportionate manual inspection. Golden
testing, with Paparazzi named as a possible approach, remains a separate
post-MVP decision.

`user-confirmed` (2026-08-26): the Detekt prerelease is a narrow build-time
exception because no stable Detekt release supports the accepted Kotlin 2.4.10
compiler metadata. The first compatible stable Detekt 2 release is the removal
trigger. An explicit `.research/blocker` search found only compiler
warnings-as-errors evidence, not a production-quality lint or aggregate gate
configuration to reuse.

`user-confirmed` (2026-08-26): pull requests use human inline comments as the
primary feedback channel, followed by a manually requested `@codex review` as
an additional independent pass. The local implementation agent owns accepted
corrections and verification; the reviewer controls thread resolution and the
maintainer owns merge. Automatic AI review remains disabled until manual runs
demonstrate useful signal without recurring noise.

`observed` (2026-08-26): the first manual hosted Codex review examined PR #1
at commit `e4eb8f4`, reported no major issue, and added no inline finding. The
same commit passed the hosted `Quality` job in 4 minutes 39 seconds. Automatic
AI review remained disabled; the maintainer still owns the merge decision.

`superseded` (2026-08-27): the 2026-08-26 correction reserved hosted GitHub
Codex review for the latest substantive code or configuration change and made
documentation-only closeout non-invalidating. It stopped documentation loops
but still allowed repeated hosted passes after substantive corrections; the
bounded rule below replaces it. Meaningful documentation continues to receive
its proportional human or local independent review.

`user-confirmed` (2026-08-27): repeated one-finding hosted reviews created an
unbounded correction cycle and repeatedly consumed the private repository's
limited GitHub Actions minutes. A pull request now receives at most one manual
hosted `@codex review` by default, after local verification, proportional
independent review, and versioned task records are complete. Accepted Critical
or Required findings are corrected and rechecked locally without another
hosted pass; P2 and lower findings remain advisory unless explicitly accepted
by the maintainer. Routine post-review bookkeeping stays in the pull-request
conversation rather than creating another commit and CI run.

`superseded` (2026-08-27): draft pull requests allocate no runner; moving
one to ready for review triggers CI. The full credential-free macOS gate then
runs for every pull request containing a non-Markdown file and every push to
`main`. Markdown-only review-ready pull requests run a cheap whole-diff
classification and skip macOS while retaining reported job results.
Classification fails closed to macOS. The classifier intentionally examines
the whole pull request, not only the latest push, so a quick documentation
follow-up cannot cancel and replace verification of earlier substantive
changes.

`superseded` (2026-08-28 through 2026-09-05): automatic pull-request and
`main` push triggers are temporarily paused after the account exhausted its
included Actions minutes. A fresh local aggregate quality pass is the accepted
temporary merge gate, and the complete workflow remains available by manual
dispatch. The 2026-09-07 manual-only decision below replaces the proposed
restoration of automatic triggers.

`user-confirmed` (2026-09-07, latest decision): GitHub CI is disabled and the
workflow source removed. Local `./gradlew quality` and required review replace
the same day's hosted-check requirement. `main` retains a PR requirement,
administrator enforcement, and force-push/deletion restrictions, but no required
status check or strict up-to-date setting. Local success is procedural rather
than server-enforced; restoring CI needs an explicit maintainer decision. The
[quality contract](../../development/engineering-quality-contract.md#continuous-integration)
and [development checklist](../../development/README.md#local-quality-and-merge-check)
own the current rule. The aggregate gate now also executes simulator-compatible
Swift XCTest; physical-device skips remain explicit.

## Gate 6 roadmap (complete)

`user-confirmed` (2026-08-25): the accepted
[MVP roadmap](../../tasks/mvp-roadmap.md) retains 36 outcome stubs: one
manual Gate 7 task, three PR #1 milestones, 31 later tasks through physical MVP
acceptance, and one separate release-readiness task.

The roadmap keeps all work serialized until credential-free CI and the one
completed-change review for PR #1 pass. The three PR #1 milestones share one
brief and execution cycle. Later waves expose only concurrency candidates;
exact plans must still prove disjoint write surfaces, frozen contracts,
isolated worktrees, and integration order when activated. Dedicated decision
tasks own the threat model, macOS helper lifecycle, macOS browser support,
encrypted-operation contract, and Apple workspace bootstrap before
implementation consumers start. The accepted diagnostic policy now constrains
every producing task without creating a shared implementation in advance.

The disposable [MVP interaction prototype](../sources/mvp-interaction-prototype.md)
is routed as bounded UX evidence for session and onboarding tasks. Its Free
play behavior, fixtures, geometry, and web implementation do not change the
accepted [DESIGN.md](../../../DESIGN.md) contract or become production
requirements.

Roadmap revision 3 is accepted. Its 2026-08-31 amendment makes `SESSION-001`
the first design-system consolidation checkpoint without adding another task
or changing dependencies, waves, or integration groups. Future work remains as
stubs until a concise brief is needed. Gates 6 and 7 are complete, but this does
not authorize application scaffolding before the separate Ready to open PR #1
checkpoint is explicitly accepted.

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

`user-confirmed` (2026-08-28): SYNC-003 later expands the accepted graph with
one dedicated macOS synchronization companion, `app.posato.macos.sync`, because
the Compose Desktop JVM host cannot own CloudKit or Keychain and the enforcement
helper must not receive synchronization secrets or entitlements. SYNC-003 owns
registration of this seventh external resource, enables iCloud/CloudKit, and
associates only the existing `iCloud.app.posato.sync` container. It creates no
new App Group or container. Target entitlements, provisioning, packaging,
signing, and signed-artifact verification remain with `SYNC-006` and `SYNC-008`.

`user-confirmed` (2026-08-28): the App ID registration, iCloud/CloudKit
capability, and association with `iCloud.app.posato.sync` passed manual
inspection. This result verifies only the public resource and association; it
does not verify a target, entitlement, profile, signed artifact, deployed
schema, or physical synchronization behavior.

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

MACOS-001 is complete in
[ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md), and
MACOS-002 is complete in
[ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md).
The accepted first macOS website slice uses exact-domain network denial for
Safari and Chrome Stable on ports 80 and 443, plus a separate fixed same-tab
presentation contract. Existing proxy automation and detected routing overlays
are incompatible; captive portals are unsupported; and sleep, wake, or primary-
service change restores before an explicit Retry. MACOS-004 must still prove
the complete automated and physical release matrix.

The roadmap assigns the remaining decisions to `TARGETS-003`, `TARGETS-004`,
`MACOS-003` through `MACOS-005`, and `IOS-001` through `IOS-002`. Their roadmap
stubs are accepted, but the remaining product and architecture questions stay
open until just-in-time briefs and decision tasks record maintainer-approved
authorities.

- What exact IPC schemas, daemon and Mach identifiers, launchd policy, embedded
  layout, and release packaging implement the accepted helper contract?
- Which iOS authorization and entitlement path is viable for public
  distribution?
- `user-confirmed`: opaque iOS application selections remain local and attach
  to a synchronized semantic policy. The invalid-selection and remapping
  lifecycle remains open.
- `user-confirmed`: an MVP session has a simple, deliberate early-termination
  flow and does not claim administrator resistance. The later UX friction and
  implementation evidence remain open.

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
- `user-confirmed` (2026-08-28): ADR 0006 uses workspace-key possession,
  a process-memory Ed25519 authoring incarnation, and an encrypted self-signed
  registration created atomically with the first business operation, without a
  Posato approval ceremony or persistent Apple signing identity.
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
- `user-confirmed` (2026-08-26): ADR 0004 accepts fixed signed peers,
  per-operation authorization, bounded versioned IPC, one-use Apply authority,
  minimal root ownership, and durable repeatable recovery for the macOS helper
  attack surface. MACOS-003 owns implementation evidence.
- `user-confirmed` (2026-08-26): the accepted
  [diagnostics and support-data policy](../../security/diagnostics-and-support-data.md)
  defines local opt-in capture, retention, redaction, user-controlled export,
  producer verification, and the no-remote-collection boundary.
- Product privacy policy and user data export or deletion behavior.
- Dependency, source-code, asset, and third-party license audit.
- Repository license, contribution policy, security contact, and public support
  boundary.

## Post-MVP session usability proposals

`user-confirmed` (2026-09-12, extended 2026-09-15): retain these improvements
for planning in the next iteration after the MVP. A bullet records any later
accepted solution; the remaining proposals do not expand MVP scope or change
accepted contracts.

- **Make website coverage easier to understand and configure.** Exact-host
  matching treated `example.com` and `www.example.com` as separate entries;
  redirects could therefore lead to a host outside the selected set.
  `user-confirmed` (2026-09-18): one stored row is what the person typed;
  `www` equivalence is a matching rule, not data. Broader subdomain coverage
  remains `open`. The
  [ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md)
  clarification was accepted in PR #72.
- **Reduce repeated macOS authorization prompts at session start.** Evaluate
  a one-time administrator opt-in for subsequent session starts, with an
  explicit revocation path and authenticated, narrowly scoped helper requests.
  Background-helper approval and permission to apply proxy settings are
  separate today. Replacing fresh one-use Apply authorization requires an
  explicit revision of
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  and review of the security implications; this note does not grant persistent
  authorization or choose its implementation.
- **Keep website entry continuous and its count accurate in onboarding.**
  `user-confirmed` (2026-09-15): after typing a website in the first-website
  step and pressing Enter, the field loses focus, so adding another website
  needs a new click; after adding two websites one at a time, the step shows
  "1 added". `observed` in code: the step clears focus after every submission
  with at least one added website, overriding the entry field's own focus
  request, and the supporting text reports only the last submission's count
  rather than the saved total. Decide how onboarding keeps entry focused while
  still revealing its continue action, and which count it states.
  `user-confirmed` (2026-09-23, `ONBOARDING-003`): entry keeps focus after
  each submission; a separate caption states the saved total from policy
  state and follows policy changes on the website step. Expanded pages scroll
  their content above the actions, so **Continue** stays above the keyboard.
- **Make website and app editing discoverable from the Session screen.**
  `user-confirmed` (2026-09-15): people look for adding or editing websites and
  apps on the Session screen, where the paused items are summarized, and take
  a while to recall that editing lives under Paused items. Evaluate a direct
  route from that summary to editing, within the accepted two-destination
  navigation in [DESIGN.md](../../../DESIGN.md).
  `user-confirmed` (2026-09-16): the search field in the Session selected-items
  browser repeatedly draws that intent, because a text field on this screen
  reads as a place to add a website. `observed` in code: that field only
  filters the read-only list (`SessionSelectionSummary.kt`). The maintainer
  reports this as a recurring frustration rather than a one-time slip, so treat
  it as the strongest candidate in this section. `user-confirmed`
  (2026-09-21, `SESSION-004`): keep the summary disclosures as a read-only
  preview, add category-specific routes to Paused items from the summary and
  browser, and make the website field read as a list-only filter. The two
  primary destinations remain unchanged. `user-confirmed` (2026-09-21, PR #71
  review): call the app route Manage apps because the system picker is a second
  action in Paused items; explain the frozen start set before its summary and
  reveal the list-only website filter through a quiet action.
- **Arrange onboarding actions better on macOS.** `user-confirmed`
  (2026-09-15): on the Mac helper permission step, **Open System Settings**,
  **Check again**, and **Not now** stack vertically at their own content
  widths, which looks uneven while the window leaves ample horizontal space.
  `observed` in code: expanded layouts size the primary onboarding action to
  its content and place every action in one column. Evaluate a horizontal
  arrangement or consistent widths for expanded layouts, keeping the compact
  full-width primary button and the DESIGN.md action hierarchy.
  `user-confirmed` (2026-09-23, `ONBOARDING-003`): expanded onboarding steps,
  including iCloud, place their actions in one wrapping action row in
  primary, secondary, quiet order; compact layouts are unchanged.

## Post-MVP feature ideas for discovery

`user-confirmed` (2026-09-13): retain the following larger, loosely defined
ideas for future iterations after the MVP. Unresolved choices remain `open`;
accepted later decisions link to their product authority and roadmap owners
below. This queue retains idea provenance without expanding the original MVP.

1. **Recurring session schedules.** `user-confirmed` (2026-09-26, PR #92):
   release 1.2 includes both `SCHEDULE-001` and `SCHEDULE-002`, delivering
   shared plans on Mac and iPhone with local offline execution. The accepted
   [product scope](../../product/schedules-and-mac-setup.md) records simple
   weekday/time plans, skipping, early end, Mac catch-up and local readiness.
   `SCHEDULE-001` still owns time zones, conflicts, convergence, platform
   limits and the security-reviewed authorization amendment. Scheduling was
   deferred from the original MVP; this is its accepted later release.
2. **Family controls for children's websites and applications.** Explore a
   parent/child use case. Device ownership, consent, access boundaries, and
   privacy need a separate product decision; the current personal-use model
   does not establish a family-control contract.
3. **Ad blocking.** Explore whether reducing advertising belongs in Posato
   and what coverage would be useful and feasible. No filtering mechanism or
   effectiveness claim is selected.
4. **Review Focusly for useful ideas.** Assess which interactions or features
   from the maintainer-provided
   [Focusly Chrome Web Store listing](https://chromewebstore.google.com/detail/focusly/ipkamplfnlmbpgmhbdfcbajjmcnfmghj)
   fit Posato. This is a future comparative-research task, not approval to
   copy the extension or adopt its advertised capabilities. The extension
   has not been installed or independently tested for this note.
5. **Reduce distractions within YouTube.** Explore hiding Shorts,
   recommended videos, and similar distracting surfaces while keeping useful
   video access. Browser versus native-app coverage and a suitable mechanism
   remain undecided; exact-host blocking alone does not define this behavior.
6. **Local session notifications on iOS and macOS.** Explore on-device
   notifications when a session starts or ends. Notification preferences,
   permission flow, and delivery behavior remain for post-MVP discovery.
7. **macOS menu bar presence without an open main window.** `user-confirmed`
   (2026-09-14): explore a status-bar menu, similar to Tunnelblick, from which
   a person can see the current session, start or end one, and open the full
   window only when needed, so that sessions keep working without the desktop
   window staying open. Today the Compose Desktop application owns policy and
   orchestration, and the privileged daemon holds a renewable ownership lease
   under
   [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md),
   so blocking stops when Posato quits. `user-confirmed` (2026-09-26):
   [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md) keeps the
   existing application running as a menu bar process after its window
   closes, with launch at login as an opt-in. `MACOS-013` delivers it.
8. **Intel Mac support, starting with a 2019 MacBook Air.** `user-confirmed`
   (2026-09-14): explore running Posato on the maintainer's 2019 Intel MacBook
   Air. `source-claim`: Apple lists macOS Sequoia (15) for MacBook Air models
   from 2020 or later, so that model tops out at macOS Sonoma (14). Support
   therefore needs two changes to
   [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md),
   which accepts only arm64 on macOS 15 or later: an x86-64 (or universal)
   build and a macOS 14 deployment target. Discovery must cover the bundled
   Java runtime and native libraries per architecture, the Swift helpers,
   packaging and notarization for both architectures, macOS 14 API
   availability, and a physical test device in the release matrix.
9. **In-app updates for macOS.** `user-confirmed` (2026-09-14): explore
   delivering new macOS versions from inside Posato, for example with Sparkle,
   after `MACOS-008` chose a manual download of each notarized build without
   an updater. Discovery must cover the update feed and its hosting, signing
   of update archives, how Posato checks for updates without weakening the
   no-telemetry promise in [`PRIVACY.md`](../../../PRIVACY.md), and the
   supported update path in
   [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md),
   which restores and verifies proxy settings and helper registration before
   an update replaces the application.
   `user-confirmed` (2026-09-22): `MACOS-010` selects Sparkle, opt-in checks,
   GitHub Releases, and installation after the session ends in
   [ADR 0008](../../decisions/0008-macos-update-delivery.md). It includes the
   unchanged-registration clarification, proposed public wording, and the
   cancellation/recovery proof required before separately activated
   `MACOS-011` delivery. No updater is implemented by the discovery.

10. **Verification without the maintainer.** `user-confirmed` (2026-09-23):
    an agent verifies every task on its own, and the maintainer only helps
    with one-time setup. The two targets are Posato on macOS in Tart virtual
    machines on the supported Mac, and Posato on a dedicated physical test
    iPhone. Both sign in with a dedicated test Apple Account, whose CloudKit
    private data is separate from the maintainer's. `inferred` from the
    `MACOS-011` Stage 1 discussion:
    - Keyboard and pointer input over the VM's VNC display counts as hardware
      input to the guest. It can drive SecurityAgent, System Settings,
      Gatekeeper, and Sparkle without loosening the ADR 0004 authorization
      rules.
    - A golden VM image keeps the one-time approvals.
    - A second VM gives a Mac-to-Mac sync peer.
    - On the iPhone, the XCUITest driver can reach SpringBoard alerts. It can
      tap the out-of-process application picker by screen coordinates,
      located with text recognition.
    - Development builds use the CloudKit Development environment, which can
      be reset.
    - Credentials stay in the host Keychain and are read at run time.

    `open` go/no-go measurements:
    - CloudKit and iCloud Keychain for the Developer ID sync companion inside
      a VM;
    - Screen Time consent and any passcode prompt through XCUITest;
    - coordinate taps in the picker;
    - deterministic VNC control;
    - the network-service-switch scenarios with the VM's single network
      interface.
11. **A useful moment on the pause page.** `user-confirmed` (2026-09-23):
    explore offering something worthwhile when a person reflexively opens a
    paused website instead of an empty pause page, such as flashcards or a
    short learning prompt. The motivating case is a developer who hands work
    to an agent and, while waiting for it, opens a distracting site out of
    habit. `inferred` constraints:
    - The macOS pause page is a local page served by the helper. It stays
      self-contained and free of attempted targets (`DESIGN-003`), fetches
      nothing remote, and records no browsing.
    - The iOS Screen Time shield can only change its icon, title, subtitle,
      and two buttons, so iOS parity is limited to text or a hand-off to the
      app.
    - Content could come from user-provided decks, synchronized like other
      data, rather than built-in material.
    - Lighter options could come first: the session's stated intention, the
      time remaining, or one short prompt.

    Whether review progress may be recorded without revealing when paused
    sites were attempted remains `open`.

    `user-confirmed` (2026-09-25) direction: the pause page must be useful
    with no setup; cards are an optional layer; an agent is only one way to
    supply them. Posato presents cards and never teaches or generates
    personal content on its own. One open deck format (question, options or
    answer, explanation, optional source link) serves every layer:
    - **Everyone, no setup:** the session's stated intention with the time
      remaining, a one-line "save for later" note returned after the session,
      and light built-in prompts such as standing up for a minute.
    - **Learners without technical tools:** an in-app question-and-answer
      editor as simple as adding websites, import from CSV and Anki
      (`inferred`: `.apkg` is a zipped SQLite database readable locally),
      and possibly built-in starter decks, whose content, licensing, and
      translation cost make them later or never.
    - **Personalized without an agent:** Posato offers a prompt to copy into
      any chatbot with one's notes and validates the pasted result, and
      possibly on-device generation from shared text (`source-claim`: Apple
      Foundation Models on iOS and macOS 26, quality to confirm).
    - **Developers:** a watched folder where a learning agent, such as a
      `teach` skill workspace, writes decks and reads back results, so the
      agent picks the next cards. It reuses the same importer.

    Suggested order: intention, time remaining, and "save for later" first;
    then cards from the editor, CSV, Anki, and the chatbot prompt, with the
    watched folder; spaced review only after the privacy decision; a text
    card in the iOS shield last. `hypothesis`: a shield button could toggle
    between question and answer through shared app-group state. A
    "waiting for an agent" hook that starts a short session when work is
    handed to a coding agent would need a new local way to start sessions
    and its own product decision. No server, product account, or remote
    content: the privacy boundary stays unchanged.
12. **Polish as the first additional language.** `user-confirmed`
    (2026-09-24):
    - Posato follows the system language, with no in-app switch. People can
      still choose a per-app language in the system settings.
    - The first Polish version covers the whole UI of both applications with
      Polish plural forms, the macOS pause page, and the iOS permission
      descriptions. It also covers date and time formatting, the App Store
      listing and screenshots, and a Polish posato.app including the privacy
      policy.
    - The agent drafts the Polish copy and the maintainer, a native speaker,
      approves it. This needs a narrow `AGENTS.md` exception for localized
      product resources; the rest of the repository stays in English.

    `inferred` from the current code:
    - The English strings live in the shared Compose resources, which have no
      plurals.
    - The pause page is rendered by the Swift helper.
    - Driver recipes select elements by English labels, which matters for
      `QUALITY-010`.
13. **Navigation 3 and system back gestures.** `user-confirmed` (2026-09-22,
    the `IOS-004` decision): adopt Navigation 3 with system back gestures as
    a separate task. `inferred`:
    - The applications already have screen stacks, such as About Posato to
      Licenses to a license text, that return only through explicit **Back**
      actions.
    - There is no interactive edge swipe on iPhone or iPad, and no keyboard
      back on the Mac.
    - `architecture-direction` names Navigation 3 as the accepted default for
      multi-screen flows.

    Only the stacks within a destination move to Navigation 3. Destination
    choices follow `DESIGN.md`, including the accepted release 1.2 addition
    of Schedules by `SCHEDULE-002`.
14. **Export to and import from a file.** `user-confirmed` (2026-09-24,
    preliminary): let a person export the saved lists to a file and import
    them from one, for backup, a fresh install, or moving between devices.
    `open`:
    - The format, and whether the file is encrypted. The list of websites is
      personal data, and the privacy policy promises nothing leaves the
      device unasked.
    - How applications are represented. On iOS, Screen Time selections are
      opaque device tokens that cannot move between devices, so only names or
      nothing can travel.
    - Merge versus replace on import, and how imported changes enter the sync
      intent log.
15. **Quick sharing of saved websites across Apple Accounts.**
    `user-confirmed` (2026-09-24, preliminary): a fast way to pass saved
    websites to a device signed in to a different iCloud account, for example
    a partner's or a second personal account, where iCloud sync cannot reach.
    `inferred` options: AirDrop or the share sheet carrying an idea-14 export
    file, a QR code or link that holds the list, or a one-time pairing. `open`:
    - the privacy of a shared list, and whether it is encrypted;
    - whether sharing is one-time or ongoing;
    - how it relates to the portable workspace in `SYNC-018`.
16. **A standalone macOS and iOS verification tool.** `user-confirmed`
    (2026-09-25): consider extracting the reusable core of `posato-control`
    into a general tool for macOS and iOS development testing. `observed` in
    `QUALITY-010`: the reusable parts are the Tart virtual machine layer (VNC
    input with on-screen text recognition answering SecurityAgent, Login
    Items, privacy panes, Gatekeeper, and iCloud renewal) and the XCUITest
    driver's system scopes (SpringBoard, other applications, passcode entry
    from the Keychain with log redaction), behind one JSON envelope for
    agents. About ten files tie it to Posato: the Gradle layout, bundle
    identifiers, helper process, databases, and pause page. `open`:
    - an application descriptor instead of Posato constants;
    - system-dialog definitions as data per macOS version and language,
      since today's labels and coordinates fit English macOS 26 at one
      resolution;
    - coverage beyond one Mac, one iPhone, and macOS 26 with iOS 26;
    - the experimental Virtualization VNC server, the JVM dependency, and
      the licenses of Tart and `tart-guest-agent` (`source-claim`: Fair
      Source terms, to confirm);
    - who maintains a public tool as each macOS release changes its dialogs.
17. **Silencing notifications without blocking the application.**
    `user-confirmed` (2026-09-25): during a session, silence notifications
    from chosen applications and websites while the applications themselves
    stay usable. `inferred` feasibility limits, to confirm:
    - neither platform offers a public API for one application to filter
      another application's notifications, and third parties cannot switch
      Focus on their own;
    - whether a Screen Time shield on iOS also silences the shielded
      application's notifications is `open`;
    - browser web push arrives through the browser's push service rather
      than the site's domain, so the macOS proxy does not reach it;
      per-site notification permission belongs to the browser.
18. **Helper CPU spike in a first enforced session.** `observed`
    (2026-09-26, `MACOS-012`, Tart clone): in the first enforced session of a
    freshly set-up clone, the normal-user helper used about 60% of one CPU for
    roughly 14 minutes, then fell idle. A later session in the same clone used
    0.2%. The cause is `open`; system traffic through the loopback proxy after
    boot is a `hypothesis`. Reproduce it, find the cause, and bound the
    helper's cost under heavy proxied traffic.
19. **Easy-to-find Mac options for login launch and prompt-free sessions.**
    `user-confirmed` (2026-09-26, PR #92): `ONBOARDING-004` in release 1.2
    offers both independent, initially off choices in the existing Mac
    permission step, with a defer action. Existing users get one dismissible
    offer; later hints are contextual and wait while password-requiring
    setup is unsafe during a session. The accepted UI lives in
    [DESIGN.md](../../../DESIGN.md#release-12-setup-and-schedules), and
    [product scope](../../product/schedules-and-mac-setup.md) distinguishes
    optional login readiness from explicit consent to automatic enforcement.
    The current Start/Resume grant does not authorize scheduled Apply.

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
[preparation checklist](../../tasks/first-mvp-pr-preparation-todo.md),
including the manual Apple Task 0 after the product identity and target graph
are known. The accepted roadmap makes Gate 7 `APPLE-001` and groups
`FOUNDATION-001`, `QUALITY-001`, and `CI-001` into one PR #1 brief,
execution record, and completed-change review. Everything not required by PR
#1 or its immediate dependants is decided as late as the corresponding small
task requires it.

`user-confirmed`: PR #1 creates fresh production modules, uses accepted target
identifiers, runs one minimal shared Compose screen on macOS and iOS,
introduces small semantic platform contracts, and establishes behavior-focused
tests when applicable plus CI. Gate 5 did not configure CI; Gate 6 retains CI
as a milestone in the shared PR #1 cycle that completes before PR #1 merges or
parallel implementation begins. PR #1 implements neither blocking nor
synchronization.

`user-confirmed` (2026-08-26): APPLE-001's registered resources,
non-Keychain portal capabilities, associations, team visibility, and retained
Keychain suffix all pass. Gate 7 is complete without claiming Keychain
functionality, provisioning authorization, or signed entitlements. The Ready
to open PR #1 checkpoint was subsequently accepted. Hosted `CI-001` now passes;
the manual Codex review also passes, and the active external handoff is the
maintainer merge decision.
