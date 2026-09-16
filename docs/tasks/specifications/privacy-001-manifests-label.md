# `PRIVACY-001`: Declare Posato's privacy manifests and App Store privacy label

- **Review tier:** `standard`
- **Tier reason:** Declarative manifests and label answers change no data flow; an independent check still compares every declared reason and answer with the shipped binaries and the privacy policy.
- **Dependencies:** completed `IOS-003` (merged as `9324a60`). `WEB-001` (merged as `1a113f4`) publishes the privacy policy URL.
- **Integration group:** `PR-PRIVACY-PUBLICATION`, roadmap wave Release/R2, in parallel with `DESIGN-003`, `MACOS-009`, and `SYNC-017` (maintainer raised the parallel limit to four on 2026-09-15).
- **Authority:** [MVP roadmap](../mvp-roadmap.md) (revision 17), [privacy policy](../../../PRIVACY.md), [diagnostics policy](../../security/diagnostics-and-support-data.md), [privacy and trust topic](../../wiki/topics/privacy-and-trust-model.md), [IOS-003 record](../executions/ios-003-app-store-release.md), [RELEASE-001 record](../executions/release-001-first-release-readiness.md).

## Outcome

The iOS app and its `ActivityMonitor` extension ship privacy manifests that declare every required-reason API the shipped binaries use, and the App Store privacy label answers are prepared, reconciled with `PRIVACY.md`, and ready for the maintainer to enter in App Store Connect.

## Boundaries

- `observed` starting point: no `PrivacyInfo.xcprivacy` exists; the iOS app reads file attributes (`IosApplicationMappingsProvider.swift`, `SuspendedExpiryClear.swift`), a file timestamp API category. IOS-003's upload showed no privacy-manifest error, which does not prove completeness.
- Scan the built binaries, not only Swift sources: the Kotlin/Native shared framework, Compose Multiplatform, and SQLDelight's native driver can call required-reason APIs such as file timestamps, system boot time, or user defaults. Declare only reasons that match actual use.
- Label answers follow Apple's definition of collected data; `PRIVACY.md` and the diagnostics policy are the product truth (no analytics, tracking, diagnostics, or developer-accessible data; iCloud data stays in the person's private database). Any mismatch is escalated, never smoothed over in either document.
- The macOS app is distributed with Developer ID and needs no App Store label; record whether the macOS bundles also get a manifest (recommended: none unless a dependency ships one).
- This task owns the privacy manifest files and their target membership in `iosApp.xcodeproj`. Entering answers in App Store Connect is a maintainer web action; hosting the policy belongs to `WEB-001`.
- Non-goals: changing data handling, the privacy policy text, or tracking domains; App Store submission.

## Acceptance

- `AC-01` — The app and extension bundles in a Release archive each contain a valid privacy manifest with tracking false, no tracking domains, no collected data types, and every required-reason API category found in the binaries with an approved reason.
- `AC-02` — A dated scan of the shipped binaries lists each required-reason API symbol found and the declared reason that covers it.
- `AC-03` — The prepared label answers, with a short justification per data category, agree with `PRIVACY.md`, and the maintainer accepts them.
- `AC-04` — A TestFlight upload containing the manifests passes validation and processing without privacy-manifest warnings.

## Verification

- Release archive and upload validation; `plutil -lint` on each manifest; the binary symbol scan.
- `./gradlew quality`.
- Independent completed-change review against the scan, `PRIVACY.md`, and current Apple documentation, checked and dated.

## Decisions or blockers

- **Maintainer action:** publishing the label in App Store Connect (done 2026-09-16).
- **Decided** (`user-confirmed`, 2026-09-15): no manifest for the macOS bundles; required-reason codes are recorded in the [execution record](../executions/privacy-001-manifests-label.md).
