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
| Linux | `:desktopApp` on the JVM, `.deb` for Ubuntu 24.04 or later, x86-64 and arm64 | Root helper writes a marked block in `/etc/hosts` for each blocked host and its `www` counterpart | Chosen from desktop entries; the helper ends matching processes while a pause runs | A systemd system service, installed once through `pkexec`; the application talks to it over a Unix socket owned by the installing user, mode `0600` |
| Android | New `:androidApp`, Android 13 (API 33) or later | Local `VpnService` that only carries DNS and answers blocked names with no address | Usage access (`UsageStatsManager`) polled by a foreground service, which opens a full-screen block screen over a blocked application | Runtime grants: VPN consent, usage access, display over other apps, notifications |
| macOS | Unchanged | Unchanged | Unchanged | Unchanged |

The Linux helper is a small Kotlin JVM program shipped in the package and
started by systemd with the bundled runtime. It accepts one request type:
the complete desired state (hosts, executable matchers, session end time)
or a clear. It keeps that state in a root-owned file and clears it by itself
at the session end, so a stopped application cannot leave a pause applied
past its end. It never reads browsing, never logs hosts, and restores the
exact `/etc/hosts` content outside its marked block.

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
  new names. Replay, duplication, tampering, and wrong-context files are
  rejected by the existing ADR 0006 validation. A folder writer can still
  delete files; deletion is not detected, as ADR 0002 already states.
- **Pairing.** A member shows a one-time code of 128 random bits as 26
  base32 characters and a QR code. The offer file holds the workspace key
  and context ids encrypted with AES-GCM under a key derived from the code
  with HKDF-SHA256; its name is also derived from the code, so the joining
  device needs only the code and the folder. The offer expires after
  10 minutes. The joiner deletes it after a successful read and the member
  deletes it at expiry or when the code is dismissed. The code is never
  written to disk, logged, or put in diagnostics.
- **Key storage.** Android wraps the workspace key with a non-exportable
  AES key in Android Keystore. Desktop hosts keep it in a file readable
  only by the user (`0600`, directory `0700`) in the application data
  directory, like an SSH private key. This is weaker than the Keychain and is
  stated on the limits page.
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
- A leaked pairing code within its 10 minutes, or a stolen device, gives
  lasting access until the workspace is replaced. A folder writer can deny
  service by deleting files.
- Website blocking on Linux and Android is exact-name DNS denial with no
  pause page. A browser with its own DNS over HTTPS, or Android's strict
  Private DNS, bypasses it; the limits page says so.
- Verification extends `posato-control` with an Android emulator target and
  a Linux Tart guest, and shares one host folder between guests to stand in
  for a synchronization service.
