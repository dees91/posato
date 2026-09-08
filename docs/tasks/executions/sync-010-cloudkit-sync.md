# Execution: `SYNC-010`

- **Brief:** [Publish and consume pending encrypted bundles with truthful sync status and retry](../specifications/sync-010-cloudkit-sync.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent plan review complete (changes-required, resolved in the brief); completed-change code review passed; physical verification complete with the accepted Mac sign-out waiver
- **Branch:** `feature/sync-010-cloudkit-sync`
- **Updated:** 2026-09-08

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

- **Verdict:** `changes-required` (independent review, 2026-09-08; 1
  Critical, 9 Required, 6 Recommended, 2 Optional), corrections applied to
  the brief before implementation.
- **Critical or Required findings:** `C-1` a second HKDF step for the
  transport key silently amended ADR 0006, which makes the 32-byte workspace
  key the transport key itself; `R-1` acknowledging a bundle by deleting the
  outbox row outside the writer would freeze the writer, whose checkpoint
  verification compares the whole snapshot; `R-2` `6.sqm` was a schema
  migration for query-only changes; `R-3` the core was runtime-scoped while a
  second iOS runtime can exist; `R-4` no per-exchange anchor check, so a
  stale device could publish into a re-created workspace; `R-5` removal
  could delete a zone whose anchor this device never joined; `R-6` `AC-05`
  claimed the non-removing device could establish, while its kept
  established row yields action required; `R-7` the receipt semantics for a
  rejected bundle were undefined and hid a pinned cursor; `R-8` `D1` claimed
  atomicity across two separate transactions and had no failure or pre-link
  story; `R-9` a mid-operation account switch is not physically observable.
- **Resolution:** `C-1` the key bytes are the transport key, no derivation.
  `R-1` a writer-owned acknowledgement under the revision check. `R-2`
  queries only, no migration. `R-3` process-scoped core sharing the flight
  mutex. `R-4` and `R-5` the established check gates every exchange and the
  zone delete. `R-6` `AC-05` and the `D2` copy state the other device's
  action-required state and own removal. `R-7` `exactRefetchAvailable =
  false`, pinned cursor recorded as an accepted limit. `R-8` `D1` orders the
  two commits, defines the failure and pre-link behavior, and records the
  interim divergence as an accepted risk. `R-9` `AC-03` is a sign-out before
  an attempt with the other device receiving the bundle once. Accepted
  Recommended items: coalesced triggers, launch and foreground only on an
  established workspace, airplane-mode retry row, completed write surface,
  a scoped use-and-clear on `WorkspaceKeyValue`, the undelivered-changes
  sentence in the removal confirmation, and the orchestrator placed beside
  `AppleBootstrap`. Declined: shortening the brief below what the High-risk
  boundaries need.

## Result

- Implemented the process-owned exchange and writer, revision-checked publication
  acknowledgement, cursor acceptance and expiry restart, established gating,
  confirmed workspace removal, and local-domain outbox feed. No schema migration.
- Added the existing pinned lifecycle runtime Compose component for foreground
  events. Native bounded fetch now disables automatic all-page fetching; Apple's
  default would otherwise exceed the one-bundle page contract. These are the two
  concrete additions to the planned write surface, with no version upgrade.
- PoC lifecycle and immutable-retry evidence informed ownership and tests; no
  experiment source, runners, or captures were imported.

## Completed-change review

- **Verdict:** `pass` (independent completed-change code review, 2026-09-08).
- **Critical or Required findings:** none.
- **Resolution:** reviewer inspected all tracked and new files, ran 32 focused
  JVM tests successfully, and passed `git diff --check`. Aggregate and physical
  checks passed separately. Standard correction review also passed:
  empty-cursor guard and regression, 20 iOS adapter tests.

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
| Final cleanup | pass | Mac: linked, pending 0, accepted 6, original 1 website; iPhone: 0 websites |
| `git diff --check` and scoped privacy scan | pass | no new private material |

## Blockers and accepted risks

- `user-confirmed` (2026-09-08): iPhone sign-out evidence is sufficient for
  `AC-03`; the maintainer declined sign-out on the working Mac. Mac account
  gating retains adapter/common tests, with no physical Mac sign-out claim.
- iPhone replica rows and key-item absence are not independently readable by
  the driver; native verified outcomes, status, and Mac receipt are the evidence.
- Accepted MVP limits recorded by the plan review: a rejected bundle pins the
  cursor until a later task adds exact refetch; edits made before linking are
  not backfilled until `SYNC-011`.

## Final

- **Status:** `done`
- **Outcome:** implementation, checks, independent reviews and accepted physical
  matrix complete. PR #43 is ready for review; no merge performed.
