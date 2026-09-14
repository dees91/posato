# Execution: `IOS-003`

- **Brief:** [App Store release build](../specifications/ios-003-app-store-release.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent Claude Code agent (plan and completed change)
- **Branch:** `feature/ios-003-app-store-release`
- **Updated:** 2026-09-14

## Starting facts

- `observed` (2026-09-14, rebased on `c8112c4`): the app target's Release configuration has no
  `CODE_SIGN_ENTITLEMENTS`. `POSATO_FAMILY_CONTROLS_DEVELOPMENT` is set only for Debug `iphoneos`
  and gates six Swift branches in the app target; the `ActivityMonitor` extension has no gated code
  and already signs with its entitlements in both configurations. `MARKETING_VERSION = 1.0.0` and
  `CURRENT_PROJECT_VERSION = 1` are repeated per target. `DESIGN-002` supplies `AppIcon` in the app's
  resource phase, but `ASSETCATALOG_COMPILER_APPICON_NAME` is unset. Legal files ship as Compose
  resources under `files/legal`. The generated app `Info.plist` declares iPhone and iPad but no
  supported orientations.
- `observed`: Xcode 26.6 with the iOS 26.5 SDK meets the upload SDK requirement; this Mac holds only an
  Apple Development identity; `posato-provisioning doctor` passes with the team API key.
- `observed` (Apple documentation, 2026-09-14): the App Store Connect API `apps` resource offers list,
  read, and modify but no create, so the maintainer creates the record in App Store Connect. Family
  Controls distribution is requested by the Account Holder separately for the app and for each Screen
  Time extension, and shows **Assigned** under Capability Requests when approved.
  `ITSAppUsesNonExemptEncryption` is `NO` only when all encryption is exempt.

## Maintainer decisions (2026-09-14)

- Build number: the latest App Store Connect build plus one, supplied at archive time, never tracked.
- App Store name: "Posato" with no fallback; stop and ask if it is unavailable.
- Implementer: Claude Code in this worktree.
- Family Controls distribution request: not yet sent; the maintainer sends it now.

## Plan

Phase A needs no Apple approval; B is maintainer action; C waits for approval. Raw command output,
entitlement dumps, and signing details stay under ignored `build/verification/`; tracked text records
summaries without team, key, profile, or device values.

1. **Version (A).** Add root `Version.xcconfig` containing exactly `MARKETING_VERSION = 1.0.0` and
   reference it (`../Version.xcconfig`) as the project-level base configuration for Debug and Release.
   Delete per-target `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION`; keep one project-level
   `CURRENT_PROJECT_VERSION = 1` as the local default.
2. **Entitlements and enforcement (A).** Rename `PosatoDebug.entitlements` to `Posato.entitlements`
   with unchanged content and use it for `iphoneos` in both app configurations. Rename the condition to
   `POSATO_FAMILY_CONTROLS` and set it for `iphoneos` in both configurations; the Simulator keeps the
   unavailable branch. Historical records naming the old identifiers stay unchanged.
3. **Icon and encryption (A).** Set `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon` on the app target.
   Add `ITSAppUsesNonExemptEncryption` to the app `Info.plist` only after the maintainer confirms the
   answer; the candidate assessment is below.
4. **Local checks (A).** `./gradlew quality`; confirm no target-level version setting remains and that
   `-showBuildSettings` reports the same version pair for the app, extension, and tests; a
   development-signed Release archive for a generic iOS device; inspect entitlements, both version
   pairs, the compiled icon, and the three legal files inside the archived app.
5. **Record (B, after plan review).** The maintainer creates the iOS record: name Posato, primary
   language English (U.S.), bundle ID `app.posato.ios`, a SKU of their choice.
6. **Distribution (C).** Archive with `CURRENT_PROJECT_VERSION=<latest App Store Connect build + 1>`
   (1 for the first upload). Archive and export both pass `-allowProvisioningUpdates` and the API key
   flags from `local.properties` on the command line. Export once to a local IPA with an untracked
   options file under `build/` (method `app-store-connect`, `manageAppVersionAndBuildNumber` off).
   Check entitlements against the expected set below, validate the IPA with App Store Connect, and
   upload that same IPA. If validation rejects the iPad orientation declaration, the maintainer decides
   between declaring orientations and dropping iPad; nothing uploads until then.
7. **TestFlight run (C).** Requires the encryption answer from step 3 so the build is not held for
   missing compliance. Add the maintainer as an internal tester. End any active session, then
   **uninstall** the development build, without using Remove workspace, which publishes records the
   Mac still reads. Install from TestFlight, confirm the first-install flow and the Screen Time
   permission prompt, and keep synchronization unconfigured. Drive the installed build without
   `install -t device`; if the driver cannot launch a build it did not install, launch it by hand and
   use snapshot, tap, and screenshot. Run the `MVP-001` block, expiry, and early-end steps, then uninstall
   it and reinstall the development build.
8. **Closeout.** Completed-change review, execution-record update, wiki updates to
   `ios-enforcement.md` (distribution path) and `first-release-readiness.md` (blocker row), and one log
   entry.

## Expected entitlements (AC-01)

Equal between the Debug device build and the exported app: `com.apple.developer.family-controls`, the
App Group `group.app.posato.ios.session`, the iCloud container, CloudKit service, and the team-prefixed
`app.posato.sync` keychain group. Equal between the Debug and exported extension: Family Controls and
the App Group. Expected differences in exports: `get-task-allow` false,
`com.apple.developer.icloud-container-environment` Production, and `beta-reports-active` true.

## Encryption assessment (candidate, `inferred`, not a legal opinion)

Posato encrypts synchronized user data with standard algorithms: AES-GCM and Ed25519 from Apple's
CryptoKit, and HKDF built in common Kotlin on the operating system's HMAC (ADR 0006). Transport uses
system HTTPS, and no proprietary algorithm exists. Candidate answers to App Store Connect's questions:
the app uses encryption, qualifies for an exemption, and is not proprietary, hence
`ITSAppUsesNonExemptEncryption = NO`. `open`: whether a year-end self-classification report applies. The
maintainer confirms before step 3 adds the key.

## High-risk plan review

- **Verdict:** `changes-required`, then corrections submitted for re-review.
- **Critical or Required findings:** (R1) replacing the development build keeps linked sync state and
  Screen Time approval, so AC-04 could not observe the prompt and could reach Production CloudKit;
  (R2) "matching Debug" had no expected set; (R3) no pre-upload validation of the iPad orientation gap.
- **Resolution:** step 7 uninstalls first and confirms the first-install flow; expected entitlements are
  listed; step 6 validates the inspected IPA before upload with a maintainer decision on rejection.
  Recommended items folded in: API key flags on export, uploading the inspected IPA, compliance
  dependency, HKDF in the assessment, version-setting check, evidence redaction, driver fallback.

## Result

- Pending.

## Completed-change review

- **Verdict:** `pending`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- **Blocker (maintainer):** Family Controls distribution approval for `app.posato.ios` and
  `app.posato.ios.activitymonitor`; clears when both show **Assigned** with App Store provisioning
  support. Steps 6 and 7 wait for it.
- **Risk:** distribution builds use the CloudKit Production environment without a deployed schema
  (`SYNC-017`); step 7 keeps synchronization unconfigured and makes no sync claim. Synchronizable
  Keychain items survive the uninstall.
- **Risk:** processing may reject or warn about missing privacy manifests (`PRIVACY-001`). A rejection
  becomes a maintainer scope decision, not silent work in this task.
- `hypothesis`: the driver's UI-testing runner can drive a distribution-signed build by bundle ID.

## Final

- **Status:** `active`
- **Outcome:** pending
