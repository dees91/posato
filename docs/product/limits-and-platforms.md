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

Posato 1.0 is available for Mac; the iPhone app is coming to the App Store.

| Platform | Channel | Availability |
| --- | --- | --- |
| macOS | Signed and notarized download (Developer ID) | [GitHub Releases](https://github.com/dees91/posato/releases/latest) |
| iOS | App Store | Coming soon |

The release verdict is recorded in the
[release candidate record](../tasks/executions/release-002-release-candidate.md).
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

Posato targets the current and previous major versions of macOS and iOS. The
exact release test matrix is still being confirmed.

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
