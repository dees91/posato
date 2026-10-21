# Execution: `LINUX-003`

- **Brief:** [Posato for Linux keeps running in the background](../specifications/linux-003-background.md)
- **Status:** `in progress`; plan reviewed and rechecked, GNOME golden built
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

Folded after the independent plan review (4 Critical, 9 Required; the
maintainer decided C2, C4, R6, and R9, `user-confirmed` 2026-10-21).

1. **GNOME golden** (done 2026-10-21, `b0ebacce`): `posato-golden-linux-gnome`
   with GDM and `ubuntu-desktop-minimal`, in the unattended verification
   guide and `~/machine-wiki`, claimed in `machine-ledger` first. The stored
   session is Xorg (`ubuntu-xorg`), for `xdotool` and `import -window root`;
   `linux create --golden gnome --session wayland` switches GDM's
   `DefaultSession` and AccountsService to `ubuntu`, and GNOME runs on
   Wayland under Tart's virtio GPU (`observed`, run
   `20261021-145024-9768`), where `linux screen` uses `gnome-screenshot -f`
   (GNOME Shell's `Screenshot` D-Bus method answers `AccessDenied`).
2. **Tool first** (`posato-control`, README, the skill's
   `features/linux-and-android.md`, auditing scenarios that assume closing
   quits): a GNOME launch mode in which `/tmp/posato-dbus` holds the
   session bus's address instead of a private bus, so the item reaches the
   watcher and the shell's AT-SPI bus stays in use, with toolkit
   accessibility on (an autostarted process gets the ATK setting through
   `~/.config/environment.d` on the golden); `tray -t linux [--activate
   LABEL]` over `gdbus` (layout and `clicked` event); `linux screen`;
   `linux relogin`.
3. **D-Bus probe in the real package first:** merge `META-INF/services` in
   `linuxAppJar`, run `jdeps` on the jar and add `jdk.security.auth` to the
   jlink modules if it is needed, then install the `.deb` in a GNOME clone
   with a minimal probe that connects to the session bus, owns
   `app.posato.Posato`, exports a stub item, and registers it with the
   watcher; step 6 builds the real item on it.
4. **Tests first** (`desktopApp` jvmTest): dbusmenu layout per `DESIGN.md`
   state with stable ids; autostart entry contents, atomic write, removal,
   and Tweaks-written `Hidden=true` and `X-GNOME-Autostart-enabled=false`
   read as off; file-lock contention (lock held and name unreachable: the
   second process exits without opening the database).
5. **Linux resident composable** (no Mac file changes): the new Linux
   exclusive lock next to the database, then the
   graph with `hostsSession = false`, `presence.runWhileResident()` at
   application level, window visibility, the first-close rule with a
   marker under `$XDG_DATA_HOME/Posato` and a Compose alert, quit through
   `quitPromptFor` with the Linux schedule-only prompt in a focused
   `DialogWindow`, Ctrl+Q, `--background`, surfaces released when hidden,
   exit on bus disconnect.
6. **Status area:** `dbus-java-core` and
   `dbus-java-transport-native-unixsocket`, pinned at 5.2.2 (MIT, about
   400 KB, with `slf4j-api` MIT): the item, the menu, `Show` with the
   activation token, watcher tracking through `NameOwnerChanged`, refresh
   on `AboutToShow` bounded to once a minute while open.
7. **Login item:** a platform-neutral port with the Linux row in "On this
   computer" and the rules in the brief; `prerm` removes the installing
   user's entry.
8. **Text:** the Linux strings and the `DESIGN.md` Linux paragraph,
   `THIRD_PARTY_NOTICES.md` and the package notices, and the limits page
   (no icon on GNOME without the AppIndicator extension).
9. **Verify** `AC-01` to `AC-07` (Wayland once for `AC-01` and `AC-04`),
   `./gradlew quality`, independent review. Rebase after #184 merges.

## Decisions

- StatusNotifierItem over AWT `SystemTray` (spike): native menu, no
  transparency fault, works on KDE, Xfce, and MATE, independent of X11.
- `dbus-java` over a helper process: no extra system packages, one process.
- A per-user autostart entry over `/etc/xdg/autostart`: login launch is a
  person's choice, off by default, as on the Mac.
- `user-confirmed` 2026-10-21: Mac code untouched (C4); quit asks only while
  a schedule is enabled (C2); without a watcher Posato stays only for a pause
  or an enabled schedule (R6); a GDM GNOME golden line (R9); the `DESIGN.md`
  Linux paragraph is accepted, its wording is reviewed in the PR.
- Plan-review recommendations not taken now are listed in the review
  section with their reason.

## Result

Not started.

## Review

Plan recheck of `0ea7fffa`: 3 Required (lock path, Wayland unattended,
explicit D-Bus probe), folded above. Plan review 2026-10-21 (independent agent): 4 Critical and 9 Required,
all folded above; recommendations taken: activation token and Show
retry, minimal bus surface, symbolic icon with fallback and change
signals, `prerm` autostart removal, exact pins and package notices,
lock and Tweaks tests, rebase after #184. A shorter recheck follows.

## Checks

| Check | Result |
| --- | --- |
| Spike clone | `linux create` run `20261021-141729-d109`; destroyed run `20261021-142832-04f0` |
