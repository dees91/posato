# `RELEASE-004`: Verify the 1.2.0 candidates and publish Posato 1.2

- **Review tier:** `high-risk`
- **Tier reason:** Signed artifacts, the stable update feed, and an App Review submission are account-level release operations that cannot be taken back once people install them. Release 1.2 is also the first with a new synchronized operation format (schedules) and an automatic start path on the Mac.
- **Dependencies:** `MACOS-013`, `MACOS-014`, `IOS-006` (merged); PR #92 (setup direction); `NOTIFY-001` (#94), `ONBOARDING-004` (#95), `SCHEDULE-001` (#93) and every `SCHEDULE-002` slice: #98 (1), #97 (2), #99 (3), #101 (4), #102 (5), #100 (6); `DOCS-003` (#103), which prepares the media, pages, store text and screenshots, and release notes. Release 1.2, wave R1.2/W5.
- **Integration group:** `PR-RELEASE-1-2`, milestone `1.2.0`.
- **Authority:** [release roadmap](../release-roadmap.md) (the 1.2 release guard), [RELEASE-003 brief](release-003-release-1-1.md) and [record](../executions/release-003-release-1-1.md) (the proven publication route), [ADR 0008](../../decisions/0008-macos-update-delivery.md), [Apple provisioning](../../development/apple-provisioning.md), [schedule rules](../../product/schedules-decisions.md) (the compatibility decision), and the [threat model](../../security/apple-mvp-threat-model.md).

## Outcome

For one named source revision that contains every 1.2 row, a notarized macOS candidate on the stable feed and an iOS candidate pass the release checks and unattended verification. Then Posato 1.2.0 is published: tag `v1.2.0`, a GitHub Release with the DMG, `appcast.xml` and `SHA256SUMS`, the 1.1 users' in-app update to 1.2, release notes, the updated public pages, and the iOS build submitted to App Review.

## Boundaries

- **Start.** Execution starts only after the maintainer merges the 1.2 rows. Until then this row holds only its brief, plan and draft notes. The roadmap's release guard stands: no release from `main` before `ONBOARDING-004` and `SCHEDULE-002`; a 1.1.x fix ships from `v1.1.0`.
- **Who does what.** The agent does every step it can through the repository, `gh`, the App Store Connect API (`tools/posato-provisioning`) and the driver. The maintainer keeps the merges, the Keychain prompt for the release key, the publication go, and anything App Store Connect's API cannot do.
- **Version.** `MARKETING_VERSION` becomes `1.2.0` for both applications. The macOS build number is 27 with `-PposatoMacOsPreviousBuildNumber=26` (1.1.0 shipped build 26). The iOS build number exceeds the highest in App Store Connect (1.1.0 used 4).
- **Update path.** This is the first release delivered through the `MACOS-011` updater: a 1.1.0 install in a Tart VM must find, download, verify and install 1.2.0 from the published feed, keeping its data and helper; 1.1 had no standing grant, so the one-time setup offer then asks once for the administrator password.
- **Compatibility.** Per the schedule rules, a linked 1.1 device stops syncing once a 1.2 device saves a schedule. The release notes and the first **Add schedule** on a linked workspace say "Update Posato on your other devices to keep them in sync."
- **Non-goals:** new features; a defect found here gets its own row unless the maintainer decides to fix it in this release.

## Acceptance

- `AC-01` — Both candidates come from the named revision and pass the release checks: clean-clone `quality`, Developer ID signature, notarization, stapling, Gatekeeper, a validated feed, and TestFlight processing with release entitlements.
- `AC-02` — The notarized candidate passes the core flow in Tart VMs on macOS 26 and macOS 15: fresh install with the unified setup, a 1.1.0 install replaced by 1.2.0 with preserved state and the one-time setup offer, a manual pause, a schedule that starts and ends on its own, and the pause notices. The in-app update from 1.1.0 runs right after publication, because 1.1.0 reads only the signed stable feed, whose download link resolves only once the release is public; a failure rolls the stable feed back to 1.1.0. On two linked candidates, a schedule saved on one device appears and starts on the other, and the other shows the notice that a pause started elsewhere; otherwise the `DOCS-003` fallback copy is published. The same revision passes the core flow and a scheduled start on the test iPhone.
- `AC-03` — Tag `v1.2.0` and its GitHub Release exist and are marked latest; the downloaded assets match the validated outputs; the public `appcast.xml` verifies and offers 1.2.0 to 1.1.0.
- `AC-04` — The privacy policy (notifications, schedules), availability page with the verified 1.2 matrix, README and `posato.app` describe 1.2 with working links, using the `DOCS-003` material; the iOS build is submitted to App Review with the `DOCS-003` description, What's New, and screenshots.
- `AC-05` — The execution record holds a dated verdict for the named revision, the asset checksums, the consumed build numbers, and the `TB-08`/`T-13` review of the release artifacts.

## Verification

- Independent plan review before the version bump or any candidate build; one completed-change review before tagging.
- Unattended runs through `verify-posato`: `vm install --dmg` on the `primary` and `legacy` lines, the in-app update from 1.1.0, and the test iPhone. Evidence stays in ignored `build/verification/release-004/`.
- After publication, download and verify the assets and the public feed, and check the README and site pages with `curl` and a browser.

## Decisions or blockers

- **Blocker (maintainer):** merging #92, #93, #94, #95 and the `SCHEDULE-002` slices. The ADR amendments were accepted and the test iPhone was unlocked for XCTest on 2026-09-27.
- **Blocker (maintainer):** the release key's Keychain prompt during feed signing, and the publication go.
- **Decisions (`user-confirmed`, 2026-09-28):** publish macOS and submit iOS in the same sitting, with iOS released automatically after approval; the maintainer accepts that an iPhone on an earlier version (App Store devices stay on 1.0.0 once 1.1.0 is withdrawn) stops syncing with a Mac that saved a schedule until iOS 1.2 is live; the sync code that rejects the unknown operation is identical in 1.0.0 and 1.1.0 (`inferred` from the source). iOS 1.1.0, still waiting for review, is withdrawn and its version record becomes 1.2.0. Only iOS 26 is claimed as verified for 1.2; iOS 18 stays supported without a 1.2 check.
