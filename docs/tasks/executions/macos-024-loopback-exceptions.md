# Execution: `MACOS-024`

- **Brief:** [`macos-024-loopback-exceptions.md`](../specifications/macos-024-loopback-exceptions.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** pending (independent agent for the plan and the completed change)
- **Branch:** `fix/macos-024-loopback-exceptions`
- **Updated:** 2026-09-30

## Cause

`observed` at `main` 3e19215 and on the host's Codex 0.159.2:

- `ProxyOwnershipEngine` and `SystemProxyConfiguration` own only the HTTP
  and HTTPS tuples; `ExceptionsList` is neither read, compared, recorded,
  nor written.
- The listener forwards absolute-form HTTP only to port 80 and tunnels
  `CONNECT` only to port 443 (`BoundedProxyRouter.swift`), so a request to a
  loopback server on any other port is refused and the client sees an empty
  response. `user-confirmed` (2026-09-30): Codex MCP failed with
  `codex_tui failed to start`; a direct connection or `NO_PROXY` worked.
- Codex imports only `kSCPropNetProxiesHTTP*` and `kSCPropNetProxiesHTTPS*`
  with `SCDynamicStoreCopyProxies` (`nm -u`), and hyper-util 0.1.20, which the
  binary links, reads no exceptions list on macOS. Exceptions alone therefore
  cannot fix the reported case; the listener must relay loopback.

## Decisions

`user-confirmed` on 2026-09-30:

- **D1.** An exceptions list changed out of band during a session is
  preserved on restore and the state becomes `recoveryRequired`, as for the
  tuples; a list equal to the baseline is not a conflict (plan review R1).
- **D2.** An exceptions mismatch during an active session ends the
  active-enforcement claim and starts restoration, like a tuple mismatch.
- **D3.** The state schema becomes version 2; a version 1 record is read as
  owning no exceptions change; the file path stays.
- **D4.** A baseline list over 256 entries, with an entry over 2,048 UTF-8
  bytes, or with a non-string entry fails before mutation.
- **D5.** Version 2 stores digests and the appended suffix instead of the
  list, which keeps the ADR 0004 privacy rule and fits the 64 KiB state file
  (plan review R2, R3).
- **D6.** Both directions ship: the exceptions (A) and a loopback relay in
  the listener (B), chosen after the Codex finding. End-to-end proof uses a
  small probe built on the host with Codex's HTTP stack (reqwest with system
  proxy support) instead of Codex itself.

## Plan

1. **Probe and reproduction (`AC-01`).** Build the probe outside the
   repository as a one-off verification artifact: a reqwest client with system proxy support that
   requests a URL and, optionally, holds a streamed response open for 45
   seconds. In a fresh `primary` Tart clone with a `main` build, one
   `vm exec` script starts a loopback HTTP server on `127.0.0.1` and `::1`
   (built with the probe), runs the probe against `127.0.0.1`, `localhost`,
   and `[::1]` on a non-80 port, and stops the server. Without a session all
   succeed; during a session that blocks one website they fail. Also record
   `scutil --proxy` and one `NSURLSession` request through JXA, to learn
   whether CFNetwork already bypasses loopback.
2. **`posato-control` extension (plan review R6).** `vm network --service
   <name> --bypass-domains <list> | --bypass-empty` and a per-service read of
   the bypass list, using the guest administrator password like the existing
   actions, with driver tests. Record what `networksetup -setproxybypassdomains
   <service> Empty` writes; if it cannot remove the key, the no-key baseline
   is covered only by the unit tests. A daemon kill during a session is added
   to the driver if no existing command can do it.
3. **Failing regressions first.** Swift Testing with the in-memory
   configuration and persistence, only where E2E cannot reliably expose the
   failure:
   - applied-list construction: order kept, exact case-sensitive matching,
     only missing entries appended in the fixed order, absent key created,
     all-present baseline yields no owned change;
   - bounds (`D4`): oversized, non-string, and non-array baselines fail with
     the incompatible-network outcome before any record save or write;
   - a version 1 record restores the tuples and leaves the exceptions
     untouched, and stays version 1 semantics through phase rewrites;
   - crash windows: `prepared` with nothing written and with everything
     written, and a restore that committed before the record was removed,
     which must reach Idle without `recoveryRequired` (R1);
   - an unreadable or out-of-bounds current list during restore and maintain
     still restores the tuples and ends `recoveryRequired` (R4);
   - record bounds: the applied digest and suffix are consistent, and the
     worst-case version 2 record fits the 64 KiB file;
   - router: loopback absolute-form and `CONNECT` on a non-80/443 port route
     to a relay, the own listener port and `*.localhost` stay refused,
     `localhost` never resolves through DNS, and a selected host still
     blocks; the existing `SystemProxyConfigurationTests` assertion that
     `ExceptionsList` passes through unchanged is updated, not dropped.
4. **Implement.** Daemon: `ProxySnapshot` gains an exceptions value with an
   opaque unreadable case; `SystemProxyConfiguration` reads and writes it
   under the existing lock, with a leave-untouched target, and verifies the
   whole dictionary; `OwnershipRecord` schema 2 per `D5`;
   `ProxyOwnershipEngine` applies `D1`, `D2`, and the prepared-baseline
   comparison. Helper: the chain-check overlay adds the three entries, and
   the router and relay implement the loopback relay without idle timeout.
   Map the `D4` failure to the existing incompatible-network copy and check
   it in the app.
5. **Local checks.** `swift test` in `macosHelper`, the driver tests, the
   aggregate quality gate, and the package build `posato-control build`
   uses.
6. **E2E after the change (`AC-02`, `AC-03`).** Same VM setup with a
   baseline list containing `*.local` and `169.254/16`:
   - during a session: the reqwest probe, which ignores the exceptions
     list like Codex and so goes through the listener, reaches
     `127.0.0.1`, `localhost`, and `[::1]` on a non-80 port and keeps the
     45-second stream (relay); the `NSURLSession` request reaches them
     directly if CFNetwork honors the list (exceptions); the selected
     website shows the pause page in Safari; an unrelated website loads;
     the per-service list shows the baseline entries in order followed by
     the three loopback entries;
   - the exact baseline list returns after early end, natural end, a killed
     session helper, and a killed daemon during a session;
   - a baseline without `ExceptionsList` returns without the key, if step 2
     can produce it;
   - `D2`/`D1`: changing the bypass list during a session ends active
     enforcement, keeps the changed list, and reports `recoveryRequired`,
     with the tuples restored.
7. **Amendment acceptance (`AC-04`),** a second independent plan review of
   this revised plan before step 3, the completed-change review, closeout of
   this record, and one wiki-log entry.

## High-risk plan review

- **Verdict:** `changes-required` (first pass, 2026-09-30, independent
  agent)
- **Critical or Required findings:** R1 restore treated a baseline-equal
  list as a conflict; R2 `D4` bounds exceed the 64 KiB state file; R3 storing
  the list breaks the ADR 0004 privacy rule; R4 an unreadable current list
  blocks tuple restore; R5 the planned client probably cannot reproduce the
  failure and the reported client ignores exceptions; R6 E2E needs a
  `posato-control` extension.
- **Resolution:** R1 and R4 folded into the amendment and step 3; R2 and R3
  by `D5`; R5 by `D6` and step 1; R6 by step 2. Recommended items folded:
  applied-list record check, chain-check overlay, ADR 0005 wording, exact
  matching, updated existing test, daemon-kill run, `D4` error mapping.
  Second pass pending.

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
