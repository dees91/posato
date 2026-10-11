# ADR 0010: Bring Posato to Linux and Android with a Folder Workspace

## Status

- **Status:** Proposed
- **Date:** 2026-10-10
- **Decision owner:** Project maintainer
- **Provenance:** the direction is `user-confirmed` (2026-10-10, release 1.5
  composition): Linux and Android, synchronization through a user-selected
  folder, the Mac desktop application in the same workspace, a one-time
  pairing code without device revocation, Android application blocking
  without an accessibility service, and (later the same day) iOS in folder
  mode as well. The mechanisms below are
  `agent-delegated` and need `user-confirmed` before this ADR is accepted.
- **Owners:** `PLATFORM-001` decides; `SYNC-018`, `LINUX-001`, and
  `ANDROID-001` deliver.

## Context

[ADR 0002](0002-synchronization-trust-and-workspace-modes.md) reserves a
portable workspace over one user-selected folder for Apple, Android, and
Linux, with independent device identities, signed membership, per-device
key wrapping, and revocation. [ADR 0003](0003-mvp-application-architecture-baseline.md)
keeps Android and Linux out of the Apple MVP. [ADR 0006](0006-apple-mvp-encrypted-operation-and-convergence.md)
defines a transport-neutral encrypted bundle and a deterministic reducer, and
its writers already use a fresh author identity per writer incarnation, so
the bundle format needs no device identity.

The maintainer wants Linux and Android now, kept simple: the folder is kept
in sync by a service the person already uses, and Posato does not care which.

## Decision

### Platforms and order

Linux and Android join release 1.5. Windows follows later (`PLATFORM-002`).
All platforms share the Compose interface in `:shared`.

| Platform | Host | Websites | Applications | Privilege |
| --- | --- | --- | --- | --- |
| Linux | `:desktopApp` on the JVM, `.deb` for Ubuntu 24.04 or later; arm64 is built and verified here, the x86-64 package needs an x86-64 build machine (`RELEASE-007`) | Root helper writes a marked block in `/etc/hosts` that maps each blocked host and its `www` counterpart to `0.0.0.0` and `::`, so connections fail | Chosen from desktop entries; the helper ends matching processes while a pause runs | A systemd system service, installed once through `pkexec`; the application talks to it over a Unix socket in the root-owned `/run/posato`, which accepts only the installing user and root by their peer credentials |
| Android | New `:androidApp`, Android 13 (API 33) or later | Local `VpnService` that only carries DNS to one IPv4 address, answers blocked names with `0.0.0.0` and `::`, so connections fail, and forwards the rest over protected sockets to the DNS of the network underneath | Usage access (`UsageStatsManager`) polled by a foreground service, which opens a full-screen block screen over a blocked application, also one already in front when the pause starts; the launcher, Settings, the phone, and Posato are never offered for blocking | Runtime grants: VPN consent, usage access, display over other apps, notifications, exact alarms, and the battery-optimization exemption; a `specialUse` foreground service runs while a pause or an enabled schedule exists |
| macOS | Unchanged | Unchanged | Unchanged | Unchanged |

The Linux helper is a small Kotlin JVM program shipped in the package at a
root-owned path and started by systemd with the bundled runtime. Its socket
lives in the root-owned `/run/posato` and is open to every local user; every
connection is checked with `SO_PEERCRED` against the user recorded at
installation and root, and a connection that does not send its request
within five seconds is closed. It accepts three
requests:

- **apply**: session id, session end, hosts, and executable paths. Each host
  must be lowercase letters, digits, hyphens, and dots (punycode for other
  names), at most 253 characters, and at most 4,096 entries in total;
  anything else refuses the whole request.
- **clear**.
- **status**: the applied session id, if any, and the id of a session the
  helper ended by itself at its end time and that the application has not
  acknowledged yet.

The helper keeps its state in a root-owned file and clears by itself at the
session end, so a stopped application cannot leave a pause applied past its
end. The application maps `status`, `holdsSession`, and the suspended-expiry
calls of `EnforcementPort` onto that status; `adopt` needs no request.
`/etc/hosts` is rewritten atomically with its mode and owner kept; every
marked block, including a duplicated or unterminated one, is removed before
the new block is written, and nothing outside the markers changes. While a
pause runs, the helper ends only processes owned by the installing uid whose
`/proc/<pid>/exe` is one of the applied paths or lies in the applied snap,
never a process of root or of another user. Only a native program that a
desktop entry starts itself, or a whole snap, can be chosen: an entry that
starts an interpreter, a launcher such as `flatpak`, or a script is not
offered, because ending it would end every program it runs. The package's pre-removal script
clears the block and stops the service. The helper never reads browsing and
never logs hosts.

Android has no root and no accessibility service. A blocked application can
be visible for up to one polling interval before the block screen covers it.

### Folder workspace

A workspace has exactly one transport: CloudKit (iOS and the Mac, as today)
or one folder. Every application offers the folder: the Mac, Linux, Android,
and iOS. On iOS the person picks the folder in the Files picker, from any
location a File Provider offers (iCloud Drive, Dropbox, OneDrive, and
others); Posato keeps a security-scoped bookmark to it and reads and writes
through file coordination. The iOS application synchronizes the folder when
it comes to the front; it cannot poll while suspended, and background
refresh is left to a later release.

Layout under the chosen folder, all names fixed by Posato:

```text
Posato/
  workspace          plain: magic, format, workspace id, transport epoch, key epoch
  bundles/<id>.pbundle   one ADR 0006 encrypted bundle per file, id = 32 hex chars
  pairing/<id>.ppair     at most one live pairing offer per device
```

- Writers write a file under a temporary name in the same directory and
  rename it, so a reader never sees a partial bundle. A reader ignores names
  it did not write in this form, such as conflicted copies.
- Readers keep the set of bundle names they have committed and fetch only
  new names, in name order. Replay, duplication, tampering, and
  wrong-context files are rejected by the existing ADR 0006 validation. A
  file whose size or header does not check, such as one still downloading,
  is skipped without being marked, retried on the next pass, and never
  blocks the other files. A folder writer can still delete files; deletion
  is not detected, as ADR 0002 already states.
- A folder gives no change events, so each device polls: every minute while
  the desktop application runs, and on Android when Posato comes to the
  front and every minute while its foreground service runs.
- A device writes bundles only while the `workspace` file it established
  is present and matches. When it disappears or changes, the device stops
  synchronizing with "Sync needs attention" and offers **Remove workspace**
  to clear its local copy; it never re-creates the workspace by itself.
  Creating a workspace refuses when a `workspace` file already exists; two
  devices that create one at the same moment before their folders
  synchronize can still diverge, and the help text says to create the
  workspace on one device first.
- **Pairing.** A member shows a one-time code of 128 random bits as 26
  base32 characters plus one check character, so a typing error is told
  apart from an offer that has not synchronized yet. HKDF-SHA256 derives two
  values from the code with separate labels: the offer file name and an
  AES-GCM key. The offer file holds the workspace key item encrypted under
  that key; the magic, the version, the file name, and the expiry time
  (10 minutes after creation) are its associated data, and the joiner
  refuses an offer past its expiry or for a different `workspace` file. The
  joiner deletes the offer after a successful read; the member deletes it
  when the code is dismissed, and deletes its stale offers at every launch.
  Expiry and deletion are cleanup, not a cryptographic limit: a service that
  keeps file history keeps the offer, so a leaked code plus access to the
  folder or its history opens the workspace key. The code is never written
  to disk, logged, or put in diagnostics.
- **Key storage.** Android wraps the workspace key with a non-exportable
  AES key in Android Keystore. Desktop hosts keep it in a file readable
  only by the user (`0600`, directory `0700`) in the application data
  directory, like an SSH private key; the Mac does the same in folder mode,
  and iOS keeps the file in its container with complete file protection.
  This is weaker than the Keychain and is stated on the limits page.
  Android excludes the key and the database from backup, and a Keystore key
  that is no longer usable makes the device rejoin with a new code.
- **Membership.** Any device with the folder and a valid code becomes a
  member. There are no signed membership operations, per-device key
  wrapping, key rotation, or revocation. To remove a device, the person
  removes the workspace and creates a new one in a new folder. **Remove
  workspace** deletes the local key and replica, and deletes the `Posato`
  directory in the folder.
- There is no migration between CloudKit and a folder. A Mac that already
  syncs with iCloud removes that workspace before it chooses a folder.

### What this revises

This ADR revises ADR 0002 "Portable workspace" for release 1.5: the
independent device identities, signed membership, per-device key wrapping,
key epochs with revocation, and the CloudKit-to-folder migration are
replaced by the pairing code and the limits above. ADR 0003 gains the
Android application, the Linux desktop target, and the Linux helper. ADR
0002's common encrypted layer and ADR 0006's format 1 are unchanged.

### Limits to publish

This text is `agent-delegated`. It moves to the
[limits page](../product/limits-and-platforms.md) only after the maintainer
accepts this ADR, and `RELEASE-007` publishes it with release 1.5.

- On Linux, website blocking maps each paused website and its `www` name to
  `0.0.0.0` and `::` in `/etc/hosts`, so connections to it fail. There is no pause
  page. A browser that uses its own DNS over HTTPS, visiting a site by its IP
  address, or another name of the same site bypasses it.
- On Linux, Posato installs a system service once, with your password. While
  a pause runs, the service ends the paused apps you chose, so unsaved work in
  them can be lost. It clears the pause at its end time even when Posato is
  closed. Removing the package clears an active pause.
- On Android, website blocking runs as a local VPN that only answers name
  lookups. Another VPN cannot run at the same time, strict Private DNS and a
  browser with its own DNS over HTTPS bypass it, and lookups over TCP are not
  filtered. There is no pause page.
- On Android, a paused app is covered by a block screen; it can be visible for
  for up to about a second before the screen appears. On Linux, an app whose
  desktop entry starts a script, an interpreter, or Flatpak cannot be paused. The launcher,
  Settings, the phone, and Posato cannot be paused. Posato needs usage access,
  display over other apps, notifications, exact alarms, and to be excluded
  from battery optimization; without them, blocking or schedules may not run.
- Folder sync works through a folder that a service you already use keeps in
  sync, such as Dropbox, OneDrive, iCloud Drive, or Syncthing. Posato writes
  only encrypted files there, but it cannot control when, or whether, that
  service delivers them, and anyone who can delete files in the folder can
  stop your devices from syncing.
- A device joins a folder workspace with a one-time pairing code that is
  valid for 10 minutes. Someone with the code and access to the folder, or to
  its file history, can open the workspace. There is no way to remove a single
  device: to stop sharing with one, remove the workspace and create a new one
  in another folder.
- In folder mode, each device keeps the workspace key in a file that only
  your user can read, not in the Keychain. On Android it is protected by the
  Android Keystore.
- On Android, the folder must be a local folder that another app keeps in
  sync, such as Syncthing; most cloud storage apps for Android do not keep a
  local folder in sync. On iPhone, Posato syncs the folder when you open it.
- A workspace syncs through iCloud or through one folder, never both, and
  there is no move between them: remove the iCloud workspace before choosing
  a folder.

## Consequences

- One person's Linux, Android, and Mac devices share pauses, pause sets,
  and schedules without a product account or server.
- A leaked pairing code plus access to the folder or its history, or a
  stolen device, gives lasting access until the workspace is replaced. A folder writer can deny
  service by deleting files.
- Website blocking on Linux and Android is exact-name DNS denial with no
  pause page. A browser with its own DNS over HTTPS, or Android's strict
  Private DNS, bypasses it; on Android, DNS over TCP is not filtered and
  another VPN cannot run at the same time. The limits page says so.
- On Android the folder must be a local folder that another application
  keeps synchronized, such as Syncthing; most cloud storage applications
  for Android do not keep a local folder synchronized.
- Verification extends `posato-control` with an Android emulator target and
  a Linux Tart guest, and shares one host folder between guests to stand in
  for a synchronization service.
