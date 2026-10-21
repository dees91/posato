# `LINUX-003`: Posato for Linux keeps running in the background

- **Review tier:** High-risk
- **Tier reason:** It changes the Linux application's lifecycle (window
  close, quit, a second launch, a login launch), adds the first runtime
  dependency for D-Bus, changes the package scripts, and decides when
  schedules can start. A wrong lifecycle leaves a pause or a schedule
  unenforced, or two Posato processes writing one database.
- **Dependencies:** `LINUX-001` (merged in #168); release 1.5, wave 4b.
  `LINUX-002` (follow the desktop theme) touches the same platform code in
  `shared/src/jvmMain`; whichever merges second rebases. `DOCS-006` and
  `RELEASE-007` wait for this row.
- **Integration group:** PR-LINUX-BACKGROUND
- **Authority:** [Release roadmap](../release-roadmap.md) revision 25, row
  `LINUX-003`; idea 50 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md);
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md);
  `DESIGN.md` "Menu bar presence" (the Mac behavior this row mirrors).
  The maintainer chose to ship it in 1.5 whatever it costs (`user-confirmed`
  2026-10-21).
- **Record:** [execution record](../executions/linux-003-background.md)

## Outcome

Closing Posato's window on Linux keeps Posato running with an icon in the
desktop's status area, as the Mac keeps it in the menu bar. Its menu shows
the session state and the same actions as the Mac menu. With **Open Posato
at login** on, Posato starts hidden when the person logs in. Schedules
therefore start and end on time while the window is closed, and a pause
the person started stays visible and can be ended from the icon.

## Boundaries

- **Status icon:** a StatusNotifierItem with a `com.canonical.dbusmenu`
  menu on the session bus, which Ubuntu 24.04's built-in AppIndicator
  extension, KDE Plasma, and the Xfce and MATE panels show natively. The
  menu mirrors `DESIGN.md` "Menu bar presence" through the existing
  `PresenceMenu` model. No AWT `SystemTray` (see the spike in the record).
- **Window close:** hides the window, like `FirstCloseChoice.HIDE` on the
  Mac, with the same one-time notice during a session. When the desktop has
  no status area (no StatusNotifierWatcher on the bus), closing still keeps
  Posato running; the notice says how to open it again from the
  applications menu.
- **One instance:** a second launch (the applications menu, autostart)
  shows the running window instead of starting another process, through a
  well-known bus name.
- **Quit:** from the icon's menu, with the Mac's quit confirmation during a
  session or with enabled schedules. Logout never waits on it.
- **Open at login:** a switch in the "On this computer" options, off by
  default, writing and removing `~/.config/autostart/posato.desktop` (with
  `TryExec`, so an entry left after the package is removed is ignored). A
  login launch keeps the window closed.
- **Package:** no system-wide autostart; `prerm` keeps restoring
  `/etc/hosts` and the service as today.
- **Non-goals:** a Wayland-native tray (the icon goes over D-Bus, which
  does not depend on the display server), GNOME without the AppIndicator
  extension (Posato keeps running without an icon there), a status icon
  countdown, and any change to the Mac.
- **`DESIGN.md`:** a Linux paragraph beside "Menu bar presence" needs the
  maintainer's confirmation before it is added.

## Acceptance

- `AC-01`: In a Tart Ubuntu 24.04 clone with the GNOME session, closing the
  window during a running pause keeps Posato running and its icon visible;
  the icon's menu names the session and ends it early through the window.
- `AC-02`: With the window closed and no pause, a schedule that starts a
  minute later starts on time and its website is observed blocked; it ends
  on time.
- `AC-03`: With **Open Posato at login** on, a new login starts Posato
  hidden with its icon, and a due schedule starts; with the switch off,
  nothing starts.
- `AC-04`: A second launch shows the existing window; one process remains.
- `AC-05`: **Quit Posato** from the icon during a pause asks first; after
  quitting, the pausing service still ends the pause at its end time.
- `AC-06`: Without a status area (the Openbox golden session), closing the
  window keeps Posato running and the notice explains how to reopen it.

## Verification

- `posato-control`, extended first (tool-first rule): a GNOME session for
  the Linux clone, a launch on the session's own bus, a `tray` command that
  reads and activates the status menu over D-Bus, a whole-screen capture,
  and a log-out-and-in step. Then each criterion above, with run ids.
- Tests first for the parts E2E cannot isolate: the dbusmenu layout built
  from `PresenceMenu` and the autostart file contents.

## Decisions or blockers

- `DESIGN.md` Linux text: maintainer confirmation (open).
- Runtime dependency `dbus-java` (MIT) and its `slf4j-api` (MIT): recorded
  in the plan and `THIRD_PARTY_NOTICES.md`.
