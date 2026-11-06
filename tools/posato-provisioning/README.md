# posato-provisioning

Obtains Apple **development** provisioning for the five Posato App IDs through
the App Store Connect API, so no portal step blocks an agent. Its `store`
commands drive the iOS App Store release (version, What's New, screenshots,
build, review submission) so no release needs an ad hoc API script. Its `play`
commands upload, promote, and list the Android app on Google Play through the
Play Developer API.

The full guide, including the App Store Connect key the maintainer must create,
is [`docs/development/apple-provisioning.md`](../../docs/development/apple-provisioning.md).
The Google Play guide, including the service account the maintainer must
create, is [`docs/development/google-play.md`](../../docs/development/google-play.md).

Nothing this tool drives runs in `./gradlew quality` or in CI. The aggregate
gate runs only this module's ktlint, Detekt, and unit tests; routine CI stays
credential-free.

## Setup

```shell
cp ../posato/local.properties .      # fresh worktree only; copy the whole file
./gradlew :posato-provisioning:installDist
alias posato-provisioning="$PWD/tools/posato-provisioning/build/install/posato-provisioning/bin/posato-provisioning"
posato-provisioning doctor
```

## Configuration

Read from the ignored `local.properties`, with the environment winning and a
blank value counting as absent.

| Key | Environment variable |
| --- | --- |
| `posato.asc.keyId` | `POSATO_ASC_KEY_ID` |
| `posato.asc.issuerId` | `POSATO_ASC_ISSUER_ID` |
| `posato.asc.privateKeyPath` | `POSATO_ASC_PRIVATE_KEY_PATH` |
| `posato.apple.developmentTeam` | `POSATO_APPLE_DEVELOPMENT_TEAM` |
| `posato.play.serviceAccountKeyPath` | `POSATO_PLAY_SERVICE_ACCOUNT_KEY_PATH` |

## Commands

| Command | Effect |
| --- | --- |
| `doctor` | Every provisioning condition as OK, MISSING, or UNKNOWN with one remedy. Non-zero while an error-severity condition is unmet. |
| `devices register [--tart-vm <name>]...` | Registers this Mac, every **wired** iPhone, and each named running Tart VM the account lacks. |
| `certificates ensure [--create]` | Reuses the certificate this Mac signs with; creates one only with `--create`. |
| `profiles ensure <app-id> [--platform ios\|macos] [--replace]` | Makes one App ID's development profile current and installs it. |
| `store status [--version X.Y.Z] [--locale <code>]...` | Read-only: iOS App Store versions, builds, the next build number, and one version's build, What's New, screenshot delivery, and the required listing fields each localization and each `--locale` still lacks. |
| `store prepare --version X.Y.Z --build N --whats-new <file> --release after-approval\|manual [--description <file>] [--keywords <file>] [--support-url <url>] [--marketing-url <url>] [--privacy-url <url>] [--screenshots <dir>] [--subtitle <text>] [--locale <code>] [--rename-from X.Y.Z]` | Creates or updates the App Store version, attaches a `VALID` build, and sets the listing texts and links and replaces the screenshot sets for `--locale` (default `en-US`); changes only what differs. A localization the version or the App Information does not have yet is created. `--rename-from` renames the one unreleased version instead of creating one. |
| `store submit --version X.Y.Z` | Submits to App Review once the build and every screenshot are ready; does nothing when already in review. |
| `store withdraw --version X.Y.Z` | Cancels the version's review submission and waits until the version is editable; does nothing when it already is. |
| `play status` | Read-only: every Google Play track with its releases, and the listing locales. |
| `play upload --aab <file> --mapping <mapping.txt> --track internal\|production [--notes-dir <dir>] [--status completed\|draft]` | Uploads the bundle and its R8 mapping, sets the track's release with per-language notes, and commits one edit. |
| `play promote --version-code <n> --from <track> --to <track> [--status completed\|draft]` | Moves a tested release to another track without uploading again. |
| `play listing --dir <dir>` | Replaces each locale's `<locale>/play/` title, descriptions, icon, feature graphic, and phone screenshots in one edit, after checking every limit locally. |

Global options: `--human` for a short summary, `--verbose` for a redacted
transcript on stderr. Exit codes are `2` usage, `3` a condition to clear, `4` a
missing account resource, `1` a failure while working.

## What it never records

The key identifier, the issuer, the team, any device identifier, the
certificate common name, the `.p8` path, the Play service account's address,
key id, key path, and access token, every screenshot and Play upload session
URL, and every response body stay out of the envelope, out of stderr, and out
of any file. A failed request reports its category and, at most, the service's
enumerated error codes; Google Play's free-text error message reaches only the
`--verbose` transcript, redacted. The tool writes no transcript file, and it
never edits `local.properties`. Screenshot upload URLs and the Play resumable
upload session URL are the only service-supplied URLs it follows, under the
policies in the two guides.
