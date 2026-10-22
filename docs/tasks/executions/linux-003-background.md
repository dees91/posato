# Execution: `LINUX-003`

- **Brief:** [Posato for Linux keeps running in the background](../specifications/linux-003-background.md)
- **Status:** `done`; completed-change review folded
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Branch:** `linux-003-background`

## Spike (2026-10-21, main `a392cfd8`)

A Tart clone of the Openbox golden with `ubuntu-session`, `gnome-shell`,
and `gnome-shell-extension-appindicator` installed, logged in to
`ubuntu-xorg` (GNOME Shell 46, session mode `ubuntu`, which enables
`ubuntu-appindicators@ubuntu.com`). Screens are in the ignored
`build/verification/linux-003-spike/`. `observed` unless marked.

- **AWT `SystemTray`:** supported; the extension shows the icon through
  legacy XEmbed, but the menu is an unthemed AWT window and the icon has a
  white corner (`01-awt-tray.png`, `03-awt-rclick.png`). Compose's `Tray`
  wraps the same API.
- **StatusNotifierItem** (a Python AyatanaAppIndicator3 probe): the shell
  draws the menu itself, with a disabled first line (`05-sni-menu.png`);
  `org.kde.StatusNotifierWatcher` answers on the session bus.
- **Autostart:** `~/.config/autostart/<name>.desktop` ran at the next
  login, on the real session bus.
- **Bus:** `launch -t linux` uses a private bus in `/tmp/posato-dbus`
  for AT-SPI, which no watcher listens on.

## Plan

Folded after the plan review (4 Critical, 9 Required) and its recheck (3
Required); the maintainer decided C2, C4, R6, and R9 (`user-confirmed`
2026-10-21).

1. GNOME golden (`b0ebacce`): GDM, `ubuntu-desktop-minimal`, Xorg stored,
   Wayland by `--session wayland`, whole-screen capture with
   `gnome-screenshot -f` on Wayland; guide, `~/machine-wiki`, ledger claim.
2. Tool first: session bus in `/tmp/posato-dbus` on GNOME with the shell's
   AT-SPI bus, `linux tray`, `linux screen`, `linux relogin`,
   `linux session`.
3. D-Bus probe in the real `.deb` (merged `META-INF/services`, `jdeps`).
4. Tests first: menu ids, autostart rules, lock contention, menu open
   window, no session bus.
5. Linux resident composable with no Mac file changed; status area; login
   item; `prerm`; notices; `DESIGN.md` Linux paragraph.
6. `AC-01` to `AC-07`, Wayland once, `./gradlew quality`, review.

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

- **Status icon:** a StatusNotifierItem with a dbusmenu menu over
  `dbus-java` 5.2.2, symbolic SVG icons with an `index.theme` (the
  AppIndicator extension finds `IconThemePath` icons only through one) and
  pixmaps; jlink adds `java.xml` and `jdk.security.auth` (`jdeps`).
- **Lifecycle:** Linux-only lock, `Show` forwarding, `--background`,
  resident composable with `runWhileResident()`, the Linux close and quit
  rules, notices in focused dialog windows, About's **In the background**.
- **Found while verifying:** GNOME never reports the menu closing, so
  `LayoutUpdated` went out every minute (10 in 10 minutes); an opened menu
  now counts as open for two minutes. Without a session bus, dbus-java's
  unchecked `InvalidBusAddressException` ended Posato at start on Openbox;
  the address is now read first. The GNOME golden's desktop settings
  handed the network to an absent NetworkManager; the golden uses
  systemd-networkd (guide updated). Both regressions have tests first.
  The panel menu was empty (`Properties.Get` without a variant; item
  registered before the menu had lines): properties are variants now, the
  item registers with its first lines, separators keep their ids.
- **Left to `DOCS-006`:** the limits page line (no icon on GNOME without
  the AppIndicator extension; #178 moves the Linux limits there).

## Review

Plan recheck of `0ea7fffa`: 3 Required (lock path, Wayland unattended,
explicit D-Bus probe), folded above. Plan review 2026-10-21 (independent agent): 4 Critical and 9 Required,
all folded above; recommendations taken: activation token and Show
retry, minimal bus surface, symbolic icon with fallback and change
signals, `prerm` autostart removal, exact pins and package notices,
lock and Tweaks tests, rebase after #184 (done, onto `79b87c00`).
Completed-change review 2026-10-22 (independent agent): no Critical; fixed
a hidden window that nothing could show again without a session bus (now
closing quits as **Quit Posato** does), head evidence, and the tool and
skill documentation; taken: the AC-06 citation, the AppIndicator
extension turned off and on while hidden, Show events kept from startup,
`@Volatile` state, the runtime-directory bus fallback, the quit dialog's
default focus and Escape, and the findings in the verification guide.
Failing first: `StatusMenuTest`, `StatusMenuEntriesTest`, the empty panel
menu on GNOME, and `LinuxStatusAreaTest` (Openbox crash, blank address);
lock, autostart, and menu tests failed to compile before their code.

## Checks

| Check | Result |
| --- | --- |
| Spike and probe | spike `20261021-141729-d109`; packaged D-Bus probe `20261021-151018-b053` to `-151244-bbcb` |
| `AC-01`, `AC-04` Xorg (`e29213be`) | close during pause `-152218-c317`, end early from icon `-152238-f25c`; `--background` and plain second launch, one process `-152731-21bf` |
| `AC-02` (`e29213be`) | schedule started with the window closed, one pin row, blocked `-152634-8d75`; ended on time `-154149-1453` |
| `AC-03` (`1f9754ff`) | switch on `-191809-7844`, login launch `--background` with icon `-192022-9445`, due schedule blocked `-192345-838a`; switch off, entry gone, nothing after login `-194515-45a1` |
| `AC-05` (`1f9754ff`) | dialog with the window hidden `-193955-c7a6`; after Quit still blocked `-194014-9381`, ended by the service `-194502-1a83` |
| `AC-07` (`1f9754ff`, no ATK) | 600 s: CPU 0.48%, threads 43 to 38, RSS 115.4 to 116.7 MB, no `LayoutUpdated` (first run on `e29213be`: 10, fixed) |
| Wayland `AC-01`, `AC-04` (`1f9754ff`) | `-200422-5069`, `-200442-7c00`, `-200501-5ca4` |
| `AC-06` Openbox (`1e479838`) | closing with nothing quits `-200947-f722`; with a schedule stays with the notice `-201001-960d` to `-201118-2854` |
| GNOME (`dac8bad7`) | extension off with a schedule stays, on again re-registers `20261022-074933-e4b9`; off with nothing quits after the grace `-075014-d821` |
| Head (`519de482`), GNOME | panel menu by right and left click, ordered after a pause and a schedule start (`build/verification/linux-003-menu/`); tray read `20261022-082520-5b57`; end early from the icon `-082535-6f85`; close keeps it `-082924-4236`; schedule starts closed, blocked `-083118-b413`; `--background` and plain second launch, one process `-083137-f9e4`; quit asks `-083148-13a6` |
| Head (`dac8bad7`), Openbox, no bus | closing with nothing quits `-075224-a26c`; with a schedule asks to quit, Escape keeps it `-075352-c73d`, `-075404-5958` |
| `./gradlew quality` | passed on `3cf5971a` (the last code and record change, on `b82c4ab1`) |

Run ids start with `20261021` unless written in full.
