# Limits and Supported Platforms

## Status

- **Status:** Accepted
- **Accepted:** 2026-09-16
- **Provenance:** `user-confirmed`

The Availability, Limits, and Supported platforms sections were relocated
verbatim from the root README as accepted in `RELEASE-001`; `RELEASE-002` and
`RELEASE-003` and `RELEASE-004` updated Availability for the 1.0, 1.1, and 1.2 releases, and `DOCS-004` and `DOCS-005` for 1.3 and 1.4; the README keeps a
summary and links here. `DOCS-006` added the Linux, Android, and folder sync
limits accepted with [ADR 0010](../decisions/0010-linux-android-and-folder-workspace.md)
for 1.5. The Planned platforms section records the maintainer's 2026-09-16
direction for later releases.

## Availability

Posato 1.5 for Mac is available from GitHub Releases, as `Posato-1.5.0.dmg` for Apple silicon and `Posato-1.5.0-intel.dmg` for Intel Macs, and Posato for iPhone from the App Store. Posato for Linux and Android is new in 1.5 and available from GitHub Releases: `posato_1.5.0_arm64.deb` and `posato_1.5.0_amd64.deb` for Ubuntu, and `Posato-1.5.0.apk` for Android.

| Platform | Channel | Availability |
| --- | --- | --- |
| macOS | Signed and notarized download (Developer ID) | [GitHub Releases](https://github.com/dees91/posato/releases/latest) |
| iOS | App Store | [App Store](https://apps.apple.com/app/posato/id6812237585) |
| Linux | `.deb` package for arm64 and x86-64 | [GitHub Releases](https://github.com/dees91/posato/releases/latest) |
| Android | Signed APK, installed from outside Google Play | [GitHub Releases](https://github.com/dees91/posato/releases/latest) |

Posato for Mac is a signed and notarized download from GitHub Releases.
Versions with the updater can check for new releases after you agree, or
when you choose Check for updates. Downloading and installing requires your
action, and installation waits until your session has ended. To move from
Posato 1.0 to the first version with updates, download the new DMG, quit
Posato, replace the application, and open it again. Manual downloads remain
available if an in-app update cannot complete.

Posato for Linux and Android does not check for updates. To update, download
the new package from GitHub Releases and install it over the old one, the
same way as the first time.

The release verdict is recorded in the
[1.1 release record](../tasks/executions/release-003-release-1-1.md).
Posato can also be [built from source](../development/README.md#build-from-source)
for development and evaluation.

## Limits

Posato adds deliberate friction; it is not a lock you cannot open.

- It does not resist a device administrator and can always be removed. Ending a
  session early is always possible.
- On macOS, blocking works only while Posato is running. Closing its window
  keeps it running in the menu bar, and after the one-time setup Posato opens
  quietly at login. If Posato quits, or after sleep, wake, or a network change,
  a pause you started yourself stops blocking until you resume it in Posato.
  Without the setup, starting or
  resuming needs administrator approval each time. A session received from
  your iPhone blocks on the Mac only after you resume it there; without the
  setup, that also needs administrator approval.
- On macOS, schedules start only on a Mac that finished the setup and only
  while Posato is running. If Posato opens, including at login, or the Mac
  wakes during a scheduled pause, the pause starts again on its own, usually
  within a minute, and still ends at its planned time; no password is asked. Quitting
  Posato stops new scheduled starts until it opens again; closing the window
  does not.
- On macOS, paused apps are quit while a session is active, including apps that
  were already open when it started, so unsaved work in them can be lost.
- On macOS, website blocking covers **Safari** and **Google Chrome Stable** for
  web traffic on ports 80 and 443 while they use the system proxy settings.
  Firefox, other browsers, in-app browsers, and apps that bypass the system
  proxy are not covered. Visiting a paused site by its IP address is not
  blocked, and iCloud Private Relay can bypass blocking without being detected.
  A session does not start blocking while a VPN or a manually configured proxy
  is active. Connections to servers on the Mac itself (`localhost`,
  `127.0.0.1`, and `::1`) keep working during a session. Pages already loaded, cached, or downloaded are not erased, and the
  pause page may occasionally not appear even though the site stays blocked.
- On macOS, Posato uses a background helper that requires administrator approval
  and may ask for Automation permission to show its pause page in the current
  tab.
- On iPhone, the system clears restrictions after a session ends and may keep
  them for a while past the end time. Restrictions from sessions shorter than
  15 minutes clear only when Posato is open at the end or when you open it
  again.
- On iPhone, if Safari was already open, the first page it opens after a
  pause that blocks websites ends may stay blank. This appears to be how iOS
  behaves when the restriction is lifted, and Posato cannot avoid it. The
  next page you open loads.
- On Linux, website blocking maps each paused website and its `www` name to
  `0.0.0.0` and `::` in `/etc/hosts`, so connections to it fail. There is no pause
  page. A browser that uses its own DNS over HTTPS, visiting a site by its IP
  address, or another name of the same site bypasses it.
- On Linux, Posato installs a system service once, with your password. While
  a pause runs, the service ends the paused apps you chose, so unsaved work in
  them can be lost. It clears the pause at its end time even when Posato is
  closed. Removing the package clears an active pause. An app whose desktop
  entry starts a script, an interpreter, or Flatpak cannot be paused.
- On Linux, schedules start only while Posato is running. Closing its window
  keeps it running with an icon in the status area, and **Open Posato at
  login** in About Posato starts it in the background when you log in.
  On a desktop whose status area does not show app icons, such as GNOME
  without the AppIndicator extension, there is no icon; Posato then stays
  in the background only while a pause runs or a schedule is on, and you
  open it again from the applications menu. Quitting Posato stops new
  scheduled starts.
- On Android, website blocking runs as a local VPN that only answers name
  lookups. Another VPN cannot run at the same time, strict Private DNS and a
  browser with its own DNS over HTTPS bypass it, and lookups over TCP are not
  filtered. There is no pause page.
- On Android, a paused app is covered by a block screen; it can be visible
  for up to about a second before the screen appears. The launcher, Settings,
  the phone app, and Posato cannot be paused. Posato needs usage access,
  display over other apps, notifications, exact alarms, and to be excluded
  from battery optimization; without them, blocking or schedules may not run.
- Schedule times follow each device's own clock. A schedule is at least 15
  minutes long, and on iPhone it needs Screen Time access.
- A device on an earlier version of Posato stops syncing once another device runs
  Posato 1.3. Update Posato on every device to keep them in sync.
- Sync is best effort. Posato cannot promise when, or whether, a change reaches
  your other device, and it cannot wake a sleeping device.
- If every copy of the workspace key is lost, synchronized data cannot be
  recovered. Data already copied to another device cannot be erased remotely.
- Folder sync works through a folder that a service you already use keeps in
  sync, such as Dropbox, OneDrive, iCloud Drive, or Syncthing. Posato writes
  only encrypted files there, but it cannot control when, or whether, that
  service delivers them, and anyone who can delete files in the folder can
  stop your devices from syncing.
- A device joins a folder workspace with a one-time pairing code that is
  valid for 10 minutes, scanned as a QR code, opened as a link, or typed.
  Someone with the code or the link and access to the folder, or to its file
  history, can open the workspace, so share the link only with your own
  devices. There is no way to remove a single
  device: to stop sharing with one, remove the workspace and create a new one
  in another folder.
- In folder mode, each device keeps the workspace key in a file that only
  your user can read, not in the Keychain. On Android it is protected by the
  Android Keystore.
- On Android, the folder must be a local folder that another app keeps in
  sync, such as Syncthing; most cloud storage apps for Android do not keep a
  local folder in sync. On iPhone, Posato syncs the folder when you open it.
- An exported `.posato` file holds pause set names, websites, the default
  set, and schedules, never app choices. Without a code, anyone who gets the
  file can read it. The nine-character code stops a casual reader, not
  someone who spends a lot of computing power on guessing. An import is a
  one-time copy, not sync, and imported schedules arrive switched off.
- A workspace syncs through iCloud or through one folder, never both, and
  there is no move between them: remove the iCloud workspace before choosing
  a folder.

## Supported platforms

- Posato 1.2 for Mac: macOS 15 or later on Apple silicon.
- From Posato 1.3 for Mac: macOS 13 or later on Apple silicon, and macOS 13
  or later on Intel Macs through a separate Intel download. The Intel build
  refuses to open on Apple silicon and points to the Apple silicon download.
- iOS 18 or later.
- From Posato 1.5 for Linux: Ubuntu 24.04 or later on arm64 and x86-64, as a
  `.deb` package.
- From Posato 1.5 for Android: Android 13 or later.

Apple's last security update for macOS 13 Ventura was 13.7.8, released on
20 August 2025. Posato keeps supporting Intel Macs on macOS 13 while its
build tools still produce and run that version, which is checked for every
release; ending that support is announced one release ahead.

Posato targets the current and previous major versions of macOS and iOS.
The [1.2 release record](../tasks/executions/release-004-release-1-2.md) and the
earlier [platform matrix check](../tasks/executions/quality-007-platform-matrix.md)
cover both lines:

| System | What was checked |
| --- | --- |
| macOS 15 | The notarized Posato 1.2.0 on macOS 15.6.1 (the newest macOS 15 restore image) in a virtual machine on Apple silicon: installation from the disk image, the one-time setup, a pause without a password, a schedule that started after the Mac restarted and Posato opened at login, the start notice, and early end from the menu bar |
| macOS 26 | The notarized Posato 1.2.0 in virtual machines on macOS 26.6: the same flow on two Macs linked through iCloud, with a schedule synced to the other Mac and started on both, early end reaching the other Mac, and the notice that a pause started on another Mac; Posato 1.1.0 replaced by 1.2.0 with its websites and iCloud workspace kept, the one-time setup offer, and a device still on 1.1 pausing its sync until it updated |
| macOS 13 (Intel build) | Candidates of Posato 1.3 for Intel Macs, run under Rosetta in a macOS 13.6 virtual machine on Apple silicon, not on an Intel Mac: installation from the disk image, the one-time setup without a restart, a pause without a password, the pause page and the application picker, a schedule that started after the Mac restarted and Posato opened at login, the start notice, early end from the menu bar, removal of the background helper, and an in-app update between two Intel candidates. Sync with another Mac was checked for the same Intel build under Rosetta on macOS 15.6.1, because a macOS 13 virtual machine cannot sign in to an Apple Account |
| macOS 13 and 14 (Apple silicon) | Not checked separately; supported by the maintainer's decision on the strength of the macOS 13 build target and the checks on macOS 15 and 26 |
| iOS 18 | Posato 1.1.0 from TestFlight, checked by hand on an iPhone with iOS 18: websites and apps, blocking, and early end. Posato 1.2 was not checked on iOS 18 |
| iOS 26 | Posato 1.2 on a test iPhone with iOS 26.5: the core flow, blocking, and unblocking, and a schedule that started and ended while Posato was closed; with a Mac, a schedule made on the Mac starting on the iPhone, early end reaching the Mac, and the pause notices ([unattended verification](../tasks/executions/quality-010-unattended-verification.md)) |

Later macOS 15 updates were not checked separately.

Not verified for the Intel build: behavior that differs between Rosetta and
a real Intel processor, including the check that recognizes a native Intel
Mac; sync on macOS 13; and whether **Download** in the Apple silicon notice
opens the download page.

The core flow was [verified end to end on one Mac and one iPhone](../tasks/executions/mvp-001-end-to-end-acceptance.md),
including blocking, early end from either device, expiry, relaunch, and a
device that was offline during a session.

## Planned platforms

| Platform | Status |
| --- | --- |
| Windows desktop | Planned for a later release |

No dates are committed. Intel Macs on macOS 13 or later are supported from
Posato 1.3, and Linux and Android from Posato 1.5 (see Supported platforms). The current release covers only the
platforms above; the [MVP scope](mvp-scope.md#platform-order-and-support-baseline)
records that these platforms follow later and do not gate it.
