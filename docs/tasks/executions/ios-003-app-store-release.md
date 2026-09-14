# Execution: `IOS-003`

- **Brief:** [App Store release build](../specifications/ios-003-app-store-release.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** pending (independent agent for plan and completed change)
- **Branch:** `feature/ios-003-app-store-release`
- **Updated:** 2026-09-14

## Starting facts

- `observed` (2026-09-14, rebased on `c8112c4`): the app target's Release configuration has no
  `CODE_SIGN_ENTITLEMENTS`. `POSATO_FAMILY_CONTROLS_DEVELOPMENT` is set only for Debug `iphoneos`
  and gates six Swift branches in the app target; the `ActivityMonitor` extension has no gated code
  and already signs with its entitlements in both configurations. `MARKETING_VERSION = 1.0.0` and
  `CURRENT_PROJECT_VERSION = 1` are repeated per target. `DESIGN-002` supplies `AppIcon` in the app's
  resource phase, but `ASSETCATALOG_COMPILER_APPICON_NAME` is unset. Legal files ship as Compose
  resources under `files/legal`.
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

Phase A needs no Apple approval; B is maintainer action; C waits for approval.

1. **Version (A).** Add root `Version.xcconfig` containing exactly `MARKETING_VERSION = 1.0.0`, reference
   it as the project-level base configuration for Debug and Release, and delete per-target
   `MARKETING_VERSION`. Keep one project-level `CURRENT_PROJECT_VERSION = 1` as the local default and
   delete the per-target copies, so the app and extension always agree.
2. **Entitlements and enforcement (A).** Rename `PosatoDebug.entitlements` to `Posato.entitlements`
   with unchanged content and use it for `iphoneos` in both app configurations. Rename the condition to
   `POSATO_FAMILY_CONTROLS` and set it for `iphoneos` in both configurations; the Simulator keeps the
   unavailable branch.
3. **Icon and encryption (A).** Set `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon` on the app target.
   Add `ITSAppUsesNonExemptEncryption` to the app `Info.plist` only after the maintainer confirms the
   answer; the candidate assessment is below.
4. **Local checks (A).** `./gradlew quality`; a development-signed Release archive for a generic iOS
   device; inspect app and extension entitlements, both version pairs, the compiled icon, and the three
   legal files inside the archived app.
5. **Record (B, after plan review).** The maintainer creates the iOS record: name Posato, primary
   language English (U.S.), bundle ID `app.posato.ios`, a SKU of their choice.
6. **Distribution (C).** Archive with `CURRENT_PROJECT_VERSION=<latest App Store Connect build + 1>`
   (1 for the first upload) and `-allowProvisioningUpdates` with the API key from `local.properties`
   on the command line. Export with an untracked options file under `build/`, method
   `app-store-connect` and `manageAppVersionAndBuildNumber` off; inspect the exported entitlements;
   then export the same archive with destination `upload`.
7. **TestFlight run (C).** Add the maintainer as an internal tester and install on the wired iPhone,
   which replaces the development build. Drive the installed build without `install -t device`, run
   the `MVP-001` block, expiry, and early-end steps with sync left off, then reinstall the development
   build.
8. **Closeout.** Completed-change review, execution-record update, wiki updates to
   `ios-enforcement.md` (distribution path) and `first-release-readiness.md` (blocker row), and one log
   entry.

## Encryption assessment (candidate, not a legal opinion)

Posato encrypts synchronized user data with standard algorithms from Apple's CryptoKit (AES-GCM,
Ed25519 per ADR 0006) and uses system HTTPS; it implements no proprietary algorithm. Candidate answer:
`NO`, as encryption exempt from documentation upload. `open`: whether a year-end self-classification
report applies. The maintainer confirms before step 3 adds the key.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

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
  (`SYNC-017`); step 7 therefore keeps sync off and makes no sync claim.
- **Risk:** processing may reject or warn about missing privacy manifests (`PRIVACY-001`). A rejection
  becomes a maintainer scope decision, not silent work in this task.
- **Risk:** the TestFlight install replaces the development build on the maintainer's iPhone and may
  require fresh Screen Time approval; end any active session first.
- `hypothesis`: the driver's UI-testing runner can drive a distribution-signed build by bundle ID.

## Final

- **Status:** `active`
- **Outcome:** pending
