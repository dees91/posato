# Limits and Supported Platforms

## Status

- **Status:** Accepted
- **Accepted:** 2026-09-16
- **Provenance:** `user-confirmed`

The Availability, Limits, and Supported platforms sections were relocated
verbatim from the root README as accepted in `RELEASE-001`, and `RELEASE-002`
updated Availability for the 1.0 release; the README keeps a
summary and links here. The Planned platforms section records the maintainer's
2026-09-16 direction for later releases.

## Availability

Posato 1.1 is available for Mac and iPhone.

| Platform | Channel | Availability |
| --- | --- | --- |
| macOS | Signed and notarized download (Developer ID) | [GitHub Releases](https://github.com/dees91/posato/releases/latest) |
| iOS | App Store | [App Store](https://apps.apple.com/app/posato/id6812237585) |

Posato for Mac is a signed and notarized download from GitHub Releases.
Versions with the updater can check for new releases after you agree, or
when you choose Check for updates. Downloading and installing requires your
action, and installation waits until your session has ended. To move from
Posato 1.0 to the first version with updates, download the new DMG, quit
Posato, replace the application, and open it again. Manual downloads remain
available if an in-app update cannot complete.

The release verdict is recorded in the
[1.1 release record](../tasks/executions/release-003-release-1-1.md).
Posato can also be [built from source](../development/README.md#build-from-source)
for development and evaluation.

## Limits

Posato adds deliberate friction; it is not a lock you cannot open.

- It does not resist a device administrator and can always be removed. Ending a
  session early is always possible.
- On macOS, blocking works only while Posato is running. If Posato quits, or
  after sleep, wake, or a network change, blocking stops until you resume it in
  Posato with administrator approval. A session received from your iPhone also
  needs that approval before the Mac blocks anything.
- On macOS, paused apps are quit while a session is active, including apps that
  were already open when it started, so unsaved work in them can be lost.
- On macOS, website blocking covers **Safari** and **Google Chrome Stable** for
  web traffic on ports 80 and 443 while they use the system proxy settings.
  Firefox, other browsers, in-app browsers, and apps that bypass the system
  proxy are not covered. Visiting a paused site by its IP address is not
  blocked, and iCloud Private Relay can bypass blocking without being detected.
  A session does not start blocking while a VPN or a manually configured proxy
  is active. Pages already loaded, cached, or downloaded are not erased, and the
  pause page may occasionally not appear even though the site stays blocked.
- On macOS, Posato uses a background helper that requires administrator approval
  and may ask for Automation permission to show its pause page in the current
  tab.
- On iPhone, the system clears restrictions after a session ends and may keep
  them for a while past the end time. Restrictions from sessions shorter than
  15 minutes clear only when Posato is open at the end or when you open it
  again.
- Sync is best effort. Posato cannot promise when, or whether, a change reaches
  your other device, and it cannot wake a sleeping device.
- If every copy of the workspace key is lost, synchronized data cannot be
  recovered. Data already copied to another device cannot be erased remotely.

## Supported platforms

- macOS 15 or later on Apple silicon.
- iOS 18 or later.

Posato targets the current and previous major versions of macOS and iOS.
The [platform matrix check](../tasks/executions/quality-007-platform-matrix.md)
covers both lines:

| System | What was checked |
| --- | --- |
| macOS 15 | A pre-release 1.1 build on macOS 15.6.1 (the newest macOS 15 restore image) in a virtual machine on Apple silicon: setup with iCloud and the helper, websites and apps, blocking, relaunch, early end, expiry, and sync with a Mac on macOS 26 in both directions |
| macOS 26 | The same flow in virtual machines on macOS 26.6 ([unattended verification](../tasks/executions/quality-010-unattended-verification.md)) |
| iOS 18 | Posato 1.0.0 from the App Store, checked by hand on an iPhone with iOS 18 |
| iOS 26 | The core flow, blocking, and unblocking on a test iPhone with iOS 26.5 ([unattended verification](../tasks/executions/quality-010-unattended-verification.md)) |

Later macOS 15 updates were not checked separately.

The core flow was [verified end to end on one Mac and one iPhone](../tasks/executions/mvp-001-end-to-end-acceptance.md),
including blocking, early end from either device, expiry, relaunch, and a
device that was offline during a session.

## Planned platforms

| Platform | Status |
| --- | --- |
| Intel Macs (x86-64) | Planned for a later release |
| Android | Planned for a later release |
| Linux desktop | Planned for a later release |
| Windows desktop | Planned for a later release |

No dates are committed. The current release covers only the two platforms
above; the [MVP scope](mvp-scope.md#platform-order-and-support-baseline)
records that these platforms follow later and do not gate it.
