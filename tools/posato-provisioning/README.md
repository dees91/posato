# posato-provisioning

Obtains Apple **development** provisioning for the five Posato App IDs through
the App Store Connect API, so no portal step blocks an agent.

The full guide, including the App Store Connect key the maintainer must create,
is [`docs/development/apple-provisioning.md`](../../docs/development/apple-provisioning.md).

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

## Commands

| Command | Effect |
| --- | --- |
| `doctor` | Every provisioning condition as OK, MISSING, or UNKNOWN with one remedy. Non-zero while an error-severity condition is unmet. |
| `devices register [--tart-vm <name>]...` | Registers this Mac, every **wired** iPhone, and each named running Tart VM the account lacks. |
| `certificates ensure [--create]` | Reuses the certificate this Mac signs with; creates one only with `--create`. |
| `profiles ensure <app-id> [--platform ios\|macos] [--replace]` | Makes one App ID's development profile current and installs it. |

Global options: `--human` for a short summary, `--verbose` for a redacted
transcript on stderr. Exit codes are `2` usage, `3` a condition to clear, `4` a
missing account resource, `1` a failure while working.

## What it never records

The key identifier, the issuer, the team, any device identifier, the
certificate common name, the `.p8` path, and every response body stay out of
the envelope, out of stderr, and out of any file. A failed request reports its
category and, at most, App Store Connect's enumerated error codes. The tool
writes no transcript file, and it never edits `local.properties`.
