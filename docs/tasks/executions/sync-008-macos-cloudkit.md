# Execution: `SYNC-008`

- **Brief:** [Exchange bounded encrypted bundles through the macOS CloudKit boundary](../specifications/sync-008-macos-cloudkit.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending (plan review and completed-change review)
- **Branch:** `feature/sync-008-macos-cloudkit`
- **Worktree:** `~/Projects/Polyglot/posato-sync-008`
- **Updated:** 2026-09-04

## Plan

1. Obtain the independent plan review. The brief's decisions are accepted:
   the payload limit, no `CKSyncEngine`, the `jvmMain` mailbox interface, the
   zone deletion primitive, the 30-second deadline, and no anchor re-read.
2. Add the companion operations 5–11 with the `cloudkit` capability bit, an
   injectable CloudKit seam, record encoding and validation for the zone,
   anchor, and bundle types, the change-fetch pager with the opaque cursor,
   and preflight, postflight, and account-change handling reused from
   `SYNC-006`; Swift tests for every outcome and rejection path.
3. Implement in `shared/src/jvmMain` the `BootstrapCloudPort` adapter and
   the JVM mailbox primitive interface over the existing transport, with
   exhaustive outcome mapping, cursor and payload bounds, buffer clearing,
   and the enumerated redaction test; JVM tests over the in-process fake and
   the scripted fake companion.
4. Run `./gradlew quality` in ad-hoc mode, then the Apple Development
   package with the untracked profile and the `AC-06` physical checklist,
   aborting before any write if the zone already exists, and finishing with
   the exact zone deletion and absence proof.
5. Complete the independent completed-change review, rerun affected checks,
   update the synchronization wiki topic, and close this record with the
   single wiki-log entry in the closeout commit.

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
| Swift tests, format, lint (`:macosSyncCompanion:check`) | pending | |
| JVM protocol, adapter, mailbox, fake-process tests (`:shared:jvmTest`) | pending | |
| `./gradlew quality`, `git diff --check`, suppression and private-data scans | pending | |
| Apple Development package with the untracked profile | pending | |
| `AC-06` physical CloudKit round trip and zone cleanup | pending | |

## Blockers and accepted risks

- Physical gate: the Apple Development package, the maintainer's iCloud
  account on the Mac, network, and an absent `PosatoSyncV1` zone in the
  CloudKit Development environment; the run records pass or blocked without
  record bytes, identifiers, or account values.
- Accepted limit: cross-device exchange with iOS, delayed delivery, and
  account switching during a fetch are `SYNC-009` and `SYNC-010` evidence.

## Final

- **Status:** pending
- **Outcome:** pending
