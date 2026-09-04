# `SESSION-001`: Provide one bounded manual session and consolidate repeated UI

- **Review tier:** `high-risk`
- **Tier reason:** The task introduces the first session state machine whose
  start, early-end, and terminal-expiry semantics later drive platform
  enforcement and synchronized session intent, migrates the app-private
  schema, and consolidates shared UI contracts across both application
  shells; it also runs in parallel with two other High-risk worktrees.
- **Dependencies:** completed `TARGETS-001` and `TARGETS-002`; `SYNC-002`
  supplies the 16-byte session identifier type and the format-1 duration
  limit
- **Integration group:** `PR-SESSION-CORE`
- **Authority:** `SESSION-001` in MVP roadmap revision 7 (wave P2/W2.7 and
  the consolidation-checkpoint planning boundary), `DESIGN.md`,
  [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md),
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  (session bounds and the terminal local expiry rule), the threat model
  (`A-02`, `TB-01`, `T-03`), and the maintainer decision of 2026-09-04 that
  this task also consolidates the repeated `feature/targets` UI patterns

## Outcome

On both application shells a person chooses a duration or end time, reviews
the resolved end time, effective local items, and any missing permission or
mapping, starts one manual session, sees a truthful status summary, and ends
it early through a clear confirmation or by normal expiry; the session state
survives restart and cannot be revived after observed expiry.

## Boundaries

- Implement a new `feature/session` package: the inactive, active, and ended
  states, setup validation (duration or end time, ADR 0006 bounds, product
  maximum), the review model with effective local items and action-required
  reasons derived from existing target and mapping availability, start, early
  end, and the terminal local expiry marker keyed by the ADR 0006 session
  identifier and committed before the session is exposed as inactive.
- Persist exactly one local session and its expiry marker atomically in the
  app-private SQLDelight database through a new migration; existing target,
  mapping, replica, and bootstrap data survive unchanged. The marker stores
  no observation timestamp and never appears in diagnostics.
- Present the `DESIGN.md` status summary, session setup and review, active
  surface with **End session early**, and action-required notice in the
  shared Compose UI on macOS and iOS using the accepted vocabulary; the
  existing Paused items screen remains reachable.
- Consolidate only UI patterns that the Paused items screen and the session
  screens both use into `app.posato.core.designsystem`; no comprehensive
  component library, prototype geometry, or speculative tokens.
- No platform enforcement, blocked presentation, schedules, synchronized
  session intent, suspended-expiry callback, session history, or
  notification. `MACOS-004`, `MACOS-005`, `IOS-001`, `IOS-002`,
  `SESSION-002`, and `SYNC-012` own those.
- Exclusive write surface while `SYNC-005` and `SYNC-006` run in parallel:
  new `shared/src/**/feature/session/**`; `shared/src/**/feature/targets/ui/**`
  and `shared/src/**/core/**` (consolidation);
  `shared/src/commonMain/kotlin/app/posato/PosatoApplication.kt`; the DI
  graphs `shared/src/*/kotlin/app/posato/di/**`;
  `shared/src/iosMain/kotlin/app/posato/MainViewController.kt`;
  `desktopApp/src/**`; `shared/build.gradle.kts` and
  `gradle/libs.versions.toml` (only if a navigation or ViewModel dependency
  is needed); `shared/src/commonMain/sqldelight/**` (new migration only);
  `composeResources/**`; `DESIGN.md` (token or rule additions backed by
  rendered evidence); `docs/wiki/topics/brand-and-design-baseline.md`; and
  the verify-posato feature map for sessions. Do not touch `iosApp/**`,
  `macosSyncCompanion/**`, `settings.gradle.kts`, `desktopApp/build.gradle.kts`,
  `desktopApp/Config/**`, `shared/src/**/feature/sync/**`, or `macosHelper/**`.

## Acceptance

- `AC-01` — Setup accepts a duration or end time only within the accepted
  bounds, resolves one end time from the chosen clock, and rejects past,
  zero, or over-maximum input without replacing prior valid state.
- `AC-02` — Start persists one active session atomically; restart, a second
  start while active, and a wall-clock rollback after observed expiry never
  produce two sessions or revive an expired one.
- `AC-03` — Early end requires confirmation and marks the session ended;
  observed expiry commits the terminal marker before the status summary
  reports **No session active**.
- `AC-04` — On macOS and the iOS Simulator the status summary answers the
  `DESIGN.md` hierarchy questions, review lists effective local items and
  names any action-required reason, and every state is distinguishable
  without color alone.
- `AC-05` — The Paused items screen keeps its behavior after consolidation,
  every promoted component is used by at least two production screens, and
  `./gradlew quality` passes on JVM and iOS Simulator.

## Verification

- State-machine, validation, clock, and expiry contract tests with a fake
  clock in `commonTest` on JVM and iOS Simulator, including restart and
  rollback cases; ViewModel state tests for review and action-required
  derivation.
- Real-database SQLDelight tests for the migration, atomic start and end,
  and marker retention; `./gradlew quality`; `git diff --check`; suppression
  and private-data scans.
- `verify-posato` runs on `desktop` and `sim` covering setup, review, start,
  restart, early end, and expiry, with evidence under `build/verification/`.
- Independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

- Recommended: observe expiry only while the app runs, through a resumable
  timer plus re-evaluation on foreground and screen entry; background
  execution and callbacks stay with `IOS-002` and `SESSION-002`.
- Recommended: resolve the end time from the wall clock as an epoch instant
  (the value ADR 0006 synchronizes) and treat a rollback as still active
  until the stored end passes again, except after the terminal marker.
- Recommended: product bounds of 5 minutes to 24 hours in one-minute steps;
  a start with zero effective local items is refused as action-required
  rather than started silently.
- Recommended: the session record uses a new local table keyed by the
  ADR 0006 session identifier so `SYNC-012` can author operations from it,
  not the replica's `sync_terminal_expiry` table.
- Recommended: two top-level routes, session and Paused items, using
  Navigation 3 as the ADR 0003 default candidate; the exact dependency is a
  plan-review item.
