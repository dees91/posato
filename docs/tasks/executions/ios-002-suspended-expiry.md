# Execution: `IOS-002`

- **Brief:** [Clear Posato-owned restrictions after normal expiry while the iOS app is suspended](../specifications/ios-002-suspended-expiry.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending until assigned; independent plan review required before implementation
- **Branch:** `feature/ios-002-suspended-expiry`
- **Worktree:** `~/Projects/Polyglot/posato-ios-002`
- **Updated:** 2026-09-05

## Plan

1. Resolve the short-session decision in the brief with the maintainer, then
   independent plan review.
2. Extension target, entitlements, shared Swift clear logic, App Group record.
3. Scheduler seam in `iosApp` and the Kotlin `iosMain` adapter with outcomes
   and the reconciliation read.
4. Swift and Kotlin tests, redaction test.
5. Device signing for the extension, physical checklist, `./gradlew quality`,
   completed-change review, record and wiki closeout, pull request.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Not started.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- Open decision in the brief (sessions below the 15-minute platform minimum)
  must be accepted before implementation.
- The extension's development profile is a physical gate; the clearing path
  is stated in the brief.

## Final

- **Status:** `active`
- **Outcome:** pending
