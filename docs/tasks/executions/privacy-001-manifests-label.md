# Execution: `PRIVACY-001`

- **Brief:** [Privacy manifests and label](../specifications/privacy-001-manifests-label.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude Code
- **Reviewer:** independent Claude Code agent
- **Branch:** `feature/privacy-001-manifests-label`
- **Updated:** 2026-09-16

## Plan

1. Read Apple's required-reason API and App privacy details documentation (2026-09-15).
2. Build an unsigned Release `iphoneos` app and scan both Mach-O binaries for listed symbols.
3. Add `PrivacyInfo.xcprivacy` to the app and the `ActivityMonitor` extension after maintainer decisions.
4. Prepare label answers against `PRIVACY.md` and the diagnostics policy for maintainer acceptance.
5. Lint, rebuild, `quality`, independent review, then a TestFlight upload for `AC-01` and `AC-04`.
6. Set the policy URL through the App Store Connect API; the maintainer publishes the label.

## Binary scan (`AC-02`)

`observed` (2026-09-15, unsigned Release `iphoneos` build of `9e7e677` plus the brief): imported
symbols from every category in Apple's `NSPrivacyAccessedAPIType` list, with callers found by
disassembly. The Kotlin framework is static, so its code is in the app executable.

| Binary | Category | Symbol | Caller | Declared reason |
| --- | --- | --- | --- | --- |
| `Posato` | File timestamp | `fstat` | Skia `sk_fmmap` (Compose Multiplatform, Skiko) | `0A2A.1` |
| `Posato` | File timestamp | `stat` | ICU `uprv_mapFile_skiko` (Skiko) | `0A2A.1` |
| `ActivityMonitor` | none | none | — | none |

- No `mach_absolute_time`, `systemUptime`, disk-space, active-keyboard, or `UserDefaults` symbol
  remains in either binary; Skia's `dng_sdk` reference to `mach_absolute_time` is dead-stripped.
- Both binaries import `NSFileSize` only: Swift `attributesOfItem` reads file sizes in the app and
  App Group containers. It is not a listed symbol, and no timestamp key is imported.
- The app also imports `sysctlbyname`, called only by the compiler runtime's CPU feature check, and
  `NSProcessInfo`; neither is a required-reason API.
- No Kotlin or Compose artifact in the Gradle caches ships its own privacy manifest.

## Maintainer decisions

`user-confirmed` (2026-09-15):

- App manifest declares the file timestamp category with `0A2A.1` only, following JetBrains'
  Compose Multiplatform guidance. Recorded risk: Apple's text reserves `0A2A.1` for third-party
  SDKs and does not name the app's own metadata reads; upload validation checked it (see Verification).
- Extension manifest declares no accessed API category, as its binary scan found none.
- macOS bundles get no privacy manifest: they ship with Developer ID outside the App Store, and
  required-reason declarations apply to iOS, iPadOS, tvOS, visionOS, and watchOS.
- The App Store privacy label answers below are accepted (`AC-03`).
- 2026-09-16: the maintainer released the TestFlight upload and published the label in App Store
  Connect; the API has no endpoint for the data-collection questionnaire.

## App Store privacy label (`AC-03`)

Apple defines collection as transmitting data off the device so that the developer or its
third-party partners can access it longer than needed to serve the request in real time.

- **Data collection:** "No, we do not collect data from this app."
- **Tracking:** none; manifests declare `NSPrivacyTracking` false with no tracking domains.
- **Justification by category:**
  - Contact info, health and fitness, financial info, location, sensitive info, contacts,
    purchases, search history: Posato has no feature that reads or asks for them.
  - Browsing history: Posato records no page visits; domains the person enters to pause are
    settings, not viewed content, and are covered under user content.
  - User content and other data: websites, app choices, and sessions stay in app-private storage.
    With iCloud sync on, the website list, shared app group name, and session start and end are
    encrypted on the device and stored in the person's private CloudKit database, which the
    developer cannot read; app choices never sync (`PRIVACY.md`, iCloud sync).
  - Identifiers: no account, advertising identifier, or device identifier is sent to the developer.
  - Usage data and diagnostics: no analytics, crash upload, or telemetry (diagnostics policy,
    remote collection).
- **Apple and backups:** iCloud metadata Apple observes and iCloud Backup fall under Apple's policy,
  and Apple's guidance states that developers are not responsible for disclosing data Apple
  collects; local backups stay with the person.
- **Privacy policy URL:** `https://posato.app/privacy/`, published by `WEB-001` (answers 200 on
  2026-09-16).

## Result

- `iosApp/iosApp/PrivacyInfo.xcprivacy` and `iosApp/ActivityMonitor/PrivacyInfo.xcprivacy` are
  resources of their targets; the extension gained a Resources build phase.
- All acceptance criteria are met. Build 1.0.0 (2), archived from `77a1819`, is on TestFlight with
  both manifests; the App Store privacy label is published with `https://posato.app/privacy/`.

## Completed-change review

- **Verdict:** `approved` after two corrections.
- **Critical or Required findings:** (1) the label justification said entered domains stay on the
  device and implied app choices sync, both contradicting `PRIVACY.md`; (2) the corrected record
  claimed `AC-01` without a Release archive.
- **Resolution:** the justification follows `PRIVACY.md` and `AC-01` stays open with `AC-04`;
  Apple-observed metadata, backups, and the two non-listed imports came from advisory findings.
- **Scope checked:** both manifests source and bundled, every `project.pbxproj` hunk and object ID,
  `nm`, `strings`, and `otool` on both binaries, the Skiko klib, and Apple documentation.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `plutil -lint` on both manifests and `project.pbxproj` | pass | all OK |
| Unsigned Release `iphoneos` build with manifests | pass | both bundles contain the manifest at their root, byte-identical to source |
| `./gradlew quality` | pass | after the manifest and project change, and again after rebasing onto `1a113f4` |
| Release archive 1.0.0 (2) | pass | both manifests byte-identical to source; same `stat` and `fstat` scan result |
| Exported IPA | pass | Apple Distribution; entitlement set as in `IOS-003`, `get-task-allow` false, CloudKit Production |
| `altool` validation and upload | pass | `VERIFY SUCCEEDED`, `UPLOAD SUCCEEDED` |
| App Store Connect processing | pass | build `VALID`; build upload `COMPLETE` with no errors, warnings, or infos |
| Privacy policy URL | pass | set through the API; read back as `https://posato.app/privacy/` |
| Label published | pass, `user-confirmed` | maintainer selected "Data Not Collected" and published |

## Blockers and accepted risks

- Accepted risk: `0A2A.1` passed processing, but Apple may still report it at App Review; the fix
  would be a manifest-only change.
- Next TestFlight upload needs build 3 or later.

## Final

- **Status:** `done`
- **Outcome:** met
