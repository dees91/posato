# Execution: `MACOS-024`

- **Brief:** [`macos-024-loopback-exceptions.md`](../specifications/macos-024-loopback-exceptions.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** pending (independent agent for the plan and the completed change)
- **Branch:** `fix/macos-024-loopback-exceptions`
- **Updated:** 2026-09-30

## Cause

`observed` in code at `main` 3e19215:

- `ProxyOwnershipEngine` and `SystemProxyConfiguration` own only the HTTP
  and HTTPS tuples. `ExceptionsList` is neither read, compared, recorded,
  nor written, so a session leaves it as it was.
- With Posato's proxy applied, a client that follows the system proxy
  settings sends loopback requests to the listener. ADR 0005 rejects every
  loopback authority except the pause page, so the client gets an empty
  response. `user-confirmed` (2026-09-30): Codex MCP failed with
  `codex_tui failed to start`, and a direct connection or `NO_PROXY` worked.

## Decisions

`user-confirmed` on 2026-09-30, recorded in the
[ADR 0004 amendment](../../decisions/0004-macos-helper-ownership-and-lifecycle.md#macos-024-loopback-proxy-exceptions-amendment):

- **D1.** An exceptions list changed out of band during a session is
  preserved on restore and the state becomes `recoveryRequired`, as for the
  tuples.
- **D2.** An exceptions mismatch during an active session ends the
  active-enforcement claim and starts restoration, like a tuple mismatch.
- **D3.** The state schema becomes version 2; a version 1 record is read as
  owning no exceptions change; the file path stays.
- **D4.** A baseline list over 256 entries, with an entry over 2,048 UTF-8
  bytes, or with a non-string entry fails before mutation.

## Plan

1. **Reproduce on `main` (`AC-01`).** In a fresh `primary` Tart clone with a
   `main` build: start a loopback HTTP server in the guest on `127.0.0.1`
   and `::1`, find a client that follows the system proxy settings and the
   exceptions list (first probe: `NSURLSession` through JXA; the probe is
   recorded because CFNetwork may bypass loopback by itself), confirm the
   request succeeds without a session, then start a session that blocks one
   website and assert the loopback request fails. Record `scutil --proxy`
   before and during the session.
2. **Failing regressions first.** Swift Testing with the existing in-memory
   configuration and persistence. Isolated coverage is kept only for
   failures E2E cannot produce reliably:
   - applied-list construction: order kept, only missing entries appended in
     the fixed order, no duplicates, absent key created, all-present baseline
     yields no owned change. A regression here silently rewrites a person's
     list, and a VM run exercises one or two list shapes only;
   - bounds (`D4`): oversized or non-string baselines fail before any write
     or record save;
   - a version 1 record written by the previous daemon restores the tuples
     and leaves the exceptions untouched (`D3`); E2E cannot produce a
     record from an older daemon during a session;
   - a crash after `prepared` with the exceptions already written or not
     yet written reconciles to the baseline; E2E cannot stop the daemon
     between those two points;
   - `SystemProxyConfiguration.replacingTuples` removes `ExceptionsList`
     when the baseline had none and keeps `ExcludeSimpleHostnames`.
   Restore, conflict (`D1`), and maintain mismatch (`D2`) are proved by E2E
   in step 5.
3. **Implement.** `ProxySnapshot` gains the exceptions presence and value;
   `SystemProxyConfiguration` reads and writes it under the existing lock
   and verifies the whole resulting dictionary; `OwnershipRecord` schema 2
   adds `baselineExceptions` and `appliedExceptions` with version 1 decoding;
   `ProxyOwnershipEngine` computes, compares, and restores the group per
   `D1`/`D2`. No Kotlin, wire, or helper change is expected: the chain check
   and the listener stay as they are.
4. **Local checks.** `swift test` in `macosHelper`, the aggregate quality
   gate, and the package build that `posato-control build` uses.
5. **E2E after the change (`AC-02`, `AC-03`).** Same VM setup with a
   baseline list containing `*.local` and `169.254/16`:
   - during a session: requests to `127.0.0.1`, `localhost`, and `[::1]`
     succeed; the selected website shows the pause page in Safari; an
     unrelated website loads; `scutil --proxy` shows the baseline entries in
     order followed by the three loopback entries;
   - the exact baseline list returns after early end, natural end, and after
     the session helper is killed during a session (lease-driven restore);
   - a baseline without `ExceptionsList` returns without the key;
   - `D2`/`D1`: changing the bypass list with `networksetup` during a session
     ends active enforcement, keeps the changed list, and reports
     `recoveryRequired`, with the tuples restored.
6. **Amendment acceptance (`AC-04`),** completed-change review, closeout of
   this record, and one wiki-log entry.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- None yet.

## Final

- **Status:** pending
- **Outcome:** pending
