# Execution: `PLATFORM-001`, `SYNC-018`, `LINUX-001`, `ANDROID-001`

- **Brief:** [release-1-5-linux-android.md](../specifications/release-1-5-linux-android.md)
- **Status:** `active`; **review tier:** high-risk
- **Implementer:** Claude Code agent; **reviewer:** independent agent
- **Branch:** `platform-linux-android` (one branch; see Result)
- **Updated:** 2026-10-20

## Plan

1. The folder supplies the existing sync ports (the directory is the zone,
   `workspace` the anchor, a pairing code delivers the key, a one-minute poll).
2. The sync section offers iCloud (Apple hosts) or a folder: choose, create
   or join with a code, show a code, remove.
3. Linux: XDG paths, a root `linuxHelper` (hosts block, process ending,
   self-clear), desktop-entry app choices, a `.deb`.
4. Android: `:androidApp` with a DNS-only `VpnService`, a usage-access block
   screen, exact alarms, boot wake-ups.
5. `posato-control` support for each target, docs, one wiki-log entry.

## Plan and change reviews

- **Plan review** (2026-10-10): changes-required, nine Critical or Required
  findings (bad files, pairing expiry and labels, removal semantics, fetch
  trigger, helper protocol and input rules, Android alarms and Doze, missing
  criteria, unresolvable names), resolved in ADR 0010, the brief, and the
  plan before implementation.
- **Completed-change review** (2026-10-11, head `bc6b55c2`): changes-required,
  ten Required findings (Linux service liveness and tick failures,
  interpreter matching, Android DNS network and the app already in front,
  `:androidApp` outside the quality gate, manifest lint suppressions,
  agent-delegated limits in the product page, ADR claims, quality on the
  head), all resolved; five Recommended accepted, the iOS stale-bookmark
  renewal not taken.
- **Pull request review** (2026-10-11, `4c1f2db1`): five P1 and two P2,
  corrected in `a0f896e6` (tunnel restored after a reboot, IPv6 through the
  tunnel, removed apps and websites kept paused on Android and Linux, the
  battery request in onboarding, the iPhone picker's folder kept by `flow
  folder link`, iPhone verification) and checked on devices (below).

## Result

- **One branch instead of the planned stack:** the rows share onboarding,
  the sync section, and `posato-control`; commits stay grouped by row.
- **Folder transport (`SYNC-018`):** `FolderSyncPorts` over a file-system
  port (`java.nio`; Foundation with file coordination and security-scoped
  bookmarks on iOS), `FolderPairing`, `SelectableSyncPorts`. Android seals
  the key with Keystore, the desktop keeps a `0600` file, iOS uses complete
  file protection.
- **Pairing by QR code and link** (`user-confirmed` 2026-10-19): the code
  dialog shows a QR code of `posato://join?code=<code>` with **Share link** or
  **Copy link**; iOS, Android, and the Mac open the link into the code field
  and join once a folder is chosen. QRose 1.1.2 (later versions need SDK 37).
- **Defects found by E2E and fixed test first:** stale folder listings,
  a leftover link attempt, Ed25519 keys on Android, refusals not cleared;
  relay races in the tool. On a Galaxy XCover7 (Android 14) **Allow access**
  ended Posato on Android 13 to 15 (an API 36 overload; the Android 17
  emulator hid it), fixed with the maintainer-approved deprecation exception
  (`62d7d3b7`).
- **`posato-control` parity for Android and Linux** (maintainer's request,
  2026-10-20): `-t android` (UIAutomator; phones, emulators, system requests)
  and `-t linux` (Posato's AT-SPI tree through `java-atk-wrapper`, presses by
  accessibility action) take the shared commands and every flow; new `flow
  onboard`, `observe`, `install --grants`, Android in `devices list`,
  `openURL` on Android. Text recognition and Maestro were weighed and set
  aside (`agent-delegated`): the first loses roles, the second adds a second
  flow language over the same UIAutomator.
- **Android polish (maintainer's request, 2026-10-20):** edge to edge on every
  release, predictive back from Android 13, Back from Pause sets or Schedules
  to Session (DESIGN.md, `user-confirmed`), menus that open after the keyboard
  has gone, an access step that shows access already given (Android, Linux),
  and access text naming every grant; `flow schedule` on the phone 270 to
  142 s through located-once repeated taps.
- **Linux and Android** as planned; **backlog:** `SESSION-010` (idea 39);
  **docs:** ADR 0010 (Proposed), limits, driver README, skill, guide, wiki.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Contract tests written first (folder mailbox, pairing, stale folder, workspace, helper request, hosts block, process match, DNS packet, UIAutomator and AT-SPI trees, name answers) | Passed in `quality` | `shared` jvmTest, `linuxHelper:test`, `androidApp` and `posato-control` tests |
| `AC-01` two Tart Macs, relay, code join, set and session, removal | Passed (2026-10-10) | `build/verification/e2e/m1-pass-20261010.txt` |
| `AC-02` Linux clone at `73f93653`: install, service, hosts block, calculator ended, early end, malformed host refused, self-clear, expiry after relaunch, removal | Passed | `build/verification/e2e/m2-final.log` |
| `AC-03` Android emulator at `73f93653`: onboarding, DNS block, Clock covered, early end; a Mac's schedule starting in the background and after `adb reboot` | Passed | `build/verification/e2e/m3-final.log`, `m5-b-pass-20261011.txt` |
| `AC-04` pauses both ways between Android and Linux; a new Linux device ended mid-exchange catches up | Passed | `build/verification/e2e/m5-a-pass-20261011.txt`, `m5-c-pass-20261011.txt` |
| `AC-05` mistyped code refused, workspace intact, right code joins; expiry, tampering, other workspace by contract tests | Passed | `build/verification/e2e/ac05-pass-20261011.txt` |
| `AC-06` removal on Linux: folder emptied, Android needs attention, nothing re-created | Passed | `build/verification/e2e/ac06-multi-pass-20261011.txt` |
| Mac at the 2026-10-11 revision: folder and iCloud link and removal | Passed | `build/verification/e2e/mac-final.log` |
| QR code and link at `ff8d7f68`: the Mac's QR decodes to the link, **Copy link**, Android joins by a link opened before its folder, Android's QR and share sheet, iPhone Simulator link and dialog | Passed | `build/verification/e2e/qr-e2e.log` |
| `AC-07` test iPhone (cable) at `4c1f2db` plus the listing fix: Files picker to a Mac's iCloud Drive folder, code join, the Mac's sets and pause | Passed after one fix (Blockers) | run `20261011-084100-205c` |
| Galaxy XCover7 (Android 14) at `87624b54`: `install --grants settings` and `flow onboard` answer the VPN, notification, and battery requests | Passed after the fix | runs `20261020-124131-88a9`, `20261020-123955-3baf` |
| A website removed mid-pause (Android, and the linked Linux clone) stays paused until the end, then resolves | Passed | runs `20261020-134148-f513`, `-134154-9b2f`, `-134802-7fbe`, `-134803-409e` |
| Linux: Calculator removed from a running pause with a website stays ended until the end, then runs | Passed | runs `20261020-134943-8a5f`, `20261020-135501-8b1a` |
| Android `adb reboot` during a manual pause: resolves before the first unlock, paused again about 30 s after it, Clock covered | Passed; the unlock is a person's | runs `20261020-135843-416c`, `-140158-00c6`, `-140204-1640` |
| Folder workspace between the Linux clone and the phone with `flow folder` on both | Passed | runs `20261020-131234-166f`, `-131756-3987`, `-132404-343e` |
| Test iPhone (cable) at `87624b54`: picker, `flow folder link` without `--path` waits, the join link (`devicectl --payload-url`) joins, the Mac's set arrives; after the Mac's removal "Sync needs attention" and removal | Passed | `build/verification/e2e/ios-link3.log`, runs `20261020-141551-0eed`, `-142738-9aa9` |
| Android polish on the Galaxy XCover7 at `dd980458` sources: no grey status bar, the access step with every grant shows Access allowed (the previous build showed Allow access, run `20261020-145201-3eb8`), the Pause set menu opens below its button with the keyboard up (before: above, run `20261020-144602-3693`), Back from Schedules and Pause sets to Session and out of Posato from Session; predictive back gesture on the Android 17 emulator shows the list under the set | Passed | runs `20261020-145115-a672`, `-145508-4364`, `-145758-5bde`, `-150958-6a62` |
| Mac VM and Linux clone with the same sources: set, schedule with a set, session with a set, website paused; Linux onboarding without a website | Passed | `build/verification/e2e/menus-mac-linux.log`, run `20261020-151241-0d35` |
| `./gradlew quality` at `dd980458` | Passed | gradle run of the session |

## Blockers and accepted risks

- **`AC-07` found one defect, fixed:** a plain listing of an iCloud Drive
  folder on the iPhone kept the items of the first listing; listing now asks
  the provider to download the folder and reads under file coordination. No
  isolated test reproduces a File Provider; the device runs are the evidence.
- `open` (iPhone, iCloud Drive): another device's removal was not seen within
  six minutes once, and was seen after about two minutes another time.
- IPv6 through the Android tunnel is checked by reading only (no IPv6 route
  on the phone's network); Android 13 itself was not run.
- After a reboot, Android enforces only after the first unlock: Posato's data
  is credential-encrypted until then.
- The x86-64 Linux package needs an x86-64 build machine (`RELEASE-007`); the
  GitHub Projects mirror has no "1.5" Release option yet.
- Real folder services (Dropbox, OneDrive, Syncthing) are not verified; the
  relay stands in for them, and iCloud Drive was used on Apple hosts only.

## Final

- **Status:** ready for review
