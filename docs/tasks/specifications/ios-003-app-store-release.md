# `IOS-003`: Ship an App Store Release build of the iOS app to TestFlight

- **Review tier:** `high-risk`
- **Tier reason:** Distribution signing, the Family Controls distribution entitlement, and creating an App Store Connect record and uploads are account-level release operations.
- **Dependencies:** completed `RELEASE-001` (merged as `f96c0c6`, blocked verdict with this row as owner). The upload also needs the `DESIGN-002` application icon.
- **Integration group:** `PR-IOS-DISTRIBUTION`, roadmap wave Release/R2, in parallel with `MACOS-008` and `DESIGN-002`.
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [RELEASE-001 record](../executions/release-001-first-release-readiness.md) (AC-04 rows), [architecture baseline](../../decisions/0003-mvp-application-architecture-baseline.md), [threat model](../../security/apple-mvp-threat-model.md), [Apple provisioning](../../development/apple-provisioning.md), [`posato-provisioning`](../../../tools/posato-provisioning/README.md).

## Outcome

The Release configuration of the app and its `ActivityMonitor` extension carries the Family Controls entitlement and enforcement build condition, the shared version, a build number, an encryption declaration, and the application icon. An archive is uploaded to a new App Store Connect record, installs from internal TestFlight on a physical iPhone, and blocks as the development build does.

## Boundaries

- Today Release has no entitlements file for the app and compiles enforcement only under the Debug-only `POSATO_FAMILY_CONTROLS_DEVELOPMENT` condition, so a Release build cannot block. Give Release the same capabilities with distribution signing; keep Debug, the Simulator build, and `./gradlew quality` working.
- Create the App Store Connect record and upload through the App Store Connect API where it allows; keys, profiles, and team identifiers never enter Git. Submitting for App Store review or releasing is out of scope and belongs to `RELEASE-002` and the maintainer.
- Shared version contract, frozen for this wave: `Version.xcconfig` at the repository root contains exactly `MARKETING_VERSION = 1.0.0`. This task adds that identical file and makes all iOS targets read it instead of per-target values; `MACOS-008` adds the same file. Build numbers stay per platform and increase with every upload.
- This task owns `iosApp.xcodeproj`. `DESIGN-002` supplies an `AppIcon` set in `iosApp/iosApp/Assets.xcassets` and the in-app licenses screen; this task sets the icon build setting and verifies the notices ship in the archive.
- Record the `ITSAppUsesNonExemptEncryption` assessment for Posato's application-layer encryption (ADR 0006) without a legal opinion; the maintainer confirms the declared answer.
- Non-goals: privacy manifests and label (`PRIVACY-001`), production CloudKit and sync verification (`SYNC-017`), store screenshots and listing copy (`DESIGN-002`), deployment-target changes.

## Acceptance

- `AC-01` — A Release archive of the app and extension is distribution-signed with Family Controls, App Group, iCloud, and Keychain entitlements matching Debug, and enables enforcement in both targets.
- `AC-02` — Every target reports the version from `Version.xcconfig` and a build number above the last upload; the encryption declaration is in the build or the record.
- `AC-03` — The App Store Connect record exists for `app.posato.ios`, and an upload passes processing without validation errors.
- `AC-04` — The TestFlight build on a physical iPhone requests Screen Time access, blocks the MVP-001 website and app choices during a session, and clears them after expiry and after an early end.

## Verification

- Independent plan review before creating the record or uploading; independent completed-change review after.
- `./gradlew quality`, a Release archive and export, and entitlement inspection of the exported app and extension.
- Physical TestFlight run through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md); evidence stays under `build/verification/`.

## Decisions or blockers

- **Blocker (maintainer):** Family Controls distribution approval for `app.posato.ios` and `app.posato.ios.activitymonitor`; request it now, because approval time is outside the project's control.
- **Blocker:** the upload waits for the `DESIGN-002` icon to merge; configuration, the record, and a local archive can proceed before that.
- **Open:** App Store name availability for "Posato"; the build-number source (recommended: the latest App Store Connect build number plus one).
