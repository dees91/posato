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
for 1.5, and `DOCS-007` the Windows limits drafted in `PLATFORM-002` and
confirmed by the maintainer on 2026-11-04, for 1.6. `ANDROID-003` added
Google Play to Availability and updated the Android limits for 1.6.1. The
Planned platforms section records the maintainer's 2026-09-16 direction for
later releases.

## Availability

Posato 1.6 for Mac is available from GitHub Releases, as `Posato-1.6.0.dmg` for Apple silicon and `Posato-1.6.0-intel.dmg` for Intel Macs, and Posato for iPhone from the App Store. Posato 1.6.1 for Android is available from Google Play; a phone without Google Play can install `Posato-1.6.0.apk` from GitHub Releases. Posato for Linux and Windows is available from GitHub Releases: `posato_1.6.0_arm64.deb` and `posato_1.6.0_amd64.deb` for Ubuntu and, new in 1.6, `Posato-1.6.0-x64.msi` and `Posato-1.6.0-arm64.msi` for Windows 11.

| Platform | Channel | Availability |
| --- | --- | --- |
| macOS | Signed and notarized download (Developer ID) | [GitHub Releases](https://github.com/dees91/posato/releases/latest) |
| iOS | App Store | [App Store](https://apps.apple.com/app/posato/id6812237585) |
| Linux | `.deb` package for arm64 and x86-64 | [GitHub Releases](https://github.com/dees91/posato/releases/latest) |
| Android | Google Play, and a signed APK for phones without Google Play | [Google Play](https://play.google.com/store/apps/details?id=app.posato.android), [GitHub Releases](https://github.com/dees91/posato/releases/latest) |
| Windows | MSI package for x64 and arm64 | [GitHub Releases](https://github.com/dees91/posato/releases/latest) |

Posato for Mac is a signed and notarized download from GitHub Releases.
Versions with the updater can check for new releases after you agree, or
when you choose Check for updates. Downloading and installing requires your
action, and installation waits until your session has ended. To move from
Posato 1.0 to the first version with updates, download the new DMG, quit
Posato, replace the application, and open it again. Manual downloads remain
available if an in-app update cannot complete.

Posato for Android installed from Google Play updates there. Posato for
Linux and Windows, and the Android APK from GitHub Releases, do not check for
updates. To update, download the new package from GitHub Releases and install
it over the old one, the same way as the first time.

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
  quietly at login. If Posato quits, or the Mac sleeps, wakes, or changes
  network, a pause you started yourself stops blocking until you resume it in Posato.
  Without the setup, starting or
  resuming needs administrator approval each time. A session received from
  your iPhone blocks on the Mac only after you resume it there; without the
  setup, that also needs administrator approval.
- On macOS, schedules start only on a Mac that finished the setup and only
  while Posato is running. If Posato opens, including at login, or the Mac
  wakes during a scheduled pause, the pause starts again on its own, usually
  within a minute, and still ends at its planned time. Posato does not ask
  for a password. Quitting Posato stops new scheduled starts until it opens again; closing the window
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
  pause that blocks websites has ended may stay blank. This appears to be how iOS
  behaves when the restriction is lifted, and Posato cannot avoid it. The
  next page you open loads.
- On Linux, website blocking maps each paused website and its `www` name to
  `0.0.0.0` and `::` in `/etc/hosts`, so connections to it fail. A browser
  that uses its own DNS over HTTPS, a visit to the site by its IP address, or
  another name of the same site bypasses this blocking. There is no pause
  page.
- On Linux, Posato installs a system service once, with your password. While
  a pause runs, the service ends the paused apps you chose, so unsaved work in
  them can be lost. It clears the pause at its end time even when Posato is
  closed. Removing the package clears an active pause. An app whose desktop
  entry starts a script, an interpreter, or Flatpak cannot be paused.
- On Linux, schedules start only while Posato is running. Closing its window
  keeps it running with an icon in the status area, and **Open Posato at
  login** in About Posato starts it in the background when you log in.
  On a desktop whose status area does not show app icons, such as GNOME
  without the AppIndicator extension, there is no icon. Posato then stays
  in the background only while a pause runs or a schedule is on, and you
  open it again from the applications menu. Quitting Posato stops new
  scheduled starts.
- On Windows, website blocking stops connections to the addresses that a
  paused website's name resolves to. A site that shares addresses with
  other sites, such as one behind a large content network, can block
  those sites too. A site that moves to new addresses can open until
  Posato detects them, within about a minute. A browser with its own DNS over
  HTTPS can reach addresses Posato has not seen, and a connection already
  open when the pause starts stays open. A VPN or a proxy bypasses this
  blocking, and there is no pause page.
- On Windows, the installer sets up a system service with administrator
  approval. While a pause runs, the service ends the paused apps you
  chose, so unsaved work in them can be lost. It clears the pause at its
  end time even when Posato is closed, and uninstalling Posato clears an
  active pause. Microsoft Store apps and shortcuts that start a script or
  a command line cannot be paused. Posato pauses apps and websites only for
  the Windows account that installed it. The installer is not signed yet, so Windows SmartScreen
  warns before it runs; choose **More info**, then **Run anyway**. With Smart
  App Control on, Windows blocks the unsigned installer, and Posato cannot be
  installed until the packages are signed.
- On Windows, schedules start only while Posato is running. Closing its
  window keeps it running with an icon in the notification area, and
  **Open Posato at login** in About Posato starts it in the background when
  you log in. Quitting Posato stops new scheduled starts.
- On Android, website blocking runs as a local VPN that only answers name
  lookups. Another VPN cannot run at the same time, strict Private DNS and a
  browser with its own DNS over HTTPS bypass it, and lookups over TCP are not
  filtered. There is no pause page.
- On Android, a paused app is covered by a pause screen; it can be visible
  for up to about a second before the screen appears. The launcher, Settings,
  the phone app, and Posato cannot be paused. Posato needs usage access,
  display over other apps, notifications, and exact alarms; without them,
  blocking or schedules may not run. From 1.6.1 it no longer needs to be
  excluded from battery optimization; the 1.6.0 APK from GitHub Releases
  still does.
- A website that opens briefly during a pause (release 1.6) follows each
  device's own clock and time zone, so devices in different time zones open
  it at different moments. When its window ends, new page loads are
  blocked. On Linux, Android, and Windows, a connection that is already
  open, or an address the browser or system remembered, can keep the site
  working for a while; on macOS, Posato also closes the connections it
  relays for that site. On iPhone and iPad, the system decides when
  Posato's background extension runs. A window can open up to about a
  minute late, and if the system never runs the extension at the window's
  end, the website stays open until the extension next runs or you open
  Posato. Safari on iPhone and iPad shows the system's restricted page,
  which does not say when the website opens; Session in Posato does.
  Clearing a rule on another device takes effect on an iPhone or iPad only
  when Posato opens there, and on Linux or Windows only while Posato runs;
  until then, or until the pause ends, the website keeps opening on its old
  rule.
- A device on an earlier version of Posato stops syncing once another device
  takes a break in a scheduled pause or sets or clears a rule for a website
  to open briefly. Update Posato on every device to keep them in sync.
- A break lasts 5, 10, or 15 minutes, and each scheduled pause allows one.
  It reaches your other devices only when they sync, so a device that has
  not synced keeps restricting until the break reaches it or ends. On iPhone
  and iPad with Posato closed, restrictions can return a few minutes after
  the break ends, because the system decides when Posato's background
  extension runs. When the system cannot schedule that return, Posato does
  not start the break. On Android, Linux, and Windows, restrictions return within about
  a minute of the device next waking if it slept through the break's end.
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
  valid for 10 minutes. You can scan it as a QR code, open it as a link, or
  type it. Anyone who has the code or the link, and also access to the
  folder or its file history, can open the workspace, so share the code and
  the link only with your own devices. There is no way to remove a single
  device: to stop sharing with one, remove the workspace and create a new one
  in another folder.
- In folder mode, each device keeps the workspace key in a file that only
  your user account can read, not in the Keychain. On Android it is protected by the
  Android Keystore.
- On Android, the folder must be a local folder that another app keeps in
  sync, such as Syncthing; most cloud storage apps for Android do not keep a
  local folder in sync. From 1.6.1 you choose it in Android's folder picker,
  and Posato can access only that folder; after an update from 1.6.0, Posato
  asks you to choose it again. On iPhone, Posato syncs the folder when you
  open Posato.
- An exported `.posato` file holds pause set names, websites and their
  rules for opening briefly, the default set, and schedules, never app
  choices. If you export without a code, anyone who gets the
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
- From Posato 1.6 for Windows: Windows 11 on x64 and arm64, as an MSI
  package.

Apple's last security update for macOS 13 Ventura was 13.7.8, released on
20 August 2025. Posato keeps supporting Intel Macs on macOS 13 while its
build tools still produce and run that version, which is checked for every
release; ending that support is announced one release ahead.

## Planned platforms

No other platform is planned yet. Intel Macs on macOS 13 or later are
supported from Posato 1.3, Linux and Android from Posato 1.5, and Windows
from Posato 1.6 (see Supported platforms); the
[MVP scope](mvp-scope.md#platform-order-and-support-baseline) records that
platforms after the Mac and iPhone followed later and did not gate the
first release.
