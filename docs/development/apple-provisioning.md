# Apple Development Provisioning

`posato-provisioning` obtains the Apple **development** resources the Posato
targets need, so no portal step blocks an agent. It registers this Mac and the
connected iPhone, confirms the development certificate this Mac signs with, and
creates, downloads, and installs a development profile for any of the five
Posato App IDs.

It does not touch distribution certificates, App Store Connect app records,
TestFlight, notarization, or App ID capabilities, and it never runs in CI.
Routine CI stays credential-free.

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
| `devices register` | Registers this Mac and every **wired** iPhone the account does not already hold. |
| `certificates ensure [--create]` | Confirms this Mac signs with a certificate the account also holds. With `--create`, generates a key pair, requests a certificate, and imports it. |
| `profiles ensure <app-id> [--platform ios\|macos] [--replace]` | Makes the development profile for one App ID current and installs it. |

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

Requests go only to `https://api.appstoreconnect.apple.com/v1/`, over paths
built from constants, with redirects refused. One 60-second deadline covers all
attempts, a response is capped at 4 MiB, and a second page of results stops the
command rather than following a URL the service supplied.

Only `GET` is retried. A `POST` or `DELETE` that times out may already have
been applied, so repeating it could create a duplicate certificate against
Apple's per-team cap, a conflicting device, or a second profile claiming a
unique name. Rerunning the command is the safe recovery, because the reuse
logic sees whatever the first attempt created. An unauthorized response is
never retried.

Never printed, logged, or written: the key identifier, the issuer identifier,
the team identifier, any device identifier, the certificate common name, the
path of the `.p8`, and any response body. A failed request reports its category
and, at most, App Store Connect's enumerated error codes, which are a closed
vocabulary carrying no data. The tool writes no transcript file.

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
