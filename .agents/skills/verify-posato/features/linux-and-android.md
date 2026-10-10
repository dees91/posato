# Linux and Android

Posato on Linux (a `.deb` for Ubuntu 24.04 or later) and Android (13 or
later) shows the same Session, Pause sets, and Schedules screens as the Apple
applications. During a pause, Linux maps each blocked website and its `www`
name to no address in `/etc/hosts` and ends the chosen applications, through
a root service installed once with the person's approval. Android answers
blocked names with no address through a local DNS-only VPN, and covers a
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

Preconditions: Linux uses the clone from `$PC linux create` with the package
inputs built (`./gradlew :desktopApp:linuxPackageInputs`), and no macOS clone
runs at the same time. Android uses the emulator named by
`posato.android.serial`, never one someone else works on, with
`./gradlew :androidApp:assembleDebug` built.

- **Linux install and onboarding:** `$PC linux install`, `$PC linux launch`,
  then `$PC linux click --text "Make some space" --timeout-seconds 90`,
  `click --text Continue --exact`, `click --text "Allow pausing on this
  computer"`, and `$PC linux wait --text "Pausing service installed"
  --timeout-seconds 60`. Proof: the wait succeeds and `$PC linux exec
  --script 'systemctl is-active posato-helper'` prints `active`.
- **Linux website pause:** add `example.org`, start a pause, and wait for
  `SESSION ACTIVE`. Proof: `exec --script 'grep -c "# posato$" /etc/hosts'`
  prints `4`, `getent ahosts example.org` starts with `0.0.0.0` or `::`, and
  `curl` to it fails. After **End session early** the file equals a copy taken
  before the run.
- **Linux application:** **Manage apps**, **Apps**, **Choose apps**, tick
  Calculator, **Choose**. Proof: during a pause a started
  `/usr/bin/gnome-calculator` is gone within seconds; after the end it stays.
  Count processes by `readlink /proc/<pid>/exe`, because `pgrep -x` sees a
  name cut to 15 characters.
- **Linux self-clear:** start a pause, `exec --script 'pkill -x posato; sudo
  -n date -s "+30 minutes"'`. Proof: `/etc/hosts` is restored within seconds,
  and after `$PC linux launch` the screen shows "The session ended at". Move
  the clock back afterwards.
- **Android install and onboarding:** `$PC android install` (it gives the
  grants a person gives in Settings), `$PC android launch`, then tap through
  the first-run flow with `$PC android tap --text <label>`. Proof: Session
  shows `Start a session`.
- **Android website pause:** start a pause with `example.org`. Proof:
  `$PC android shell --script 'ping -c 1 -W 2 example.org'` names
  `127.0.0.1` or `0.0.0.0`, another domain resolves to a public address, and
  after **End session early** example.org resolves to a public address again.
- **Android application:** choose Clock in **Choose apps**, start a pause,
  then `shell --script 'am start -n com.google.android.deskclock/com.android.deskclock.DeskClock'`.
  Proof: `$PC android wait --text "Paused by Posato"`.
- **Android background:** add a schedule that starts in three minutes, press
  Home, and wait. Proof: the DNS answer turns blocked at the start; repeat
  across `adb reboot`.

## Gotchas

- Do not run the Linux clone together with a macOS clone; the Linux guest has
  crashed under that load. Run cross-device phases with one kind at a time.
- Text recognition on Linux reads a screenshot; type a pairing code in lower
  case and wait a second after clicking a field, or the first characters can
  be lost.
- `pkill -f posato` inside `linux exec` matches the shell that runs it; use
  `pkill -x posato`.
- On Android the on-screen keyboard can hide buttons: press
  `KEYCODE_ESCAPE` before tapping, and let `tap` scroll to the target.
- Android private DNS set to strict mode, and a browser with its own DNS over
  HTTPS, bypass website blocking by design; the emulator's default settings
  do not.
