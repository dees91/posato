# Execution: `RELEASE-002`

- **Brief:** [Verify the release candidates and publish Posato 1.0](../specifications/release-002-release-candidate.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code session
- **Reviewers:** independent plan-review agent; completed-change and readiness reviewer pending
- **Branch:** `feature/release-002-release-candidate` (PR #66)
- **Updated:** 2026-09-17

## Observed starting point (2026-09-17)

- `main` at `65443bd`; repository private with 33 remote branches besides `main`, one archive tag, no releases, 34 stored Actions artifacts and 36 workflow runs without workflows; classic `main` protection with admins enforced and force pushes blocked, no rulesets.
- Six commits on `main` from 2026-09-02 carry session-link trailers; a seventh sits only on a local `refs/original/` backup ref.
- The seven private task links are `View task` share links in review-bot comments on pull requests 4, 10, 33, 34, 41, 43, and 49, not in pull-request bodies as the `RELEASE-001` record states; the other review-bot links point to public settings pages.
- Commit IDs in this section predate the history rewrite, which moved `main` from `65443bd` to `84d0c47` with identical trees; citations of rewritten `main` commits elsewhere in tracked documents were updated to the new IDs, while work-branch and prototype commit IDs were not rewritten and resolve through pull-request refs.
- macOS releases through `:desktopApp:notarizeMacOsRelease` (last Developer ID build 6); iOS archive and upload are manual steps from `IOS-003` (last TestFlight build 2); `Version.xcconfig` is `1.0.0`; `ITSAppUsesNonExemptEncryption` is `NO`.

## Decisions (`user-confirmed`, 2026-09-17)

- **History:** best-effort rewrite that removes only the trailer lines, without a GitHub Support purge; every stale remote branch and all Actions artifacts and runs are deleted after the list is confirmed. Old commits stay reachable through `refs/pull/*` and direct commit URLs, accepted as residual. The archive tag of the retired HTML prototype is deleted as no longer needed; its history survives only in pull request #32.
- **Task links:** the seven bot comments are deleted, not edited, because GitHub keeps and publishes comment edit history.
- **Platform matrix:** not repeated, including a device smoke test of the final candidates. The MVP flow was accepted on release builds (`MACOS-009` notarized build 6, `SYNC-017` CloudKit Production, `PRIVACY-001` TestFlight build 2), and later product changes touch only privacy manifests, pause-page styling, and the removal itself. macOS 15 and iOS 18 stay unverified, the `ONBOARDING-001` and `ONBOARDING-002` release-build rechecks close with this decision, and public copy keeps its target-only wording.
- **iPad:** the iPhone-only copy on iPad is an accepted limit; the listing adds iPad screenshots only.
- **Publication:** tag, GitHub Release, README, and `posato.app` download links are part of this task (revision 20); the tag placement is decided before publication.

## Plan

1. **Records.** This record, the brief amendments, and the roadmap row correction; independent plan review.
2. **Exposure cleanup (AC-02).** Confirm the deletion list; delete the bot comments (bodies kept outside the repository), artifacts, runs, stale branches, and backup ref. Rewrite a fresh mirror with `git filter-repo --preserve-commit-hashes`, dropping only `Claude-Session:` lines, and compare tree, author, committer, dates, and message per commit. In an authorized window, apply a full protection body allowing force pushes, push `main` with lease, move the open pull-request branches with `rebase --onto`, and restore and compare protection. Rescan branches, tags, and hosted content; count the `refs/pull/*` residual. Tracked documents keep pre-rewrite commit IDs as history. (Rewrite method, branch move, and citations superseded; see Result.)
3. **Candidates (AC-01).** Revision R is the rewritten `main` tip: clean-clone `./gradlew quality`; Developer ID build 7 with signature, notarization, stapling, and Gatekeeper checks and its SHA-256; iOS build 3 archived, entitlement-inspected, uploaded, and `VALID` on TestFlight; `TB-08`/`T-13` review of both artifacts.
4. **Verdict (AC-03).** Fill the obligation table, give the dated verdict for R, the artifacts, and the channels; independent completed-change and readiness review.
5. **Publication (AC-04, AC-05).** After authorization: public visibility, reporting and Issues settings, README and documentation release wording, `posato.app` repository, support, and download links, annotated `v1.0.0` tag and GitHub Release with the DMG and checksums verified after download, and App Review submission once EU trader status is approved.

## High-risk plan review

- **Revision 1:** `changes-required` (independent agent, 2026-09-17). Critical: editing bot comments leaves the links in public edit history. Required: the rewrite residual covers every pull request opened after 2026-09-02, not two pull refs; open branches need `rebase --onto`; protection needs full `PUT` bodies; `filter-repo` must preserve cited commit IDs and push only named refs after re-adding the remote; a tag on R would publish the pre-release README.
- **Revision 2:** `changes-required`. Required: the maintainer's platform-matrix decision was not folded in.
- **Resolution:** all findings folded into the plan, brief, and roadmap row; recommended points (deletion recount, backup ref, workflow runs, App Store submission fields, `DESIGN.md` and CSP for the site, record length) adopted. **Revision 3:** `approved`.

## Result

- **Exposure (2026-09-17).** Seven bot comments deleted and their content reposted without the link; 34 artifacts, 36 workflow runs, 32 closed remote branches, the prototype archive tag, and stale local refs deleted. The rewrite changed 234 published commits (`main` 222, open branches 12; two more on the since-deleted prototype tag) with identical trees, authors, committers, dates, and messages except the removed trailer lines; `main` moved from `65443bd` to `84d0c47`.
- **Deviations.** `git filter-repo` strips the GitHub signatures of merge commits and would have changed 347 commits, so a plumbing rewrite changed only commits with a trailer or a rewritten parent, and the open branches were mapped instead of rebased; 51 GitHub-signed merge commits after 2026-09-02 lost their verified signature (maintainer accepted). Required pull requests with enforced admins also block a force push, so the window disabled admin enforcement too; protection was restored and compared equal. Citations of rewritten `main` commits in tracked documents were updated instead of kept.
- **Candidates from R = `84d0c47`.** macOS 1.0.0 (7) `Posato-1.0.0.dmg`, SHA-256 `d011271e3b77eec12e67f24ae9ee97c52a8b762438c05bc431cc56f4aaaddb30`; iOS 1.0.0 (3) uploaded and in internal TestFlight testing; distribution checks ran on the exported IPA, kept with the archive outside Git.

## Obligation table

| Obligation | AC | Evidence | Result | Owner / next action |
| --- | --- | --- | --- | --- |
| Session-link trailers removed from published branches and tags | AC-02 | fresh mirror: 0 in 3 branches; no tags remain | pass | — |
| Private task links removed from pull requests | AC-02 | 0 private links in current bodies, comments, review comments, and reviews; 136 public settings links remain; edit histories of pull requests 15, 16, and 17 each keep one body revision with a session link | blocked | maintainer deletes the three revisions before the visibility change; rescan edit histories |
| Exposure rescan of branches, tags, and hosted content | AC-02 | blobs: only synthetic key markers and `/Users/someone/` fixtures; 0 issues, releases, artifacts, runs, commit comments, secrets, variables, hooks | pass | — |
| Residual through pull-request refs | AC-02 | 6 trailer commits reachable from refs of pull requests 12 and 15–65; only GitHub Support could purge them | accepted by the maintainer | — |
| Clean-clone `quality` on R; local release checklist for signed candidates (`RELEASE-001`) | AC-01 | no `local.properties`; 5 min 4 s; exit 0; this record's candidate checks serve as the checklist | pass | — |
| Developer ID candidate | AC-01 | G2 Developer ID with secure timestamps and hardened runtime on the app, helper, and sync companion; `codesign --verify --deep --strict`, `stapler validate`, and `spctl` (`Notarized Developer ID`) pass for app and DMG; arm64; version 1.0.0 (7) | pass | — |
| TestFlight candidate | AC-01 | Apple Distribution; `get-task-allow` false; Family Controls, CloudKit Production, app group in app and extension; privacy manifest; `altool` validation and upload succeeded; build `VALID`, upload `COMPLETE` with no issues | pass | — |
| Supported platform matrix; `ONBOARDING-001`/`ONBOARDING-002` release-build rechecks | AC-01 | earlier release-build evidence only | accepted by the maintainer | — |
| Earlier `RELEASE-001` blockers: distribution signing, iOS Release enforcement, production CloudKit, store assets, privacy publication | AC-03 | cleared by `MACOS-008`, `MACOS-009`, `IOS-003`, `SYNC-017`, `DESIGN-002`, `PRIVACY-001`, `WEB-001` and the candidates above | pass | — |
| Product-name trademark search | AC-03 | maintainer manual search 2026-09-17: UPRP, EUIPO TMview, WIPO Global Brand Database, USPTO; no results; no legal opinion | pass | — |
| `TB-08`/`T-13` signing and update review | AC-03 | least entitlements (app JIT only, helper none, sync CloudKit and one keychain group); credentials and profiles outside Git; updates are whole notarized bundles without an updater, so security fixes need a manual download | pass with accepted risk | — |
| Export compliance | AC-03 | iOS `ITSAppUsesNonExemptEncryption` `NO`; no Apple declaration for Developer ID (`MACOS-008`, inferred, no legal opinion) | pass | — |
| EU trader status before App Review | AC-03 | submitted 2026-09-17 | blocked | Apple review; blocks only the App Store submission |
| `PRIVACY-001` `0A2A.1` reason | AC-03 | accepted again at build 3 processing; App Review may still question it | accepted risk | manifest-only fix if rejected |
| Known limits carried from dependency rows | AC-03 | `IOS-003` iPad copy and unrerun suspended expiry on distribution builds; `IOS-001` reinstall behavior; `MACOS-009` Remove retry; `DESIGN-003` one helper launch failure until restart; `DESIGN-002` installed icon appearance | accepted by the maintainer (iPad copy); others pending acceptance | maintainer at the verdict |
| Walkthrough attachment reachable signed out (`DOCS-001`, `DOCS-002`) | AC-05 | — | pending | after visibility change |
| Release wording: README under-15-minute "only", store listing "open source"; site repository link, Issues and security routes, download buttons (`WEB-001`) | AC-05 | — | pending | publication |
| iPad store screenshots before App Review (`IOS-003`) | AC-05 | — | pending | App Store submission |
| Public review-bot and pull-request content: Cloudflare account ID in Pages bot dashboard links; secret gists with interface screenshots | AC-02 | not credentials; screenshots show only the app interface | pending | maintainer: accept or remove before the visibility change |
| Private vulnerability reporting and Issues enabled | AC-05 | — | pending | maintainer after visibility change |

## Completed-change and readiness review

- **Verdict:** `changes-required` (independent agent, 2026-09-17). Critical: session links remain in the edit history of three pull-request bodies. Required: the citation update moved two prototype commit IDs to objects no ref contains.
- **Resolution:** the edit-history row is blocked on the maintainer's deletion and a rescan; the two prototype IDs were restored. Recommended points folded: missing obligation rows, a dated and gated verdict, the exported IPA kept as evidence, the pull-request residual range, and the superseded plan marker.

## Verdict

- **Proposed on 2026-09-17, pending maintainer acceptance:** **ready**, once the three edit-history revisions are deleted and rescanned and the pending known limits and public bot content are accepted, for revision `84d0c47`, the Developer ID DMG 1.0.0 (7), and TestFlight 1.0.0 (3) for publication through GitHub Releases; the App Store submission waits for EU trader approval.

## Blockers and accepted risks

- macOS 15, iOS 18, and the final candidates on devices are unverified by maintainer decision.
- Pre-rewrite commits stay reachable through pull-request refs and commit URLs after the repository becomes public.
