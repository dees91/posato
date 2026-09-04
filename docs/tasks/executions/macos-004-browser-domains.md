# Execution: `MACOS-004`

- **Brief:** [Deny selected exact domains in Safari and Chrome with safe recovery](../specifications/macos-004-browser-domains.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending (plan review and completed-change review)
- **Branch:** `feature/macos-004-browser-domains`
- **Worktree:** `~/Projects/Polyglot/posato-macos-004`
- **Updated:** 2026-09-04

## Plan

1. Obtain the independent plan review and the maintainer's confirmation of
   the five recommended decisions in the brief; confirm that `commonMain`
   needs no enforcement contract this wave.
2. Add the helper-only pipe operation that carries the bounded canonical
   domain set (and an optional end time for the page), the daemon-side
   rejection, and the `MacOsHelperClient` extension plus the small
   orchestrator in `desktopApp` (configure, Apply, verified-active, Restore,
   unknown-outcome reconciliation).
3. Implement the loopback listener in the helper: bounded request parsing,
   exact-host policy, HTTP relay and `CONNECT` tunnel over direct upstream
   sockets, the `/blocked` route and page, the host-free signal, and the
   candidate and effective proxy-chain checks around Apply.
4. Implement the Safari and Chrome same-tab adapters over Apple Events with
   URL reduction, membership check, rate limit, and mismatch handling; add
   the usage description to the helper `Info.plist`.
5. Write the Swift and JVM tests from the ADR 0005 evidence list, the
   redaction test, and the environment-gated physical harness.
6. Run the physical checklist with the maintainer (Enable, Apply,
   browser rows, presentation rows, conflict rows, sleep/wake, forced helper
   termination, reboot, canary), record categorical results, then
   `./gradlew quality`, the completed-change review, the wiki topic update,
   and the single wiki-log entry in the closeout commit.

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

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- Physical rows need the maintainer at the Mac for System Settings approval,
  administrator authentication, and the Automation prompts; the run mutates
  and must restore the real proxy settings.
- The two-major-version release matrix and the exact-version support claim
  are `RELEASE-001` evidence, not claimed here.

## Final

- **Status:** pending
- **Outcome:** pending
