# Execution: `LINUX-003`

- **Brief:** [Posato for Linux keeps running in the background](../specifications/linux-003-background.md)
- **Status:** `planned`; waiting for the independent plan review
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Branch:** `linux-003-background`

## Spike (2026-10-21, main `a392cfd8`)

A Tart clone of the Openbox golden with `ubuntu-session`, `gnome-shell`,
and `gnome-shell-extension-appindicator` installed, logged in to
`ubuntu-xorg` (GNOME Shell 46, session mode `ubuntu`, which enables
`ubuntu-appindicators@ubuntu.com`). Screens are in the ignored
`build/verification/linux-003-spike/`. `observed` unless marked.

- **AWT `SystemTray`** (JDK 21, a 22-pixel icon and a `PopupMenu`):
  `isSupported()` is true and the extension shows the icon at the right of
  the top bar through its legacy XEmbed support (`01-awt-tray.png`). The
  right-click menu is an unthemed AWT window with a small bitmap font, and
  the icon shows a white corner where it should be transparent
  (`03-awt-rclick.png`). Compose Desktop's `Tray` wraps the same AWT API.
- **StatusNotifierItem** (a Python AyatanaAppIndicator3 probe with four
  items): the shell draws the menu itself, in its own style, with a
  disabled first line, as the Mac menu does (`05-sni-menu.png`).
  `org.kde.StatusNotifierWatcher` answers on the session bus. The probe's
  named icon was blank because the guest has no icon theme; Posato sends
  its own pixmap.
- **Autostart:** `~/.config/autostart/<name>.desktop` ran at the next
  login, on the real session bus `unix:path=/run/user/1000/bus`.
- **Bus:** `posato-control launch -t linux` starts Posato on a private bus
  in `/tmp/posato-dbus` for AT-SPI. A StatusNotifierItem registered there
  reaches no watcher, so verification must launch on the session's bus.
- **Not covered:** a Wayland GNOME session (the golden uses LightDM, which
  starts Xorg); `inferred` that the StatusNotifierItem behaves the same,
  because it goes over D-Bus, not the display server.

## Plan

1. **Tool first** (`posato-control`, its README, and the unattended
   verification guide): `linux create --session gnome` (installs and
   selects the GNOME session in the clone, or a new golden line if the
   install is too slow); `launch -t linux` on the session bus when the
   session has one; `tray -t linux [--activate LABEL]` that reads the
   StatusNotifierItem and its dbusmenu layout with `gdbus` and sends the
   `clicked` event; `linux screen` for a whole-screen capture;
   `linux relogin` that restarts the display manager and waits for the
   session.
2. **Tests first** (`desktopApp` jvmTest): the dbusmenu layout built from a
   `PresenceMenu` for each `DESIGN.md` menu state, ids stable across
   refreshes; the autostart entry's contents (`Exec`, `TryExec`, hidden
   flag) and its removal. These exist because E2E sees only the shell's
   rendering, not the ids or a stale entry.
3. **Shared residency:** move the platform-neutral parts of
   `ResidentPosato` (window visibility, first close, quit confirmation,
   menu refresh and actions) behind small ports so the Mac keeps its native
   code and Linux supplies its own: status area, alerts as Compose dialogs,
   the first-close marker under `$XDG_DATA_HOME/Posato`, and the
   login-item switch.
4. **Linux status area:** `dbus-java-core` 5.2.2 with
   `dbus-java-transport-native-unixsocket` (MIT; about 400 KB, plus
   `slf4j-api`, MIT): export `org.kde.StatusNotifierItem` (Id, Category,
   Status, Title, IconPixmap of the Posato mark at 22 and 44 pixels,
   ToolTip, Menu, `Activate` opens the window) and
   `com.canonical.dbusmenu` (`GetLayout`, `GetGroupProperties`, `Event`,
   `AboutToShow`, `LayoutUpdated`), register with the watcher, and
   re-register when the watcher restarts. Without a watcher, no icon.
5. **Lifecycle:** `LinuxMain` owns `app.posato.Posato` on the session bus;
   a second process calls its `Show` and exits. `--hidden` starts without a
   window. Closing hides; quitting goes through the confirmation.
6. **Open at login:** the switch in "On this computer" writes or removes the
   autostart entry with `Exec=/opt/posato/bin/posato --hidden` and
   `TryExec`; onboarding's Linux steps stay unchanged.
7. **Notices:** `THIRD_PARTY_NOTICES.md` (dbus-java, slf4j), the limits
   page (no icon on GNOME without the AppIndicator extension), and a
   `DESIGN.md` Linux paragraph after the maintainer confirms it.
8. **Verify** `AC-01` to `AC-06` in GNOME clones and `AC-06` in the
   Openbox golden; `./gradlew quality`; independent review.

## Decisions

Agent decisions under the delegation of 2026-10-21 unless marked.

- StatusNotifierItem over AWT `SystemTray`: native menu, no transparency
  fault, works on KDE, Xfce, and MATE, and does not depend on X11.
- `dbus-java` over a Python or `gdbus` helper process: no extra system
  packages, one process, and the same connection serves the single-instance
  name.
- A per-user autostart entry over `/etc/xdg/autostart`: login launch is a
  person's choice, off by default, as on the Mac.

## Result

Not started.

## Review

Plan review: pending.

## Checks

| Check | Result |
| --- | --- |
| Spike clone | `linux create` run `20261021-141729-d109`; destroyed run `20261021-142832-04f0` |
