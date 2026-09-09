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
  re-enters the flow on an upgraded install, or strands a device in it.
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
  permission outcome"; "Incoming operations remain in the replica until the
  later convergence slices connect them to visible policies and sessions";
  "Native permissions remain system-owned; Posato never grants its own
  authorization"; no auto-dismissal of decision-critical information;
  amended by this task for the current implementation boundary under `D1`),
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
  this task for truthful states; `TB-07` "never imply independent Posato
  admission or revocation"; an owning task updates or explicitly remains
  within the model when it adds a persistent store or a public security
  claim),
  [the brand baseline](../../wiki/topics/brand-and-design-baseline.md)
  ("Present the reason for a platform permission before the system prompt";
  state first, consequence second, action third; "Never congratulate"),
  the `user-confirmed` iOS synthesis sentence "The onboarding flow requires a
  local selection on each platform" (superseded under `D2`), and the
  `TARGETS-004` authorization contract (amended under `D3`)

## Outcome

On a fresh install of either application the person reads what Posato is for
and what it does not collect, chooses or declines **Sync with iCloud**, sees
this device's permission rationale before any system prompt and can run the
real request or defer it, adds a first website or skips, and lands on the
Session screen; nothing reaches iCloud, the synchronizable Keychain, a system
permission prompt, or the macOS helper process before the person's explicit
step action; every state the flow shows is read back from the real service;
an install upgraded by this build opens on Session as before; and no later
launch shows the flow again unless the local database is gone.

## Boundaries

- The flow is a linear first-run stepper under `feature/onboarding`, hosted by
  `PosatoApplication` before the two destinations (`D1`): purpose, privacy,
  iCloud, this device, first website, summary. It renders outside
  `PosatoNavigationScaffold`, under `PosatoTheme` and the safe-drawing insets
  with `ApplicationNavigationHeader` and the `PosatoSize.Content` width,
  because the sidebar branch of the scaffold always draws its column.
  `PosatoSetupStep` shows progress and each step is a real state from an
  existing service. No new navigation library: a linear setup has no back
  stack, so ADR 0003's Navigation 3 clause does not fire; the step state
  lives in one `@Stable` holder like `SyncBootstrapUiState`.
- Completion is read as one of unknown, incomplete, or complete before
  anything renders, so a used database never flashes the flow and the
  driver's `Paused items` wait cannot pass early. A failed completion write
  still lands on Session for this process; the flow returning at the next
  launch is the truthful outcome.
- Purpose and privacy come first and Continue is their only acknowledgement;
  every service step (iCloud, permission, first website) offers a defer
  action that leaves the device local-only or unpermitted, because the
  product must work without iCloud and without Screen Time.
- The privacy step states only what an accepted authority proves: no product
  account; no browsing history, allowed-navigation events, or usage counters;
  settings in the person's private iCloud database, encrypted before they
  leave the device; application choices stay on each device; the Apple
  Account and iCloud Keychain decide which devices join; no telemetry or
  crash upload. No sentence states that websites, applications, or sessions
  appear on another device, because convergence is `SYNC-011` and
  `SYNC-012`; the iCloud step describes linking this device and that changes
  made after linking leave it encrypted. It claims no anonymity, delivery
  time, perfect prevention, or administrator resistance, and it never implies
  that Posato admits or revokes a device on its own.
- The iCloud step reuses the single `SyncBootstrapUiState` that
  `PosatoApplication.Content` already creates, so the foreground trigger
  stays single: one press is one bootstrap attempt, the seven statuses render
  truthfully in the step, including waiting for the key with the same retry,
  and "Not now" continues local-only (`D4`). No second consent control, no
  synchronization time, no device list, no success claim beyond the returned
  status.
- The permission step presents the reason before any prompt and never claims
  a grant, through two narrow `commonMain` ports with platform actuals; the
  ports carry status enums only, never a `HelperResult`, an
  `AuthorizationCenter` value, or a system-settings object. Whether one or
  two Metro providers carry them is settled by `:shared:detekt` on the real
  parameter count, not assumed.
  - iPhone (`D3`): the request runs through a new `requestAuthorization`
    capability on `IosApplicationMappingsProvider`, executed by
    `IosLocalApplicationMappings` under its operation mutex and the
    `TARGETS-005` generation guard so a stale request can never present a
    later picker; the status-to-access mapping lives in a
    FamilyControls-free Swift type so Simulator tests cover it; the
    simulator and non-development branch keeps returning unavailable; the
    read-back access state is displayed; the Paused items picker path is
    unchanged.
  - Mac (`D3`): the desktop actual first runs
    `MacOsHelperSigningVerifier.verify` on the installed helper inside
    `runCatching` and maps failure to unavailable without touching the
    client, because a doomed `enable()` leaves `MacOsHelperClient` with a
    pending unknown request that fails every later call; then **Enable on
    this Mac** runs the existing `enable()` and `status()` on
    `Dispatchers.IO` with a real-graph dispatcher assertion; approval
    required opens `x-apple.systempreferences:com.apple.LoginItems-Settings.extension`
    through `java.awt.Desktop.browse` and offers **Check again**, because
    `enable()` alone never moves approval required to ready and the helper
    never opens System Settings; the administrator authentication that every
    session start asks for is explained and never raised (`SESSION-002`).
- The first-website step uses the existing exact-domain entry, validation,
  and store; the draft stays in the composable's text-field state;
  websites-only is a valid setup; onboarding creates no application group
  and no application selection (`D2`).
- The summary step reads its sentences from the services ("Local only on
  this iPhone. Screen Time not enabled. 1 website.") and offers one action,
  **Open Session**. It congratulates nothing and claims nothing the status
  does not say.
- Completion is one local fact in a singleton `local_setup_state` row added
  by migration `6.sqm` (`D5`). The migration seeds the row when the database
  already carries product state (a domain, an application policy, a bootstrap
  row, or a session), so an upgraded install never sees the flow. Otherwise
  the row is written when the person finishes or skips the last step and
  cleared only when the local database is gone. Removing a workspace,
  changing the account, or relaunching never re-enters the flow; re-linking
  stays on the Session screen's sync section.
- Accessibility rules bind every step: 44-point actions, meaningful labels
  and roles, logical focus, VoiceOver and keyboard paths, non-color state
  cues, wrapped action rows at large text, Reduce Motion honored, and no
  auto-dismissal of a decision.
- Write surface: new `shared/src/commonMain/**/feature/onboarding/**`,
  `PosatoApplication.kt`, `ApplicationNavigation.kt`, `strings.xml`, a new
  `LocalSetup.sq` plus `migrations/6.sqm`, the iOS capability in
  `iosMain/**/feature/targets/data/**` and `iosApp/**/IosApplicationMappingsProvider.swift`
  with its Swift tests, a desktop helper-state provider in `desktopApp/**`
  with `Main.kt` and `createDesktopApplicationGraph` passing the existing
  client, both DI graphs and their composition tests, `DESIGN.md`, the
  `verify-posato` skill (`SKILL.md`, `features/README.md`, every feature
  file that uses `--fresh` or `reset`, new `features/onboarding.md`,
  fixtures), `docs/wiki/topics/ios-enforcement.md` (the superseded selection
  sentence and the authorization entry synthesis), and the wiki log.
- Non-goal: the second-device join, the physical waiting-for-key proof, and
  local mapping completion on the second device (`ONBOARDING-002`); policy
  and session convergence (`SYNC-011`, `SYNC-012`); the public privacy notice
  and release-time permission persistence (`RELEASE-001`); diagnostic capture
  consent (no producer exists); Navigation 3; schedules, QR enrollment,
  cross-device approval, total-key-loss recovery; the macOS Automation prompt
  (`MACOS-004`); changes to helper approval or recovery mechanics under
  `macosHelper/**`; a settings destination to re-run setup; a redaction test
  for carriers that hold only enums and booleans.

## Acceptance

- `AC-01` — A fresh install on the Simulator, the iPhone, and the Mac shows
  the flow before any destination, with no navigation chrome; purpose and
  privacy cannot be skipped; finishing or skipping the last step persists
  completion, and every later launch opens directly on Session. A database
  that already holds product state, migrated by this build, opens on Session
  without the flow. A fresh local database (reinstall, `launch --fresh`,
  `reset`) shows the flow again; removing a workspace does not.
- `AC-02` — Until the iCloud step's press, `sync_bootstrap_state` holds no
  row and no CloudKit or synchronizable-Keychain access occurs; until the
  permission step's action, no system prompt appears, `requestAuthorization`
  is not called, and no `PosatoMacOSHelper` process exists (checked with
  `pgrep` during the flow); declining both keeps it that way through
  completion. The press runs exactly one bootstrap attempt and the step shows
  the returned status from the seven accepted ones, with waiting for the key
  and action required rendered as such and never as success.
- `AC-03` — Every sentence of the purpose, privacy, iCloud, and summary steps
  maps to an accepted authority listed in the boundaries, states no
  cross-device arrival, and the independent review checks that mapping; no
  element shows onboarding success, a congratulation, a simulated permission
  outcome, an invented time, or a device list.
- `AC-04` — On the iPhone the rationale precedes the system prompt, the step
  runs the real request, and the displayed state afterwards equals the
  system's answer (approved, denied, restricted, or unavailable), with the
  Paused items picker path unchanged. On the Mac the step reports unavailable
  on an unverifiable helper without touching the client, reports the real
  helper state after **Enable on this Mac**, opens System Settings and offers
  **Check again** when approval is required, and describes the administrator
  authentication without raising it. "Later" is available on both and leaves
  the existing contextual paths intact.
- `AC-05` — The first-website step adds a domain through the existing
  validation and it appears in Paused items and the Session summary; skipping
  it completes setup with zero items and the Session screen's existing
  no-items guidance; no application group or selection is created by the
  flow.
- `AC-06` — `./gradlew quality` passes with no new suppression and the
  migration verified with existing rows intact; an unattended Simulator
  fixture drives the whole flow declining iCloud; the skill names the
  fresh-launch prelude fixture after every `--fresh` and `reset` so every
  existing recipe still passes; large-text, keyboard, and VoiceOver passes
  are captured for at least one step on each platform.

## Verification

- `commonTest` for the step holder over fakes: order and skip rules, no
  provider access before each step's action, the seven sync statuses
  rendered, permission results mapped from each access and helper state,
  completion persisted once, the failed-write case landing on Session, the
  tri-state read, and the fresh-database case showing the flow again.
- `SqlDelight` migration verification for `6.sqm`: an empty database gets no
  row, a database with a domain, a policy, a bootstrap row, or a session gets
  the seeded row.
- `jvmTest` and `iosTest` over the real graphs: the ports resolve,
  construction touches no provider, the desktop port runs on `Dispatchers.IO`,
  and the Session-first path holds when the completion row exists.
- Swift tests for the separated authorization capability (request without
  presenting the picker; each status mapped in the FamilyControls-free type);
  desktop tests for the helper-state provider over a fake client including
  the unverifiable-helper short circuit and the approval-required route.
- Simulator, unattended (`first-install.json`): fresh launch, privacy, "Not
  now" on iCloud, permission unavailable, add one website, land on Session;
  `db query` shows zero `sync_bootstrap_state` rows and one domain. The
  degraded iCloud outcome is a separate manual row. A `first-install-skip.json`
  prelude (Continue, Continue, Not now, Later, Skip) is referenced by every
  recipe that launches fresh.
- Physical iPhone, attended: uninstall and install, the flow, the real Screen
  Time prompt (maintainer step), **Sync with iCloud** establishing on an
  emptied account (use **Remove workspace** first), relaunch opens on
  Session. Physical Mac: fresh local database through `reset -t desktop`
  with backup and restore, the flow, **Enable on this Mac** reporting the
  already-approved helper, establish, relaunch; then the upgrade row: the
  restored database migrated by this build opens on Session. Evidence under
  the ignored `build/verification/`; the approval-required helper state is
  unit-tested because the maintainer's Mac already approved the helper.
- Closeout statement in the execution record for the threat model:
  `local_setup_state` holds no asset class, and every privacy sentence maps
  to an existing control or residual (`R-01`, `R-03`, `R-04`); the model is
  edited only if a sentence exceeds it.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path scan.

## Decisions or blockers

- `D1` decided (`user-confirmed`, 2026-09-09, the recommendation below): a
  dedicated first-run flow before the two destinations, hosted outside the
  navigation scaffold as described, with the "current implementation
  boundary" section of `DESIGN.md` amended in this pull request to describe
  the flow (the `SYNC-009` and `SYNC-010` precedent). Alternative: an inline
  setup checklist on the Session screen that routes to the existing controls;
  cheaper, but it cannot put purpose and privacy before the first service
  prompt and leaves the accepted onboarding pattern unbuilt.
- `D2` decided (`user-confirmed`, 2026-09-09): no gate. The first
  website is offered, not required, and applications are described as
  optional device-local choices made later from Paused items. This supersedes
  the `user-confirmed` (2026-08-25) sentence "The onboarding flow requires a
  local selection on each platform" in the iOS enforcement synthesis and the
  brand baseline's onboarding diagram step "Choose local apps on this
  device"; the closeout records the supersession. Reasons: websites-only is
  already valid product copy, applications are unavailable on the Simulator
  and on a Mac without a development-signed package, and the MVP outcome
  needs a local selection only on the device that will pause applications.
  Alternative: keep the sentence and require one website and one local
  selection where available before completion.
- `D3` decided (`user-confirmed`, 2026-09-09): the reading that both
  explains and acts. iPhone: the step runs the real request through
  the new provider capability, which amends the `TARGETS-004` rule
  "authorization starts only from one contextual shared action" to "from the
  contextual picker action or the first-install rationale step"; the request
  stays individual, system-owned, and read back. Mac: the step runs the
  existing helper `enable()` behind the signing pre-check, reports its state,
  opens System Settings on approval required, and leaves administrator
  authentication to the first session start. Alternative: explain only and
  route to the existing controls, which keeps `TARGETS-004` untouched but
  makes "asks permissions in context" a signpost rather than a step.
- `D4` decided (`user-confirmed`, 2026-09-09, waiting for the key): the iCloud step renders every
  `AppleSync` status, including waiting for the key, because the code is
  shared and either device may find an existing anchor; `ONBOARDING-002`
  owns the physical proof of join and wait. This task proves establishing on
  an empty account per platform. A reinstalled iPhone whose account still
  holds a key item joins truthfully through the same step.
- `D5` decided (`user-confirmed`, 2026-09-09, completion persistence): one singleton row through
  `6.sqm`, seeded for databases that already hold product state, no
  timestamp, cleared only with the database, so `reset` and `launch --fresh`
  re-show the flow and the driver has one reset path. Alternative: platform
  preferences, which add a second reset path and a platform seam for one
  boolean and still need the upgrade seed.
- `D6` decided (`user-confirmed`, 2026-09-09, evidence): first-install runs on each platform
  independently as listed under verification; a "first installation" is one
  device, so no two-device row belongs here.
- Physical gate: the maintainer's iPhone and Mac; the Screen Time prompt and
  any System Settings approval are attended steps; the iCloud account must be
  emptied with **Remove workspace** before each establishing row.
