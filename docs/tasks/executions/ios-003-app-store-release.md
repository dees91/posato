# Execution: `IOS-003`

- **Brief:** [App Store release build](../specifications/ios-003-app-store-release.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent Claude Code agent (plan and completed change)
- **Branch:** `feature/ios-003-app-store-release`
- **Updated:** 2026-09-14

## Starting facts

- `observed` (2026-09-14, on `c8112c4`): Release has no app `CODE_SIGN_ENTITLEMENTS`; the Debug-only
  `iphoneos` condition `POSATO_FAMILY_CONTROLS_DEVELOPMENT` gates six app Swift branches, while the
  extension has no gated code and signs with its entitlements in both configurations. Version and build
  number repeat per target. `AppIcon` is in the resource phase but not selected. Legal files ship as
  Compose resources under `files/legal`. The generated app `Info.plist` declares iPhone and iPad but no
  orientations.
- `observed`: Xcode 26.6 with the iOS 26.5 SDK meets the upload requirement; this Mac holds only an Apple
  Development identity; `posato-provisioning doctor` passes with the team API key.
- `observed` (Apple documentation, 2026-09-14): the App Store Connect API cannot create an app record.
  The Account Holder requests Family Controls distribution separately for the app and each Screen Time
  extension; approval shows **Assigned**. `ITSAppUsesNonExemptEncryption` is `NO` only when all
  encryption is exempt.

## Maintainer decisions (2026-09-14)

- Build number: latest App Store Connect build plus one, supplied at archive time, never tracked.
- App Store name "Posato" with no fallback; stop and ask if it is unavailable.
- Encryption: `ITSAppUsesNonExemptEncryption = NO`, accepting the candidate assessment below.
- `observed` (2026-09-14): the current request form has no bundle-identifier field; the maintainer
  accepted its terms and Apple assigned Family Controls (Distribution) to the whole account within
  a minute. Phase C is no longer blocked by approval.

## Plan

Phase A needs no Apple approval; B is maintainer action; C waits for approval. Raw output and signing
details stay under ignored `build/verification/`; tracked text holds no team, key, profile, or device value.

1. **Version (A).** Root `Version.xcconfig` contains exactly `MARKETING_VERSION = 1.0.0` and is the
   project-level base configuration for Debug and Release. Per-target version and build settings go;
   one project-level `CURRENT_PROJECT_VERSION = 1` stays as the local default.
2. **Entitlements and enforcement (A).** Rename `PosatoDebug.entitlements` to `Posato.entitlements`
   unchanged and use it for `iphoneos` in both app configurations. Rename the condition to
   `POSATO_FAMILY_CONTROLS`, set for `iphoneos` in both; historical records keep the old names.
3. **Icon and encryption (A).** Select `AppIcon`. Add `ITSAppUsesNonExemptEncryption` to the app
   `Info.plist` only after the maintainer confirms the assessment below.
4. **Local checks (A).** `./gradlew quality`; no target-level version setting remains and
   `-showBuildSettings` agrees for app, extension, and tests; a development-signed Release archive with
   entitlements, version pairs, icon, and the three legal files inspected.
5. **Record (B).** The maintainer creates the iOS record: Posato, English (U.S.), `app.posato.ios`, own SKU.
6. **Distribution (C).** Archive with `CURRENT_PROJECT_VERSION=<latest build + 1>`; archive and export
   pass `-allowProvisioningUpdates` and the API key flags from `local.properties`. Export one IPA
   (`app-store-connect`, `manageAppVersionAndBuildNumber` off, untracked options under `build/`), check
   it against the expected entitlements, validate it with App Store Connect, and upload that IPA. An
   iPad orientation rejection goes to the maintainer before any upload.
7. **TestFlight run (C).** Needs the encryption answer in the plist or App Store Connect. The maintainer
   confirms losing the iPhone's local development data, including the App Group container. End any
   session, uninstall the development build without Remove workspace, install from TestFlight, confirm
   first install and the Screen Time prompt, and keep sync unconfigured. Drive the build without
   `install -t device` (fallback: manual launch plus snapshot, tap, screenshot); run the `MVP-001`
   block, expiry, and early-end steps; then restore the development build.
8. **Closeout.** Completed-change review, this record, `ios-enforcement.md` and
   `first-release-readiness.md` wiki updates, one log entry.

## Expected entitlements (AC-01)

Equal between the Debug device app and the export: Family Controls, App Group
`group.app.posato.ios.session`, the iCloud container, CloudKit, and the team-prefixed `app.posato.sync`
keychain group; for the extension, Family Controls and the App Group. Expected export differences:
`get-task-allow` false, `icloud-container-environment` Production, `beta-reports-active` true.

## Encryption assessment (candidate, `inferred`, not a legal opinion)

Synchronized data is encrypted with standard algorithms: CryptoKit AES-GCM and Ed25519, and HKDF in
common Kotlin on the system HMAC (ADR 0006); transport is system HTTPS; nothing is proprietary.
Candidate answers: uses encryption, qualifies for an exemption, not proprietary, hence `NO`. `open`:
whether a year-end self-classification report applies. The maintainer decides.

## High-risk plan review

- **Verdict:** `approved` after one correction round.
- **Critical or Required findings:** (R1) replacing the development build kept sync state and Screen
  Time approval; (R2) "matching Debug" had no expected set; (R3) no pre-upload validation of the iPad
  orientation gap.
- **Resolution:** uninstall-first step 7, expected entitlements section, validated single-IPA upload
  with a maintainer decision on rejection. Recommended items folded into steps 4, 6, 7 and the assessment.

## Result

- Pending.

## Completed-change review

- **Verdict:** `pending`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- **Cleared (2026-09-14):** Family Controls distribution, assigned at account level.
- **Risk:** distribution builds use CloudKit Production without a schema (`SYNC-017`); no sync claim.
  Synchronizable Keychain items survive the uninstall.
- **Risk:** a privacy-manifest rejection (`PRIVACY-001`) becomes a maintainer scope decision.
- `hypothesis`: the driver's UI-testing runner can drive a distribution-signed build by bundle ID.

## Final

- **Status:** `active`
- **Outcome:** pending
