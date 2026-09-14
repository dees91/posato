# `MACOS-008`: Distribute the macOS app as a notarized Developer ID package

- **Review tier:** `high-risk`
- **Tier reason:** Developer ID signing, notarization submissions, and release packaging use account-level Apple resources and produce the artifact users will trust.
- **Dependencies:** completed `RELEASE-001` (merged as `18a17b2`, blocked verdict with this row as owner).
- **Integration group:** `PR-MAC-DISTRIBUTION`, roadmap wave Release/R2, in parallel with `IOS-003` and `DESIGN-002`.
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [RELEASE-001 record](../executions/release-001-first-release-readiness.md) (AC-04 rows), [architecture baseline](../../decisions/0003-mvp-application-architecture-baseline.md), [macOS helper ADR](../../decisions/0004-macos-helper-ownership-and-lifecycle.md), [threat model](../../security/apple-mvp-threat-model.md) (`TB-08`, `T-13`), [third-party notices](../../../THIRD_PARTY_NOTICES.md).

## Outcome

A release build task produces a Developer ID signed, securely timestamped, notarized, and stapled Posato package for macOS 15 or later on arm64. Downloaded like a user would, it opens without a Gatekeeper override, sets up the helper, and blocks as the development build does, with the shared version and complete notices inside the bundle.

## Boundaries

- Add a release packaging path next to the development one. Every nested executable and library (application launcher, bundled Java runtime, native Skia, SQLite JDBC and window-chrome libraries, helper, daemon, sync companion) is signed with hardened runtime and a secure timestamp and without `get-task-allow`; entitlements stay minimal. Development packaging and `./gradlew quality` keep working.
- Notarize with `notarytool` using the existing App Store Connect API key from `local.properties`; certificates, keys, profiles, and team identifiers never enter Git. A Developer ID provisioning profile for the sync companion is in scope; CloudKit Production behavior is verified by `SYNC-017`, not here.
- Shared version contract, frozen for this wave: `Version.xcconfig` at the repository root contains exactly `MARKETING_VERSION = 1.0.0`. This task adds that identical file and reads it for every bundle version string; `IOS-003` adds the same file for iOS. Build numbers stay per platform.
- `DESIGN-002` supplies `desktopApp/Config/Posato.icns` and the in-app licenses screen; this task wires the icon into the package and owns `desktopApp/build.gradle.kts`.
- Choose the release JDK and bundle its own legal notices; update `THIRD_PARTY_NOTICES.md` if the runtime changes. Record the export-compliance assessment for a download outside the App Store without a legal opinion.
- Non-goals: App Store distribution for macOS, an in-app updater, Intel or macOS 14 support, production CloudKit, publishing a download.

## Acceptance

- `AC-01` — The release package passes `codesign --verify --deep --strict`, `spctl --assess` reports a notarized Developer ID source, notarization is accepted, and `stapler validate` passes; no nested code lacks a secure timestamp or hardened runtime.
- `AC-02` — Application, helper, and companion bundles report the version from `Version.xcconfig`; the build-number policy is recorded.
- `AC-03` — The chosen runtime's notices and the repository notices are present in the signed bundle and match what ships.
- `AC-04` — On a physical Mac with no development build installed, a quarantined copy of the package opens, completes helper setup, and blocks the MVP-001 website and application scenarios; updating over the previous candidate and removal follow ADR 0004 without a stale helper registration.

## Verification

- Independent plan review before signing or submitting anything; independent completed-change review after.
- `./gradlew quality`, the release packaging task, and the signature, Gatekeeper, notarization, and stapling checks above on the produced artifact.
- Physical run through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) on the release artifact; evidence stays under `build/verification/`.

## Decisions or blockers

- **Blocker (maintainer):** a Developer ID Application certificate on this Mac; creating it needs the Account Holder role if the API cannot.
- **Open, recommendation in brackets:** package container [signed and notarized DMG]; release JDK vendor [Eclipse Temurin 21]; update delivery [manual download of a new notarized build, no updater, per ADR 0004].
