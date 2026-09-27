# `SCHEDULE-002` slice 4: Schedules start on their own on a Mac

- **Review tier:** `high-risk`
- **Tier reason:** A resident host applies restrictions through the helper without a person present, shares enforcement with manual sessions, and ends occurrences on every device.
- **Dependencies:** slice 6 (#100, the consent and "ready for schedules"), slice 3 (#99, the store and its pins and terminals), slice 2 (#97, the engine), `NOTIFY-001` (#94), `MACOS-013` (merged, the menu bar residency), `MACOS-014` (merged, the standing grant).
- **Integration group:** `PR-SCHEDULE-DELIVERY`, milestone `1.2.0`.
- **Authority:** [schedule rules](../../product/schedules-decisions.md) ("One pause at a time", "Mac execution", "Notifications", integration contracts 2, 4 and 5), [`DESIGN.md`](../../../DESIGN.md#release-12-setup-and-schedules), the proposed ADR 0004 and ADR 0009 amendments in #93.

## Outcome

On a Mac that is ready for schedules, an enabled schedule starts restrictions at its start time with no dialog, also when the window is closed, and catches up when Posato opens during the interval. It ends them at the planned end unless a manual session still runs. Session shows the one running pause with its latest end and **End early**, which ends the manual session and every running occurrence on this device and tells the other devices. A Mac that is not ready never asks for a password: it says "Setup required on this Mac" and posts one notification. Notices follow the combined pause.

## Boundaries

- **Grant only.** A scheduled start applies only through the standing grant; it never falls back to the prompting apply.
- **Shared enforcement.** Manual sessions and running occurrences share the helper through claims: a manual end inside a schedule keeps restrictions; a session received from another device is not enforced by an occurrence.
- **Pins.** A running occurrence is pinned (key and original start) and survives edits and relaunch as contract 4 says; off, delete, skip and End early end it and write the terminal marker.
- **Another account.** While another account has the console, the occurrence waits and posts nothing.
- **Non-goals:** the iPhone host (slice 5); re-applying when paused items change during a scheduled pause (next session applies them); pruning old facts.

## Acceptance

- `AC-01` — Isolated tests, written first: pinned occurrences under edits, the host policy (pin, finish, one attempt per trigger, release), claims (join without re-apply, manual end inside a schedule), grant-only apply never prompting, the start gate (other account, missing consent, grant off), combined notices, and the store's end/finish/pin operations.
- `AC-02` — Mac (Tart): a schedule 2 minutes ahead starts with the window closed and no dialog (`observe --expect blocked`), ends on time (`allowed`), catches up after a relaunch inside the interval, **End early** ends it and survives a relaunch, **Skip next** prevents a start, and with the grant off it shows "Setup required on this Mac" with no dialog.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac. -->

- `./gradlew quality`; the E2E above with screenshots and a recording; an independent plan review before code and a completed-change review.
