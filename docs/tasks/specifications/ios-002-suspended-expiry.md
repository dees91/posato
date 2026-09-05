# `IOS-002`: Clear Posato-owned restrictions after normal expiry while the iOS app is suspended

- **Review tier:** `high-risk`
- **Tier reason:** The task adds a second signed process that runs without the
  application, holds the Family Controls entitlement, and clears restrictions
  on the person's phone from shared App Group state; a wrong schedule leaves
  the phone restricted after the session, a wrong clear removes another
  source's settings, and the callback timing is owned by Apple.
- **Dependencies:** completed `IOS-001` (named store `app.posato.session`,
  selection store in `group.app.posato.ios.session`), `TARGETS-004`,
  `SESSION-001` (session model, read-only here), `APPLE-001`
  (`app.posato.ios.activitymonitor` registered with Family Controls
  development and the App Group)
- **Integration group:** `PR-IOS-EXPIRY`
- **Authority:** `IOS-002` in MVP roadmap revision 9,
  [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
  (iOS enforcement boundary, Device Activity monitor extension, minimum App
  Group state), the threat model (`A-07`, `TB-03`, `T-10`, `T-11`, `T-14`),
  the diagnostics policy, and the `IOS-001` decision that the shared record
  for `IOS-002` is limited to the store name and a version

## Outcome

On a development-signed iPhone, a manual session whose end passes while the
application is suspended or terminated has its Posato-owned restrictions
cleared by the Device Activity monitor extension at the next callback
opportunity, nothing else is cleared, and the application reads the cleared
state truthfully when it next runs; the product never promises the exact
wall-clock instant.

## Boundaries

- Add the Xcode-owned extension target `app.posato.ios.activitymonitor`
  (Device Activity monitor extension point) with the Family Controls and App
  Group entitlements. On the interval-end callback for the Posato activity it
  calls `clearAllSettings()` on the named store `app.posato.session` only and
  writes one minimal versioned App Group record (schema version, session
  identifier, cleared-at). It reads no selection tokens, no domains, and
  nothing from the shared Kotlin framework; it is Swift only and logs nothing.
- Add a Swift scheduler seam in `iosApp` that starts one non-repeating
  monitoring schedule from session start to session end under a fixed
  activity name, stops it on clear or early end, and is safe to repeat; and a
  Kotlin `iosMain` adapter in `feature/enforcement` with platform-neutral
  outcomes (scheduled, cancelled, below-platform-minimum,
  authorization-required, unavailable, platform-failure) plus a read of the
  cleared record for reconciliation. Apple types stay in Swift; no
  `expect`/`actual`.
- Extension logic that is testable (clear-only-the-named-store, record write,
  activity-name match) lives in a Swift file compiled into both the extension
  and the test bundle behind injectable store and record seams.
- Non-goals: session wiring and the expiry, early-end, and Retry flows
  (`SESSION-002`); custom shield, shield-action, or report extensions;
  foreground expiry, which `SESSION-001` already owns; synchronization; macOS.
- Exclusive write surface while `MACOS-005` and `APPLE-002` run in parallel:
  `iosApp/**` (extension target, project file, entitlements, Swift seams,
  `iosAppTests/**`), `shared/src/iosMain/**/feature/enforcement/**`,
  `shared/src/iosTest/**/feature/enforcement/**`, and
  `docs/wiki/topics/ios-enforcement.md`. Shared by rebase:
  `docs/wiki/log.md`. Do not touch `shared/src/commonMain/**`,
  `feature/session/**`, `feature/targets/**`, `feature/sync/**`, the DI
  graphs, `MainViewController.kt`, `desktopApp/**`, `macosHelper/**`,
  `tools/**`, Gradle files, or the verify-posato skill and its fixtures. No
  verify-posato feature file: there is no user path until `SESSION-002`.

## Acceptance

- `AC-01` — On the device, with a restriction applied and a schedule whose end
  is in the past while the application is force-quit, the paused site and the
  shielded application become usable again without reopening the application;
  the observed delay is recorded, not promised.
- `AC-02` — The extension clears only the named store: a restriction created
  in a differently named store survives the callback, and a callback for a
  foreign activity name clears nothing.
- `AC-03` — Scheduling an interval shorter than the platform minimum, an end
  before the start, or a schedule without authorization returns the matching
  outcome and starts nothing; stop is idempotent and leaves no monitoring
  behind.
- `AC-04` — After an extension clear, the application reads the App Group
  record and reports the session as ended by expiry rather than active; a
  missing or newer-version record reads as unknown, never as active.
- `AC-05` — No domain, token, identifier, or Apple error text appears in any
  `toString()`, log, diagnostic, or test artifact; `./gradlew quality` and the
  Kotlin `iosTest` suite pass on the Simulator.

## Verification

- Swift tests in `iosAppTests` over injectable seams: named-store-only clear,
  foreign-activity no-op, record write and version handling, schedule
  validation (minimum, ordering), stop idempotence.
- Kotlin `iosTest` contract tests over a fake scheduler for every outcome plus
  the enumerated redaction test for the new carriers.
- Physical iPhone checklist recorded pass or blocked per row: extension clear
  after force-quit, foreign store untouched, cancelled schedule never fires,
  reopen reads ended; evidence under `build/verification/` without
  screenshots or identifiers in tracked files.
- `./gradlew quality`, `git diff --check`, suppression and private-data
  scans; independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

- `user-confirmed` (2026-09-05): sessions shorter than the Device Activity
  minimum interval of 15 minutes cannot be scheduled. The scheduler reports
  `below-platform-minimum` and starts nothing; `SESSION-002` relies on
  foreground expiry for such sessions, stating the limit in product copy.
  Do not pad the schedule.
- Decided by authority: the App Group record is the minimum (version, session
  identifier, cleared-at); selection tokens and domains never enter it.
- Physical gate: the new target needs a development profile for
  `app.posato.ios.activitymonitor` with Family Controls and the App Group.
  Xcode automatic signing with the maintainer's team is the first path; if it
  fails, the `APPLE-002` tool running in parallel is the named consumer for
  creating that profile, otherwise the maintainer creates it in the portal
  and the task records the step.
- Open: the callback delay observed on the device and behaviour after a
  reboot inside the interval are recorded from the device, not assumed.
