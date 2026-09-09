# `ONBOARDING-001`: Complete first-install privacy, Apple workspace, authorization, and target setup without a product account

- **Review tier:** `high-risk`
- **Tier reason:** The flow is the product's first public privacy claim
  surface, so every sentence it shows is a security statement the threat
  model must cover. It adds a persistent first-run store with a schema
  migration, gives the iOS Family Controls request a second entry point,
  reaches the macOS helper's background registration from product code for
  the first time, and replaces the application root that every existing
  verification recipe assumes. A wrong step promises something the product
  cannot keep, touches iCloud or a system permission before the person acts,
  or strands a device in a flow it cannot leave.
- **Dependencies:** completed `SYNC-010` (`AppleSync` state and the seven
  statuses), `SYNC-009` (consent control), `TARGETS-001` (website entry),
  `TARGETS-003` (macOS picker), `TARGETS-004` and `TARGETS-005` (iOS
  authorization, access states, recovery), `MACOS-003` (helper registration
  and status), `SESSION-002` (action-required rule: no administrator prompt
  at launch), `DESIGN-001` (`PosatoPrivacyPoint`, `PosatoSetupStep`,
  `PosatoSyncFooter` adopted but unused), `QUALITY-002`, `QUALITY-004`, and
  `QUALITY-005` (driver, provisioning gate, unattended fixtures)
- **Integration group:** `PR-FIRST-INSTALL`
- **Authority:** `ONBOARDING-001` in MVP roadmap revision 12 (wave P4/W4.1;
  this task does not amend the roadmap; Gate 6 outcome "A first installation
  completes purpose, privacy, iCloud, contextual permission, and target setup
  without a product account", decision gate "No new ceremony beyond accepted
  Apple trust"),
  [the MVP scope](../../product/mvp-scope.md) (primary flow steps 1, 3, and
  4; "Each installation exposes one **Sync with iCloud** action"; no product
  account, QR, invitation, cross-device approval, browsing history, usage
  counters, delivery promise, or administrator resistance),
  [`DESIGN.md`](../../../DESIGN.md) ("Onboarding explains purpose/privacy,
  invokes Sync with iCloud, waits for an existing workspace key, and asks
  permissions in context"; "Never show … onboarding success, simulated
  permission outcome"; "Native permissions remain system-owned; Posato never
  grants its own authorization"; no auto-dismissal of decision-critical
  information; amended by this task for the current implementation boundary
  under `D1`),
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  ("Blocker reports that prerequisite and does not duplicate or bypass it";
  Apple trust is the membership boundary; waiting-for-workspace-key never
  creates a parallel workspace),
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  ("Bootstrap runs only after the explicit **Sync with iCloud** action"),
  [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
  (feature-first packages; construction inert; native values never cross into
  `commonMain`; Navigation 3 only with production code that needs it),
  [the diagnostics policy](../../security/diagnostics-and-support-data.md)
  (no telemetry, analytics, crash upload, or product endpoint exists),
  [the threat model](../../security/apple-mvp-threat-model.md) (`R-04` names
  this task for truthful states; an owning task stays within the model when it
  adds a persistent store or a public security claim),
  [the brand baseline](../../wiki/topics/brand-and-design-baseline.md)
  ("Present the reason for a platform permission before the system prompt";
  state first, consequence second, action third), and the `TARGETS-004`
  authorization contract, which `D3` amends with maintainer acceptance

## Outcome

On a fresh install of either application the person reads what Posato is for
and what it does not collect, chooses or declines **Sync with iCloud**, sees
this device's permission rationale before any system prompt and can run the
real request or defer it, adds a first website or skips, and lands on the
Session screen; nothing reaches iCloud, the synchronizable Keychain, Screen
Time, or the macOS helper before the person's explicit step action; every
state the flow shows is read back from the real service; and no later launch
shows the flow again unless the local database is gone.

## Boundaries

- The flow is a linear first-run stepper under `feature/onboarding`, hosted by
  `PosatoApplication` before the two destinations (`D1`): purpose, privacy,
  iCloud, this device, first website, done. Main navigation stays hidden
  until completion, `PosatoSetupStep` shows progress, and each step is a real
  state from an existing service. No new navigation library: a linear setup
  has no back stack, so ADR 0003's Navigation 3 clause does not fire; the
  step state lives in one `@Stable` holder or ViewModel like
  `SyncBootstrapUiState`.
- Purpose and privacy come first and cannot be skipped; every service step
  (iCloud, permission, first website) offers a defer action that leaves the
  device local-only or unpermitted, because the product must work without
  iCloud and without Screen Time.
- The privacy step states only what an accepted authority proves: no product
  account; no browsing history, allowed-navigation events, or usage counters;
  settings in the person's private iCloud database, encrypted before they
  leave the device; application choices stay on each device; the Apple
  Account and iCloud Keychain decide which devices join; no telemetry or
  crash upload. It claims no anonymity, delivery time, perfect prevention, or
  administrator resistance, and it makes no membership promise Posato cannot
  revoke.
- The iCloud step reuses `AppleSync` through `SyncBootstrapUiState`: one
  press is one bootstrap attempt, the seven statuses render truthfully in the
  step, including waiting for the key with the same retry, and "Not now"
  continues local-only (`D4`). No second consent control, no synchronization
  time, no device list, no success claim beyond the returned status.
- The permission step presents the reason before any prompt and never claims
  a grant. iPhone: it runs the real Family Controls request through a new
  provider capability separated from the picker and displays the read-back
  access state; the picker path from Paused items is unchanged (`D3`). Mac:
  it explains the background helper approval and the administrator
  authentication that every session start asks for, offers **Enable on this
  Mac** through the existing helper `enable()` and reports the real helper
  state, routes to System Settings when approval is required, and never
  raises the administrator prompt itself (`SESSION-002` rule).
- The first-website step uses the existing exact-domain entry, validation,
  and store; websites-only is a valid setup; onboarding creates no
  application group and no application selection (`D2`).
- Completion is one local fact in a singleton `local_setup_state` row added
  by migration `6.sqm` (`D5`). It is written when the person finishes or
  skips the last step, read once at composition, and cleared only when the
  local database is gone. Removing a workspace, changing the account, or
  relaunching never re-enters the flow; re-linking stays on the Session
  screen's sync section.
- Construction stays inert and platform values stay native: the flow reads
  helper and authorization state through narrow `commonMain` ports with
  desktop and iOS actuals; no `HelperResult`, `AuthorizationCenter`, or
  system-settings object crosses into shared code. `PosatoApplication`
  already carries eight injected dependencies, so the new capabilities enter
  through one port, not more constructor parameters.
- Accessibility rules bind every step: 44-point actions, meaningful labels
  and roles, logical focus, VoiceOver and keyboard paths, non-color state
  cues, wrapped action rows at large text, Reduce Motion honored, and no
  auto-dismissal of a decision.
- Write surface: new `shared/src/commonMain/**/feature/onboarding/**`,
  `PosatoApplication.kt`, `ApplicationNavigation.kt`, `strings.xml`, a new
  `LocalSetup.sq` plus `migrations/6.sqm`, the iOS provider capability in
  `iosMain/**/feature/targets/data/**` and `iosApp/**/IosApplicationMappingsProvider.swift`,
  a desktop helper-state provider in `desktopApp/**` beside `Main.kt`, both
  DI graphs and their composition tests, `DESIGN.md`, the `verify-posato`
  skill (`SKILL.md`, `features/README.md`, new `features/onboarding.md`,
  fixtures), the product-framing or brand wiki topic, and the wiki log.
- Non-goal: the second-device join, the physical waiting-for-key proof, and
  local mapping completion on the second device (`ONBOARDING-002`); policy
  and session convergence (`SYNC-011`, `SYNC-012`); the public privacy notice
  and release-time permission persistence (`RELEASE-001`); diagnostic capture
  consent (no producer exists); Navigation 3; schedules, QR enrollment,
  cross-device approval, total-key-loss recovery; the macOS Automation prompt
  (`MACOS-004`); changes to helper approval or recovery mechanics; a settings
  destination to re-run setup.

## Acceptance

- `AC-01` — A fresh install on the Simulator, the iPhone, and the Mac shows
  the flow before any destination, with navigation hidden; purpose and
  privacy cannot be skipped; finishing or skipping the last step persists
  completion, and every later launch opens directly on Session. A fresh
  local database (reinstall, `launch --fresh`, `reset`) shows the flow again;
  removing a workspace does not.
- `AC-02` — Before the iCloud step's press, `sync_bootstrap_state` holds no
  row and no CloudKit, Keychain, Screen Time, or helper call has happened;
  declining keeps it that way through completion. The press runs exactly one
  bootstrap attempt and the step shows the returned status from the seven
  accepted ones, with waiting for the key and action required rendered as
  such and never as success.
- `AC-03` — Every sentence of the purpose and privacy steps maps to an
  accepted authority listed in the boundaries, and the independent review
  checks that mapping; no element shows onboarding success, a simulated
  permission outcome, an invented time, or a device list.
- `AC-04` — On the iPhone the rationale precedes the system prompt, the step
  runs the real request, and the displayed state afterwards equals the
  system's answer (approved, denied, restricted, or unavailable), with the
  Paused items picker path unchanged. On the Mac the step reports the real
  helper state after **Enable on this Mac**, routes to System Settings when
  approval is required, describes the administrator authentication without
  raising it, and reports unavailable truthfully on an ad-hoc package.
  "Later" is available on both and leaves the existing contextual paths
  intact.
- `AC-05` — The first-website step adds a domain through the existing
  validation and it appears in Paused items and the Session summary; skipping
  it completes setup with zero items and the Session screen's existing
  no-items guidance; no application group or selection is created by the
  flow.
- `AC-06` — `./gradlew quality` passes with no new suppression and the
  migration verified; the new state carriers are redacted and covered by a
  redaction test; an unattended Simulator fixture drives the whole flow; the
  skill documents the fresh-launch prelude so every existing recipe still
  passes on a used database; large-text, keyboard, and VoiceOver passes are
  captured for at least one step on each platform.

## Verification

- `commonTest` for the step holder over fakes: order and skip rules, no
  provider access before each step's action, the seven sync statuses
  rendered, permission results mapped from each access state, completion
  persisted once, and the fresh-database case showing the flow again.
- `SqlDelight` migration verification for `6.sqm` with existing rows intact;
  a redaction test for the onboarding state.
- `jvmTest` and `iosTest` over the real graphs: the helper-state and
  authorization ports resolve, construction touches no provider, and the
  Session-first path holds when the completion row exists.
- Swift tests for the separated authorization capability (request without
  presenting the picker; each status mapped); desktop tests for the
  helper-state provider over a fake client.
- Simulator, unattended: fresh launch, privacy, decline iCloud (or the
  degraded outcome), permission unavailable, add one website, land on
  Session; `db query` shows zero `sync_bootstrap_state` rows and one domain.
- Physical iPhone, attended: uninstall and install, the flow, the real Screen
  Time prompt (maintainer step), **Sync with iCloud** establishing on an
  emptied account (use **Remove workspace** first), relaunch opens on
  Session. Physical Mac: fresh local database through `reset -t desktop`
  with backup and restore, the flow, **Enable on this Mac** reporting the
  already-approved helper, establish, relaunch. Evidence under the ignored
  `build/verification/`; the approval-required helper state is unit-tested
  because the maintainer's Mac already approved the helper.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path scan.

## Decisions or blockers

- `D1` open, blocking (flow placement and `DESIGN.md`): recommended: a
  dedicated first-run flow before the two destinations, as described in the
  boundaries, with navigation hidden until completion, and the "current
  implementation boundary" section of `DESIGN.md` amended in this pull
  request to describe the flow (the `SYNC-009` and `SYNC-010` precedent).
  Alternative: an inline setup checklist on the Session screen that routes
  to the existing controls; cheaper, but it cannot put purpose and privacy
  before the first service prompt and leaves the accepted onboarding pattern
  unbuilt.
- `D2` open, blocking (target setup gate): recommended: no gate. The first
  website is offered, not required, and applications are described as
  optional device-local choices made later from Paused items. The prototype's
  "website and app required" rule was a walkthrough fixture, and websites-only
  is already valid product copy. Alternative: require one website before
  completion.
- `D3` open, blocking (permissions in context): recommended: the reading
  that both explains and acts. iPhone: the step runs the real request through
  a new provider capability, which amends the `TARGETS-004` rule "authorization
  starts only from one contextual shared action" to "from the contextual
  picker action or the first-install rationale step"; the request stays
  individual, system-owned, and read back. Mac: the step runs the existing
  helper `enable()` and reports its state; administrator authentication is
  explained and left to the first session start. Alternative: explain only
  and route to the existing controls, which keeps `TARGETS-004` untouched but
  makes "asks permissions in context" a signpost rather than a step.
- `D4` recommended (waiting for the key): the iCloud step renders every
  `AppleSync` status, including waiting for the key, because the code is
  shared and either device may find an existing anchor; `ONBOARDING-002`
  owns the physical proof of join and wait. This task proves establishing on
  an empty account per platform.
- `D5` recommended (completion persistence): one singleton row through
  `6.sqm`, no timestamp, cleared only with the database, so `reset` and
  `launch --fresh` re-show the flow and the driver has one reset path.
  Alternative: platform preferences, which add a second reset path and a
  platform seam for one boolean.
- `D6` recommended (evidence): first-install runs on each platform
  independently as listed under verification; a "first installation" is one
  device, so no two-device row belongs here.
- Physical gate: the maintainer's iPhone and Mac; the Screen Time prompt and
  any System Settings approval are attended steps; the iCloud account must be
  emptied with **Remove workspace** before each establishing row.
