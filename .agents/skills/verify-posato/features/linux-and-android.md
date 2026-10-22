# Linux and Android

Posato on Linux (a `.deb` for Ubuntu 24.04 or later) and Android (13 or
later) shows the same Session, Pause sets, and Schedules screens as the Apple
applications. During a pause, Linux maps each blocked website and its `www`
name to `0.0.0.0` and `::` in `/etc/hosts` and ends the chosen applications, through
a root service installed once with the person's approval. Android answers
blocked names with `0.0.0.0` and `::` through a local DNS-only VPN, and covers a
chosen application with a block screen. Neither shows a pause page.

## Sub-features

- `linux-onboarding`: first run installs the pausing service through
  `pkexec` and reports "Pausing service installed".
- `linux-websites`: a pause writes a marked block of four lines per website
  to `/etc/hosts`; ending it restores the file exactly.
- `linux-apps`: **Choose apps** lists desktop entries; a chosen
  application's processes end while a pause runs and stay allowed after it.
- `linux-self-clear`: with the application closed, the service clears the
  block at the session end, and the next launch shows "The session ended at".
- `linux-refusal`: the service refuses a request with a malformed host and
  applies nothing.
- `linux-removal`: removing the package clears an applied block and the
  service.
- `android-onboarding`: first run walks through the grants and reports each
  as on.
- `android-websites`: a pause answers blocked names with `0.0.0.0` or
  `127.0.0.1`; other names resolve; ending it releases them.
- `android-apps`: a chosen application is covered by "Paused by Posato"
  within one polling interval; the launcher, Settings, the phone, and Posato
  are never offered.
- `android-background`: a pause or schedule applies with Posato in the
  background and after a reboot.

## How to get to it (user POV)

- Linux: install the package, open Posato from the applications menu, follow
  the first-run flow, and press **Allow pausing on this computer**.
- Android: open Posato, follow the first-run flow, and turn on each access it
  asks for.
- Both: **Manage apps** on Session or a pause set, then **Apps** and
  **Choose apps**.

## Driving it with posato-control

Android and Linux are targets like the Mac and iOS: the shared commands and
every `flow` take `-t android` or `-t linux`, and queries name the same roles
and labels (see the driver README, "Linux and Android").

Preconditions: Linux uses the clone from `$PC linux create` and no macOS
clone runs at the same time. Android uses a phone or Posato's own emulator,
chosen with `--udid <serial>` or `posato.android.serial`, never an emulator
someone else works on; `$PC devices list` shows what adb lists. Run `$PC
doctor -t android|linux` first: it names another app's VPN, strict private
DNS, or a screen lock on the phone, and a missing clone or package.

- **Install and onboarding, as a person meets them:** `$PC build -t
  <target>`, `$PC install -t android --grants settings` (or `install -t
  linux`), `$PC launch -t <target>`, then `$PC flow onboard -t <target>
  [--website example.org]`. On Android it answers the VPN, notification, and
  battery requests; on Linux it installs the pausing service. Proof: the flow
  ends on Session; on Android `$PC android shell --script 'dumpsys deviceidle
  whitelist'` lists `app.posato.android`, on Linux `$PC linux exec --script
  'systemctl is-active posato-helper'` prints `active`. `install -t android`
  without `--grants` gives every grant, for runs that do not test onboarding.
- **Sets, sessions, schedules:** `flow set`, `flow session [--set S]
  [--minutes M]`, and `flow schedule` as on the Mac.
- **Website pause:** start a pause with `example.org` in its set. Proof: `$PC
  observe -t <target> --expect blocked --website http://example.org/` (the
  name answers `0.0.0.0`, `127.0.0.1`, or `::`), and `--expect allowed` for a
  site outside the set. On Linux also `$PC linux exec --script 'grep -c "#
  posato$" /etc/hosts'` prints `4` per website, and after **End session
  early** the file equals a copy taken before the run.
- **Application:** **Manage apps**, **Apps**, **Choose apps**, tick the
  application (Clock on Android, Calculator on Linux), **Choose**. Proof
  during a pause: `$PC observe -t android --expect blocked --application
  <package>` (Samsung's Clock is `com.sec.android.app.clockpackage`, the
  stock one `com.google.android.deskclock`) or `$PC observe -t linux --expect
  blocked --application /usr/bin/gnome-calculator`; after the end the same
  command with `--expect allowed`.
- **Linux self-clear:** start a pause, `$PC linux exec --script 'pkill -x
  posato; sudo -n date -s "+30 minutes"'`. Proof: `/etc/hosts` is restored
  within seconds, and after `$PC launch -t linux` the screen shows "The
  session ended at". Move the clock back afterwards.
- **Android background and reboot:** add a schedule that starts in three
  minutes, press Home (`$PC press -t android --key home`), and wait. Proof:
  `observe` reports the website blocked at the start; repeat across `$PC
  android shell --script reboot`. A phone with a screen lock runs Posato only
  after the first unlock, which needs a person; an emulator has none.

- **Linux status icon and background (`LINUX-003`):** use the GNOME line,
  `$PC linux create --golden gnome` (add `--session wayland` once for
  Wayland), where `launch -t linux` uses the session's own bus so the icon
  reaches the shell. `$PC linux tray` reads the icon and its menu over D-Bus,
  `$PC linux tray --activate "End session early…"` clicks an item, and `$PC
  linux screen` captures the whole screen with the top bar. Close the window
  with `xdotool key alt+F4` on Xorg or `wmctrl -c Posato` on Wayland (install
  `wmctrl` in the clone), then prove the process stays (`pgrep -x posato`)
  and a schedule starts (`observe`). `$PC linux relogin` runs the autostart
  entry; `$PC linux session xorg|wayland` switches the session and keeps the
  data. Proof of a login launch: `pgrep -a posato` shows `--background`.

## Gotchas

- Do not run the Linux clone together with a macOS clone; the Linux guest has
  crashed under that load. Run cross-device phases with one kind at a time.
- `pkill -f posato` inside `linux exec` matches the shell that runs it; use
  `pkill -x posato`.
- On Android the on-screen keyboard covers the lower screen and takes a
  swipe as gesture typing; scrolling (`scrollTo`, the flows' reveal) puts it
  away with Back first. UIAutomator lists only what is on screen, so reveal a
  control before tapping it.
- Android back: the system Back key or gesture returns one screen; at the top
  of Pause sets or Schedules it returns to Session, at the top of Session it
  leaves Posato. `press -t android --key back` drives it; a predictive
  gesture is an edge swipe on an emulator with gesture navigation (`input
  motionevent DOWN 2 1200`, a few `MOVE`s, `UP`), with a screenshot before
  `UP` showing the previous screen under the current one.
- Linux reads Compose's tree through `java-atk-wrapper`; a program started in
  the guest outside `observe` must use the bus in `/tmp/posato-dbus`, or it
  starts its own AT-SPI bus and Posato's tree disappears until `launch -t
  linux` runs again.
- Android private DNS set to strict mode, and a browser with its own DNS over
  HTTPS, bypass website blocking by design; the emulator's default settings
  do not.
- On Wayland, `xdotool` input does not reach Posato's window, so flows that
  type or press by text (`flow set`, `flow session` from an empty set) fail;
  prepare data in the Xorg session and switch with `linux session wayland`.
- GNOME's panel never reports that the status menu closed, and the
  AppIndicator extension finds themed icons only through an `index.theme`;
  `LINUX-003` relies on both facts. `linux tray` reads the menu over the
  bus, which works even when the panel's own menu stays empty; prove the
  panel menu with a right click on the icon and a screenshot
  (`gnome-screenshot -f` in the session), and look for `expected "(v)"` in
  the shell's journal.
