# ADR 0009: Keep Posato for Mac present in the menu bar

## Status

- **Status:** Accepted direction; implementation pending `MACOS-013`
- **Date:** 2026-09-26
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`
- **Owner:** `MACOS-012` decides; `MACOS-013` delivers after separate activation.

This record compares how Posato for Mac keeps a session running and reachable
from a status-bar menu while its main window is closed. It recommends one
process model and writes out the amendments it needs. The maintainer accepted
every recommendation on 2026-09-26 (see the maintainer decision below). This
accepts the direction and the delivery gates, not a working menu bar. The
amendments below are applied to their authorities only after `MACOS-013`'s
verified delivery.

## Context and evidence

`observed` on main `13fc900`:

- **The window is the process.** `Window(onCloseRequest = ::exitApplication)`
  in `desktopApp/.../Main.kt` quits Posato when its only window closes. Cmd-Q
  and closing the window behave the same.
- **Blocking stops on quit.** The JVM starts the normal-user helper with
  inherited pipes and a signed-parent check. The helper renews the daemon's
  ownership lease every 5 seconds against a 15-second deadline. When the JVM
  exits, the helper's input reaches end of file and it restores. If that
  fails, the daemon restores when the connection closes or the lease lapses.
  The daemon has no notion of when a session ends.
- **Session time is kept only while the window is composed.**
  `SessionTransitionOwner.runWhileHosted`, the 1-second loop that notices
  expiry, runs from a `LaunchedEffect` inside the window. So do
  `MacUpdater.start` and the update-consent prompt. The loop ticks even when
  no session exists.
- **Synchronization is foreground-driven.** An exchange runs on window
  foreground, **Sync now**, a policy edit, or a session transition. There is
  no timer, subscription, or push.
- **Relaunch needs approval.** A relaunch with an active session enters
  `RESUME_REQUIRED`. Blocking returns only after **Resume restrictions** and
  an administrator prompt. The daemon also restores on sleep, so a wake
  needs the same approval.
- **One instance comes only from LaunchServices.** The instance lock is
  shared. A second process started directly runs beside the first, and
  update admission then refuses with `OTHER_INSTANCE`.
- **Nothing exists yet** in the desktop application for launch at login, a
  status item, a Dock reopen handler, a quit hook, or an activation-policy
  change.

The public README and limits page say that Posato must stay open for blocking
to work. Idea 7 in the wiki idea queue asks for a status-bar menu, like
Tunnelblick's, from which a person sees the current session, starts or ends
one, and opens the full window only when needed.

### Measured resource use

Figures are `observed` in a Tart clone of the primary line: macOS 26.6.2,
4 virtual CPUs, 8 GB. Each run settled for 2 minutes and was then sampled
for 10 minutes. CPU is the process's CPU-time delta over the sample. Wakeups
and energy impact come from `top`'s interval sample. A VM does not measure
hardware energy, so energy impact is useful only to compare rows with each
other.

Two clones were used. P0 without a session ran in the first; every other row
ran in the second, after first-run setup had enabled the helper. `IDLEW` is
cumulative in `top`, so wakeups are the sample's delta divided by 600
seconds. The root daemon's footprint could not be read without root; its
CPU time is from `ps`.

| Probe | Process | Footprint | CPU over 10 min | Idle wakeups/s | Energy impact |
| --- | --- | --- | --- | --- | --- |
| P0 window open, no session | JVM | 257 MB | 5.9 s (1.0%) | 63 | 4.1 |
| P1 window open, no session | JVM | 268 MB | 6.4 s (1.1%) | 81 | 5.1 |
| P0 window open, enforced session | JVM | 293 MB | 7.4 s (1.2%) | 24 | 2.4 |
| | helper | 8.3 MB | 364 s (61%), transient | 0.1 | 60.6 |
| | daemon | — | 0.6 s | — | 0.1 |
| P1 window closed, no session | JVM | 261 MB | 4.4 s (0.7%) | 22 | 1.8 |
| P1 window closed, enforced session | JVM | 269 MB | 4.1 s (0.7%) | 23 | 1.8 |
| | helper | 6.8 MB | 1.0 s (0.2%) | 0.2 | 0.1 |
| | daemon | — | 1.8 s | — | 0.1 |
| P3 native status agent | agent | 13 MB | 0.02 s | 0.03 | 0.0 |

What the figures show:

- **Resident cost is memory, not CPU.** A resident Posato with its window
  closed holds about 260-270 MB and under 1% of one core. That is the lowest
  CPU and energy measured for the JVM. In the same clone, the P1 process with
  its window open and idle woke about four times as often, with about three
  times the energy impact, as with its window closed. The P0 row with the
  window open during a session woke far less than either window-open idle
  row. That difference is not explained here.
- **Enforcement adds little once settled.** In the settled session, the
  helper stayed under 7 MB, and the helper and daemon together used under
  0.5% of a core. The daemon's footprint was not read.
- **The P0 helper figure is a transient.** In the first enforced session of
  that clone, the helper used about 60% of a core. It burned about 7.5
  minutes of CPU in the session's first 14 minutes, then fell idle, and the
  next session did not repeat it. The helper is identical under every model,
  so this does not separate the alternatives. The last section records it.
- **Native code saves memory only.** P3's 13 MB is the floor that B would
  pay instead of the JVM. It saves about 250 MB of memory and under 1% of a
  core, at the cost described in the comparison below. C pays P3 plus the
  JVM.

### Menu capability

`observed` in the second clone, from a throwaway patch (P1) that closes the
window without exiting and adds Compose's `Tray`. The patch is not in the
repository.

- **Resident behavior works as expected.**
  - After the window closed, the process, its tray icon, and the helper
    stayed. Switching the activation policy to accessory removed the Dock
    icon, and LaunchServices reported the application as `UIElement`.
  - With the window closed during an enforced session, `posato-control
    observe --website http://example.com/ --expect blocked` still returned
    the pause page.
  - The session owner, hosted outside the window, expired a session on time
    with no window. The expiry marker was written, and the menu's first line
    changed to "No session active".
  - Opening the bundle through LaunchServices while it was resident showed
    the window again in the same process, with the Dock icon, as a
    foreground application.
  - The patch re-created the window content. It opened at Session rather
    than where the person left it. Keeping the window composed while hidden
    is a `MACOS-013` requirement.
- **The template icon works.** JDK 21's `CTrayIcon` honors
  `apple.awt.enableTemplateImages`, and the black pause glyph rendered like
  the system's own status icons. Menu items reach the accessibility tree as a
  native `NSMenu` with titles and enabled state.
- **The AWT status item is not accessible.** Its menu-bar item has no title
  or description, because the tooltip is not exposed, and its only action,
  `AXPress`, does not open the menu. Only a real mouse click does. VoiceOver
  and keyboard users could not reach the menu.
- **The tray triggers a notification prompt.** Initializing the AWT tray made
  macOS show a "Posato Notifications" permission banner at launch. P0 never
  shows this banner. It would pre-empt the in-context permission flow planned
  by `NOTIFY-001`.
- **Cmd-W does nothing.** The Compose window has no Close command. A person
  can close the window only with its close button until a **Close Window**
  item (Cmd-W) is added.
- **Launch at login.** Registering `SMAppService.mainApp` from the
  application returned `enabled` at once, without approval. It added a
  separate **Posato — Application** row under **Open at Login**, apart from
  the enforcement helper's **PosatoMacOSHelper** row under **App Background
  Activity**, and macOS posted a "Login Item Added" notice. Because the two
  records are separate, disabling one leaves the other alone (`inferred`);
  the removal itself was not driven.

`observed` from P3, a 40-line Swift accessory agent with a native
`NSStatusItem`, a template SF Symbol, and an `NSMenu`:

- the status item carries its accessibility description;
- its menu appears in the accessibility tree with every title;
- `AXPress` opens the menu. The call returned `cannotComplete` while the menu
  stayed open, which is how status-item menus report it.

This is the same AppKit API a JNI leaf in the JVM process uses. The existing
updater leaf already inserts a native menu item with a callback into Kotlin.
A separate in-JVM JNI prototype (P2) was therefore not built; `MACOS-013`
proves it in the product.

## Alternatives considered

- **A. Resident application process.** The Compose Desktop process stays
  running after its window closes. It hosts the session owner, the helper
  pipes, the updater, synchronization, and a status-bar menu.
- **B. Orchestration in a native helper.** A resident Swift process owns
  session timing, enforcement orchestration, and the menu. The JVM becomes a
  window that edits policy and exits.
- **C. Native menu agent beside a windowless JVM.** A small Swift agent owns
  only the status item and menu. A JVM process without a window keeps the
  session, and the agent talks to it over new IPC.

B and C were not built. Their evidence is the code and ADR analysis below,
labelled `inferred`, plus the measured floor of a native status agent (P3).
Effort estimates are relative, not measured delivery times.

| Criterion | A. Resident application | B. Native orchestration | C. Native agent and windowless JVM |
| --- | --- | --- | --- |
| Policy ownership (ADR 0003, 0004) | Unchanged: Kotlin owns policy, session meaning, and orchestration | Moves session timing, expiry, reconciliation, and sync triggers into Swift, against "the JVM application and shared Kotlin remain the only owners of product policy" | Kotlin keeps ownership; a new agent-to-JVM IPC boundary is added |
| Who renews the lease | Unchanged: the helper, while its signed parent lives | The native process would have to become the helper's parent or merge with it; the signed-parent check changes | Unchanged helper, but its parent is the windowless JVM, so the JVM must stay resident anyway |
| Crash and relaunch | Unchanged: helper end of file, daemon disconnect and lease; relaunch enters `RESUME_REQUIRED` | New durable session state in Swift and a second reconciliation path | Two processes to supervise; an agent crash leaves an invisible JVM, a JVM crash leaves a menu that cannot act |
| Idle resource use | The JVM's cost (P1) | The agent's floor (P3) plus the helper; the JVM exits | The agent's floor plus the JVM, so never below A |
| CloudKit sync while closed | Same process; needs a trigger that does not depend on window foreground | Needs the companion driven from Swift or the JVM woken for every exchange | Same as A, plus IPC to reflect the result in the menu |
| Update admission (ADR 0008) | Same process and gate; the updater start moves out of the window | The gate's no-active-session check needs the native session state; the gate is rebuilt | Same gate in the JVM; the agent is one more process to stop before replacement |
| Hooks for `NOTIFY-001` and `SCHEDULE-001` | Session transitions already happen in this process | Transitions in Swift; shared Kotlin loses them | Transitions in the JVM; notifications from either process |
| Menu technology | Native `NSStatusItem` through the in-process AppKit leaf; AWT `Tray` fails accessibility (P1) | Native `NSStatusItem` | Native `NSStatusItem` |
| Effort | Smallest: move three window-scoped effects, add a menu, reopen, quit, and login handling | Largest: re-implement and re-test tested Kotlin session and sync orchestration | Large: new IPC, a second lifecycle, and all of A |

A is recommended. B buys lower idle memory by rewriting the most tested part
of the product in a second language and moving policy ownership out of Kotlin.
C costs at least as much as A at runtime and adds a process boundary without
removing the JVM.

## Decision

Accepted as recommended (`user-confirmed`, 2026-09-26).

### Process and ownership

Posato for Mac stays one Compose Desktop process (model A). Closing the
window no longer ends the process: the process and its helper keep running,
so an enforced session continues. The process ends only through **Quit**, a
system logout or restart, a supported update relaunch, or a crash.

The work now hosted by the window moves to the application scope, which lives
as long as the process:

- the session owner's tick loop and restore (`runWhileHosted`);
- `MacUpdater.start`;
- the reconciliation that today runs on window foreground.

The tick runs every second only while a session is active or due. With no
session, it waits for the next transition instead of polling. Window
foreground, menu opening, and synchronization results still trigger the
existing reconciliation.

No new process, IPC boundary, or privilege is added. The helper, daemon,
lease, reconciliation, and signed-parent relationship of ADR 0004 stay as
they are. Closing the window changes nothing below the JVM.

### Status-bar menu

The status item is a native `NSStatusItem` owned by the application's
in-process AppKit leaf (see the ADR 0003 amendment). AWT's `SystemTray`,
which Compose's `Tray` uses, is rejected for three reasons:

- VoiceOver and keyboard users cannot open its menu;
- the item has no accessible name;
- it raises a notification permission prompt at launch.

The menu is a summary of state the window already shows, not a second
product surface:

- **No session:** "No session active", **Start a session…**, **Open
  Posato**, and **Quit Posato**. **Start a session…** opens the window at
  setup, because duration, review, and the administrator prompt belong to
  the existing flow.
- **Active and enforcing:** "Session active until *time*", the remaining
  whole minutes, **End session early…**, **Open Posato**, and **Quit
  Posato**. **End session early…** opens the window at the existing
  confirmation (Ready to return?, End session, Keep this pause). The menu
  never ends a session in one click. `SESSION-005` adds its stronger
  friction in that same confirmation.
- **Active but not enforcing:** the timer is not presented as protection.
  For `RESUME_REQUIRED`, a failed or pending apply, or an unavailable
  helper, the first line reads "Restrictions not active on this Mac", the
  same words as the Session notice, and the first action is **Resume
  restrictions…**, which opens the window at that notice. The administrator prompt is raised
  only by the person's action in the window, never by the menu opening.
- **Update or maintenance in progress:** the menu names it and offers **Open
  Posato**.

The remaining time is refreshed when the menu opens and at most once a minute
while it stays open. Nothing ticks for the menu while it is closed. The icon
is a monochrome template image of the Posato pause mark, adapting to light,
dark, and increased-contrast menu bars. It does not change color, animate,
or show a countdown in the menu bar. An active session may use a filled
variant and an accessibility description that names the state. Every item
has a text title that VoiceOver reads; state is never conveyed by the icon
alone.

### Window, Dock, and reopen

- Closing the window hides it and leaves the process running. The first time
  a window close leaves a session running, a one-time note says that
  blocking continues and that Posato stays in the menu bar. The note offers
  **Quit Posato** and **OK**.
- While the window is open, Posato is a regular application with a Dock icon
  and its application menu. After the window closes, it becomes an accessory
  application: no Dock icon, and the menu-bar item is its only surface.
- Opening Posato again (Finder, Spotlight, Launchpad, **Open Posato**, or a
  Dock click while visible) shows the existing window state. LaunchServices
  delivers a reopen to the running process instead of starting a second one.
  A second process started outside LaunchServices keeps today's behavior;
  update admission refuses it with `OTHER_INSTANCE`.

### Quit during a session

Accepted: **warn**. **Quit Posato** from the menu, the application menu,
or Cmd-Q during an active session shows a confirmation:

> **Quit Posato?**
> Blocking stops on this Mac until you open Posato and resume it. The session
> stays active on your other devices.
>
> **Keep Posato open** (default) · **Quit**

Quitting does not end the session: the session is shared through the
workspace, and ending it is the deliberate early-end flow. Refusing to quit
would contradict "not a lock you cannot open" and cannot be kept for a system
logout, restart, or supported update relaunch. Those system-initiated
terminations never wait on the confirmation. With no active session, Quit
does not ask.

### Launch at login

Accepted: opt-in. An **Open Posato at login** switch lives in the **This
Mac** options and is off by default. It registers `SMAppService.mainApp` and
reads its status back. It does not appear during first-run setup.

A login launch starts with the window closed. When an active session exists,
the process enters the existing `RESUME_REQUIRED` state. The menu offers
**Resume restrictions…**, and no administrator prompt appears until the
person chooses it.

`observed` (P1): registration adds a separate **Open at Login** record
beside the helper's **App Background Activity** record, with no approval.
Removing the login item leaving the daemon untouched is `inferred` from the
separate records. `MACOS-013` proves it. macOS announces the new login item
itself, so the switch needs no extra confirmation.

### Synchronization while the window is closed

Accepted: an exchange runs when the menu opens and when the window opens,
as today on foreground, plus at most one exchange every 30 minutes while a
workspace is linked and the process runs. It uses the existing short-lived
companion and conflated worker; no subscription or push is added. A received
session is still adopted in `RESUME_REQUIRED` on the Mac and needs the
person's approval before blocking.

### Preserved guarantees

- **Update admission (ADR 0008).** The gate follows the process, not the
  window. `MacUpdater.start` runs once per process at application scope.
  Sparkle's scheduled checks now happen in a process that can run for days,
  so the found-update and ready-to-install windows can appear while the main
  window is closed. `MACOS-013` must show them with Posato activated as a
  regular application; this was not measured here. The consent question is
  still asked once, the first time the destinations appear after setup. An
  active session still refuses admission. In the ADR 0008 cancellation and
  termination scenarios, "window close" no longer terminates anything. Only
  Quit, a crash, or a system termination does, and those keep their proven
  behavior.
- **Removal (MACOS-009).** It stays in This Mac options in the window, is
  reached through **Open Posato**, and is still refused during a session.
- **ADR 0004.** No change to the helper, daemon, lease, authorization, or
  reconciliation. The clarification that Quit ends the helper is rewritten so
  that it names Quit, not closing the window.
- **Sleep and wake.** The daemon still restores on sleep. After wake, the
  menu shows the not-enforcing state with **Resume restrictions…**. A
  resident process does not make blocking survive sleep.

### Constraints handed on

- **`MACOS-014` (fewer administrator prompts).** With a resident process, the
  remaining prompts come from Resume after wake, after a login launch, and
  after adopting a session from another device. Any persistent authorization
  must keep the prompt behind the person's action and must not give the menu
  or a login launch a silent apply path unless its own security review
  accepts that.
- **`NOTIFY-001`.** Session start, end, and expiry transitions happen in the
  resident process at application scope. Mac notifications post from there
  with the application's bundle identity. No new process is needed.
- **`SCHEDULE-001`.** `user-confirmed` (2026-09-26): the maintainer asked
  whether recurring schedules (for example 9:00-17:00 every weekday) change
  this recommendation. They do not change D1-D5. They add these constraints:
  - **Host.** The resident process, together with launch at login, is the
    macOS scheduling host. The session owner's "due" state includes the next
    scheduled start, and wake and clock changes re-evaluate it.
  - **Approval.** A scheduled start meets the same administrator approval as
    Resume until `MACOS-014` changes it. Without that change, a Mac
    schedule starts the session in the not-enforcing state, "Restrictions
    not active on this Mac", and waits for the person. It never raises an
    unprompted administrator dialog.
  - **Local evaluation.** Each device evaluates a synchronized schedule
    locally at its time, so the exchange interval (D4) bounds how fast a
    schedule edit arrives, not whether a scheduled session starts.
  - **Launch at login.** Creating a schedule on a Mac offers **Open Posato at
    login** in context. The default stays off (D3).
  - **Quit.** The quit confirmation also names upcoming scheduled sessions
    that will not start on this Mac while Posato is closed.
- **`MACOS-019`.** Unchanged. This decision keeps the root daemon and
  Authorization Services, so App Sandbox stays out of scope.

## Authority amendments

Accepted verbatim. `MACOS-013` applies them after its verified delivery,
as `MACOS-011` did for ADR 0008.

### ADR 0003: widen the window leaf into an application-presence leaf

> `MACOS-013` widens the DESIGN-001 window-presentation leaf into one
> application-presence leaf in `desktopApp`. In addition to window
> presentation, it owns the status item and its menu, the activation-policy
> switch between regular and accessory, reopen and termination hooks, and
> registration of the application itself as a login item through
> `SMAppService.mainApp`. It remains a signed, in-process AppKit/JNI leaf
> loaded from application resources.
>
> Menu content and every action decision stay in Kotlin. The leaf renders
> titles and enabled states it receives and reports item selection and menu
> opening back. It performs no networking, holds no policy, application
> selections, workspace keys, or authorization material, and talks to no
> Posato process. The status item exposes an accessibility description and
> opens its menu for VoiceOver and keyboard. AWT's `SystemTray` is not used.
>
> The desktop application process may stay resident after its window closes.
> Its lifetime, not the window's, bounds the session owner, the enforcement
> helper pipes, the updater, and synchronization triggers. No new process,
> IPC boundary, or privilege is added. The updater leaf, the enforcement
> helper, the root daemon, and the synchronization companion keep their
> accepted boundaries.

### ADR 0004: distinguish closing the window from quitting

In the 2026-09-15 `MACOS-008` clarification, the first bullet becomes:

> - Quitting Posato ends the helper. That covers **Quit Posato**, Cmd-Q, a
>   logout or restart, a supported update relaunch, or a crash. Closing the
>   main window does not quit Posato and leaves the helper and its lease
>   untouched. The helper restores any Apply it owns when its input closes. If
>   that restore fails, the daemon restores on disconnect or when the lease
>   expires.

Add to Lifecycle and recovery:

> The desktop application may remain resident with no window (ADR 0009).
> Window visibility is not a lifecycle event for the helper or daemon. A login
> launch or any relaunch never re-applies ownership without a new foreground
> Apply authorized as above; an active session found at launch waits in the
> Resume state.

### ADR 0008: define window close for the update gate

Append to the MACOS-011 delivery plan's step 5, once `MACOS-013` has passed
its update-gate evidence:

> With the resident process of ADR 0009, closing the main window terminates
> nothing (`observed` in the `MACOS-012` prototype). In the cancellation and
> termination scenarios, "window close" reads as closing the window of a
> process that keeps running. The gate's guarantees attach to Quit, crash,
> and system termination. `MACOS-013` verified that they hold with the
> window closed.

Clarify the Consent, session behavior, and data bullet on Sparkle's settings:

> "Add a resident process" refers to the updater: Sparkle keeps its schedule
> in the application process and adds no resident process of its own; its
> installer processes run only during an admitted installation. It does not
> bar the resident application process of ADR 0009.

### DESIGN.md: menu bar, window, and quit rules

Add under Layout and Responsive Behavior, macOS:

> `user-confirmed` (`MACOS-012`, 2026-09-26): Posato stays running
> when its window closes and lives in the menu bar.
>
> - **Status item.** A monochrome template of the Posato pause mark that
>   follows the menu bar's appearance. It has no color, animation, badge, or
>   countdown in the bar. An active session may use the filled variant. The
>   item's accessibility description names Posato and the state, for example
>   "Posato, session active".
> - **Menu.** The first line names the state, and there is at most one
>   primary action. Every other item routes into the window's existing flow.
>   - No session: No session active · Start a session… · Open Posato · Quit
>     Posato.
>   - Active and enforcing: Session active until *time* · *n* min left · End
>     session early… · Open Posato · Quit Posato.
>   - Active, not enforcing: Restrictions not active on this Mac · Resume
>     restrictions… · End session early… · Open Posato · Quit Posato.
>   - Updating or maintenance: the Session notice's text · Open Posato · Quit
>     Posato.
>
>   An ellipsis marks an item that opens the window at that flow. The menu
>   never ends a session, grants permission, or raises an administrator prompt
>   by itself. Remaining time refreshes when the menu opens and at most once a
>   minute while it is open.
> - **Window.** The close button and **Close Window** (Cmd-W) hide the window.
>   Posato is a regular application with a Dock icon and menus while its
>   window is open, and an accessory with only the status item while it is
>   closed. Opening Posato again from Finder, Spotlight, or **Open Posato**
>   shows the window in the destination and state it had.
> - **First close during a session.** One notice, shown once per device: "Posato
>   is still running. Blocking continues while Posato is in the menu bar." with
>   **OK** and **Quit Posato**.
> - **Quit.** **Quit Posato** and Cmd-Q quit at once when no session is active.
>   During a session a confirmation reads **Quit Posato?** "Blocking stops on
>   this Mac until you open Posato and resume it. The session stays active on
>   your other devices." with **Keep Posato open** (default) and **Quit**.
>   Logout, restart, and update relaunch never wait on it.
> - **Open at login.** In This Mac options, a switch **Open Posato at login**,
>   off by default. It is not offered during first-run setup. A login launch
>   keeps the window closed and never asks for approval on its own.
>   **Remove from this Mac** also turns the switch off, so no login item
>   remains after Posato is moved to the Trash.
> - **Session notice.** The `RESUME_REQUIRED` notice no longer blames closing
>   the app. It reads "Restrictions not active on this Mac." It keeps
>   **Resume restrictions**, because quitting, sleep, a login launch, and a
>   session received from another device all lead there.

### Public wording

README Limits, the Mac bullet, becomes:

> **Mac:** blocking works while Posato runs, including in the menu bar with
> its window closed. Quitting Posato stops it. Starting or resuming blocking
> needs administrator approval. Paused apps are quit, so save your work first.
> Website blocking covers Safari and Google Chrome Stable using the system
> proxy.

The website's Limits list (`website/src/pages/index.astro`), the Mac item,
becomes:

> **Mac:** blocking works while Posato runs, including in the menu bar with
> its window closed. Quitting Posato stops it. Starting or resuming blocking
> needs administrator approval. Paused apps are quit, so save your work before
> a session starts. Website blocking covers Safari and Google Chrome Stable
> while they use the system proxy settings.

`docs/product/limits-and-platforms.md`, the second bullet, becomes:

> On macOS, blocking works only while Posato is running. Closing its window
> keeps it running in the menu bar. If Posato quits, or after sleep, wake, or
> a network change, blocking stops until you resume it in Posato with
> administrator approval. A session received from your iPhone also needs that
> approval before the Mac blocks anything.

## MACOS-013 delivery plan and acceptance

Do not start code from this discovery alone. After the maintainer names
`MACOS-013`, create its High-risk brief and obtain its independent plan
review. The bounded work:

1. Move the session owner, updater start, and foreground reconciliation to
   application scope. Replace `onCloseRequest = ::exitApplication` with
   hiding the window while keeping it composed, so its destination and state
   survive. Tick only while a session is active or due. Unit-test the state
   that decides the menu's line and primary action from the session and
   enforcement states, including `RESUME_REQUIRED`, pending, failed, and
   maintenance.
2. Widen the window leaf per the ADR 0003 amendment: status item, menu,
   activation policy, reopen, Cmd-W, the quit confirmation through
   `applicationShouldTerminate` distinguishing user quit from logout,
   restart, and update relaunch, and `SMAppService.mainApp`. Menu actions call
   Kotlin.
3. Add:
   - the This Mac switch and its removal with **Remove from this Mac**;
   - the first-close notice and the reworded Session notice;
   - synchronization triggers for menu opening and the accepted interval;
   - the README, limits page, and website wording.
4. Extend `posato-control` before relying on it:
   - `launch` and `--adopt` for a process whose window is closed;
   - `menu` to open the status item through its accessibility press and read
     or press items;
   - `window close` separate from `terminate` and Quit;
   - a login launch through `vm` logout and login, or reboot;
   - process resource sampling.

   `observed` by `MACOS-012`: the driver waits for a window at launch, fails
   `snapshot` with `DESKTOP_WINDOW_UNAVAILABLE` without one, and cannot reach a
   text-less status item with `vm click`.
5. Obtain the completed-change review, run `quality`, apply the amendments,
   and update the README and limits page.

| Scenario | Required unattended evidence in a Tart clone |
| --- | --- |
| Start from the menu | With the window closed, **Start a session…** opens setup; after the administrator prompt, closing the window keeps `observe --expect blocked` true |
| Inspect from the menu | With the window closed, the menu names the session, the end time, and the minutes left; VoiceOver reads the item and its menu through the accessibility press |
| End from the menu | **End session early…** reaches the existing confirmation; Keep this pause changes nothing; End session gives `--expect allowed` |
| Expiry with the window closed | A session expires on time with no window; the menu returns to no session, and the proxy is restored |
| Not enforcing | After relaunch and after sleep and wake, the menu shows Restrictions not active on this Mac and Resume restrictions…, with no prompt until chosen |
| Relaunch and reopen | Opening Posato while it is resident shows the existing window without a second process; Dock state follows the window |
| Launch at login | Switch on, log out and in: Posato starts windowless and resident; with an active session it waits in Resume; switch off removes the login item and leaves the helper's background item enabled |
| Removal with login on | **Remove from this Mac** turns Open at Login off; no Open at Login record remains |
| Quit during a session | The confirmation appears; Keep leaves blocking; Quit restores and the next launch shows Resume; logout is not blocked |
| Update gate | With a found update and the window closed, the gate still refuses during a session and admits after it ends |
| Resource use | Resident idle and session samples against the figures above; no regression over P1 |

## Maintainer decision

`user-confirmed` (2026-09-26): the maintainer accepted the recommendation
for D1-D5, after confirming that future recurring schedules do not change
them (see the `SCHEDULE-001` constraints above). `MACOS-013` may start once
the maintainer names it.

| Decision | Accepted recommendation | Alternatives not chosen |
| --- | --- | --- |
| D1. Process model | A: resident Compose Desktop process with a native status item | B: orchestration in a native helper; C: native agent beside a windowless JVM |
| D2. Quit during a session | Warn with **Keep Posato open** as the default; quitting does not end the session; system terminations are not delayed | Refuse user quit during a session; end the session on quit; quit silently as today |
| D3. Launch at login | Opt-in switch in This Mac options, off by default, windowless start, no prompt at login | Offer it during first-run setup; on by default; not offered |
| D4. Exchanges while the window is closed | On menu open and window open, plus at most one every 30 minutes while linked | Only on menu and window open; a shorter interval; none while closed |
| D5. Dock icon | Visible only while the window is open | Always visible; never visible (menu-bar-only application) |

`observed` by this task and outside its decision: in the first enforced
session of a fresh clone, the normal-user helper used about 60% of one CPU
for roughly its first 14 minutes, then fell idle. A later enforced session
in the same clone used 0.2%. The cause is `open`; system traffic through the
loopback proxy after boot is a `hypothesis`. It affects the current release
equally under every model. It is a candidate for the idea queue, not a
`MACOS-013` requirement.
