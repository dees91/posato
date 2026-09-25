# `RELEASE-003`: Verify the 1.1.0 candidates and publish Posato 1.1

- **Review tier:** `high-risk`
- **Tier reason:** Publishing signed artifacts, the first stable update feed signed with the release key, and an App Review submission are release operations on account-level resources that cannot be taken back once people install them.
- **Dependencies:** `SESSION-004`, `ONBOARDING-003`, `TARGETS-006`, `IOS-004`, `MACOS-010`, `MACOS-011`, and `QUALITY-007`, all merged by `13e0cd9`. Release 1.1, wave R1.1/W3.
- **Integration group:** `PR-RELEASE-1-1`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md), [ADR 0008](../../decisions/0008-macos-update-delivery.md) (proposed public wording), [Apple provisioning](../../development/apple-provisioning.md) (release feed and publication steps), [MACOS-011 record](../executions/macos-011-updates.md) (build numbers and limits), [QUALITY-007 record](../executions/quality-007-platform-matrix.md), [RELEASE-002 record](../executions/release-002-release-candidate.md) (1.0 publication and App Store Connect route), and the [threat model](../../security/apple-mvp-threat-model.md) (`TB-08`, `T-13`).

## Outcome

For one named source revision, a notarized macOS candidate with the stable update feed and an iOS candidate pass the release checks and unattended verification. After that, Posato 1.1.0 is published: tag `v1.1.0`, a GitHub Release with the DMG, `appcast.xml`, and `SHA256SUMS`, release notes, the updater wording in the privacy policy, the availability page, the README, and `posato.app`, and the iOS build submitted to App Review.

## Boundaries

- **Who does what.** The agent does every step it can through the repository, `gh`, the App Store Connect API, and the driver. The maintainer receives only account-owned steps, one at a time, where they block: the Keychain prompt when the release key signs the feed, and any App Store Connect step the API cannot perform.
- **Version.** `MARKETING_VERSION` becomes `1.1.0` for both applications. The macOS build number is 26: candidates 17-24 embed the release key and `QUALITY-007` notarized 25, so the feed task runs with `-PposatoMacOsPreviousBuildNumber=25`. The iOS build number exceeds the highest one in App Store Connect.
- **Candidates.** Build from one revision with the existing release paths: `generateMacOsUpdateFeed` on the release channel, and the App Store archive and upload used for 1.0. No product changes. A defect found here gets its own row, unless the maintainer decides to fix it in this release. The driver gains `vm install --replace`, so a VM can move from an installed 1.0.0 to the candidate as a person does.
- **Public wording.** Publish the ADR 0008 privacy and availability passages, reconciled with the `MACOS-011` measurements, and set the policy's effective date at publication. Record the move from 1.0 to 1.1 as a manual download, because 1.0 has no updater. Update the README, the website's Availability section, and the App Store "What's New" text. Historical records stay unchanged.
- **Publication.** Follow `publish-github-release` in one sitting right after the merge: an annotated tag on the squash commit when its product tree equals the verified revision (otherwise stop), a draft release with exactly the three assets, a byte-for-byte check of the downloaded assets against the validated outputs, then publication. After that, `releases/latest/download/appcast.xml` must resolve and verify. A later release without `appcast.xml` is published with `--latest=false`.
- **Non-goals:** new features, new signing mechanisms, delta updates, macOS or iOS target changes, and releasing the App Store version once it is approved (see decisions).

## Acceptance

- `AC-01`: Both candidates come from the named revision and pass the release checks: clean-clone `quality`, Developer ID signature, notarization, stapling, Gatekeeper, a feed that passes `generateMacOsUpdateFeed` validation, and TestFlight processing with release entitlements.
- `AC-02`: The notarized candidate passes the core flow in Tart VMs on macOS 26 and macOS 15, including installation over 1.0.0 with preserved state and the consent question. The same revision passes the core flow on the test iPhone.
- `AC-03`: Tag `v1.1.0` and its GitHub Release exist and are marked latest. The downloaded DMG passes the checksum, signature, notarization, and Gatekeeper checks. The public `appcast.xml` verifies against the release key and points to that DMG.
- `AC-04`: The privacy policy, availability page, README, and `posato.app` describe 1.1 with working links, and the iOS build is submitted to App Review.
- `AC-05`: The execution record holds a dated verdict for the named revision, the asset checksums, and the outcome of each handover: the consumed build numbers (8-25; 20 was never built), the iOS 18 claim, the store screenshots against the 1.1 UI, and the `TB-08` and `T-13` review of the release artifacts.

## Verification

- Independent plan review before any version bump, candidate build, or publication step. One independent completed-change review after verification and before tagging; the driver extension gets its own local review.
- Unattended runs through `verify-posato`: the macOS candidate with `vm install --dmg` on the `primary` and `legacy` lines, and iOS on the test iPhone. Evidence stays in ignored `build/verification/release-003/`.
- After publication, download and verify the release assets, verify the public feed, and check the README, the site pages, and their links with `curl` and a browser.

## Decisions or blockers

- **D1, iOS 18:** 1.1 changes the iOS binary, and the test iPhone runs iOS 26. Either the maintainer checks the TestFlight build on the private iOS 18 iPhone (the `AGENTS.md` exception for an iOS version the test iPhone lacks), or the availability page keeps "iOS 18 checked on 1.0.0".
- **D2, App Store release:** manual release after approval, as for 1.0, or automatic release on approval.
- **Blocker (maintainer):** the Keychain prompt for the `posato-release` key during feed signing.
