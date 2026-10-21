# `LINUX-003`: Posato for Linux keeps running in the background

- **Review tier:** High-risk
- **Tier reason:** It changes the Linux application's lifecycle (window
  close, quit, a second launch, a login launch), adds the first runtime
  D-Bus dependency, changes the package, and decides when schedules can
  start. A wrong lifecycle leaves a pause or a schedule unenforced, or two
  Posato processes writing one database.
- **Dependencies:** `LINUX-001` (merged in #168); release 1.5, wave 4b.
  `LINUX-002` (#184) touches `shared/src/jvmMain`; this branch rebases after
  it merges. `DOCS-006` and `RELEASE-007` wait for this row.
- **Integration group:** PR-LINUX-BACKGROUND
- **Authority:** [Release roadmap](../release-roadmap.md) revision 25, row
  `LINUX-003`; idea 50 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md);
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md);
  `DESIGN.md` "Menu bar presence". `user-confirmed` 2026-10-21: ship in 1.5
  whatever it costs; leave every Mac file unchanged; the Linux quit and
  no-icon rules below; a Linux paragraph in `DESIGN.md`; a GNOME golden.
- **Record:** [execution record](../executions/linux-003-background.md)

## Outcome

Closing Posato's window on Linux keeps Posato running with an icon in the
desktop's status area. Its menu shows the session state and the actions of
the Mac menu. With **Open Posato at login** on, Posato starts in the
background when the person logs in. Schedules start and end on time while
the window is closed, and a running pause can be ended from the icon.

## Boundaries

- **Mac untouched** (`user-confirmed`): `ResidentWindow.kt` and every Mac
  file stay as they are. A Linux resident composable uses the parts that are
  already platform-free: `DesktopPresence`, `presenceMenuModel`,
  `quitPromptFor`, and the first-close rule with an injected marker path and
  alert. A shared core is extracted only when Windows arrives.
- **One instance:** before the graph or the database opens, `LinuxMain`
  takes an exclusive file lock (`$XDG_RUNTIME_DIR/posato.lock`, else
  `$XDG_DATA_HOME/Posato/instance.lock`). A second process that cannot take
  it forwards **Show** over the bus name `app.posato.Posato` (with the
  activation token, retried briefly) and exits without opening the
  database. Without a session bus there is no icon and no forwarding; the
  lock still holds.
- **Status icon:** a StatusNotifierItem (`org.kde.StatusNotifierItem-<pid>-1`)
  with a `com.canonical.dbusmenu` menu, exported only on the session bus's
  `unix:` transport; the bus surface is **Show**, the item, and the menu.
  The icon is a `-symbolic` SVG of the Posato mark (`IconName` with
  `IconThemePath`) with a pixmap fallback, and sends `NewIcon`, `NewTitle`,
  and `NewToolTip` on change. The menu follows `DESIGN.md` through
  `PresenceMenu` and refreshes on `AboutToShow`, at most once a minute while
  open, and stops when it closes.
- **Schedules:** the Linux composable calls `presence.runWhileResident()` at
  application level and the window graph does not host schedules
  (`hostsSession = false`), so exactly one scheduler runs.
- **Without an icon** (`user-confirmed`): when no StatusNotifierWatcher owns
  its name, closing the window keeps Posato in the background only while a
  pause runs or a schedule is enabled, and the one-time notice says how to
  open it again from the applications menu; otherwise closing quits. The
  watcher is followed through `NameOwnerChanged`, never latched.
- **Quit** (`user-confirmed`): **Quit Posato** in the icon menu, Ctrl+Q, and a
  **Quit Posato** row in the Linux options. It asks only while a schedule is
  enabled, in a focused standalone dialog even with the window hidden. A
  running pause still ends on time through the pausing service. Logout never
  waits. Posato exits when its session bus disconnects.
- **Open at login:** a platform-neutral login-item port with a Linux row in
  "On this computer", off by default. It writes
  `$XDG_CONFIG_HOME/autostart/posato.desktop` atomically with `Type`, `Name`,
  `Icon`, `TryExec`, and `Exec=/opt/posato/bin/posato --background`, no
  `Hidden` key; an entry with `Hidden=true` or
  `X-GNOME-Autostart-enabled=false` (written by Tweaks) reads as off. A
  `--background` launch never shows the window, also as a second launch.
  The Linux schedule start gate stays independent of the switch.
- **Idle cost:** hidden with an enabled schedule, Posato releases its window
  surfaces and stays within the bounds of `AC-07`.
- **Package:** the jar merges `META-INF/services`; `jdeps` decides any
  added jlink module; `prerm` removes the installing user's autostart entry,
  and `TryExec` makes any other left-over entry inert.
- **Non-goals:** GNOME without the AppIndicator extension showing an icon,
  a countdown in the icon, and any Mac change.

## Linux copy for `DESIGN.md` (paragraph accepted; wording refined in the PR)

- First close, staying: "Posato is still running, so your pause and
  schedules keep working. Open it again from its icon or the applications
  menu." With no icon: "…from the applications menu."
- Not enforcing line: "Restrictions not active on this computer."
- Quit with a schedule enabled: **Quit Posato?** "Scheduled pauses won't
  start until you open Posato again. A running pause still ends on time."
  **Keep Posato open** (default) and **Quit**.
- Login switch: **Open Posato at login**, "Starts Posato in the background
  when you log in, so schedules start on time."

## Acceptance

- `AC-01`: In the GNOME golden clone, closing the window during a pause
  keeps Posato running with its icon; the menu names the session and ends it
  early through the window. Once also on Wayland.
- `AC-02`: Window closed, no pause: a schedule a minute ahead starts once
  (one start in the log and one occurrence row in the database), its website
  is observed blocked, and it ends on time.
- `AC-03`: With the switch on, a new login starts Posato in the background
  with its icon and a due schedule starts; with it off, nothing starts.
- `AC-04`: A second launch shows the existing window and one process
  remains; a second `--background` launch shows nothing. Once on Wayland.
- `AC-05`: **Quit Posato** with an enabled schedule shows the dialog with the
  window hidden (screenshot); after quitting during a pause, the service
  still ends it on time.
- `AC-06`: Without a watcher (Openbox golden): closing with an enabled
  schedule keeps Posato running with the notice; closing with neither a
  pause nor a schedule quits.
- `AC-07`: Hidden for 10 minutes with an enabled schedule: CPU under 1% on
  average, a stable thread count, no `LayoutUpdated` while the menu is
  closed, and resident memory not growing.

## Verification

- `posato-control` extended first; see the record's plan.
- Tests first: dbusmenu layout per menu state, autostart entry rules
  (including Tweaks-written entries), and file-lock contention.

## Decisions or blockers

- None open. Runtime dependency `dbus-java` (MIT) and `slf4j-api` (MIT),
  pinned exactly and listed in `THIRD_PARTY_NOTICES.md` and the package.
