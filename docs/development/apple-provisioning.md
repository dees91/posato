# Apple Development Provisioning

`posato-provisioning` obtains the Apple **development** resources the Posato
targets need, so no portal step blocks an agent. It registers this Mac, the
connected iPhone, and the Tart verification VMs, confirms the development certificate this Mac signs with, and
creates, downloads, and installs a development profile for any of the five
Posato App IDs.

Its `store` commands drive the iOS App Store release of the existing
`app.posato.ios` app record: the App Store version, its release type, What's
New, screenshots, attached build, and the App Review submission. It does not
create app records or distribution certificates, and it does not touch
TestFlight groups, notarization, or App ID capabilities. It never runs in CI;
routine CI stays credential-free.

## Prerequisites

One App Store Connect API **team** key with the **Admin** role, created by the
maintainer under Users and Access → Integrations. The `.p8` downloads once and
cannot be downloaded again; store it under `~/Library/Developer/Posato/`.

Nothing below is tracked. `local.properties` is ignored, and the key, the
profiles, and the certificate all live outside the checkout.

| Key | Environment variable | Purpose |
| --- | --- | --- |
| `posato.asc.keyId` | `POSATO_ASC_KEY_ID` | The key identifier shown next to the key |
| `posato.asc.issuerId` | `POSATO_ASC_ISSUER_ID` | The issuer identifier shown above the key list |
| `posato.asc.privateKeyPath` | `POSATO_ASC_PRIVATE_KEY_PATH` | Absolute path to the untracked `.p8` |
| `posato.apple.developmentTeam` | `POSATO_APPLE_DEVELOPMENT_TEAM` | The team every resource must belong to |

The environment wins over `local.properties`, and a blank value counts as
absent. This is the same convention the verification driver uses, so a
provisioned checkout configures both tools once.

## Install and run

```shell
./gradlew :posato-provisioning:installDist
tools/posato-provisioning/build/install/posato-provisioning/bin/posato-provisioning doctor
```

Every command prints one JSON envelope; `--human` prints a short summary
instead, and `--verbose` echoes each helper command and request to stderr with
every known value redacted.

## Commands

| Command | Effect |
| --- | --- |
| `doctor` | Reports every provisioning condition as OK, MISSING, or UNKNOWN with one action that would clear it. Exits non-zero while an error-severity condition is unmet. |
| `devices register [--tart-vm <name>]...` | Registers this Mac, every **wired** iPhone, and each named running Tart VM the account does not already hold. VMs are named `Posato Verification VM`. |
| `certificates ensure [--create]` | Confirms this Mac signs with a certificate the account also holds. With `--create`, generates a key pair, requests a certificate, and imports it. |
| `profiles ensure <app-id> [--platform ios\|macos] [--replace]` | Makes the development profile for one App ID current and installs it. |
| `store status [--version X.Y.Z]` | Read-only. Lists the iOS App Store versions with state and release type, the builds with marketing version and processing state, and the next free build number; with `--version`, also that version's attached build, en-US What's New, and screenshot sets with delivery states. |
| `store prepare --version X.Y.Z --build N --whats-new <file> --release after-approval\|manual [--screenshots <dir>]` | Brings one App Store version to the release state, changing only what differs. See [iOS App Store release](#ios-app-store-release). |
| `store submit --version X.Y.Z` | Submits the version to App Review once it has a build and every screenshot is `COMPLETE`. Does nothing when it is already waiting for or in review. |

Use this tool, not the portal or ad hoc App Store Connect scripts, for every
development device, certificate, and profile and for the App Store release
steps below; when it lacks an operation a task needs, extend it. The
[unattended verification guide](unattended-verification.md) uses
`--tart-vm` for its golden VMs.

Exit codes: `2` usage, `3` a condition the maintainer must clear, `4` a
resource the account does not have, `1` anything that failed while working.

`--platform` is checked against the App ID rather than used to choose one, so a
mistaken flag fails instead of quietly producing the wrong profile.

## App IDs, names, and destinations

| App ID | Platform | Installed as |
| --- | --- | --- |
| `app.posato.ios` | iOS | `Posato_iOS_App_Development.mobileprovision` |
| `app.posato.ios.activitymonitor` | iOS | `Posato_iOS_ActivityMonitor_Development.mobileprovision` |
| `app.posato.macos` | macOS | `Posato_macOS_App_Development.provisionprofile` |
| `app.posato.macos.helper` | macOS | `Posato_macOS_Helper_Development.provisionprofile` |
| `app.posato.macos.sync` | macOS | `Posato_macOS_Sync_Development.provisionprofile` |

All five land in `~/Library/Developer/Posato/`. iOS profiles are additionally
written to `~/Library/Developer/Xcode/UserData/Provisioning Profiles/` under
the profile's own identifier, which is where current Xcode reads them; the
legacy `~/Library/MobileDevice/Provisioning Profiles` is not written.

macOS profiles are not copied into the Xcode directory, because nothing reads
them from there.

## The macOS sync profile and desktop packaging

`SYNC-006` created `Posato_macOS_Sync_Development.provisionprofile` by hand and
pointed `posato.macos.syncProvisioningProfile` at it. This tool writes to that
same path, so a run replaces the hand-made profile in place and
`:desktopApp:verifyMacOsDevelopmentPackaging` picks up the new one with no
configuration change. The tool never edits `local.properties`.

That task is the real acceptance test for a macOS sync profile: it asserts the
signed companion carries the CloudKit container, the CloudKit service, one
keychain access group ending `.app.posato.sync`, and an application identifier
of `<team>.app.posato.macos.sync`.

## macOS Developer ID release

`MACOS-008` adds a release path next to development packaging. `posato-provisioning` does not create its resources:

- **Certificate.** A Developer ID Application certificate needs the Account Holder role, and the Admin team key cannot create it. The maintainer creates it in Xcode under Settings → Accounts → Manage Certificates → + → Developer ID Application.
- **Profile.** The maintainer creates a Developer ID provisioning profile named `Posato macOS Sync Developer ID` for `app.posato.macos.sync` in the portal (Profiles → + → Developer ID) and saves it as `~/Library/Developer/Posato/Posato_macOS_Sync_Developer_ID.provisionprofile`.

Both stay outside the checkout. The release is built by hand and never runs in `quality`:

```shell
./gradlew :desktopApp:notarizeMacOsRelease \
  -PposatoMacOsUpdateChannel=release \
  -PposatoMacOsBuildNumber=<next build number> \
  "-PposatoMacOsReleaseSigningIdentity=Developer ID Application: <name> (<team>)" \
  -PposatoMacOsSyncDeveloperIdProfile=~/Library/Developer/Posato/Posato_macOS_Sync_Developer_ID.provisionprofile
```

Notarization uses the same App Store Connect team key as this tool. It reads the `posato.asc.*` values from `local.properties`, or the `POSATO_ASC_*` environment variables. `-PposatoAscKeyId`, `-PposatoAscIssuerId`, and `-PposatoAscPrivateKeyPath` override them for a single run.

Create the certificate under the **G2 Sub-CA**. A certificate from the previous Sub-CA expires on 1 February 2027, and Xcode can still issue one. When the keychain holds more than one Developer ID Application identity with the same name, pass the SHA-1 hash that `security find-identity -v -p codesigning` prints for the G2 identity instead of the name.

The build number is a positive integer higher than the previous candidate's. The release tasks refuse to run without it, and it is never tracked. The marketing version comes from the root `Version.xcconfig`.

The chain works in this order:
1. It stages a separate `release-package`.
2. It signs every nested item with Developer ID and a secure timestamp.
3. It verifies the result: signatures, the embedded profile against every signed companion entitlement, versions, the icon, the notices, and the Temurin runtime.
4. It notarizes and staples the application.
5. It builds the DMG, then signs, notarizes, and staples that too.
6. It checks both artifacts with `stapler validate` and `spctl`.

A rejected submission leaves the notarization log under the task's `build/tmp` directory. The first signing run may raise a keychain prompt asking `codesign` to use the Developer ID key.

### Update feed and channels

`MACOS-011` adds Sparkle updates. Every Developer ID build names its update channel:

- `-PposatoMacOsUpdateChannel=release` embeds the stable feed `https://github.com/dees91/posato/releases/latest/download/appcast.xml` and the tracked release key. Any other feed or key fails the build.
- `-PposatoMacOsUpdateChannel=candidate` builds a test candidate. It needs its own `-PposatoMacOsUpdateFeedUrl`, an HTTPS or `http://127.0.0.1:<port>/` URL ending in `/appcast-test.xml`, and `-PposatoMacOsUpdatePublicKey`. It may never read the stable feed, and the release feed refuses a candidate build.

A development package takes no channel and embeds a feed only when both `posatoMacOsUpdateFeedUrl` and `posatoMacOsUpdatePublicKey` are passed.

The release key lives only in the maintainer's login Keychain under account `posato-release`, with an encrypted backup outside the repository. Tools read it only from the Keychain; never pass `-s`, `--ed-key-file`, or an environment variable. Test candidates signed with this key use build numbers that the next stable release must exceed; the `MACOS-011` execution record lists them. Pass the highest of them as `-PposatoMacOsPreviousBuildNumber` for the first release so the validation enforces it.

To build a release together with its signed feed:

```shell
./gradlew :desktopApp:generateMacOsUpdateFeed \
  -PposatoMacOsUpdateChannel=release \
  -PposatoMacOsBuildNumber=<next build number> \
  -PposatoMacOsPreviousBuildNumber=<previous stable build number> \
  -PposatoMacOsReleaseNotes=<plain-text notes>.txt \
  "-PposatoMacOsReleaseSigningIdentity=Developer ID Application: <name> (<team>)" \
  -PposatoMacOsSyncDeveloperIdProfile=~/Library/Developer/Posato/Posato_macOS_Sync_Developer_ID.provisionprofile
```

The task runs the whole notarized chain above, then copies the stapled DMG as `Posato-<version>.dmg` into a clean `build/compose/binaries/main/release-feed/`. It runs Sparkle's `generate_appcast` with the Keychain key, embedded plain-text notes, and no deltas. Signing raises a Keychain prompt for the release key. The task then checks the result against the DMG and the key embedded in the application, and refuses to finish unless all of the following hold:

- both the feed signature and the archive signature verify;
- the feed has exactly one item, whose `sparkle:version` equals `CFBundleVersion` and exceeds the previous build;
- the item requires macOS 15.0 and arm64 and carries no release-notes link or deltas;
- the enclosure points to `releases/download/v<version>/Posato-<version>.dmg` and its length matches the DMG.

It also writes `SHA256SUMS`.

Publish `Posato-<version>.dmg`, `appcast.xml`, and `SHA256SUMS` together (`RELEASE-003` owns publication):

1. Upload all three to a draft release and publish it only when it is complete.
2. After publishing, confirm that `https://github.com/dees91/posato/releases/latest/download/appcast.xml` resolves and that the downloaded feed still verifies.
3. Publish any release without a macOS feed with `--latest=false`, so the stable feed keeps resolving.

The task reads the feed URL, key, and build number from the application inside the DMG and requires that they match the staged application. A candidate feed uses `-PposatoMacOsUpdateChannel=candidate` and `-PposatoMacOsUpdateDownloadPrefix=<HTTPS or loopback URL ending in />`, and writes `Posato-<version>-<build>-test.dmg` with `appcast-test.xml`. `-PposatoMacOsUpdateKeyAccount` selects another Keychain account, for example a throwaway test key.

## iOS App Store release

The iPhone and iPad app ships through App Store Connect. The archive, export,
and upload use Xcode and `altool` with the same team key as this tool; the
App Store version, What's New, screenshots, build attachment, and submission
use the `store` commands. Replace `<team>`, `<key id>`, `<issuer id>`, and the
paths; keep every output outside the checkout or under the ignored `build/`.

1. **Pick the build number.** It is the highest build App Store Connect holds
   plus one; `store status` prints it as `nextBuildNumber`. The marketing
   version comes from the root `Version.xcconfig`.
2. **Archive.**

   ```shell
   xcodebuild archive -project iosApp/iosApp.xcodeproj -scheme iosApp \
     -configuration Release -destination generic/platform=iOS \
     -archivePath <out>/Posato-<version>-<build>.xcarchive \
     -allowProvisioningUpdates \
     -authenticationKeyPath <key.p8> -authenticationKeyID <key id> \
     -authenticationKeyIssuerID <issuer id> \
     DEVELOPMENT_TEAM=<team> CODE_SIGN_STYLE=Automatic CURRENT_PROJECT_VERSION=<build>
   ```

3. **Export.** Write `<ExportOptions.plist>` outside the checkout with `method`
   `app-store-connect`, `destination` `export`, `signingStyle` `automatic`,
   `teamID` `<team>`, `uploadSymbols` true, and
   `manageAppVersionAndBuildNumber` false, then run:

   ```shell
   xcodebuild -exportArchive -archivePath <out>/Posato-<version>-<build>.xcarchive \
     -exportPath <out>/export -exportOptionsPlist <ExportOptions.plist>
   ```

   The export writes `<out>/export/Posato.ipa`.

4. **Inspect the signed IPA** before uploading. Unzip it and read both bundles
   with `codesign -dvv` and `codesign -d --entitlements - --xml`:
   - `Payload/Posato.app` and `Payload/Posato.app/PlugIns/ActivityMonitor.appex`
     are signed by **Apple Distribution** for `<team>`;
   - both carry `com.apple.developer.family-controls` and the app group
     `group.app.posato.ios.session`;
   - the app also carries the `iCloud.app.posato.sync` container with the
     CloudKit service, `com.apple.developer.icloud-container-environment` set
     to `Production`, and the `<team>.app.posato.sync` keychain access group;
   - `get-task-allow` is false in both;
   - `PrivacyInfo.xcprivacy` is present in the app and in the extension.
5. **Validate and upload.** `altool` reads the key from
   `API_PRIVATE_KEYS_DIR`, a directory holding `AuthKey_<key id>.p8`:

   ```shell
   API_PRIVATE_KEYS_DIR=<dir> xcrun altool --validate-app -f <out>/export/Posato.ipa \
     -t ios --apiKey <key id> --apiIssuer <issuer id>
   API_PRIVATE_KEYS_DIR=<dir> xcrun altool --upload-app -f <out>/export/Posato.ipa \
     -t ios --apiKey <key id> --apiIssuer <issuer id>
   ```

   Wait until `store status` lists the build as `VALID`.
6. **Prepare the version.** Put the accepted What's New text from
   [`docs/store/en-US/listing.md`](../store/en-US/listing.md) in a plain-text
   file, then:

   ```shell
   posato-provisioning store prepare --version <version> --build <build> \
     --whats-new <whats-new.txt> --release after-approval \
     --screenshots docs/store/en-US/screenshots
   ```

   It refuses a build that is missing or not `VALID` before writing anything,
   and it refuses an existing version whose state is not
   `PREPARE_FOR_SUBMISSION`, `DEVELOPER_REJECTED`, `REJECTED`,
   `METADATA_REJECTED`, or `INVALID_BINARY` (`VERSION_NOT_EDITABLE`). It creates the iOS App Store version when absent, and App Store Connect
   copies the description, keywords, review details, and screenshots from the
   previous version. The command then sets the release type, the en-US What's
   New text, and the build. `--screenshots` replaces `iphone-6.9/*.png` in the
   `APP_IPHONE_67` set and `ipad-13/*.png` in the `APP_IPAD_PRO_3GEN_129` set,
   in file-name order. A set that already holds the same files with the same
   MD5 checksums is left alone. Otherwise every screenshot in it is deleted,
   and each file is reserved, uploaded, and committed with its MD5. The command
   then waits up to ten minutes until every screenshot is `COMPLETE`. Input
   PNGs must have no alpha channel. Use `--release manual` to release by hand
   after approval.
7. **Submit.** `posato-provisioning store submit --version <version>` refuses a
   version without a build, with a screenshot that is not `COMPLETE`, or in a
   state App Review does not accept, such as one already released. After a
   rejection it marks the version's rejected item resolved
   (`PATCH reviewSubmissionItems/<id>` with `resolved: true`), then resubmits
   the submission with unresolved issues that holds it. Otherwise it reuses an
   unsubmitted review submission rather than creating a second one, adds the
   version, and submits. It never creates a submission while another unresolved
   one is open. Before any write, it refuses a draft or unresolved submission
   that holds any other item, such as another version, an App Event, or a
   custom product page, so it never submits material it was not asked to.

`hypothesis`: every `store` command is designed to be idempotent, so a rerun
after a failure completes what the first run left undone, a rerun with the same
inputs changes nothing, and `store submit` changes nothing once the version is
waiting for or in review. Unit tests against recorded responses cover this, but
no `store prepare` or `store submit` has run against App Store Connect yet. The
label stays until the first live run of both on the next release confirms it.

The store screenshot capture recipe is in the
[App Store listing](../store/en-US/listing.md#screenshots).

## Idempotence and `--replace`

A repeat run issues only reads and changes nothing.

- **Devices** are created only when the account does not hold the identifier. A
  registered-but-disabled device is reported, never created again: re-enabling
  it is a portal decision.
- **Certificates** are reused whenever this Mac holds one the account also
  holds. Creation happens only with `--create`.
- **Profiles** are reused when the account's profile is active, unexpired, and
  already references this Mac's certificate and every registered device.

An **expired or invalid** profile is deleted and recreated without asking.
App Store Connect keeps profile names unique within a team, so the dead profile
blocks the name its replacement needs, and it grants nothing that could be
lost.

A profile that is **still valid** but no longer covers a newly registered
device is not replaced automatically. Something else may rely on it, so the
command stops with `PROFILE_STALE` and asks for `--replace`.

## Request bounds and what is never recorded

API requests use only `GET`, `POST`, `PATCH`, and `DELETE`. They go only to
`https://api.appstoreconnect.apple.com/v1/`, over paths built from constants
and resource identifiers that must be plain path segments, with redirects
refused. One 60-second deadline covers all attempts, a response is capped at
4 MiB, and a second page of results stops the command rather than following a
URL the service supplied. The one listing that reads only its first page is the
newest-first build list in `store status`, which reports `moreBuilds` instead
of following the next page.

Only `GET` is retried. A `POST`, `PATCH`, or `DELETE` that times out may already
have been applied, so repeating it could create a duplicate certificate against
Apple's per-team cap, a conflicting device, a second profile claiming a unique
name, or a second screenshot reservation. Rerunning the command is the safe
recovery, because the reuse logic sees whatever the first attempt created. An
unauthorized response is never retried.

**Screenshot uploads are the one exception** to service-supplied URLs. A
screenshot's bytes can only go where the reservation's upload operations say,
so `store prepare` accepts those URLs under a narrow policy, checked for every
operation before any part is sent:

- the method is exactly `PUT`, the scheme `https`, the host ends in `.apple.com`
  on the default port, and the URL carries no user information;
- the request carries only the operation's own headers, which must be plain
  names and printable values. `Content-Length` and `Host` must match what the
  client derives from the byte range and the URL; `Connection`, `Expect`,
  `Upgrade`, and `Transfer-Encoding` are refused;
- the body is exactly the file slice the operation names, and that slice must
  lie inside the file;
- redirects are refused, the App Store Connect token is never sent, each part
  has a 120-second deadline, and a part is attempted once.

Anything else refuses the whole upload with `UPLOAD_REFUSED` before a byte
leaves the Mac. Neither the transcript nor an error message repeats an upload
URL, because its query can carry a signature; the transcript records only the
part number and size.

Never printed, logged, or written: the key identifier, the issuer identifier,
the team identifier, any device identifier, the certificate common name, the
path of the `.p8`, any screenshot upload URL, and any response body. A failed
request reports its category and, at most, App Store Connect's enumerated error
codes, which are a closed vocabulary carrying no data. The tool writes no
transcript file. `store status` prints selected fields (version strings, build
numbers, states, counts, and the public What's New text) and no resource
identifiers.

Two paths into a message are closed deliberately. A response whose shape the
tool cannot read is reported by naming the resource only, because the decoder
quotes the input around the offset and that slice would be other devices'
identifiers. The home directory and the checkout path are registered as
secrets, so a helper command, a tool's own error output, or a file-system
failure renders them as `<redacted>/Library/...` rather than naming a person in
an envelope that gets pasted into a record.

The names sent to Apple when registering are the fixed strings
`Posato Development Mac` and `Posato Development iPhone`, never the host name
or the device name.

## How this differs from `posato-control doctor`

The two reports answer different questions and share no check identifiers.

| | `posato-control doctor` | `posato-provisioning doctor` |
| --- | --- | --- |
| Question | Can this checkout build, sign, and drive the applications right now? | Can this Mac obtain Apple resources, and does the account hold them? |
| Checks | The configured signing identity and profile, Xcode, permissions, the staged bundle | The API credential, the token, the five App IDs, device registration, the certificate, the installed profiles |
| Remedy | Set a `local.properties` value | Run a `posato-provisioning` command |
| Identifiers | `desktop.*`, `xcode.*`, `config.*` | `asc.*`, `provisioning.profile.*` |

## Troubleshooting

- **`ASC_UNAUTHORIZED`** — the key, the issuer, or the signature was rejected.
  Confirm the key is still active under Users and Access and that the issuer
  belongs to the same team. This is never retried, so it fails immediately.
- **`ASC_FORBIDDEN`** — the key's role is not Admin.
- **`ASC_CONFLICT`** — the resource already exists. Rerun; the command reuses it.
- **`ASC_RATE_LIMITED`** / **`ASC_UNAVAILABLE`** — retried three times for reads,
  then reported. Wait and rerun.
- **`ASC_TOO_MANY_RESULTS`** — the account holds more resources than one page.
  Remove what this Mac no longer needs in the portal.
- **`BUILD_MISSING`** / **`BUILD_NOT_READY`** — App Store Connect does not hold
  that build number for that marketing version, or has not finished processing
  it. `store status` lists the builds and their states.
- **`SUBMISSION_NOT_READY`** — the version lacks a build, has a screenshot
  that is not `COMPLETE`, or is in a state App Review does not accept, or
  another open submission is in the way: an unresolved one, or a draft or
  unresolved submission that also holds another item.
- **`VERSION_NOT_EDITABLE`** — `store prepare` found the version waiting for
  or in review, pending release, or released, and changed nothing.
- **`UPLOAD_REFUSED`** — an upload operation failed the policy above. Nothing
  was uploaded; rerun `store prepare`, and review the tool if it repeats.
- **`CERTIFICATE_MISSING`** — either this Mac has no usable certificate, or the
  account no longer lists the one it has, which is what a revocation looks
  like. Run `certificates ensure --create`.
- **`CERTIFICATE_IMPORT_FAILED`** — `security import` writes both the private key
  and the certificate into the login keychain and may raise a keychain prompt.
  By this point App Store Connect has already issued the certificate and
  consumed one of the team's slots, so the tool **keeps** the issued `.cer` and
  its private key in the owner-only working directory it names in the message,
  rather than deleting them. Approve the prompt and import both by hand with
  `security import <file> -T /usr/bin/codesign`. Do not rerun with `--create`:
  that requests a second certificate against the same cap while the first stays
  unusable. This tool deliberately does not run
  `security set-key-partition-list` to silence later prompts, because that needs
  the keychain password. Whether the import produced a usable identity is only
  knowable by asking the keychain again, which is what the command reports on.
- **No connected iPhone** — reported as UNKNOWN, never as a failure. "Connected"
  means wired: a phone paired over the local network also reports a tunnel, and
  that tunnel's state changes between readings, so selecting on it would
  register a different set of devices depending on when the command ran.
  Requiring the cable keeps the selection deterministic and stops a phone that
  merely shares the network from consuming one of the team's limited device
  slots. Connect the phone you develop on and rerun `devices register`.
