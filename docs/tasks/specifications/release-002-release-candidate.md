# `RELEASE-002`: Verify the release candidates and publish Posato 1.0

- **Review tier:** `high-risk`
- **Tier reason:** Rewriting published history, making the repository public, and publishing signed artifacts to users are irreversible release operations.
- **Dependencies:** completed `MACOS-008`, `MACOS-009`, `IOS-003`, `SYNC-017`, `DESIGN-002`, `DESIGN-003`, `PRIVACY-001`, `DOCS-001`, `WEB-001`, and `DOCS-002` (all merged by `65443bd`).
- **Integration group:** `PR-RELEASE-CANDIDATE`, roadmap wave Release/R3 (revision 20).
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [RELEASE-001 record](../executions/release-001-first-release-readiness.md) (obligation table), [first-release readiness topic](../../wiki/topics/first-release-readiness.md), [limits and platforms](../../product/limits-and-platforms.md), [threat model](../../security/apple-mvp-threat-model.md) (`TB-08`, `T-13`), and the records of the dependency rows.

## Outcome

For one named source revision, a Developer ID macOS candidate and a TestFlight iOS candidate pass the release checks, every `RELEASE-001` blocker and later handover is passed or explicitly blocked, and a dated ready or blocked verdict is recorded. After a ready verdict, Posato 1.0 is published: a version tag and GitHub Release with the notarized macOS download, a README for the released product, `posato.app` with the repository link and download buttons, and the iOS app submitted to App Review.

## Boundaries

- **Who does what.** The agent performs every step it can through the repository, `gh`, the App Store Connect API, and the Cloudflare API. The maintainer receives only actions that need the maintainer's account or explicit authorization, one at a time, at the step where they block.
- **Candidates.** Build from one revision with the existing release paths; no new product features, signing mechanisms, or deployment-target changes.
- **Platform matrix.** Not repeated by maintainer decision: the MVP flow was accepted on release builds (`MACOS-009` notarized build 6, `SYNC-017` CloudKit Production, `PRIVACY-001` TestFlight build 2), and later product changes do not touch session, synchronization, or blocking logic. The candidates are not opened on devices before publication; macOS 15 and iOS 18 stay unverified; the `ONBOARDING-001` and `ONBOARDING-002` release-build rechecks close with this decision; public copy keeps its target-only wording.
- **Repository exposure.** Remove the session-link trailers by a best-effort history rewrite that changes only those lines, delete the review-bot comments that carry task-share links (editing would publish the edit history), delete stale branches and Actions artifacts and runs, then rescan everything a visibility change publishes. Pre-rewrite commits stay reachable through `refs/pull/*` and commit URLs. The rewrite, the branch-protection change it needs, and the visibility change run only after explicit maintainer authorization.
- **Handovers.** Walkthrough attachment reachability (`DOCS-001`, `DOCS-002`); the README under-15-minute wording and the store listing "open source" wording (`WEB-001`); EU trader status before submission and the iPhone-only copy on iPad, accepted as a limit with iPad screenshots only (`IOS-003`); the signing and update review for `TB-08`/`T-13`, whose threat-model owner moves from `RELEASE-001` to this task.
- **Publication.** Follow `publish-github-release` and `git-workflow-and-versioning`: semantic version `1.0.0` matching `Version.xcconfig`, an annotated tag on the verified revision or on a descendant that changes only documentation and the website with an identical product tree, a GitHub Release with the notarized DMG, SHA-256 checksums, and release notes, verified by downloading the published asset. Update the README from pre-release to released with download links. On `posato.app`, add the repository link with a GitHub logo in the header, the GitHub Issues link and security route on the support page, and download buttons in the style of the maintainer-provided App Store and Mac badges: the Mac button points at the GitHub Release; the App Store button appears only once the App Store listing is live.
- **Maintainer-only actions.** Authorizing the history rewrite and visibility change, EU trader status and any App Store Connect step the API cannot perform, the manual trademark search, and the final App Store release once review passes.

## Acceptance

- `AC-01` — Both candidates, built from the named revision, pass the release checks: clean-clone `quality`, Developer ID signature, notarization, stapling, and Gatekeeper, and TestFlight processing with release entitlements.
- `AC-02` — Branches and tags contain no session-link trailers, pull requests and their comments contain no private task links, and a rescan of branches, tags, pull requests, comments, runs, and artifacts finds no credentials, private data, or private URLs; old commits reachable only through `refs/pull/*` are the maintainer-accepted residual.
- `AC-03` — Every `RELEASE-001` blocker and every handover above is passed, blocked with an owner, or accepted by the maintainer in one table, ending in a dated ready or blocked verdict for the named revision, artifacts, and channels.
- `AC-04` — After a ready verdict and authorization, tag `v1.0.0` and its GitHub Release exist with a downloadable DMG whose checksum, signature, notarization, and Gatekeeper assessment pass after download.
- `AC-05` — The README and `posato.app` describe the released product with working download, repository, Issues, and security links, and the iOS build is submitted to App Review; the App Store button is added when the listing is live.

## Verification

- Independent plan review before any history rewrite, candidate build, or publication step; independent completed-change and readiness review before publication.
- Signature, notarization, and TestFlight checks on the candidates; a clean-clone `./gradlew quality` on the named revision; the exposure rescan on a mirror.
- After publication: download the release asset and verify it; check README links on GitHub and `posato.app` pages, headers, and links with `curl` and a browser.

## Decisions (`user-confirmed`, 2026-09-17)

- Tag, GitHub Release, README release update, and `posato.app` download links are part of this task, not a separate row; the agent performs them.
- Download buttons follow the style of the maintainer-provided App Store and Mac badge artwork; Apple badge guideline review is not required for now.
- ~~macOS 15 is verified in a VM on the Apple silicon Mac; iOS 18 on the maintainer's iPhone.~~ `superseded` the same day: the platform matrix is not repeated (see Boundaries).
- History cleanup is best effort without a GitHub Support purge; all stale branches are deleted; the iPad copy is an accepted limit.

## Decisions or blockers

- **Blocker (maintainer):** authorization for the deletions, the protection window, and the visibility change; the tag placement; Apple's review of the submitted EU trader status.
