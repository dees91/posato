# ADR 0010: Bring Posato to Linux and Android with a Folder Workspace

## Status

- **Status:** Accepted (2026-10-20); delivered by `SYNC-018`, `LINUX-001`, and `ANDROID-001` and verified on Tart Macs, a Tart Ubuntu guest, an Android emulator and phone, and the test iPhone
- **Date:** 2026-10-10; amended 2026-10-27 with the Windows row (`PLATFORM-002`)
- **Decision owner:** Project maintainer
- **Provenance:** the direction is `user-confirmed` (2026-10-10, release 1.5
  composition): Linux and Android, synchronization through a user-selected
  folder, the Mac desktop application in the same workspace, a one-time
  pairing code without device revocation, Android application blocking
  without an accessibility service, and (later the same day) iOS in folder
  mode as well. The mechanisms below were `agent-delegated` and are
  `user-confirmed` with the acceptance on 2026-10-20. The Windows row's
  choices are `user-confirmed` on 2026-10-27: the Windows Filtering Platform
  for websites, the service installed by the per-machine MSI that records
  the installing user, a named pipe open only to that user and the system,
  and Start menu desktop programs only, without Store applications. On
  2026-10-28 the maintainer also accepted, for `PLATFORM-002`, a service
  without a restricted service SID, with least privilege to become one rule
  for every desktop helper (`PLATFORM-004`). The remaining Windows mechanism
  details and limits are `agent-delegated` and wait in the
  [`PLATFORM-002` execution record](../tasks/executions/platform-002-windows.md)
  until the maintainer confirms them.
- **Owners:** `PLATFORM-001` decides; `SYNC-018`, `LINUX-001`, and
  `ANDROID-001` deliver.

## TARGETS-013 window plan amendment

Accepted: proposed on 2026-11-01 (`TARGETS-013`), passed an independent
security review after fixes on 2026-11-01, decided on 2026-11-01 by the
owner's agent under the owner's delegation of 2026-10-31, and `user-confirmed` by the owner on 2026-11-03 in the `TARGETS-013` pull request (#221). The rule and its
bound are in the
[ADR 0006 amendment](0006-apple-mvp-encrypted-operation-and-convergence.md#targets-013-set-domain-window-amendment);
the segment model and the cap are the same as in the
[ADR 0004 amendment](0004-macos-helper-ownership-and-lifecycle.md#targets-013-window-plan-amendment).

- **Apply carries window plans (Linux and Windows).** The helper's **apply**
  keeps its session id, end, hosts, and executable paths, and gains a list
  of `DomainWindowPlan`s: one host of the request and its ordered segments,
  each with an `until` instant and an optional rule. A plan is ignored, so
  its host stays blocked for the whole request, when its host is not in the
  request, another plan names the same host, its segments are not strictly
  increasing, its last `until` is after the session end, a rule is outside
  the fixed pairs, or it has more segments than the cap of the ADR 0004 amendment (22, derived from the code's bound on running parts). A plan whose segments
  have passed is valid. Hosts, paths, and the rest of the request keep
  today's validation and refusal. The helper never logs hosts or plans; it
  keeps plans in its state file with the request (root-owned on Linux,
  readable only by the system on Windows), so a restart of the helper keeps
  them, and deletes them at clear and at the session end.
- **Wire compatibility.** An apply with no plans keeps today's five-field
  form. Plans travel in an optional sixth field with a bounded grammar and a
  byte limit derived from the host limit and the segment cap; new helpers
  accept both forms. A helper that was not restarted after an update refuses
  the six-field form as today; the application then applies the five-field
  form, which blocks every held host, and retries the plans after the next
  helper restart.
- **A refused replacement.** A rule removed or narrowed during a pause
  reaches the helper in a replacement apply. If that apply is refused, the
  helper keeps the plans it holds, the application shows the existing
  attention for a pause that is not fully applied and retries, and the
  removed rule can keep opening its website until a replacement succeeds or
  the pause ends.
- **Evaluation on the existing tick.** Each JVM helper recomputes
  `blockedNow` from the wall clock on its existing one-second fixed-delay
  tick (`TICK_MILLIS = 1_000`), which keeps working after sleep, and
  rewrites its enforcement only when the result changes. `wall(t)` (ADR 0006
  amendment) is read in the system's current time zone at each evaluation,
  never from a zone the JVM cached at start, so a time-zone change applies
  within a second. The application shows Session times in the same system
  zone; a user session with its own zone setting can differ from it. A
  one-shot timer may serve only as a fast path. So the closed-app enforcer
  is:
  - **Linux:** the root systemd helper. It rewrites the marked
    `/etc/hosts` block from `blockedNow` (lines are per host, so no shared
    ownership arises). It keeps working with the application's process
    terminated, until the request ends, as its end-time clear does today.
  - **Windows:** the `LocalSystem` service, with the process ownership and
    end-time clear of the Windows row, and the address rules below. It keeps
    working with the application's process terminated.
  - **Android:** the `VpnService` decides each DNS query with `blockedNow`
    at query time, validating the plans fail-closed as above when it reads
    the pause state. `GuardService`, the `specialUse` foreground service that
    runs while a pause exists, schedules exact alarms at each window
    boundary and segment `until`, as it does for break boundaries, to
    refresh the state and the notification. During a window the VPN answers
    a ruled host with a time to live no longer than the window's remaining
    time (at least 30 seconds), so caches expire near the close. With the
    user interface closed both keep running; a force-stop ends blocking as
    it does today.
- **Hidden window versus terminated process.** On Linux and Windows a
  terminated application process recomposes nothing: the helper follows the
  segments of the last apply until it ends, so a part that ends while the
  application is gone still opens the surviving part's windows. With the
  window hidden the resident core recomposes at part ends and rule changes
  as today.
- **Windows address ownership.** `FilterPlan` keeps the addresses per host
  instead of one flat set. The filters block the union of the addresses of
  the hosts blocked now, so an address shared by a ruled host in its window
  and a blocked host stays blocked while any blocked host needs it. Opening
  a window removes only addresses no blocked host owns; closing it adds the
  host's addresses back. The 16,384-address limit is counted and reserved
  over the addresses of every **held** host, open or not, so an open window
  frees no room: a refresh adds an address only while the held union stays
  within the limit, and closing a window always has room for the host's
  addresses. A ruled host whose addresses cannot all be reserved is treated
  as having no rule for the rest of the request, so it stays blocked.
- **Windows stale resolution.** Each resolution is tagged with the plan
  generation (a counter bumped on every apply and clear) and the boundary
  epoch (a counter bumped at every window boundary or segment change) it
  started under. A result is applied only when both are unchanged when it
  completes, and only to hosts blocked now; otherwise it is discarded and
  the next refresh resolves again. This replaces the session-id-only check,
  so a slow lookup can neither re-block an open host nor add addresses for a
  host whose window opened.
- **Windows limitation.** A ruled website that shares an address with a
  website that is blocked now (for example two sites on one content
  delivery network address) stays unavailable during its window on Windows.
  This is accepted and joins the drafted Windows limits, which `DOCS-007`
  moves to the limits page with the others; a new Windows filtering
  mechanism, such as server name or per-connection filtering, is out of
  scope. Connections already open when a window closes stay open, as when a
  pause starts (`PLATFORM-002`).
- **Linux and Android caches.** A window that closes blocks new lookups;
  addresses a browser or system resolver cached during the window can keep
  the site reachable until they expire, and open connections stay open.
  This is named on the limits page.

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

Linux and Android join release 1.5. Windows joins release 1.6
(`PLATFORM-002`). All platforms share the Compose interface in `:shared`.

| Platform | Host | Websites | Applications | Privilege |
| --- | --- | --- | --- | --- |
| Linux | `:desktopApp` on the JVM, `.deb` for Ubuntu 24.04 or later; arm64 is built and verified here, the x86-64 package needs an x86-64 build machine (`RELEASE-007`) | Root helper writes a marked block in `/etc/hosts` that maps each blocked host and its `www` counterpart to `0.0.0.0` and `::`, so connections fail | Chosen from desktop entries; the helper ends matching processes while a pause runs | A systemd system service, installed once through `pkexec`; the application talks to it over a Unix socket in the root-owned `/run/posato`, which accepts only the installing user and root by their peer credentials |
| Android | New `:androidApp`, Android 13 (API 33) or later | Local `VpnService` that only carries DNS to one IPv4 address, answers blocked names with `0.0.0.0` and `::`, so connections fail, and forwards the rest over protected sockets to the DNS of the network underneath | Usage access (`UsageStatsManager`) polled by a foreground service, which opens a full-screen block screen over a blocked application, also one already in front when the pause starts; the launcher, Settings, the phone, and Posato are never offered for blocking | Runtime grants: VPN consent, usage access, display over other apps, notifications, exact alarms, and the battery-optimization exemption; a `specialUse` foreground service runs while a pause or an enabled schedule exists |
| Windows | `:desktopApp` on the JVM, MSI packages for Windows 11 on arm64 and x64 | A system service adds Windows Filtering Platform block filters for the addresses that each blocked host and its `www` counterpart resolve to, so connections to them fail | Chosen from the Start menu's shortcuts to installed desktop programs; the service ends matching processes of the installing user while a pause runs | A service running as `LocalSystem`, installed by the per-machine MSI with its one administrator approval; the application talks to it over a local named pipe that only the installing user and the system can open |
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

### Windows

The Windows service is the Linux helper's contract on Windows: a small
Kotlin JVM program, `posato-helper.exe`, shipped in the MSI and run by the
Service Control Manager as `LocalSystem`. It accepts the same **apply**,
**clear**, **status**, and acknowledge requests with the same validation,
except that an executable is an absolute Windows path to an `.exe`. The
service ends only processes of the installing user, never a process of the
system or of another user, and clears by itself at the session end. Windows
uses the folder workspace and the resident core shared with the Mac and
Linux. The service never reads traffic and never logs hosts.

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
- **Scanning and sharing the code** (`user-confirmed` 2026-10-19: QR code
  and link instead of typing). The member also shows the code as a QR code of
  the link `posato://join?code=<code>` and offers it as that link: the share
  sheet on iOS and Android, the clipboard on the Mac and Linux. A phone's
  camera opens the link in Posato; iOS, Android, and the Mac register the
  `posato` scheme, and on Linux the link is pasted into the code field.
  Opening a link only fills the code: Posato reads the exact join link and a
  code whose check character matches, opens the folder section, and joins
  once this device waits for a code, so the person still chooses the same
  folder first. The link is the same secret as the code, with the same limits.
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

This text is accepted with this ADR. `DOCS-006` moved it to the
[limits page](../product/limits-and-platforms.md#limits), which now owns it;
`RELEASE-007` publishes it with release 1.5. At the maintainer's request
(`user-confirmed` 2026-10-21), `DOCS-006` also moved the Linux sentence about
apps that cannot be paused out of the Android bullet, so each platform's
limits read on their own; the limits are unchanged.

- On Linux, website blocking maps each paused website and its `www` name to
  `0.0.0.0` and `::` in `/etc/hosts`, so connections to it fail. There is no pause
  page. A browser that uses its own DNS over HTTPS, visiting a site by its IP
  address, or another name of the same site bypasses it.
- On Linux, Posato installs a system service once, with your password. While
  a pause runs, the service ends the paused apps you chose, so unsaved work in
  them can be lost. It clears the pause at its end time even when Posato is
  closed. Removing the package clears an active pause. An app whose desktop
  entry starts a script, an interpreter, or Flatpak cannot be paused.
- On Android, website blocking runs as a local VPN that only answers name
  lookups. Another VPN cannot run at the same time, strict Private DNS and a
  browser with its own DNS over HTTPS bypass it, and lookups over TCP are not
  filtered. There is no pause page.
- On Android, a paused app is covered by a block screen; it can be visible
  for up to about a second before the screen appears. The launcher, Settings,
  the phone app, and Posato cannot be paused. Posato needs usage access,
  display over other apps, notifications, exact alarms, and to be excluded
  from battery optimization; without them, blocking or schedules may not run.
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
- A workspace syncs through iCloud or through one folder, never both, and
  there is no move between them: remove the iCloud workspace before choosing
  a folder.

The Windows limits are drafted in the
[`PLATFORM-002` execution record](../tasks/executions/platform-002-windows.md)
and enter this list once the maintainer confirms them; `DOCS-007` then moves
them to the limits page and `RELEASE-010` publishes them with release 1.6.

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
