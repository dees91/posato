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
