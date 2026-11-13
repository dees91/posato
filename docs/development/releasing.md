# Releasing Posato

The standing route for a release: notarized macOS builds for arm64 and
x86-64 with two update feeds on GitHub Releases, the Linux `.deb` packages
for arm64 and x86-64 and a release-signed Android APK beside them, and the
iOS build through App Review. It condenses what `RELEASE-003` to
`RELEASE-007` proved; Linux and Android joined in `RELEASE-007`. Command
syntax lives in the linked authorities; this page orders the work and keeps
each trap next to the step it affects.

A `RELEASE` row still uses the recorded-task path and the High-risk tier of
the [task workflow](../tasks/README.md). Its brief says "follow
`releasing.md`; differences: ..." and lists only what this release changes,
such as a migration to prove or a check the maintainer chose not to repeat.
Composition and the release guard come from the
[release roadmap](../tasks/release-roadmap.md).

## Prerequisites

- **Signing:** a G2 Developer ID Application identity and the sync
  companion's Developer ID profile, outside the checkout
  ([macOS Developer ID release](apple-provisioning.md#macos-developer-id-release)).
- **App Store Connect:** the team key in the ignored `local.properties`
  ([Prerequisites](apple-provisioning.md#prerequisites)); it also serves
  notarization and `altool`.
- **Update keys:** the release key stays in the maintainer's login Keychain;
  candidates use a throwaway test key
  ([Update feed and channels](apple-provisioning.md#update-feed-and-channels)).
- **Intel:** Rosetta on the build Mac ([Intel builds](apple-provisioning.md#intel-builds)).
  `posato-control doctor` reports it as `host.rosetta`, and an x86-64 build
  refuses at configuration without it, naming the install command (a macOS
  update left it out once, `observed` in `RELEASE-006`).
- **Build variants:** which Gradle tasks build the development, candidate,
  release, and Intel packages, and which checks guard each, are mapped in
  [build variants](build-variants.md).
- **Verification:** the `primary` and `ventura` golden VMs, the test
  iPhone, and their `local.properties` keys; a `peer` golden VM is optional
  (since `RELEASE-010`, iCloud runs pair a Mac clone with the iPhone, and
  desktops reach each other through a folder workspace)
  ([unattended verification](unattended-verification.md)).
- **Android signing:** the release keystore outside the checkout, its
  password in the login Keychain, and four keys in the ignored
  `local.properties`: `posato.android.releaseKeystorePath`,
  `posato.android.releaseKeystoreKeychainService`,
  `posato.android.releaseKeystoreKeychainAccount`, and
  `posato.android.releaseKeyAlias`. The maintainer keeps a backup of both
  outside the build Mac: every later APK must carry the same key, or
  installations cannot update. Gradle never sees the key; `apksigner` from
  the Android SDK build tools signs after the build.
- **Google Play upload key:** a separate PKCS12 keystore,
  `~/Library/Developer/Posato/posato-play-upload.p12` with alias `upload`,
  its password in the login Keychain (service `posato-play-upload`, account
  `keystore`), and four more `local.properties` keys:
  `posato.android.uploadKeystorePath`,
  `posato.android.uploadKeystoreKeychainService`,
  `posato.android.uploadKeystoreKeychainAccount`, and
  `posato.android.uploadKeyAlias`. It signs only the AAB uploaded to Play;
  Play App Signing re-signs what it delivers with the release key above,
  enrolled through PEPK, so Play and GitHub APKs carry one certificate. Play
  Console can reset a lost upload key; the release key cannot be replaced.
- **Linux packages:** the `Linux packages` GitHub Actions workflow
  (`.github/workflows/linux-packages.yml`) builds both `.deb` files; it
  needs no secrets.
- **Tools:** `installDist` of `posato-control` and `posato-provisioning`;
  `gh` with the `project` and `workflow` scopes.

**Who does what.** The agent does every step it can, including the merges
after the publication go (since `RELEASE-005`; the maintainer merged in
`RELEASE-004`). The maintainer gives the go and does what the App Store
Connect API cannot. Keychain access to the signing keys, the release update
key included, may be pre-granted with Always Allow, so signing runs
unattended (`user-confirmed`, 2026-10-10). The accepted residual risk
(`T-13`): any process running as the maintainer can then use those keys
without a prompt, and together with the logged-in `gh` could publish a
Developer ID- and EdDSA-signed update that installations accept. When a
prompt does appear, ask for one Keychain action per message.

## 1. Open the row and plan

Create the worktree, the brief, and the draft pull request with milestone
`<version>`; set the row In Progress in the
[Projects mirror](../tasks/release-roadmap.md#github-projects-mirror). Every
other row is merged except the packaging row (`DOCS-*`), whose pull request
holds the README, site, `PRIVACY.md`, store texts and screenshots, and the
GitHub release notes, and merges at publication. Write the plan as this
page's steps plus the differences; record maintainer decisions
(`user-confirmed`): App Store release type (`after-approval` so far), checks
not repeated, iOS versions claimed.

- **macOS `<build>`**, one for both architectures. The feed task reads the
  floor from the highest build in either published feed. `<build>` must also
  exceed any build an installation reading the stable feeds can hold, such
  as a test candidate signed with the release key (listed in the `MACOS-011`
  record); raise the floor with `-PposatoMacOsPreviousBuildNumber`. Builds
  that ran only in destroyed VM clones do not count.
- **iOS `<build>`:** `store status` prints `nextBuildNumber`.
- A driver or `store` change the release needs lands under `tools/` with its
  own Standard review, outside R, so the tag rule in step 6 still holds.

**Done when:** an independent plan review approves, with every Required
finding folded into a new revision.

## 2. Release commit R

R sets `MARKETING_VERSION = <version>` in `Version.xcconfig` and adds
`docs/releases/<version>-appcast-notes.txt`, one file for both feeds, worded
to stay true whatever step 4 shows. Public text comes from the packaging row.

**Done when:** R's full hash is in the record and
`./gradlew quality iosSwiftTest` passes in a clean clone of R. Naming
`iosSwiftTest` runs the native Swift suites, which `quality` alone skips when
nothing they guard changed.

## 3. Candidates from a clean clone of R

1. **arm64:** `generateMacOsUpdateFeed`, release channel, `<build>`
   ([command](apple-provisioning.md#update-feed-and-channels)); pass the
   identity's SHA-1 when two share a name. Keep the configuration cache on,
   which `gradle.properties` sets, so a missing feed property fails before the
   build and notarization instead of after them.
2. **x86-64** in the same clone with `-PposatoMacOsArchitecture=x86_64`; it
   requires the arm64 feed beside it with the same build.
3. After each run, copy `release-feed/<arch>/` out and hash it. Merge the two
   `SHA256SUMS` into one and check it with `shasum -a 256 -c`.
4. **x86-64 verification candidate** in a second clean clone: candidate
   channel, `-PposatoMacOsAllowRosetta=true`, a loopback test feed and a
   throwaway key, never published. The release DMG refuses to run under
   Rosetta, so only this build can exercise the VM
   ([`MACOS-015` record](../tasks/executions/macos-015-intel-ventura.md)).
5. **Host checks of both DMGs:** every Mach-O has the DMG's architecture; no
   Rosetta switch; `SUFeedURL` ends in `appcast.xml` or `appcast-intel.xml`
   with one `SUPublicEDKey`; minimum macOS 13.0; equal Temurin version and
   entitlements; strict deep `codesign`, `stapler validate`, `spctl`.
6. **iOS:** archive, export, inspect, validate, upload
   ([iOS App Store release](apple-provisioning.md#ios-app-store-release)).
7. **Store record, before the sitting:** `store prepare` with the packaging
   row's files at a pinned head (`--subtitle` when it changes). If an earlier
   version waits for review, use `store withdraw` and `--rename-from`
   ([Replacing a version in review](apple-provisioning.md#replacing-a-version-in-review)).
   The `store` commands ran live in `RELEASE-004` and `RELEASE-005`
   (`observed`). Submit nothing yet.

8. **Android:** in a clean clone of R, `./gradlew :androidApp:assembleRelease
   :androidApp:bundleRelease -Pposato.android.build=<digit>`. The `release`
   build type runs R8 (code and resource shrinking) and leaves both outputs
   unsigned, with no signing configuration:
   `androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk`,
   `androidApp/build/outputs/bundle/release/androidApp-release.aab`, and the
   R8 map `androidApp/build/outputs/mapping/release/mapping.txt`, which goes
   to Play with the AAB and into the record's evidence, never into Git.
   - **Version:** `versionName` is set in `androidApp/build.gradle.kts`;
     `versionCode = major * 1000000 + minor * 10000 + patch * 100 +
     <digit>`, so 1.6.1 with `-Pposato.android.build=1` is `1060101`. The
     digit (0 to 99, 0 when omitted; anything else fails the build) is per
     upload: Play never accepts a `versionCode` twice, so each upload of one
     `versionName` takes the next digit, including a rejected or abandoned
     one. Record each consumed digit.
   - **APK** (GitHub Releases): sign it into `Posato-<version>.apk` with the
     release key, the password piped on stdin and never put on a command
     line:

     ```bash
     security find-generic-password -s <release service> -a <release account> -w |
       "$ANDROID_HOME/build-tools/<version>/apksigner" sign \
         --v2-signing-enabled true --v3-signing-enabled true \
         --ks <release keystore> --ks-key-alias <release alias> --ks-pass stdin \
         --out Posato-<version>.apk \
         androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk
     ```

     Check `apksigner verify -v --print-certs` (the certificate SHA-256
     equals the one in the first Android release record; pass
     `--min-sdk-version 24` to see the v2 block, which the default check for
     minSdk 33 does not report).
   - **AAB** (Google Play): sign it into `Posato-<version>-<digit>.aab` with
     the upload key through `jarsigner` from JDK 21 (`JAVA_HOME` pointing at
     it), which reads the password from stdin when no terminal is attached:

     ```bash
     security find-generic-password -s <upload service> -a <upload account> -w |
       "$JAVA_HOME/bin/jarsigner" -keystore <upload keystore> -storetype PKCS12 \
         -sigalg SHA256withRSA -digestalg SHA-256 \
         -signedjar Posato-<version>-<digit>.aab \
         androidApp/build/outputs/bundle/release/androidApp-release.aab <upload alias>
     ```

     Check `jarsigner -verify` ("jar verified"; its warnings about a
     self-signed certificate and a missing timestamp are expected for an
     upload key, so do not pass `-strict`) and that `keytool -printcert
     -jarfile` shows the upload certificate's SHA-256 registered in Play
     Console.
   - **Both:** `aapt2 dump badging` on the APK (`versionName`, the expected
     `versionCode`, no `testOnly` or `debuggable`), and no file under the
     clone, `.gradle`, or `~/.gradle` contains either password.
9. **Linux:** `gh workflow run linux-packages.yml -f ref=<full R SHA>`.
   Download both artifacts (retry with `curl --retry` on the artifact's zip
   URL when `gh run download` stalls), check each `.commit` file equals R
   and each `.sha256` with `shasum -a 256 -c`. The workflow already checked
   the package fields, a clean `ubuntu:24.04` install and removal, and a
   start under Xvfb on both architectures.

**Done when:** the record lists both DMG and feed hashes, the APK, AAB, and
both `.deb` hashes, and the consumed build numbers and Android build digit, the iOS build is `VALID`, and a rerun of `store prepare`
reports every item `unchanged` with `store status` at `PREPARE_FOR_SUBMISSION`.

## 4. Unattended verification

Follow [`verify-posato`](../../.agents/skills/verify-posato/SKILL.md) and its
[updates recipe](../../.agents/skills/verify-posato/features/updates.md). The
usual matrix, adjusted by the brief:

- `primary` (macOS 26), fresh arm64 DMG: setup, a manual pause, a schedule
  that starts and ends with the window closed, `observe`.
- No `legacy` (macOS 15) run: the maintainer dropped it on 2026-10-28
  (`user-confirmed`), since `primary` covers current behavior and `ventura`
  the oldest supported macOS; its golden VM is no longer kept. Recreate it
  only when a brief names a macOS 15 risk.
- Upgrade on `primary`: the previous DMG (`gh release download v<previous>`)
  set up with the helper, websites, an application, and a schedule, then
  `vm install --replace` with the new DMG.
- `ventura` (macOS 13): the x86-64 release DMG passes Gatekeeper, shows "This
  version is for Intel Macs", and leaves nothing after Quit; the verification
  candidate passes setup, a blocking pause, and a scheduled start.
- Test iPhone: the previous tag's development build with state, R installed
  over it and checked before and after the first open; the core flow on R.
- iCloud on `primary` with the development build of R, in this order:
  `flow icloud link`; `pause-sets-notice-desktop.json` before anything else
  opens Pause sets, because the first linked visit uses up the notice;
  `pause-sets-desktop.json`; the sync checks the brief names from
  [Sync with iCloud](../../.agents/skills/verify-posato/features/sync.md);
  and `flow icloud remove` before `vm destroy`. Routine task verification
  leaves iCloud out, so this is the regular check of synchronization and of
  editing right after a link.
- Once the `primary` checks are done, `vm destroy --line primary`, then
  prepare a fresh `primary` clone with the previous release as in the
  updates recipe (helper, websites, an application, a schedule, update
  consent answered) and keep it running for step 7. A line has one clone
  (`posato-run-<line>`), so this clone cannot run beside the checks above.
- Linux clone, the workflow's arm64 `.deb` through `install -t linux
  --package`: onboarding with the pausing service, a pause that writes four
  lines per website to `/etc/hosts` and ends a chosen application, and the
  file restored exactly after **End session early**. The x86-64 package has
  only the workflow's checks; no x86-64 Linux machine runs the E2E.
- Test Android phone, the signed APK through `install -t android
  --package --grants settings` (it replaces a debug build after removing
  it): onboarding as a person meets it, a pause that answers blocked names
  with `127.0.0.1` and covers a chosen application, and release after the
  end. A release APK is not debuggable, so assert through the interface.
- Folder sync on the release builds: one device creates a folder workspace,
  the others join by code through `posato-control relay`, and a pause
  started on one is enforced on the others. Linux and a macOS clone never
  run together, so chain them: Linux creates, the phone joins, then the
  Linux clone goes and a Mac joins with a code from the phone.

Gotchas:

- A clone for a notarized DMG needs no staged package. Commands that launch
  the development package refuse with `PACKAGE_OUTDATED` when the host staged
  another one after the last `vm sync`.
- `ventura` needs `posato.vm.venturaGolden` in `local.properties`.
- At most two guests run, never a golden VM beside its clone; the kept
  previous-release clone takes one slot.
- A release build's "Check for updates automatically?" modal blocks every tap
  until `update-consent` answers it.
- The previous release's setup can need `vm prompt toggle --row
  PosatoMacOSHelper`, and its labels can differ from the current fixtures.
- Release builds cannot pair with a development-signed iPhone (Production
  versus Development CloudKit); check that sync on development builds of R.
- Clear `/sdcard/PosatoSync` on the phone and the relay hub before a folder
  run; files left by an earlier test make the first device join an old
  workspace instead of creating one.
- A destroyed clone still linked to a folder workspace is refused like an
  iCloud one; remove the workspace in the app first.
- Never run `defaults find` in a guest (it raises blocking privacy prompts);
  `osascript` in a guest raises an Automation prompt for `tart-guest-agent`.

**Done when:** the record has a dated result per check with run identifiers
under the ignored `build/verification/`, and no captures in tracked files.

## 5. Completed-change review

An independent reviewer reads R, the candidates, the evidence, and the
`TB-08`/`T-13` review: entitlements against the previous release, feed
contents, the IPA's signature and entitlements, the release key never
leaving the Keychain.

**Done when:** no Critical or Required finding is open and the pull request
is ready for review.

## 6. Merge and tag, after the go

1. Set `PRIVACY.md`'s effective date to the publication date on the packaging
   pull request, rebase it onto `main`, and squash-merge it; then rebase and
   squash-merge the release pull request.
2. Tag only when `git diff --name-only <R> HEAD` lists nothing outside
   `docs/`, `website/`, `video/`, `.github/`, `README.md`, `PRIVACY.md`,
   `tools/posato-control/`, and `tools/posato-provisioning/`, and `LICENSE`,
   `NOTICE`, `THIRD_PARTY_NOTICES.md`, and `Version.xcconfig` equal R;
   otherwise stop. The tag is the annotated `v<version>` on that head.

**Done when:** the tag exists and the record holds the diff evidence.

## 7. GitHub Release and the in-app update

1. Upload exactly `Posato-<version>.dmg`, `Posato-<version>-intel.dmg`,
   `appcast.xml`, `appcast-intel.xml`, `posato_<version>_arm64.deb`,
   `posato_<version>_amd64.deb`, `Posato-<version>.apk`, and one
   `SHA256SUMS` covering the other seven to a draft release; download and compare them byte for byte with step 3; publish as
   latest with the packaging row's notes. A release without the feeds uses
   `--latest=false`.
2. `releases/latest/download/appcast.xml` and `appcast-intel.xml` resolve,
   equal the verified feeds, and verify.
3. On the kept previous-release clone: About, Check for Updates, Install Update,
   Install and Relaunch, `launch --adopt`; data, schedule, and helper are
   kept and `observe` blocks. Any loss is a failure. Before updating, check
   that clone's `scheduleConsent` from `vm onboard` (use
   `--allow-schedules` when it is false) and prove that a schedule starts on
   its own on the old version: setup records the consent from 1.2 on, but
   one 1.3.0 test clone still read it as off, which once read as an update
   defect (`RELEASE-006`, cause `open`).

Replacing a release older than `MACOS-026` shows that release's own "Quit
Posato?" during Install and Relaunch; click Quit (`observed`,
`RELEASE-005`). Installing waits for an active pause to end.

**Rollback:** `gh release edit v<previous> --latest`, hold iOS, and revert
the packaging commit on `main`; a fix build needs a number above `<build>`.
In 1.3 the Intel feed answered 404 after a rollback, an accepted exception
(`user-confirmed`, 2026-10-04) because 1.2 had no Intel feed.

**Done when:** the release is latest, both feeds verify, and the update
passed or the rollback is recorded.

## 8. iOS submission and public checks

1. Rerun `store prepare` if the store files changed on `main` since step 3,
   then `store submit --version <version>`.
2. `posato.app` (Cloudflare Pages builds from `main`), the README, the
   availability page, and `PRIVACY.md` show the release; links answer 200.
3. Set both rows Done in the Projects mirror and close milestone
   `<version>`. Ask the maintainer to upload `.github/assets/social-preview.png`
   in the repository settings when it changed.

**Done when:** `store status` shows `WAITING_FOR_REVIEW`, the site shows the
release, Projects says Done, and the milestone is closed.

## 9. Closeout

After App Review, a follow-up pull request records the publication and the
outcome, sets the record `done`, and appends at most one wiki-log entry.
Delete the never-published verification candidates. Then delete the
release's clean clones and the folder of candidates and logs outside the
checkout (every published asset is on GitHub Releases), and release their
disk claims. Copy out first any run directory the record cites that lives
only in a clean clone (`RELEASE-010` kept its update and triage runs this
way).

**Done when:** that pull request is merged.
