# Execution: `SYNC-008`

- **Brief:** [Exchange bounded encrypted bundles through the macOS CloudKit boundary](../specifications/sync-008-macos-cloudkit.md)
- **Status:** `blocked`
- **Review tier:** `high-risk`
- **Implementer:** agent session `satin-comet`
- **Reviewer:** independent agents (plan review, re-review, change review, F-01 recheck)
- **Branch:** `feature/sync-008-macos-cloudkit`
- **Worktree:** `~/Projects/Polyglot/posato-sync-008`
- **Updated:** 2026-09-04

## Plan

1. Independent plan review of the accepted brief before implementation.
2. Companion operations 5–11 with the `cloudkit` capability, injectable
   CloudKit seam, record validation, change pager, and binding-gated calls.
3. JVM `BootstrapCloudPort` adapter (30 s deadline) and mailbox primitives
   over the existing transport, with exhaustive outcome mapping.
4. `./gradlew quality`, Swift format/lint/tests, `git diff --check`.
5. Independent completed-change review; fix Critical/Required; rerun checks.
6. Maintainer runs the `AC-06` physical checklist then the PR opens.

## High-risk plan review

- **Verdict:** `block` → brief corrected → `approve` on focused re-review.
- **Critical:** C1 missing wire/outcome contract for ops 5–11; C2 `AC-02`
  asserted a nonexistent `AlreadyExists` variant (fixed to `Conflict`).
- **Required:** R1 fetch-response bound (81,946/81,986); R2 capability value
  `2` and per-op gating; R3 preflight-unavailable → `retryable`; R4 named
  manual driver for `AC-06`; R5 codes 12–15 exit with no response.
- **Resolution:** all seven fixed in the brief before implementation.

## Result

Companion implements ops 5–11 with per-op capability gating, fetch-first
logic with exact-fetch reconciliation, create-only saves, explicit one-bundle
paging, and preflight/postflight plus the account-change window reused from
`SYNC-006`. JVM side adds the cloud adapter, mailbox adapter with page
parsing, protocol codes/limits/builders, and a per-operation client frame
cap. PoC provenance: record layout and failure categories informed by
`.research/blocker` `CloudKitRecordCodec`/`CloudKitMailbox`; reimplemented,
not imported.

## Completed-change review

- **Verdict:** `block` on F-01 (Critical) → fixed → `PASS` on focused recheck.
- **F-01:** JVM transport capped every response at 65,624, breaking legal
  op-10 pages. Fixed with per-operation `frameLimit()`; the new full-page
  `transact` test fails on the old path and passes on the new one.
- **Accepted hardening:** F-02 zone call counters, F-04 reserved-op loops
  12–15 plus silent-exit test, F-05 per-op JVM decode bound, F-06 dropped
  unused `zoneID` parameter, F-07 `zoneMissing` short-circuit in
  `createAnchor`.
- **Declined:** F-03 (postflight clears on value copies) — same shape as the
  accepted `SYNC-006` precedent, no leak either way.
- **Advisory:** no YAGNI concern; op 11 justified by `AC-06`/`SYNC-009`.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Swift tests (`swift test`, 117 incl. 60 new) | pass | worktree run 2026-09-04 |
| Swift format lint + SwiftLint | pass | zero violations |
| `:shared:jvmTest` macos slice (60 incl. 34 new) | pass | test-report XML, 0 failures |
| `./gradlew quality` (aggregate incl. Detekt, iOS, packaging) | pass | `BUILD SUCCESSFUL` |
| `git diff --check`, suppression gate, private-data scan | pass | clean |
| `AC-06` physical CloudKit round trip + zone cleanup | blocked | needs maintainer gate below |

## Blockers and accepted risks

- Physical gate (clearing condition): Apple Development package with the
  untracked companion profile, the maintainer's iCloud account, network, and
  an absent `PosatoSyncV1` zone. This machine has no provisioning profiles,
  and the run writes to the maintainer's private database, so the run is a
  maintainer action with the `AC-06` checklist from the handoff.
- The `AC-06` driver instantiates the JVM adapters directly from temporary
  `desktopApp` wiring (outside this task's write surface); no DI, UI, or
  coordinator wiring is committed.
- Accepted limit: cross-device exchange, delayed delivery, and mid-fetch
  account switching are `SYNC-009`/`SYNC-010` evidence.

## Final

- **Status:** blocked on the physical gate; code complete and reviewed.
- **Outcome:** pending `AC-06` evidence, then PR.
