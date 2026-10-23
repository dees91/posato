# Execution: `RELEASE-007`

- **Brief:** [Verify the 1.5.0 candidates and publish Posato 1.5](../specifications/release-007-release-1-5.md)
- **Status:** `done`: Posato 1.5.0 published 2026-10-22; iOS 1.5.0 (8)
  approved and released by App Review
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review and completed-change review
- **Branch:** `docs/release-007-release-1-5` (#193), closeout `docs/release-007-closeout`
- **Updated:** 2026-10-23

## Plan

`releasing.md` steps 1 to 9 with the brief's differences. The maintainer is
away for the run; the go, the matrix, and the defect rule are in the brief
(`user-confirmed` 2026-10-22). No maintainer touchpoint remains: Keychain
access to the Apple keys is pre-granted, the Android keystore password reads
from the login Keychain without a prompt (`observed` 2026-10-22), and the
maintainer has backed up the keystore and its password.

**Tooling before R (its own pull request, Standard review, merged to
`main` before R so the workflow can be dispatched; `.github/` and `tools/`
fall under the step 6 rule):**

1. **Linux workflow** `.github/workflows/linux-packages.yml`: one job builds
   `:desktopApp:linuxPackageInputs` on `ubuntu-24.04` with Temurin 21; a
   matrix on `ubuntu-24.04` and `ubuntu-24.04-arm` packages it with
   `package-deb.sh` and Ubuntu's `openjdk-21-jdk` (the Tart guest's JDK),
   renames the output to `posato_<version>_<arch>.deb` with the version from
   `Version.xcconfig`, asserts `dpkg-deb -f` Package, Version, and
   Architecture, installs it with apt on the runner and in a clean
   `ubuntu:24.04` container (the dependency check x86-64 gets), starts it
   with `dbus-run-session -- xvfb-run -a`, asserts a Posato window with
   `xdotool` and no exception in the output after 30 s, removes it, and
   uploads the `.deb`, its SHA-256, and the built commit. Triggers:
   `workflow_dispatch` with a ref, and pull requests that touch the
   workflow or `desktopApp/linux/`, checked out at the head commit. No
   secrets. It must pass on its own pull request before R.
2. **`posato-control`:** `install -t linux --deb <file>` installs a given
   `.deb` in the clone instead of packaging in the guest; `install -t
   android --apk <file>` installs a given APK, uninstalling a build with
   another signature first. Database evidence on Android needs the
   debuggable build, so release APK runs assert through the interface only.

**Android signing without Gradle:** `assembleRelease` with no signing
configuration (the default `release` type, no minification), then
`apksigner sign --ks <path> --ks-key-alias posato --ks-pass stdin` with the
password piped from `security find-generic-password -w`, never on a command
line. Afterwards `build/`, `.gradle/`, and `~/.gradle` are searched for the
password and the empty result is recorded. `assembleRelease` runs once
before R, so `lintVitalRelease` or packaging failures land before R. Checks:
`apksigner verify --print-certs -v` (v2 and v3 true, certificate SHA-256
equal to the fingerprint below), `aapt2 dump badging` (versionCode 1,
versionName 1.5.0, not `testOnly`, not debuggable). A preflight checks the
`local.properties` keys and the Keychain item before the macOS builds.

Release certificate SHA-256, recorded before any build:
`9D:9C:67:F8:BF:D8:CF:0C:61:D1:0F:29:C6:D6:C3:C5:FA:78:FB:9D:66:6A:7B:F7:94:84:26:1A:21:31:D5:B1`.

**R** sets `MARKETING_VERSION = 1.5.0`; `docs/releases/1.5.0-appcast-notes.txt`
already exists from `DOCS-006`. Clean-clone `quality iosSwiftTest`.

**Candidates from clean clones of R:** macOS build 30 (arm64, x86-64, the
Intel verification candidate with a throwaway key); iOS: `nextBuildNumber`
from `store status` at run time, uploaded, `store prepare` with the
`DOCS-006` files; the signed APK as `Posato-1.5.0.apk`; Linux: the workflow
dispatched with `--ref` on this branch while its head is R, the recorded
commit asserted equal to R, both `.deb` files downloaded and their hashes
compared with the job's. The release holds eight assets: `Posato-1.5.0.dmg`,
`Posato-1.5.0-intel.dmg`, `appcast.xml`, `appcast-intel.xml`,
`posato_1.5.0_arm64.deb`, `posato_1.5.0_amd64.deb`, `Posato-1.5.0.apk`, and
one `SHA256SUMS` covering the other seven.

**Matrix (reduced as in 1.4, `user-confirmed`):**

- `primary`, arm64 release DMG: setup, a 1 h pause and an Until end of day
  pause that block, a cold boot with a schedule that starts on its own.
- `legacy` and `ventura` once each: core flow; `ventura` with the x86-64
  release DMG (Gatekeeper, Intel notice) and the verification candidate.
- iCloud link and one-press removal on development builds of R.
- Test iPhone, build of R: shield and edge swipe.
- Linux arm64 clone, the workflow's arm64 `.deb`: install, onboarding with
  the pausing service, a pause that blocks a website and ends an app.
- Galaxy test phone, the release APK: install over nothing, onboarding with
  all grants, a pause that blocks a website and covers an app.
- Folder sync on the release packages: `primary` with the arm64 release
  DMG creates a folder workspace; the Linux clone and the phone join it by
  code through `posato-control relay`; a pause started on one device
  reaches the others.
- After publication: the in-app update 1.4.0 to 1.5.0 on a kept `peer`
  clone with a schedule that starts on its own afterwards, which proves
  migration 14 on macOS.
- x86-64 `.deb`: the workflow's install and launch smoke only; no x86-64
  Linux machine runs the E2E (a stated limit).

Not repeated: the replacement over 1.4.0 and the iPhone upgrade over
1.4.0. Migration 14 (`SYNC-023`) only creates the `sync_last_exchange`
table, so its iPhone upgrade stays unproven here, a gap accepted with the
reduced matrix (`inferred` low risk).

**Plan review** (independent, 2026-10-22): approve after fixes; the
Critical (dispatching the workflow on R), the four Required (pinned `.deb`
names, the Android password outside Gradle, full APK checks, a proven
workflow with a D-Bus session and a window check) and the Recommended items
are folded above.

**Publication:** squash-merge after the completed-change review, the step 6
diff rule, annotated `v1.5.0`, a draft release with the eight assets, byte comparison, publish as latest with the `DOCS-006` notes,
feed checks, the in-app update, `store submit`, site and download link
checks, Projects Done, milestone closed. A defect follows the brief's rule.

## Result

- **Tooling** (#194, squash `6da8d19b`, Standard review: Required fixed and
  proven in the clone): the `Linux packages` workflow and `posato-control
  install --package`.
- **R** = `0c67878023308ca1977529e5cf5d72a31d8a4fa8` (`MARKETING_VERSION =
  1.5.0` on `main` `6da8d19b` plus this row's documentation); clean-clone
  `quality iosSwiftTest` passed (7 min 12 s).
- **macOS build 30**, both architectures from one clean clone of R, G2
  Developer ID: `Posato-1.5.0.dmg` `19bbac0d…12f8`, `appcast.xml`
  `26146152…fac5`, `Posato-1.5.0-intel.dmg` `6d060bee…0471`,
  `appcast-intel.xml` `22c39a04…31f2`. Host checks of both DMGs: Mach-O
  files of the DMG's architecture only, no universal binaries, no Rosetta
  switch or seam key, the stable feed URL per architecture with one
  `SUPublicEDKey`, minimum macOS 13.0, Temurin 21.0.12.1 in both, strict
  deep `codesign`, `stapler validate`, and `spctl` (Notarized Developer
  ID); the app, helper, and sync companion entitlements equal 1.4.0's.
- **Intel verification candidate** 9301 from a second clean clone:
  candidate channel, Rosetta switch, loopback feed, a throwaway key in
  Keychain account `posato-test-release-007`; never published
  (`5837d39a…ca2a`).
- **iOS 1.5.0 (8):** IPA signed with Apple Distribution, Family Controls,
  the app group, CloudKit Production, the sync keychain group,
  `get-task-allow` false, both privacy manifests; `altool` validate and
  upload passed; `VALID`. `store prepare` (`after-approval`, the `DOCS-006`
  What's New and description from R, screenshots unchanged) created the
  version; a rerun reported every item `unchanged`;
  `PREPARE_FOR_SUBMISSION`.
- **Android:** `assembleRelease` in a clean clone of R, signed with
  `apksigner` (password piped from the Keychain): `Posato-1.5.0.apk`
  `6b21b3b6…be26`; v2 and v3 signatures, certificate SHA-256 equal to the
  fingerprint above, `versionCode` 1, `versionName` 1.5.0, minSdk 33, not
  debuggable or `testOnly`. No file under the clone, the worktree's
  `build/` and `.gradle/`, or `~/.gradle` contains the password.
- **Linux:** workflow run 39834015238 on R: both jobs green;
  `posato_1.5.0_arm64.deb` `52df22ff…b738` and `posato_1.5.0_amd64.deb`
  `22c5ac7a…6553`, each `.commit` equal to R and each `.sha256` verified.
- **`SHA256SUMS`** covers the seven files and passes `shasum -a 256 -c`.
- **Consumed build numbers:** macOS 30 (both), the never-published Intel
  candidate 9301, iOS 8, Android versionCode 1.

### Verification on the candidates (2026-10-22)

Run directories are under the release worktree's ignored
`build/verification/runs/`.

| Check | Target | Result |
| --- | --- | --- |
| Install over a development build, onboarding with the pausing service, a 15 min pause: `example.org` answers `0.0.0.0`, `example.com` loads, four lines in `/etc/hosts`; Calculator chosen during the pause ends; after End session early `/etc/hosts` equals the copy taken before and both are allowed | Linux clone, workflow arm64 `.deb` | pass (`211828-a999` to `212045-1d4d`) |
| Fresh install, onboarding through the real VPN, notification, and battery requests, a pause: `example.org` answers `127.0.0.1`, Clock covered by "Paused by Posato", both released after the end | Galaxy XCover7, signed APK | pass (`212129-1d5d` to `212829-e5ba`) |
| Folder workspace created on Linux, the phone joins by code; a Linux pause is enforced on the phone after 21 s; ending it on the phone restores Linux `/etc/hosts` exactly after 35 s | Linux clone and phone, release packages, relay | pass |
| Install from the arm64 DMG, setup with the helper, update consent; joins the phone's folder workspace by code and receives its set; a 1 h pause blocks on the Mac and on the phone (about 30 s); Until end of day blocks; a schedule starts on its own after a cold boot before any manual open | `primary`, arm64 release DMG | pass (`214608-49bd`, `214702-1833`, `215318-8c63`) |
| Setup and one blocking pause | `legacy`, arm64 release DMG | pass (`220636-8436`) |
| Intel release DMG passes Gatekeeper, shows "This version is for Intel Macs" under Rosetta, leaves no process or launchd item after Quit; the candidate sets up, blocks, and starts a schedule on its own | `ventura` | pass (`221155-5850`, `221613-9d70`) |
| iCloud link (7 min 40 s), the pause-set notice, the pause-sets scenario, one press of Remove workspace (4 min 5 s) | `primary`, R's development package | pass (`222735-7c05`, `222743-8525`) |
| Package fields, install and removal in a clean `ubuntu:24.04` container, install on the runner and a start under D-Bus and Xvfb with a Posato window; no x86-64 Linux machine ran the E2E (a stated limit) | x86-64 `.deb`, workflow run 39834015238 | pass |
| Website Not Allowed in Safari, End session early, edge swipe back | test iPhone, R's development build | pass (`220052-cad4`, `220202-5ce3`) |

The folder chain ran in a different order from the plan, because a Linux
clone and a macOS clone may not run together: Linux created the workspace,
the phone joined, and `primary` joined with a code from the phone. Proven
directions: Linux to phone, phone to Linux, Mac to phone; a Mac pause on
Linux is not shown directly.

Environment notes, not product defects: the iPhone's reinstalled
development build had lost its Screen Time authorization ("Restrictions
need attention"); the consent scenario from **Choose apps** restored it and
**Retry** applied the pause. On a fresh Linux clone `posato-control` did
not write `/tmp/posato-dbus` (an empty address lets `dbus-send` reach the
X11 autolaunch bus, so the ping passes); the file was written by hand.
`flow onboard -t linux` cannot continue when the pausing service is
already installed (known, `QUALITY-013`); those screens were driven by
hand. Both are follow-ups for `posato-control`.

### TB-08 and T-13 review of the release artifacts

- R changes only `Version.xcconfig` from its parent; the branch adds only
  `docs/` after R; `.github/` and `tools/` landed on `main` in #194 before
  R.
- New since `v1.4.0`, each shipped through its own reviewed row: the Linux
  root service (`posato-helper-setup`, `postinst`, `prerm`; it edits a
  marked block of `/etc/hosts` and answers only the installing user and
  root over a root-owned socket, ADR 0010), the Android DNS-only VPN and
  block screen, folder sync with encrypted files and one-time pairing codes
  that expire after 10 minutes (`SYNC-018`, `SYNC-026`), export files with
  an optional code (`TARGETS-007`), and migration 14, which only adds the
  `sync_last_exchange` table. No macOS entitlement, iOS entitlement, or
  privacy manifest changed: the app, helper, and companion entitlements
  equal 1.4.0's in both DMGs, and the IPA carries Apple Distribution,
  CloudKit Production, and both privacy manifests.
- The Sparkle release key stayed in the maintainer's login Keychain with
  pre-granted access (the accepted `T-13` residual risk: any process
  running as the maintainer could use it, together with the logged-in `gh`,
  to publish a signed update).
- The Android keystore lives outside the repository; its password stays in
  the login Keychain and reached only `apksigner` through stdin. No file
  under the build clone, the worktree's `build/` and `.gradle/`, or
  `~/.gradle` contains it; Git history holds no keystore, signing
  configuration, or password; the Linux workflow reads no secret, has
  `contents: read`, and does not persist its token. The same `T-13`
  consequence applies: a process running as the maintainer can read the
  password from the Keychain and sign an APK that installations accept.
  The maintainer holds a backup of the keystore and its password.
- The completed-change review (independent, 2026-10-22) re-verified every
  hash, both feeds, both DMGs, the IPA, the APK, and both `.deb` files
  against R and found no Critical or Required issue in the artifacts.

**Verdict for R:** the candidates of R are fit to publish as Posato 1.5.0.

## Publication (2026-10-22)

- #193 squash-merged as `ae8428659f464dbede02c0cb009d4039786d4cd7`; `git
  diff --name-only` from R lists only `PRIVACY.md` and `docs/`, and
  `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, and `Version.xcconfig`
  equal R. Annotated tag `v1.5.0` on it.
- GitHub Release `v1.5.0`, latest, with the `DOCS-006` notes and the eight
  assets, downloaded from the draft and byte-identical to the verified
  outputs. `releases/latest/download/appcast.xml` and `appcast-intel.xml`
  equal the verified feeds (build 30); the APK and `.deb` links answer 200.
- iOS 1.5.0 (8) submitted with `store submit`: `WAITING_FOR_REVIEW`,
  `AFTER_APPROVAL`.
- `posato.app`, the limits and privacy pages, the README, and `PRIVACY.md`
  (effective October 22, 2026) answer 200; the download buttons lead to the
  latest release.
- Projects: `RELEASE-007` Done; milestone `1.5.0` closed.

### In-app update from 1.4.0

On a `peer` clone set up with the published 1.4.0 (helper, `example.com`
and Calculator in the default set, update consent allowed), a schedule
started on its own on 1.4.0 at 22:42:14 and blocked both
(`224214-81e0`, `224215-8395`). A second schedule was set for 23:14 on
1.4.0. **Check for Updates…** offered Posato 1.5 from the stable feed;
Install Update, then Install and Relaunch (macOS first asked whether
`tart-guest-agent` may bypass the window picker, a verification-only
prompt answered with `vm prompt picker-bypass`). The relaunched application
was 1.5.0 (30) with the set, its application, both schedules, and the
helper enabled; the 23:14 schedule started on its own at 23:14:11 and
blocked `example.com` and Calculator (`231412-7aeb`, `231414-08fd`); a new
check reported "You're up to date." This proves migration 14 on macOS.
No upgrade regression.

### App Review and closeout (2026-10-23)

- iOS 1.5.0 (8) passed App Review; with `AFTER_APPROVAL` it was released
  at once: `store status` reports `READY_FOR_DISTRIBUTION`.
- The never-published Intel verification candidate 9301 and its throwaway
  Sparkle key (Keychain account `posato-test-release-007`) are deleted.

### Follow-ups

- `posato-control`: on a fresh Linux clone the launch does not write
  `/tmp/posato-dbus` when no bus file exists, because `dbus-send` with an
  empty address reaches the X11 autolaunch bus and the ping passes.
- `QUALITY-013` (already in the roadmap) met again during this run.
- The `.deb` control file names `Posato <Unknown>` as maintainer (review
  Optional finding).
