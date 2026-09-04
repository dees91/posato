# Execution: `IOS-001`

- **Brief:** [Apply and clear only Posato-owned iOS restrictions](../specifications/ios-001-enforcement.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending
- **Branch:** `feature/ios-001-enforcement`
- **Worktree:** `~/Projects/Polyglot/posato-ios-001`
- **Updated:** 2026-09-04

## Plan

1. Obtain the independent plan review. The brief's decisions are accepted:
   the named store, exact `WebDomain` matching, websites-only apply, and the
   App Group migration here.
2. Define the public Kotlin `iosMain` provider interface (canonical domain
   strings, mapping identifiers, platform-neutral outcomes) and the internal
   adapter with redacted carriers; cover it with `iosTest` contract and
   redaction tests over a fake provider.
3. Implement the Swift provider: named store ownership, token lookup by
   mapping identifier from the selection store, atomic apply with
   verification, idempotent clear on the owned store only, authorization
   gating through an injectable seam.
4. Move the selection store to the App Group container with
   after-first-unlock protection, add the App Group entitlement, and prove
   the migration with a Swift test and the existing mapping flows.
5. Run Swift tests on the Simulator and the physical iPhone, record the
   manual checklist, run `./gradlew quality`, complete the independent
   completed-change review, rerun affected checks, update the iOS
   enforcement wiki page, and close this record with the single wiki-log
   entry in the closeout commit.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- pending

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending
- **Advisory findings:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- Physical iPhone with the maintainer's development team, Screen Time
  authorization, and at least one selected application are required for
  `AC-01`, `AC-02`, and `AC-04`.

## Final

- **Status:** `active`
- **Outcome:** pending
