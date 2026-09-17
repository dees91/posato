# `RELEASE-002`: Verify the release candidates and give the final ready verdict

- **Review tier:** `high-risk`
- **Tier reason:** Rewriting published history, changing what a public repository exposes, and declaring signed artifacts ready for users are irreversible release operations.
- **Dependencies:** completed `MACOS-008`, `MACOS-009`, `IOS-003`, `SYNC-017`, `DESIGN-002`, `DESIGN-003`, `PRIVACY-001`, `DOCS-001`, `WEB-001`, and `DOCS-002` (all merged by `65443bd`).
- **Integration group:** `PR-RELEASE-CANDIDATE`, roadmap wave Release/R3.
- **Authority:** [MVP roadmap](../mvp-roadmap.md) (revision 19), [RELEASE-001 record](../executions/release-001-first-release-readiness.md) (obligation table), [first-release readiness topic](../../wiki/topics/first-release-readiness.md), [limits and platforms](../../product/limits-and-platforms.md), [threat model](../../security/apple-mvp-threat-model.md) (`TB-08`, `T-13`), and the records of the dependency rows.

## Outcome

For one named source revision, a Developer ID macOS candidate and a TestFlight iOS candidate are verified on the supported platform matrix, every `RELEASE-001` blocker and later handover is passed or explicitly blocked, the repository is ready to become public, and the task gives a dated ready or blocked verdict with a publication checklist the maintainer runs.

## Boundaries

- Verify candidates built from one revision with the existing release paths; no new product features, signing mechanisms, or deployment-target changes unless the maintainer decides to narrow the targets.
- Platform matrix: macOS 26 and 15 on Apple silicon with Safari and Chrome Stable, and iOS 26 and 18, dated. Reuse earlier evidence only with its revision and limits; `ONBOARDING-001` permission persistence and `ONBOARDING-002` accessibility captures are rechecked on release builds.
- Repository exposure: remove the session-link trailers by an authorized history rewrite and the task-share links from pull-request bodies, then rescan everything a visibility change publishes. The rewrite and force-push run only after the maintainer authorizes them and lifts branch protection.
- Handovers to close: walkthrough attachment reachability, repository and GitHub Issues links plus the security route on `posato.app` and in the README, the README under-15-minute wording and store listing "open source" wording (`WEB-001`), EU trader status before App Store submission (`IOS-003`), and the signing and update review for `TB-08`/`T-13`.
- The maintainer owns repository visibility, private vulnerability reporting and Issues settings, the manual trademark search, App Store submission, and publishing the macOS download. This task prepares, verifies, and records; it performs none of them.

## Acceptance

- `AC-01` — Both candidates, built from the named revision, pass the documented release checks and the MVP flow on each platform in the matrix, or each missing combination is blocked with its reason.
- `AC-02` — Published history and pull-request bodies contain no session-link trailers or private task links, and a rescan of every ref, pull request, comment, run, and artifact finds no credentials, private data, or private URLs.
- `AC-03` — Every `RELEASE-001` blocker and every handover above is passed, blocked with an owner, or accepted by the maintainer in one table.
- `AC-04` — A dated ready or blocked verdict names the revision, artifacts, channels, and platform matrix, with an ordered publication checklist for the maintainer.

## Verification

- Independent plan review before any history rewrite or candidate build; independent completed-change and readiness review after.
- Physical runs through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) on each matrix entry; evidence stays under `build/verification/`.
- Signature, notarization, and TestFlight checks on the candidates; a clean-clone `./gradlew quality` on the named revision; the exposure rescan on a mirror.

## Decisions or blockers

- **Open:** whether devices or installations with macOS 15 and iOS 18 are available (recommended: if not, the maintainer chooses between narrowing the supported versions and blocking the verdict on those entries).
- **Open:** where the notarized macOS download is published (recommended: GitHub Releases once the repository is public, linked from `posato.app`).
- **Blocker (maintainer):** authorization and branch-protection change for the history rewrite; EU trader status in App Store Connect.
