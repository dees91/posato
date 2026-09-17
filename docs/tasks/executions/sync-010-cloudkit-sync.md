# Execution: `SYNC-010`

- **Brief:** [Publish and consume pending encrypted bundles with truthful sync status and retry](../specifications/sync-010-cloudkit-sync.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent plan review complete (changes-required, resolved in the brief); completed-change code review passed; physical verification complete with the accepted Mac sign-out waiver
- **Branch:** `feature/sync-010-cloudkit-sync`
- **Updated:** 2026-09-09

## Plan

1. Implement accepted `D1`–`D5`: mailbox port and token expiry on both platforms.
2. Add query-only acknowledgement and clear operations, owned by the writer
   under its checkpoint/revision check; clear bootstrap and replica atomically.
3. Compose one process-owned core, writer, and coalesced exchange under the
   bootstrap flight; use workspace bytes directly as TransportKey.
4. Gate exchange and removal on binding/zone/anchor; feed exact-domain changes
   after local save, and show the seven truthful status categories.
5. Verify common/store/adapter/real-graph tests and signed physical flows;
   update design, verification recipe, wiki and records, review, then open PR.

## High-risk plan review

- Initial verdict: changes-required (1 Critical, 9 Required), resolved in the
  brief before implementation. The corrected plan uses workspace bytes directly
  as TransportKey, writer-owned revision-checked acknowledgement, queries without
  migration, one process core, and the shared flight.
- Established binding/zone/anchor checks gate exchange and zone deletion; old
  peers perform their own removal before joining a replacement workspace.
  Rejection pins the cursor, local and outbox commits remain separate, and
  physical account evidence uses sign-out before an attempt. The brief retains
  the accepted limitations and the full corrected acceptance criteria.
- The 2026-09-09 correction also received High-risk plan review because queued
  authoring crosses removal/re-link boundaries. Approved with no Critical or
  Required findings after workspace capture and cancellation ownership were fixed
  in the plan; the maintainer accepted opening the writer on demand under D1.

## Result

- Implemented the process-owned exchange and writer, revision-checked publication
  acknowledgement, cursor acceptance and expiry restart, established gating,
  confirmed workspace removal, and local-domain outbox feed. No schema migration.
- Added the existing pinned lifecycle runtime Compose component for foreground
  events. Native bounded fetch now disables automatic all-page fetching; Apple's
  default would otherwise exceed the one-bundle page contract. These are the two
  concrete additions to the planned write surface, with no version upgrade.
- Local saves now commit and hand off ordered diffs without waiting on network.
  The process FIFO retains the workspace captured before commit; stale-workspace
  entries are discarded, and the writer opens on demand. Failed authoring ends
  that pass without an exchange overwriting its outcome. One lifecycle trigger
  remains; a fresh link reports syncing instead of pending local changes.
- PoC lifecycle and immutable-retry evidence informed ownership and tests; no
  experiment source, runners, or captures were imported.

## Completed-change review

- **Verdict:** `pass` (independent completed-change code review, 2026-09-08).
- **Critical or Required findings:** none.
- **Resolution:** reviewer inspected all tracked and new files, ran 32 focused
  JVM tests successfully, and passed `git diff --check`. Aggregate and physical
  checks passed separately. Standard correction review also passed:
  empty-cursor guard and regression, 20 iOS adapter tests.
- High-risk correction review (2026-09-09) passed with no Critical/Required
  findings. Reviewer inspected the correction against `30d89a6`, including new
  authoring and regression files, and ran 25 focused JVM tests successfully.

| PR feedback class after `30d89a6` | Count | Decision |
| --- | --- | --- |
| Local save waits / unopened writer | 2 P1 | fixed; writer scope narrowed with maintainer acceptance |
| Duplicate lifecycle trigger / pending copy | 2 P2 | fixed in the maintainer-approved plan |
| Whole-exchange page cap / graph path cache | 2 P2 | declined; separate policy and no failing current consumer |

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | JVM, Kotlin/Native, Swift tests, build/format/static/migration gates |
| Independent focused JVM review checks | pass | 32 tests; no Critical or Required findings |
| Signed Mac and iPhone builds + phone install | pass | ignored run `sync-010-physical` |
| Empty first-page cursor regression | red then green | real iOS adapter threw before native fetch; 20 adapter tests now pass |
| Physical Mac/iPhone exchange | pass | `sync-010-fixed`: completion on both; Mac accepted 2 then 4, pending 0 |
| Fixture cleanup | pass | Mac accepted 6, pending 0; original website counts restored |
| iPhone offline/reconnect | pass | `sync-010-account`: accepted 6→8, repeated exchange stayed 8 |
| iPhone sign-out/sign-in | pass | action required and local domain retained; Mac stayed 8, then 9 once |
| Mac sign-out | waived | maintainer accepted iPhone evidence; working Mac was never signed out |
| Removal/rejoin, both initiators | pass | `sync-010-removal`: local domains kept, peer blocked, fresh consent succeeds |
| Foreign-anchor cleanup, both directions | pass | newly created zone survives the old peer's own removal |
| Concurrent consent and losing-device key | pass | `sync-010-race`: overlapping taps, Mac waits, adopts, authors and relaunches |
| Correction regression tests | red then green | blocked established check/fetch and unopened writer; FIFO, cancellation, replacement and failures pass |
| Correction aggregate quality | pass | full `./gradlew quality` after final source correction, 2026-09-09 |
| Correction signed Mac/iPhone flows | pass | `sync-010-review`: add/edit, persistence after relaunch, remove, exchange |
| Final cleanup and repeated fetch | pass | Mac: accepted 6→16→18, then unchanged; pending 0, original 1 website; iPhone: 0 websites |
| `git diff --check` and scoped privacy scan | pass | no new private material |

## Blockers and accepted risks

- `user-confirmed` (2026-09-08): iPhone sign-out evidence is sufficient for
  `AC-03`; the maintainer declined sign-out on the working Mac. Mac account
  gating retains adapter/common tests, with no physical Mac sign-out claim.
- iPhone replica rows and key-item absence are not independently readable by
  the driver; native verified outcomes, status, and Mac receipt are the evidence.
- Accepted MVP limits recorded by the plan review: a rejected bundle pins the
  cursor until a later task adds exact refetch; edits made before linking are
  not backfilled until `SYNC-011`. Failed authoring is likewise not backfilled.
- The handoff queue is volatile until outbox authoring; process exit can lose an
  unauthored diff while retaining the committed local policy (accepted D1).

## Final

- **Status:** `done`
- **Outcome:** implementation, checks, independent reviews and accepted physical
  matrix complete. PR #43 is ready for review; no merge performed.
