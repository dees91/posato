# Execution: `SYNC-007`

- **Brief:** [Exchange bounded encrypted mailbox bundles through iOS private CloudKit](../specifications/sync-007-ios-cloudkit.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** implementation agent (2026-09-07)
- **Reviewer:** independent completed-change review pending; plan review done by the maintainer
- **Branch:** `feature/sync-007-ios-cloudkit`
- **Worktree:** `~/Projects/Polyglot/posato-sync-007`
- **Updated:** 2026-09-07

## Plan

1. Resolve the mailbox-types decision in the brief with the maintainer, then
   independent plan review.
2. Swift CloudKit provider with the injectable backend, validation, and the
   per-operation account-change window; Swift tests.
3. Kotlin `iosMain` cloud and mailbox adapters mirroring the JVM shapes;
   `iosTest` contract and redaction tests.
4. Device test for `AC-06`, physical run on the iPhone, `./gradlew quality`,
   completed-change review, record and wiki closeout, pull request.

## High-risk plan review

- **Verdict:** `changes-required`; corrections applied, approved without a
  second round per reviewer decision (2026-09-07)
- **Critical or Required findings:** `R1` (lift six mailbox types to
  `commonMain`, keep the `ByteBuffer` page parser in `jvmMain`); `R2` (mirror
  the companion `CloudErrors.swift` CloudKit error table one-to-one with a
  table-driven Swift test; record change-token-expiry `unknownOutcome` as a
  `SYNC-010` open question in the wiki); `R3` (brief `AC-05` was partly
  unprovable; corrected to seam-isolated unit tests, a Simulator-skipping
  device test, and live-provider status mapping)
- **Resolution:** brief `AC-05` corrected and the mailbox-types decision
  recorded as decided (`commonMain`); wiki open question added; Recommended
  items (cancellable suspend with `operation.cancel()`, `resultsLimit = 1`
  plus `moreComing`, `XCTSkip` device test with categorical checkpoints only,
  completed worktree setup) folded into the plan

## Result

- Implemented per the approved plan. Six mailbox types now live in
  `shared/src/commonMain/**/feature/sync/mailbox/` with the `ByteBuffer`
  page parser left in `jvmMain`; the Swift `CloudKitMailboxProvider` behind
  `IosCloudKitMailboxProvider` mirrors the companion error table, paging,
  and reconciliation flows; `iosMain` cloud and mailbox adapters mirror the
  JVM shapes with cancellable calls and pre-copy length gates.
- Material deviations: none in behavior. Toolchain notes: Kotlin
  `continuation.resume(generic)` collides with the member two-arg overload,
  so the adapter uses `resumeWith(Result.success(...))`; Swift requires
  `Data` (not `NSData`) to satisfy Kotlin-imported protocol witnesses;
  `toChangeFetchResult` was split into three functions for the Detekt
  complexity gate; ktlint autocorrect applied to the new test file.
- First device run passed the test case while the session reported failure
  collecting host diagnostics (`devicectl diagnose` flake); the rerun is
  clean end to end.

## Completed-change review

- **Verdict:** `approve` (independent re-review of the final state,
  2026-09-07)
- **Critical or Required findings:** first pass found one Required
  (`MailboxStore.saveZone` mapped an initial fetch failure to a second fetch
  instead of mapping the fault); fixed with a regression test, plus a
  self-audit fix moving the device-test teardown flag ahead of anchor work.
  Re-review confirms the fix matches the companion fault path exactly and
  reports no Critical or Required defect.
- **Resolution:** all Critical and Required findings resolved; affected
  verification rerun
- **PR review follow-up (2026-09-08):** one P1 (blocking suspend calls now
  hop to `Dispatchers.IO` via the explicit `kotlinx.coroutines.IO` import;
  the earlier `Default` hop worked but `IO` matches the JVM peer literally)
  with a same-dispatcher cancellation test proven to fail without the hop;
  one P2 timeout fix (explicit failure instead of partial state, no unit
  test possible behind the live boundary); one P2 serial-use contract
  documented; one P2 readability refactor declined (mirror shape is
  deliberate). Full quality rerun green; replies posted inline.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` after the last correction | pass | `BUILD SUCCESSFUL`, includes `iosSwiftTest` and `iosHostBuildCheck` |
| `git diff --check`, suppression and private-data scans | pass | clean; no `Suppress` tokens; no account, device, or signing values in tracked files |
| Swift `iosAppTests` on the Simulator | pass | 28/28 `CloudKitMailboxProviderTests` green, full suite green |
| Kotlin `jvmTest` sync | pass | 16/16 `MacOsMailboxAdapterTest` green on the lifted `commonMain` types |
| Kotlin `iosTest` sync | pass | 18/18 `IosCloudKitMailboxAdapterTest` green, incl. cancellation wiring and redaction |
| Physical iPhone `AC-06` (signed Debug) | pass | zone save and confirm, anchor create plus conflict, bundle save plus re-save, fetch, different-bytes rejection, delete with verified absence; database left as found |
| Signed entitlements (`codesign -d --entitlements`) | pass | team-prefixed access group, `iCloud.app.posato.sync` container, CloudKit service |

## Blockers and accepted risks

- Mailbox result types decision resolved to `commonMain` (2026-09-07).
- The iPhone is shared with `SESSION-002`; this run held the phone alone.
- Owner-name pinning stays at the request (exact zone ID under
  `CKCurrentUserDefaultName` in the private database) with zone-name
  re-checks on every fetched record; raw CloudKit owner strings are never
  compared, matching the companion.
- Clearing boundary (`R-05`): owned Kotlin copies and the owned Swift
  anchor buffer are cleared; immutable bridging copies are released, not
  zeroed, which is the accepted limit with no erasure claim.
- Coordinator threading and production provider construction stay with
  `SYNC-009`; callers must invoke the blocking provider off the main thread.

## Final

- **Status:** `done`
- **Outcome:** pull request #37 open with the full change, green local
  quality, both independent reviews complete, and no unresolved Critical or
  Required finding; hosted `@codex review` skipped (no Codex activity on
  recent PRs, same unavailability as in `TARGETS-004`); merge decision stays
  with the maintainer
