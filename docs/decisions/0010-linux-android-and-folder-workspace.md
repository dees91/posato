# ADR 0010: Bring Posato to Linux and Android with a Folder Workspace

## Status

- **Status:** Proposed
- **Date:** 2026-10-10
- **Decision owner:** Project maintainer
- **Provenance:** the direction is `user-confirmed` (2026-10-10, release 1.5
  composition): Linux and Android, synchronization through a user-selected
  folder, the Mac desktop application in the same workspace, a one-time
  pairing code without device revocation, and Android application blocking
  without an accessibility service. The mechanisms below are
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
| Linux | `:desktopApp` on the JVM, `.deb` for Ubuntu 24.04 or later; arm64 is built and verified here, the x86-64 package needs an x86-64 build machine (`RELEASE-007`) | Root helper writes a marked block in `/etc/hosts` that maps each blocked host and its `www` counterpart to `0.0.0.0` and `::`, so connections fail | Chosen from desktop entries; the helper ends matching processes while a pause runs | A systemd system service, installed once through `pkexec`; the application talks to it over a Unix socket owned by the installing user, mode `0600` |
| Android | New `:androidApp`, Android 13 (API 33) or later | Local `VpnService` that only carries DNS to one IPv4 address, answers blocked names with no address, and forwards the rest over protected sockets to the DNS of the network underneath | Usage access (`UsageStatsManager`) polled by a foreground service, which opens a full-screen block screen over a blocked application; the launcher, Settings, the phone, and Posato are never offered for blocking | Runtime grants: VPN consent, usage access, display over other apps, notifications, exact alarms, and the battery-optimization exemption; a `specialUse` foreground service runs while a pause or an enabled schedule exists |
| macOS | Unchanged | Unchanged | Unchanged | Unchanged |

The Linux helper is a small Kotlin JVM program shipped in the package at a
root-owned path and started by systemd with the bundled runtime. Its socket
lives in the root-owned `/run/posato`; every connection is checked with
`SO_PEERCRED` against the uid recorded at installation. It accepts three
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
never a process of root or of another user. The package's pre-removal script
clears the block and stops the service. The helper never reads browsing and
never logs hosts.

Android has no root and no accessibility service. A blocked application can
be visible for up to one polling interval before the block screen covers it.

### Folder workspace

A workspace has exactly one transport: CloudKit (iOS and the Mac, as today)
or one folder. The Mac and Linux desktop applications and the Android
application offer the folder. iOS stays on CloudKit.

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
  directory, like an SSH private key; the Mac does the same in folder mode.
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
