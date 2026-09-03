# Execution: `SYNC-004`

- **Brief:** [Implement the one-workspace bootstrap coordinator](../specifications/sync-004-bootstrap-core.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Muse Code session `simple-musca` (2026-09-03)
- **Reviewer:** plan review — project maintainer (in chat); completed-change
  review — independent Claude Code reviewer
- **Branch:** `feature/sync-004-bootstrap-core`
- **Worktree:** `~/Projects/Polyglot/posato-sync-004`
- **Updated:** 2026-09-03

## Plan

1. Obtain the independent plan review and the maintainer's confirmation that
   explicit workspace removal stays with `SYNC-009`.
2. Define the platform-neutral ports and typed outcomes for the exact zone,
   anchor, workspace-key item, account binding, and random bytes, with
   redacted carriers for the identifiers and binding.
3. Add the SQLDelight migration and store for candidate and established
   bootstrap state with the opaque binding, committed atomically and read back
   fail-closed.
4. Implement the serialized coordinator as the ADR 0007 ten-step protocol with
   binding preflight and postflight around every provider access, exact
   reconciliation of unknown outcomes, and losing-candidate cleanup.
5. Write deterministic fakes and the contract-test matrix from the ADR 0007
   evidence list, including two coordinators over one shared fake provider,
   crash-and-resume at each persistence boundary, and account change around
   indeterminate saves.
6. Run `./gradlew quality` on JVM and iOS Simulator, complete the independent
   completed-change review, rerun affected checks, update the synchronization
   wiki page, and close this record with the single wiki-log entry in the
   closeout commit.

## High-risk plan review

- **Verdict:** `Approve` (maintainer, in chat, 2026-09-03)
- **Critical or Required findings:** none; two open questions resolved —
  removal stays with `SYNC-009`, plan review done by the maintainer in chat.
- **Resolution:** proceeded to implementation unchanged.

## Result

- New `shared/.../feature/sync/bootstrap/` package: ports and sealed provider
  outcomes, UUID/CRC-32/item-codec encoding, a Mutex-serialized coordinator
  split into zone, candidate, anchor, and key-read phases, and an atomic
  SQLDelight store over a new `3.sqm` singleton table. The key is generated in
  memory, never persisted, and cleared after use.
- Deterministic fakes and a 37-test coordinator contract matrix (55 tests in
  the bootstrap package) covering the ADR 0007 evidence list, plus encoding
  vectors, an enumerated redaction test, and real-database migration,
  atomicity, corruption, and replica-coexistence tests.
- One deviation from the exclusive write surface: two `DROP TABLE
  sync_bootstrap_state` lines in the v1/v2 simulations of
  `LocalExactDomainPolicyStoreContractTest` (same precedent as `SYNC-002`;
  `TARGETS-005` files untouched, verified against its worktree).
- Advisory correction in the open PR (scope explicitly accepted by the
  maintainer, 2026-09-03): removed the dead `ZoneSaveResult.Conflict`
  variant (a zone-save duplicate is reconciled by the exact fetch, like
  `AlreadyExists`) and enforced strict 8-4-4-4-12 account text with
  rejection vectors, including the extra-dash case only a position check
  catches.

## Completed-change review

- **Verdict:** `Approve` (approved after corrections; independent Claude Code
  reviewer, 2026-09-03).
- **Required findings:** R1 — the established path returned `Ready` from the
  stored context without re-reading the anchor (false ready after a remote
  workspace replacement); R2 — AC-02 lacked a two-coordinator demonstration
  (both races used one coordinator with scripted state).
- **Resolution:** R1 — `establishedAttempt` re-reads the exact anchor after
  the zone check; equal ids return `Ready`, missing or different ids return
  action-required (2 new tests). R2 — sequential-join and interleaved-race
  tests with two coordinator instances over one shared fake cloud/key
  provider, including loser-only cleanup and a single surviving anchor.
  Recommended and Optional findings deferred as advisory; no scope change.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` after last correction | pass | ktlint, Detekt, JVM + iOS Simulator suites, macOS packaging |
| `git diff --check`, suppression and private-data scans | clean | no whitespace errors, no `Suppress` token in new sources |
| verify-posato simulator regression | pass | run `20260903-203129-42ad`: launch, add/remove website scenarios, DB side-effect and restoration, `sync_bootstrap_state` on device |
| Independent plan review | Approve | maintainer verdict in chat before implementation |
| Independent completed-change review | Approve after corrections | R1 and R2 resolved and covered by tests |
| Advisory correction review (Standard) | Approve | independent Claude Code re-review of the correction diff; `./gradlew quality` rerun green on JVM + iOS Simulator |

## Blockers and accepted risks

- Hosted CI stays manual-only through 2026-09-05; a fresh local
  `./gradlew quality` after the last correction is the merge gate.
- Physical CloudKit and Keychain behavior is not claimed here; it belongs to
  `SYNC-005` through `SYNC-009`.
- Remaining advisory findings (minor style nits, nullable gates instead of
  a `Continue` variant, the unreachable `Stop(Ready)` branch) intentionally
  left unchanged; not accepted into scope.

## Final

- **Status:** `done`
- **Outcome:** all acceptance criteria met; reviews complete; verification
  green after the last correction.
