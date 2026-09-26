# `MACOS-012`: Decide how Posato stays present on macOS without its main window

- **Review tier:** `high-risk`
- **Tier reason:** The decision proposes a new process and window lifetime for the application that owns session policy and the helper lease, revisions of ADR 0003 and ADR 0004, and launch at login; it sets the process boundary that `MACOS-013`, `MACOS-014`, `NOTIFY-001`, and `SCHEDULE-001` inherit. A brief independent plan review precedes the comparison, and the decision record receives a completed-change review.
- **Dependencies:** none; release 1.2, wave R1.2/W1. `MACOS-013` implements the accepted presence; `MACOS-014` and `NOTIFY-001` start from the accepted process boundary.
- **Integration group:** `PR-MENU-BAR-DECISION`, milestone `1.2.0`. Documentation, plus throwaway measurements outside product sources.
- **Authority:** [release roadmap](../release-roadmap.md), [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md) (process and native boundaries), [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md) (process ownership, lease, reconciliation, removal), [ADR 0008](../../decisions/0008-macos-update-delivery.md) (update admission after the session ends), [`DESIGN.md`](../../../DESIGN.md), [`PRIVACY.md`](../../../PRIVACY.md), [threat model](../../security/apple-mvp-threat-model.md), and idea 7 in the [wiki idea queue](../../wiki/topics/mvp-open-questions.md#post-mvp-feature-ideas-for-discovery).

## Outcome

A recorded, maintainer-accepted decision on how Posato on macOS keeps a session running and reachable from a status-bar menu while its main window is closed, with the proposed ADR 0003, ADR 0004, and `DESIGN.md` revisions written out and a delivery plan for `MACOS-013`; no product code.

## Boundaries

- Starting point (`observed`): closing the window quits the application (`onCloseRequest = ::exitApplication` in `desktopApp/.../Main.kt`), the ownership lease then expires, the daemon restores proxy settings, and blocking stops; the README discloses this.
- Compare the process models: (A) the Compose Desktop process stays resident after its window closes and drives the menu; (B) session orchestration moves into a native helper and the JVM becomes UI only; (C) a thin native menu-bar agent beside a windowless JVM process. Criteria: policy ownership under ADR 0003/0004, who renews the lease, crash and relaunch recovery, idle memory, CPU, wakeups, and energy measured in a Tart VM, CloudKit sync while the window is closed, the hooks `NOTIFY-001` and `SCHEDULE-001` need, and effort.
- The status-bar menu: its content (current session, remaining time, start, end, open the window), Compose `Tray` against a native `NSStatusItem` through the existing native bridge, template icon and dark mode, and accessibility.
- Window, Dock, and quit semantics: closing the window versus **Quit**, what **Quit** does during a session, the Dock icon and activation policy, and the relation to `SESSION-005` early-end friction.
- Launch at login: opt-in through `SMAppService`, its Background Task Management records (`MACOS-009` facts), and disablement in System Settings.
- Preserve the `MACOS-011` update admission gate, `MACOS-009` removal, and the ADR 0004 lease and reconciliation guarantees. Say whether the chosen boundary changes `MACOS-019` (App Sandbox) and what it leaves for `MACOS-014`.
- Non-goals: persistent administrator authorization (`MACOS-014`), notifications (`NOTIFY-001`), schedules, remote push, a third product destination, telemetry, and any product code.

## Acceptance

- `AC-01` — A decision record compares A, B, and C against the criteria with provenance labels, measured resource figures, and one recommendation.
- `AC-02` — The proposed ADR 0003 and ADR 0004 revision text and the `DESIGN.md` menu and window rules are written out, not summarized.
- `AC-03` — A `MACOS-013` delivery plan names the unattended VM evidence for starting, ending, and inspecting a session from the menu with the window closed, relaunch, launch at login, and quit during a session, and the constraints handed to `MACOS-014` and `NOTIFY-001`.
- `AC-04` — The maintainer's acceptance or rejection is recorded (`user-confirmed`) before `MACOS-013` starts.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Independent plan review before the comparison and an independent review of the decision record.
- Resource and menu measurements run only in a Tart VM through `posato-control --vm`, from throwaway prototypes outside tracked sources; figures go into the record with provenance, captures stay in ignored `build/verification/`.
- The standing local `./gradlew quality` merge gate still applies.

## Decisions or blockers

- `user-confirmed` (2026-09-26): process model A, and **Quit** during a session warns without ending it, as recommended in [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md), with its other decisions D3-D5.
- No blocker.
