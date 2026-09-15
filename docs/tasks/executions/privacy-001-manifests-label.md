# Execution: `PRIVACY-001`

- **Brief:** [Privacy manifests and label](../specifications/privacy-001-manifests-label.md)
- **Status:** `blocked`
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
  SDKs and does not name the app's own metadata reads; upload validation is the check.
- Extension manifest declares no accessed API category, as its binary scan found none.
- macOS bundles get no privacy manifest: they ship with Developer ID outside the App Store, and
  required-reason declarations apply to iOS, iPadOS, tvOS, visionOS, and watchOS.
- The App Store privacy label answers below are accepted (`AC-03`).
- The TestFlight upload is on hold until the maintainer releases it.

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
- `AC-02` and `AC-03` are met. `AC-01` and `AC-04` are not yet met: the unsigned Release build
  contains both valid manifests, and the Release archive check and upload validation wait for the
  held upload.

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
| Release archive, TestFlight upload validation and processing | blocked | upload held by the maintainer |

## Blockers and accepted risks

- Maintainer: release the TestFlight upload (build 2 or later) to close `AC-01` and `AC-04`.
- Maintainer: enter the accepted label in App Store Connect with `https://posato.app/privacy/`.
- Accepted risk: `0A2A.1` may be rejected for an app; the fix would be a manifest-only change.

## Final

- **Status:** `blocked`
- **Outcome:** manifests and label ready; `AC-01` and `AC-04` blocked on the held upload
