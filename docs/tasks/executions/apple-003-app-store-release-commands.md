# Execution: `APPLE-003`

- **Brief:**
  [`../specifications/apple-003-app-store-release-commands.md`](../specifications/apple-003-app-store-release-commands.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** `Claude`
- **Reviewer:** `independent completed-change reviewer`
- **Branch:** `chore/retro-release-003`
- **Updated:** `2026-09-25`

## Plan

1. Add `PATCH` and the named store operations on the existing client, with one
   narrow policy for screenshot upload URLs.
2. Add `store status`, `store prepare`, and `store submit` with refusals before
   the first write.
3. Test against recorded responses. Run `store status` live once, read-only.
   Document the release commands and the capture recipe.

## High-risk plan review

- **Verdict:** `not held before implementation`
- **Deviation:** The maintainer's directive of 2026-09-25 was to address every
  `RELEASE-003` retro finding in one pull request, one commit each. The
  work started as a lightweight retro fix, and no plan review took place. The
  completed-change review below served as the independent High-risk review
  after the fact.

## Result

- `posato-provisioning store status|prepare|submit` replace the per-release
  App Store Connect scripts. `apple-provisioning.md` documents the archive,
  export, upload, and store steps and the upload-URL exception. `listing.md`
  holds the screenshot capture recipe.
- `hypothesis`: `store prepare` and `store submit` are idempotent (a rerun
  changes only what differs, finishes a partial run, and never duplicates a
  submission). Only recorded responses cover this so far.

## Completed-change review

- **Verdict:** `changes-required`, then corrected
- **Critical or Required findings:** High-risk tier not recorded; idempotence
  stated as fact.
- **Resolution:** Recorded this brief and execution record, and labelled the
  idempotence claims `hypothesis`. Also accepted the Recommended findings:
  `store submit` now resubmits an `UNRESOLVED_ISSUES` submission that holds the
  version and never creates a second one, and `store prepare` refuses a version
  outside the editable states before its first write. The Optional finding is
  also done: the second-page hint now fits each caller.
- **Pull request review (#86):** two inline findings on `store submit`,
  addressed at the maintainer's request. A rejected item is now marked
  `resolved` before its unresolved submission is resubmitted. Items that are
  not versions, such as App Events, stay visible, so a draft or unresolved
  submission holding anything besides the requested version is refused before
  any write.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew :posato-provisioning:check` | `pass` | ktlint, Detekt, and unit tests, including request shapes, idempotence, exact bundle-id match, refusals, upload policy, and MD5 |
| `./gradlew quality` | `pass` | Before and after the review corrections, on the combined branch |
| Live `store status --version 1.1.0` | `pass` | Read-only; reported 1.1.0 `WAITING_FOR_REVIEW` with build 4, next build 5, both screenshot sets `COMPLETE` |

## Blockers and accepted risks

- Live `store prepare` and `store submit` are unverified until the next iOS
  release; the first run there confirms or corrects the `hypothesis` above.

## Final

- **Status:** `done`
- **Outcome:** Met for the tool and documentation; live writes pending the
  next release.
