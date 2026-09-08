# `SESSION-002`: Integrate safe local start, enforcement, early end, expiry, failure, and recovery

- **Review tier:** `high-risk`
- **Tier reason:** This is the first change in which a person's action drives
  real enforcement on both platforms. A wrong sequence applies restrictions
  before a session exists, leaves them active after it ends, or reports active
  enforcement while the helper or Managed Settings refused; it wires the DI
  graphs, both entry points, and both hosts, and it runs in parallel with
  `SYNC-007` on the same iPhone.
- **Dependencies:** completed `SESSION-001` (session state machine, store,
  terminal marker, screens), `MACOS-004` (`MacOsBrowserDomainEnforcer`),
  `MACOS-005` (`MacOsApplicationEnforcer`), `IOS-001` (`IosEnforcement`),
  `IOS-002` (`IosSuspendedExpiry`), `TARGETS-003` and `TARGETS-004`
  (mappings), `MACOS-006` (signed package), `DESIGN-001` (current shells),
  `QUALITY-002` and `QUALITY-004` (driver)
- **Integration group:** `PR-LOCAL-SESSION`
- **Authority:** `SESSION-002` in MVP roadmap revision 10 (wave P3/W3.5),
  [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
  (interfaces and injection, no platform type in `commonMain`),
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md) and
  [ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md)
  (helper lifecycle, Apply ownership, coexistence refusal),
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  (the terminal marker is committed before the session is exposed as inactive
  or enforcement is cleared; `SESSION-002` owns the atomic integration),
  `DESIGN.md` "Session" and "Future Product Contracts" (session-driven
  restrictions report actual enforcement state and failures; a timer or saved
  policy alone is not enforcement proof), the threat model (`A-02`, `A-07`,
  `T-03`, `T-08`, `T-11`, `T-12`), and the diagnostics policy

## Outcome

On the development-signed Mac and iPhone, starting a manual session applies
the accepted local enforcement for the effective items (macOS: exact-domain
denial and application restriction through the helper; iOS: the Posato-owned
Managed Settings restrictions plus the suspended-expiry schedule), the active
surface reports the real enforcement state and any failure with a Retry
action, early end and observed expiry clear only Posato-owned enforcement
after the session end is committed, a failed or unknown apply never shows as
active, and the whole flow is drivable through `verify-posato`.

## Boundaries

- Define the common enforcement port in
  `shared/src/commonMain/**/feature/enforcement/**`: `apply(domains,
  mappingIds, sessionEndEpochMillis)`, `clear()`, and `status()` with
  platform-neutral outcomes (applied, cleared, nothing-to-enforce,
  authorization-required, incompatible, unavailable, unknown, failed) and
  redacted carriers. The JVM adapter composes `MacOsBrowserDomainEnforcer`
  and `MacOsApplicationEnforcer` over one helper client with the browser Apply
  first and a browser Restore on application failure (fail-closed); the iOS
  adapter composes `IosEnforcement` and `IosSuspendedExpiry`. iOS `status()`
  reads the live named-store state through the existing
  `IosEnforcementProvider` (`status` read only, no new Swift); JVM `status()`
  combines the helper ownership phase with the in-memory configured
  application session. Apple types, helper results, and process handles stay
  in the platform source sets.
- Sequence start as: commit the session record, then apply enforcement, then
  derive the enforcement state; a failed, refused, or unknown apply leaves the
  session active with an action-required enforcement state and Retry, never
  an active claim. Retry clears before it re-applies, because the helper
  refuses configure while an Apply is owned. The effective set is frozen at
  Start and shown in the active summary with next-pause copy for later
  Paused-items edits; macOS Resume applies the current set, iOS re-applies it
  silently on relaunch and foreground.
- Sequence early end and observed expiry as: commit the session end (the
  terminal marker for expiry, per ADR 0006), then clear enforcement, then
  report; a failed or unknown clear is an action-required state with Retry,
  never a clean end. On iOS, screen entry and foreground read
  `readReconciliation(sessionId)` for the active session identifier, persist
  the answer before acting on it (the read consumes the record), and clear
  idempotently when it is unknown.
- iOS sessions shorter than the 15-minute platform minimum receive
  `below-platform-minimum` from the scheduler; the session relies on
  foreground expiry and the active surface states that limit in the accepted
  vocabulary. macOS has no such limit.
- Keep `SessionViewModel` and the `SESSION-001` store contracts; extend the
  UI state with the enforcement state and its Retry only. No new screen, no
  blocked-page change, no synchronized session intent (`SYNC-012`), no
  onboarding, no schedules, no notification.
- Exclusive write surface while `SYNC-007` and the `TARGETS-003` picker
  follow-up run in parallel: `shared/src/**/feature/session/**`,
  `shared/src/**/feature/enforcement/**` (new `commonMain` port, `jvmMain`
  adapter, extensions of the existing `iosMain` files), the DI graphs
  `shared/src/*/kotlin/app/posato/di/**`, `PosatoApplication.kt`,
  `MainViewController.kt`, `desktopApp/src/main/kotlin/app/posato/desktop/**`,
  `iosApp/iosApp/iosApp.swift` (construct the existing `IosManagedSettingsEnforcer`
  and `SuspendedExpiryScheduler` in the host and pass them into
  `mainViewController`), `iosApp/iosApp/IosEnforcementProvider.swift`
  (`status` read only, no new Swift, no project change),
  `.agents/skills/verify-posato/features/sessions.md` with the session
  scenario fixtures, the session-helper lines of
  `.agents/skills/verify-posato/SKILL.md` (desktop split only), and the `SESSION-002` sections of
  `docs/wiki/topics/macos-enforcement.md` and `ios-enforcement.md`. Shared
  by rebase: `docs/wiki/log.md`. Do not add Swift files or touch
  `iosApp/iosApp.xcodeproj/**` (`SYNC-007` owns them), `macosHelper/**` (the
  picker follow-up owns it), `macosSyncCompanion/**`, `feature/sync/**`,
  `feature/targets/**` beyond reads, Gradle files, or `DESIGN.md` without a
  separate accepted copy change.

## Acceptance

- `AC-01` — On the Mac, Start applies the exact-domain denial and the
  application restriction for the effective items; the active surface reports
  enforcement active only when both adapters report active; a selected domain
  is denied in Safari and a selected application is terminated during the
  session while controls stay unaffected.
- `AC-02` — On the iPhone, Start applies the Posato-owned restrictions and
  schedules the suspended-expiry interval, or reports the platform minimum for
  a short session; a selected site shows the system presentation.
- `AC-03` — Early end and observed expiry commit the session end before
  clearing; afterwards no Posato restriction remains on either platform and
  the macOS proxy baseline is restored byte-identically; a failed or unknown
  clear is reported as action-required with Retry.
- `AC-04` — A refused or failed apply (helper unavailable, administrator
  authentication cancelled, incompatible network, Screen Time authorization
  missing) leaves the session active with a truthful action-required state
  and Retry, never reports active, and never issues a second Apply while one
  is owned; relaunching during an active session follows the decided resume
  rule below.
- `AC-05` — On the iPhone, reopening after the extension cleared reads
  `expired` for the active session identifier, shows the expiry message, and
  clears idempotently; a stale record from another session never ends the
  current one.
- `AC-06` — `verify-posato` drives start, active, early end, and expiry with
  the enforcement state on `desktop` and `sim` (the Simulator reports
  unavailable truthfully), the physical rows on the Mac and the iPhone pass,
  `./gradlew quality` passes with no new suppression, and no domain, mapping
  identifier, or helper detail appears in logs, diagnostics, `toString()`, or
  evidence.

## Verification

- `commonTest` sequencing tests over fake enforcement ports: apply failure,
  unknown apply, clear failure, expiry commits the marker before clear,
  Retry clears before re-apply, restart during an active session, sub-minimum
  iOS schedule, reconciliation consumed once; the enumerated redaction test
  for the new carriers.
- JVM adapter tests over fake `MacOsBrowserDomainCommands` and
  `MacOsApplicationCommands` (both-active rule, partial failure policy, clear
  ordering); `iosTest` adapter tests over fake providers.
- `verify-posato` runs on `desktop` and `sim` with the updated session
  scenarios: desktop fixtures split into unattended action-required variants
  plus a maintainer-attended full-enforcement row driven by marker files
  (`APPLY_GO`, `ROWS_DONE`, `ABORT` under `build/verification/session-002/`);
  physical Mac run (development-signed package, helper enabled,
  Safari row, disposable test application row, early end, expiry, proxy
  baseline) and physical iPhone run (Screen Time granted, site presentation,
  force-quit expiry, reopen reconciliation); evidence under the ignored
  `build/verification/`.
- `./gradlew quality`, `git diff --check`, suppression and private-data
  scans; independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

- Open (maintainer decision before implementation): partial apply on macOS.
  Recommended: fail closed; if the browser denial applied but the application
  configure failed, or the reverse, clear what applied and report
  action-required, so "active" always means both.
- Open: relaunch during an active session on macOS. The helper died with the
  application and the daemon restored the proxy, so nothing is enforced.
  Recommended: report action-required with an explicit Resume action instead
  of raising the administrator prompt automatically at launch; iOS
  restrictions persist across relaunch and need no re-apply.
- Open: whether a start whose apply failed may be ended early without a
  confirmation step. Recommended: keep the `SESSION-001` confirmation; the
  copy names that nothing was restricted.
- Decided by authority: the session record is committed before apply, and the
  end is committed before clear; `SESSION-001` owns the marker, this task owns
  the ordering around enforcement.
- Physical gates: development-signed Mac package with helper Enable approval,
  one administrator authentication per Apply, Automation and notification
  prompts already granted by earlier tasks; development-signed iPhone with
  Screen Time authorization and the `APPLE-002` profiles. The iPhone is shared
  with `SYNC-007`; the two device runs are scheduled one after the other.
