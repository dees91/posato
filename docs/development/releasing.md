# Releasing Posato

The standing route for a release: notarized macOS builds for arm64 and
x86-64 with two update feeds on GitHub Releases, and the iOS build through
App Review. It condenses what `RELEASE-003` to `RELEASE-005` proved. Command
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
  A major macOS update can leave it out (macOS 27.0.1 had none in
  `RELEASE-006`), so check `arch -x86_64 /usr/bin/true` before step 3; when
  it fails, the maintainer runs `softwareupdate --install-rosetta
  --agree-to-license`.
- **Verification:** the `primary`, `peer`, `legacy`, and `ventura` golden
  VMs, the test iPhone, and their `local.properties` keys
  ([unattended verification](unattended-verification.md)).
- **Tools:** `installDist` of `posato-control` and `posato-provisioning`;
  `gh` with the `project` scope.

**Who does what.** The agent does every step it can, including the merges
after the publication go (since `RELEASE-005`; the maintainer merged in
`RELEASE-004`). The maintainer gives the go and does what the App Store
Connect API cannot. Keychain access to the signing keys, the release update
key included, may be pre-granted with Always Allow, so signing runs
unattended (`user-confirmed`, 2026-10-10); the risk is that any process
running as the maintainer can then use those keys without a prompt. When a
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

**Done when:** the record lists both DMG and feed hashes and the consumed
build numbers, the iOS build is `VALID`, and a rerun of `store prepare`
reports every item `unchanged` with `store status` at `PREPARE_FOR_SUBMISSION`.

## 4. Unattended verification

Follow [`verify-posato`](../../.agents/skills/verify-posato/SKILL.md) and its
[updates recipe](../../.agents/skills/verify-posato/features/updates.md). The
usual matrix, adjusted by the brief:

- `primary` (macOS 26), fresh arm64 DMG: setup, a manual pause, a schedule
  that starts and ends with the window closed, `observe`.
- `legacy` (macOS 15), fresh: the core flow and a login launch (`vm shutdown`,
  `vm boot`) with a scheduled start.
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
- Keep a `peer` clone running with the previous release set up (helper,
  websites, an application, a schedule, update consent answered) for step 7.

Gotchas:

- A clone for a notarized DMG needs no staged package. Commands that launch
  the development package refuse with `PACKAGE_OUTDATED` when the host staged
  another one after the last `vm sync`.
- `ventura` needs `posato.vm.venturaGolden` in `local.properties`.
- At most two guests run, never a golden VM beside its clone; the kept
  `peer` clone takes one slot.
- A release build's "Check for updates automatically?" modal blocks every tap
  until `update-consent` answers it.
- The previous release's setup can need `vm prompt toggle --row
  PosatoMacOSHelper`, and its labels can differ from the current fixtures.
- Release builds cannot pair with a development-signed iPhone (Production
  versus Development CloudKit); check that sync on development builds of R.
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
   `appcast.xml`, `appcast-intel.xml`, and the merged `SHA256SUMS` to a draft
   release; download and compare them byte for byte with step 3; publish as
   latest with the packaging row's notes. A release without the feeds uses
   `--latest=false`.
2. `releases/latest/download/appcast.xml` and `appcast-intel.xml` resolve,
   equal the verified feeds, and verify.
3. On the kept `peer` clone: About, Check for Updates, Install Update,
   Install and Relaunch, `launch --adopt`; data, schedule, and helper are
   kept and `observe` blocks. Any loss is a failure.

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
Delete the never-published verification candidates.

**Done when:** that pull request is merged.
