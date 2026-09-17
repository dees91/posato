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
- Commit IDs in this section predate the history rewrite, which moved `main` from `65443bd` to `84d0c47` with identical trees; citations of rewritten `main` commits elsewhere in tracked documents were updated to the new IDs, while work-branch commit IDs were not rewritten and resolve through pull-request refs.
- macOS releases through `:desktopApp:notarizeMacOsRelease` (last Developer ID build 6); iOS archive and upload are manual steps from `IOS-003` (last TestFlight build 2); `Version.xcconfig` is `1.0.0`; `ITSAppUsesNonExemptEncryption` is `NO`.

## Decisions (`user-confirmed`, 2026-09-17)

- **History:** best-effort rewrite that removes only the trailer lines, without a GitHub Support purge; every stale remote branch and all Actions artifacts and runs are deleted after the list is confirmed. Old commits stay reachable through `refs/pull/*` and direct commit URLs, accepted as residual. The archive tag of the retired HTML prototype is deleted as no longer needed; its history survives only in pull request #32.
- **Task links:** the seven bot comments are deleted, not edited, because GitHub keeps and publishes comment edit history.
- **Platform matrix:** not repeated, including a device smoke test of the final candidates. The MVP flow was accepted on release builds (`MACOS-009` notarized build 6, `SYNC-017` CloudKit Production, `PRIVACY-001` TestFlight build 2), and later product changes touch only privacy manifests, pause-page styling, and the removal itself. macOS 15 and iOS 18 stay unverified, the `ONBOARDING-001` and `ONBOARDING-002` release-build rechecks close with this decision, and public copy keeps its target-only wording.
- **iPad:** the iPhone-only copy on iPad is an accepted limit; the listing adds iPad screenshots only.
- **Publication:** tag, GitHub Release, README, and `posato.app` download links are part of this task (revision 20); the tag placement is decided before publication.

## Plan

1. **Records.** This record, the brief amendments, and the roadmap row correction; independent plan review.
2. **Exposure cleanup (AC-02).** Confirm the deletion list; delete the bot comments (bodies kept outside the repository), artifacts, runs, stale branches, and backup ref. Rewrite a fresh mirror with `git filter-repo --preserve-commit-hashes`, dropping only `Claude-Session:` lines, and compare tree, author, committer, dates, and message per commit. In an authorized window, apply a full protection body allowing force pushes, push `main` with lease, move the open pull-request branches with `rebase --onto`, and restore and compare protection. Rescan branches, tags, and hosted content; count the `refs/pull/*` residual. Tracked documents keep pre-rewrite commit IDs as history.
3. **Candidates (AC-01).** Revision R is the rewritten `main` tip: clean-clone `./gradlew quality`; Developer ID build 7 with signature, notarization, stapling, and Gatekeeper checks and its SHA-256; iOS build 3 archived, entitlement-inspected, uploaded, and `VALID` on TestFlight; `TB-08`/`T-13` review of both artifacts.
4. **Verdict (AC-03).** Fill the obligation table, give the dated verdict for R, the artifacts, and the channels; independent completed-change and readiness review.
5. **Publication (AC-04, AC-05).** After authorization: public visibility, reporting and Issues settings, README and documentation release wording, `posato.app` repository, support, and download links, annotated `v1.0.0` tag and GitHub Release with the DMG and checksums verified after download, and App Review submission once EU trader status is approved.

## High-risk plan review

- **Revision 1:** `changes-required` (independent agent, 2026-09-17). Critical: editing bot comments leaves the links in public edit history. Required: the rewrite residual covers every pull request opened after 2026-09-02, not two pull refs; open branches need `rebase --onto`; protection needs full `PUT` bodies; `filter-repo` must preserve cited commit IDs and push only named refs after re-adding the remote; a tag on R would publish the pre-release README.
- **Revision 2:** `changes-required`. Required: the maintainer's platform-matrix decision was not folded in.
- **Resolution:** all findings folded into the plan, brief, and roadmap row; recommended points (deletion recount, backup ref, workflow runs, App Store submission fields, `DESIGN.md` and CSP for the site, record length) adopted. **Revision 3:** `approved`.

## Obligation table

| Obligation | AC | Evidence | Result | Owner / next action |
| --- | --- | --- | --- | --- |
| Session-link trailers removed from published branches and tags | AC-02 | — | pending | phase 2 |
| Private task links removed from pull requests | AC-02 | — | pending | phase 2 |
| Exposure rescan of branches, tags, and hosted content | AC-02 | — | pending | phase 2 |
| Clean-clone `quality` on R | AC-01 | — | pending | phase 3 |
| Developer ID candidate signed, notarized, stapled, Gatekeeper-accepted | AC-01 | — | pending | phase 3 |
| TestFlight candidate uploaded and `VALID` with release entitlements | AC-01 | — | pending | phase 3 |
| Supported platform matrix | AC-01 | earlier release-build evidence only | accepted by the maintainer | — |
| `ONBOARDING-001` permission persistence and `ONBOARDING-002` accessibility on release builds | AC-03 | matrix decision | accepted by the maintainer | — |
| Product-name trademark search | AC-03 | maintainer manual search 2026-09-17: UPRP, EUIPO TMview, WIPO Global Brand Database, USPTO; no results; no legal opinion | pass | — |
| `TB-08`/`T-13` signing and update review | AC-03 | — | pending | phase 3 |
| EU trader status before App Review | AC-03 | submitted 2026-09-17, awaiting Apple | blocked | Apple review |
| Walkthrough attachment reachable signed out (`DOCS-001`, `DOCS-002`) | AC-05 | — | pending | after visibility change |
| README under-15-minute wording; store listing "open source" wording (`WEB-001`) | AC-05 | — | pending | phase 5 |
| iPad shows iPhone-only copy (`IOS-003`) | AC-03 | decision | accepted by the maintainer | — |
| Private vulnerability reporting and Issues enabled | AC-05 | — | pending | maintainer after visibility change |
| `PRIVACY-001` `0A2A.1` reason may be questioned in App Review | AC-03 | — | pending | phase 5 |

## Blockers and accepted risks

- macOS 15, iOS 18, and the final candidates on devices are unverified by maintainer decision.
- Pre-rewrite commits stay reachable through pull-request refs and commit URLs after the repository becomes public.
