# `SCHEDULE-002`: Shared recurring schedules

- **Review tier:** `high-risk`
- **Tier reason:** Schedules add synchronized operation kinds, a new automatic start path through the Mac helper's standing grant, and iPhone Device Activity monitoring.
- **Dependencies:** `SCHEDULE-001` (PR #93: rules, the ADR 0004/0006/0009 amendments, and the slice plan), `NOTIFY-001` (PR #94), `ONBOARDING-004` (PR #95), `IOS-006` (merged).
- **Integration group:** `PR-SCHEDULE-DELIVERY`, milestone `1.2.0`.
- **Authority:**
  - [schedule rules](../../product/schedules-decisions.md) and their implementation plan;
  - [product scope](../../product/schedules-and-mac-setup.md);
  - [`DESIGN.md`](../../../DESIGN.md#release-12-setup-and-schedules);
  - the proposed amendments in [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md), [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md) and [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md).

## Outcome

A person creates named weekday/time plans that pause their chosen websites and apps on every set-up device, sees the next run and each device's readiness, can skip the next occurrence or end one early, and a Mac that was asleep or closed catches up to the original end.

The row ships as the six stacked slices in the rules document. Each slice is its own pull request:

1. model and sync (operation kinds 8-11, terminal markers, optional kinds 128-255);
2. the occurrence engine;
3. Schedules UI and storage;
4. the Mac host (resident evaluator, automatic Apply through the grant, "setup required", notifications);
5. the iPhone host (Device Activity, the extension's apply and clear, catch-up);
6. setup integration (the automatic-start consent and its upgrade offer).

## Boundaries

- **Accepted amendments first.** Slices 1, 4 and 6 implement the wire format and the automatic-start authorization that PR #93 proposes. They start only after the maintainer accepts those amendments.
- **Engine purity.** Slice 2 is a pure function of plans, facts, the clock and a time-zone port; it reads no storage and starts nothing.
- **No new dependency.** The time-zone port is implemented with `java.time` on the JVM and with the platform calendar on iOS (slice 5); no date-time library is added.
- **Non-goals:** holidays, per-device schedules, a home time zone, and remote wake.

## Acceptance

- `AC-01` — Slice 2: isolated tests, written failing first, cover the weekday of the start, crossing midnight, the 15-minute and 24-hour bounds, a spring-forward start, a fall-back start and a fall-back occurrence longer than 24 hours, skip/end/terminal facts, overlapping plans shown as one pause, and the next run after a skip or during a running occurrence.
- `AC-02` — Slice 2: the JVM time-zone port resolves skipped and repeated wall times by the decided rules against real zone data.
- `AC-03` to `AC-08` — Slices 1 and 3-6, each with the E2E the rules document names (Tart clones for the Mac, the test iPhone for Device Activity).

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac. -->

- Slice 2: `:shared:jvmTest`, iOS test compilation, mutation checks, `./gradlew quality`, and an independent completed-change review. It has no user-visible behavior, so it has no E2E or screenshots.
- Later slices: as listed per slice in the rules document.
