# Execution: `RELEASE-007`

- **Brief:** [Verify the 1.5.0 candidates and publish Posato 1.5](../specifications/release-007-release-1-5.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review and completed-change review
- **Branch:** `docs/release-007-release-1-5` (#193)
- **Updated:** 2026-10-22

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
| Website Not Allowed in Safari, End session early, edge swipe back | test iPhone, R's development build | pass (`220052-cad4`, `220202-5ce3`) |

Environment notes, not product defects: the iPhone's reinstalled
development build had lost its Screen Time authorization ("Restrictions
need attention"); the consent scenario from **Choose apps** restored it and
**Retry** applied the pause. On a fresh Linux clone `posato-control` did
not write `/tmp/posato-dbus` (an empty address lets `dbus-send` reach the
X11 autolaunch bus, so the ping passes); the file was written by hand.
`flow onboard -t linux` cannot continue when the pausing service is
already installed (known, `QUALITY-013`); those screens were driven by
hand. Both are follow-ups for `posato-control`.
