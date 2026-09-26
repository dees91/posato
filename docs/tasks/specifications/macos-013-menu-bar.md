# `MACOS-013`: Keep Posato for Mac running in the menu bar

- **Review tier:** `high-risk`
- **Tier reason:** The task changes the process and window lifetime of the application that owns session policy and the helper lease, adds a native status item and termination hook to the in-process AppKit leaf, registers a login item, and applies the ADR 0003, ADR 0004, and ADR 0008 amendments. A brief independent plan review precedes implementation, and the completed change receives an independent review.
- **Dependencies:** `MACOS-012` ([ADR 0009](../../decisions/0009-macos-menu-bar-presence.md), accepted 2026-09-26); release 1.2, wave R1.2/W2. `MACOS-014` and `NOTIFY-001` start from the same process boundary; shared desktop surfaces serialize with them.
- **Integration group:** `PR-MENU-BAR`, milestone `1.2.0`.
- **Authority:** [release roadmap](../release-roadmap.md), [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md) (decision, amendments, and delivery plan), [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md), [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md), [ADR 0008](../../decisions/0008-macos-update-delivery.md), and [`DESIGN.md`](../../../DESIGN.md).

## Outcome

With its window closed, Posato for Mac keeps running from a status-bar menu. Blocking continues, and the person can see, start, end, and resume a session there, as ADR 0009 decided.

## Boundaries

- Move the session owner, updater start, and foreground reconciliation to application scope. Closing the window hides it and keeps it composed. Tick only while a session is active or due.
- Widen the window leaf into the application-presence leaf:
  - a native `NSStatusItem` whose menu content and decisions come from Kotlin;
  - the regular and accessory activation policy, reopen, and **Close Window** (Cmd-W);
  - the Quit confirmation, which never delays logout, restart, or an update relaunch;
  - `SMAppService.mainApp`.

  AWT `SystemTray` is not used.
- Add the ADR 0009 rules:
  - the menu states;
  - the first-close notice;
  - the **Open Posato at login** switch in This Mac options, which **Remove from this Mac** turns off;
  - the reworded `RESUME_REQUIRED` notice;
  - exchanges on menu open and at most every 30 minutes while linked.
- Extend `posato-control` for a windowless application: launch and adopt without a window, status-menu open, read, and press, window close apart from Quit, a login launch in a VM, and process resource sampling.
- Apply the amendments quoted in ADR 0009 once the evidence passes: ADR 0003, ADR 0004, ADR 0008, `DESIGN.md`, README, limits page, and website. Public copy uses no em dash.
- Non-goals: fewer administrator prompts (`MACOS-014`), notifications (`NOTIFY-001`), schedules (`SCHEDULE-001`), and the helper CPU spike (`MACOS-021`). The task makes no change to the helper, daemon, lease, authorization, or update-gate semantics.

## Acceptance

- `AC-01` — The execution record lists the failures that E2E cannot reliably expose before any implementation: the menu state for every session and enforcement state, including maintenance; the Quit decision for user and system termination; the idle tick and its resumption; the 30-minute exchange; and the updater's in-progress state. Each has an isolated test that was shown failing before its implementation and passes after it.
- `AC-02` — Every row of the ADR 0009 evidence table passes unattended in a Tart clone:
  - start, inspect, and end from the menu with the window closed;
  - expiry;
  - not enforcing after relaunch and after sleep and wake;
  - reopen;
  - launch at login;
  - removal with login on;
  - quit during a session;
  - the update gate;
  - resource use no worse than P1.
- `AC-03` — VoiceOver reaches the status item and its menu through the accessibility press. The item has an accessible name. No notification prompt appears at launch.
- `AC-04` — The ADR 0009 amendments are applied, and ADR 0009 records the delivery.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- E2E is the primary proof (engineering quality contract revision 15). Every ADR 0009 row ends with a repeatable artifact: revision, initial state, command or scenario, expected and actual result, and run directory. Isolated `AC-01` tests are written failing first. The complete `./gradlew quality` runs after the last correction.
- The ADR 0009 evidence table is driven through the extended `posato-control` in Tart clones of `primary`, using a dev-signed build; the update gate uses the development package's loopback feed with a throwaway key. Captures stay in ignored `build/verification/`.

## Decisions or blockers

- No open product decision. D1-D5 are accepted in ADR 0009.
