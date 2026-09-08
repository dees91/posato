# Execution: `SYNC-009`

- **Brief:** [Integrate Keychain and CloudKit bootstrap on both apps without creating a parallel workspace](../specifications/sync-009-apple-bootstrap.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** implementation agent (2026-09-08)
- **Reviewer:** independent plan review pending; completed-change review pending
- **Branch:** `feature/sync-009-apple-bootstrap`
- **Worktree:** `~/Projects/Polyglot/posato-sync-009`
- **Updated:** 2026-09-08

## Plan

1. Resolve the two open decisions in the brief with the maintainer (when
   bootstrap runs, what the graph exposes), then independent plan review.
2. Add the narrow `commonMain` composition entry point: single-flight,
   background dispatcher, established-context read; `commonTest` over fakes.
3. Desktop graph: verified companion client, both macOS adapters,
   `JdkSyncCryptoProvider`, `SqlBootstrapStore`, replica store; `Main.kt`
   lifecycle. Adopt the deferred `SYNC-008` preflight and postflight helper.
4. iOS graph: both iOS adapters and `IosSyncCryptoProvider`; adopt the deferred
   `SYNC-007` production provider construction and off-main-thread rule.
5. Physical Mac and iPhone rows under the maintainer's iCloud account,
   `./gradlew quality`, independent completed-change review, closeout and PR.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending implementation.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | `pending` | |
| `commonTest` composition entry point | `pending` | |
| `jvmTest` and `iosTest` graph composition | `pending` | |
| Physical Mac and iPhone bootstrap rows | `pending` | maintainer iCloud account, sequential with other physical work |

## Blockers and accepted risks

- Two open decisions in the brief block implementation until the maintainer
  answers: bootstrap timing and what the graph exposes.
- The physical rows need the maintainer's iCloud account on both devices and a
  private database without the custom zone; the destructive removal row runs
  last so the database is left as found.
- Wave 7 runs in parallel with `QUALITY-005` (verification driver) and
  `SESSION-003` (session schema). Write surfaces are disjoint by design; this
  task must not touch `feature/session/**`, `feature/enforcement/**`, or
  `tools/posato-control/**`.

## Final

- **Status:** `active`
- **Outcome:** pending
