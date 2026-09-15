# Execution: `IOS-003`

- **Brief:** [App Store release build](../specifications/ios-003-app-store-release.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent Claude Code agent (plan and completed change)
- **Branch:** `feature/ios-003-app-store-release`
- **Updated:** 2026-09-15

## Starting facts

- `observed` (2026-09-14, on `c8112c4`): Release had no app entitlements; the Debug-only `iphoneos`
  condition gated six app Swift branches; version and build number repeated per target; `AppIcon`
  was not selected; the generated app `Info.plist` declared iPhone and iPad without orientations.
- `observed` (Apple documentation, 2026-09-14): the App Store Connect API cannot create an app record;
  `ITSAppUsesNonExemptEncryption` is `NO` only when all encryption is exempt.

## Maintainer decisions

- 2026-09-14: build number is the latest App Store Connect build plus one, supplied at archive time;
  name "Posato" with no fallback; `ITSAppUsesNonExemptEncryption = NO` per the assessment below.
- 2026-09-15: accepted the renewed Paid Apps Agreement (no banking or tax setup), which App Store
  Connect required before creating any record, even for a free app.
- 2026-09-15, scope change after validation error 90474: keep iPad with all four orientations.

## Plan

1. Root `Version.xcconfig` as the project base configuration; one project-level build-number default.
2. `Posato.entitlements` and `POSATO_FAMILY_CONTROLS` for `iphoneos` in Debug and Release.
3. Select `AppIcon`; declare the encryption answer after maintainer confirmation.
4. `quality`, build settings, and a development-signed Release archive inspection.
5. Maintainer creates the App Store Connect record.
6. Archive build 1, export one IPA with automatic distribution signing, inspect, validate, upload.
7. Uninstall the development build, install from TestFlight, drive `MVP-001` enforcement, restore.
8. Closeout review, record, wiki.

## Encryption assessment (`inferred`, not a legal opinion)

Synchronized data is encrypted with standard algorithms: CryptoKit AES-GCM and Ed25519, and HKDF in
common Kotlin on the system HMAC (ADR 0006); transport is system HTTPS; nothing is proprietary. App
Store Connect answers: uses encryption, qualifies for an exemption, not proprietary. `open`: whether a
year-end self-classification report applies.

## High-risk plan review

- **Verdict:** `approved` after one correction round.
- **Critical or Required findings:** (R1) replacing the development build kept sync state and Screen
  Time approval; (R2) "matching Debug" had no expected entitlement set; (R3) no pre-upload validation.
- **Resolution:** uninstall-first TestFlight run, expected entitlements listed, validated single-IPA upload.

## Result

- Release builds of the app and extension now sign with Family Controls, the App Group, iCloud,
  CloudKit, and Keychain entitlements and compile enforcement. All targets read `Version.xcconfig`.
- Build 1.0.0 (1) is on internal TestFlight for record Posato (`app.posato.ios`).
- Deviation, `user-confirmed` (maintainer's portal and Apple email): the Family Controls distribution
  request form has no bundle-identifier field; Apple assigned the entitlement to the account within a
  minute. `observed`: export still failed until the maintainer enabled **Family Controls
  (Distribution)** on each App ID.
- Deviation: validation required `CFBundleDisplayName` in the extension (90360) and orientations for
  iPad (90474). The extension is named Posato; iPad declares all four orientations, and iPhone declares
  portrait and both landscape orientations, which keeps its previous implicit behavior.

## Completed-change review

- **Verdict:** `approved` after one correction.
- **Critical or Required findings:** the readiness topic labelled iPhone blocking `observed` although
  only the maintainer saw it.
- **Resolution:** blocking and clearing are labelled `user-confirmed` there; the account-level
  assignment is labelled `user-confirmed` in the record and enforcement topic.
- **Scope checked:** full diff, build settings for every target, stale references, secret patterns, and
  all seven run directories; no tracked consumer of the old names remains outside historical records.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | after the project change and again after the orientation change |
| Build settings, all targets, Debug and Release | pass | 1.0.0 (1) from project level; entitlements and condition on `iphoneos` only |
| Development-signed Release archive | pass | expected entitlements, icon compiled, three legal files byte-identical |
| Exported IPA | pass | Apple Distribution; expected entitlement set with `get-task-allow` false, Production container environment, `beta-reports-active`; encryption key `false` |
| App Store Connect validation and upload | pass | first validation failed (90360, 90474); after fixes `VERIFY SUCCEEDED`, upload succeeded, processing complete without a compliance hold |
| iPad Air 11-inch (M4) Simulator | pass | portrait onboarding and navigation run `20260915-093552-a298`; landscape run `20260915-094523-37cc` |
| TestFlight build, physical iPhone | pass | first install and system Screen Time prompt `20260915-102352-db65`; grant, website, picker `20260915-102612-b4e1`; 25-minute session with restrictions active `20260915-103044-f4f0`; early end `20260915-103205-cae3`; 5-minute natural expiry at the reviewed end time `20260915-103409-008e` |
| Enforcement on the iPhone | pass, `user-confirmed` | the maintainer saw Safari `example.com` and the selected Calculator blocked during the session and usable after early end and after expiry; no screenshots |

## Blockers and accepted risks

- Distribution builds use CloudKit Production without a deployed schema (`SYNC-017`); sync stayed
  unconfigured and no sync claim is made.
- No privacy-manifest error appeared in validation or processing; the manifest and label remain `PRIVACY-001`.
- Follow-up: iPad shows iPhone-specific copy ("On this iPhone only") and needs store screenshots before
  review; physical iPad and iPhone landscape layouts were not observed.
- Follow-up for `RELEASE-002`: EU trader status (Digital Services Act) before App Store submission.
- Not re-run on the distribution build: suspended-app expiry through the extension and reboot.

## Final

- **Status:** `done`
- **Outcome:** met; AC-04 blocking and clearing are `user-confirmed` on one iPhone
