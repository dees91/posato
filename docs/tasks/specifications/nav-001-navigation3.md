# `NAV-001`: Screen stacks on Navigation 3 with system back

- **Review tier:** Standard
- **Tier reason:** Application-shell navigation and a new pinned dependency;
  no privacy, security, persistence, or synchronization change.
- **Dependencies:** `SCHEDULE-002` (done); release 1.3, wave 1. `SCHEDULE-004`
  waits for this row.
- **Integration group:** PR-NAVIGATION
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `NAV-001`; idea 13; ADR 0003 (Navigation 3 as the default candidate for
  multi-screen flows); maintainer named the row on 2026-09-29.

## Outcome

Every screen stack inside a destination runs on Navigation 3, and the
platform's back gesture returns one screen: the interactive edge swipe on
iPhone and iPad, and keyboard and trackpad back on the Mac.

## Boundaries

- Covers the stacks inside Session, websites and applications, Schedules,
  and About Posato to Licenses to a license text. Onboarding stays
  forward-only and has no back (`user-confirmed`, 2026-09-29).
- The destinations and their switching stay as `DESIGN.md` defines them;
  back never switches destination or leaves the application.
- Explicit **Back** actions stay and behave as before. A gesture back does
  exactly what the screen's explicit **Back** does, including the draft
  handling `DESIGN.md` defines, and is unavailable while a password or
  system prompt is in progress.
- Pin the exact Navigation 3 version after checking that its Compose
  Multiplatform artifacts support the JVM desktop and iOS targets in use,
  as ADR 0003 requires.
- Non-goals: new destinations, deep links, state restoration across process
  death, and the blocklist screens of `SCHEDULE-004`.

## Acceptance

- `AC-01` — On the test iPhone, the edge swipe returns from Licenses to About
  Posato and from a schedule editor to Schedules, following the finger.
- `AC-02` — On the Mac in Tart, the keyboard back command and trackpad back
  return one screen in the same stacks.
- `AC-03` — Each explicit **Back** action, draft handling in editors, and
  destination switching behave as on `main`.
- `AC-04` — The iPad sidebar layout from `IOS-004` supports the same swipe
  within its detail stack.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Driver runs on the test iPhone (`-t device`) and a Tart VM (`--vm primary`)
  through each stack: gesture back, explicit **Back**, and an editor with a draft.
  Extend `posato-control` with an edge swipe and a back key if it lacks them.
- iPad: no iPad test device exists, so AC-04 runs in the iPad Simulator
  through the driver's XCUITest path, without synthetic desktop input.

## Decisions or blockers

- `user-confirmed` (2026-09-29): the Mac back commands are Command-[ and
  Escape, plus the trackpad's two-finger swipe between pages through native
  AppKit swipe tracking. The VM drives that swipe with synthetic phased
  scroll events, which prove completion and cancellation but not the
  progress of a real trackpad.
