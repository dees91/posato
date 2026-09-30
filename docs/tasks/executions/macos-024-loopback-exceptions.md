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
- **D7.** Loopback relays are capped at 32 client and upstream pairs, and
  their idle timeout is dropped only after the first response byte or
  `200 Connection Established` (plan review Req-3).
- **AC-04 met:** the maintainer accepted the ADR 0004 and ADR 0005
  amendment text on 2026-09-30.

## Plan

1. **`posato-control` extension (plan review R6, second-pass Req-6).**
   With driver tests:
   - `vm push <local file> <guest path>` copies one bounded file into the
     guest user's home, so the probe and server can run there;
   - `vm network --service <name> --bypass-domains <list> | --bypass-empty`
     and a per-service read of the list, using the guest administrator
     password like the existing actions;
   - a daemon kill during a session, unless an existing command can do it.
   Record what `networksetup -setproxybypassdomains <service> Empty` writes;
   if it cannot remove the key, produce the absent-key baseline with a root
   `SCPreferences` one-liner through the same administrator path.
2. **Probe and reproduction (`AC-01`).** Build a one-off probe outside the
   repository with reqwest and hyper-util pinned to the versions in Codex
   0.159.2's `Cargo.lock`, recorded in the result: it sends a GET, a POST
   with a body of a few KiB, and a streamed GET held for 45 seconds. A small
   loopback server built with it listens on `127.0.0.1` and `::1` on a
   non-80 port and records, per request, whether it came through the proxy
   (the proxy's injected `Connection: close` and a stripped
   `Proxy-Connection`) or directly. In a fresh `primary` Tart clone with a
   `main` build, one `vm exec` script starts the server, runs the probe
   against `127.0.0.1`, `localhost`, and `[::1]`, and stops the server:
   - without a session all succeed directly;
   - during a session that blocks one website they fail;
   - control: with a baseline list that already holds the three loopback
     entries they still fail, which proves the probe ignores the list.
   Record `scutil --proxy` and one `NSURLSession` request through JXA,
   classified by the server, to observe whether CFNetwork honors the list
   or bypasses loopback by itself, and one `CFNetworkCopyProxiesForURL`
   result for a `*.localhost` host with the loopback entries in the list.
3. **Failing regressions first.** Swift Testing with the in-memory
   configuration and persistence, only where E2E cannot reliably expose the
   failure (engineering quality contract, failure inventory):
   - *applied-list construction:* order kept, exact case-sensitive matching,
     only missing entries appended in the fixed order, absent key created,
     all-present baseline writes nothing but is still compared. Credible
     failure: a person's list silently rewritten or duplicated; a VM run
     covers one or two list shapes only;
   - *digest rules:* injective encoding, byte comparison without Unicode
     normalization, restore refused when the current list minus the suffix
     does not match the baseline digest, and the version 2 validity rules.
     Credible failure: a changed list restored as if unchanged; E2E cannot
     construct canonically equivalent or colliding list shapes reliably;
   - *bounds (`D4`):* oversized, non-string, and non-array baselines fail
     with the incompatible-network outcome before any record save or write.
     Credible failure: an integrity prompt or a partial write; the driver
     cannot write a non-string or non-array value;
   - *version 1 compatibility:* a version 1 record restores the tuples,
     leaves the exceptions untouched, and keeps that meaning through phase
     rewrites. Credible failure: a stale list adopted as a baseline; an
     update is blocked while Posato owns settings, so E2E never meets one;
   - *crash windows:* `prepared` with nothing written and with everything
     written, and a restore that committed before the record was removed,
     which must reach Idle without `recoveryRequired` (R1). Credible
     failure: a false recovery prompt; E2E cannot stop the daemon between
     those points;
   - *unreadable current list:* restore and maintain still restore the
     tuples and end `recoveryRequired` (R4). Credible failure: the system
     proxy left at 127.0.0.1; the driver cannot write such a value;
   - *router matrix:* the three hosts (case, trailing dot, brackets) on a
     non-80/443 port route to a loopback relay with literal addresses and
     the request-target authority as `Host`; the listener's own port, `0.0.0.0`, `127.0.0.2`,
     `[::ffff:127.0.0.1]`, `[0:0:0:0:0:0:0:1]`, `127.1`, and `*.localhost`
     stay under the 80/443 rule; an origin-form `Host: localhost:N` stays
     refused; a selected host still blocks. Credible failure: a widened
     loopback match or a DNS lookup; E2E covers the three happy paths only;
   - *relay cap:* the 33rd loopback relay is refused while a browser
     request still succeeds, and the idle timeout still closes a relay that
     never gets a first response byte. Credible failure: browser traffic
     starved by stuck local streams; E2E cannot hold 32 streams reliably;
   - the existing `SystemProxyConfigurationTests` assertion that
     `ExceptionsList` passes through unchanged is updated, not dropped.
4. **Implement.** Daemon: `ProxySnapshot` gains an exceptions value with an
   opaque unreadable case; `SystemProxyConfiguration` reads and writes it
   under the existing lock, with a leave-untouched target, and verifies the
   whole dictionary; `OwnershipRecord` schema 2 per `D5`, with validity accepting schema 1
   and 2;
   `ProxyOwnershipEngine` applies `D1`, `D2`, and the prepared-baseline
   comparison. Helper: the chain-check overlay adds the three entries, and
   the router and relay implement the loopback relay with its cap, literal
   addresses, request-target `Host`, keepalive, and the post-first-byte idle rule.
   Map the `D4` failure to the existing incompatible-network copy and check
   it in the app.
5. **Local checks.** `swift test` in `macosHelper`, the driver tests, the
   aggregate quality gate, and the package build `posato-control build`
   uses.
6. **E2E after the change (`AC-02`, `AC-03`).** Same VM setup with a
   baseline list containing `*.local` and `169.254/16`:
   - during a session: the reqwest probe, which ignores the exceptions
     list like Codex and so goes through the listener, reaches
     `127.0.0.1`, `localhost`, and `[::1]` on a non-80 port, the server
     classifies the requests as proxied, the posted body arrives, and the
     45-second stream survives (relay); a request to the listener's own port
     is still refused; the `NSURLSession` request reaches them
     directly, as classified by the server, if CFNetwork honors the list
     (exceptions); the selected
     website shows the pause page in Safari; an unrelated website loads;
     the per-service list shows the baseline entries in order followed by
     the three loopback entries;
   - the exact baseline list returns after early end, natural end, a killed
     session helper, and a killed daemon during a session;
   - a baseline without `ExceptionsList` returns without the key;
   - `D2`/`D1`: changing the bypass list during a session ends active
     enforcement, keeps the changed list, and reports `recoveryRequired`,
     with the tuples restored.
7. **Amendment acceptance (`AC-04`)** before step 3, the completed-change
   review, the GitHub Projects mirror update for the revised row, closeout
   of this record, and one wiki-log entry.

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
  by `D5`; R5 by `D6` and the probe step; R6 by the driver step. Recommended items folded:
  applied-list record check, chain-check overlay, ADR 0005 wording, exact
  matching, updated existing test, daemon-kill run, `D4` error mapping.
- **Verdict:** `changes-required` (second pass on bcfc90c, independent
  agent)
- **Critical or Required findings:** Req-1 an all-present baseline was not
  compared, leaving a bypass; Req-2 digest encoding and validity rules
  unspecified; Req-3 relays without idle timeout could exhaust the shared
  connection cap; Req-4 the ADR 0005 amendment did not name the base rules
  it replaces or the exact host matching; Req-5 the probe did not prove it
  goes through the listener; Req-6 no way to copy the probe into the guest;
  Req-7 failure inventory missing; Req-8 brief and roadmap row not amended
  for `D6`.
- **Resolution:** all folded into the amendments, the brief, the roadmap
  row clarification, and steps 1 to 3; the 32-pair relay cap and the idle
  timeout rule are the implementer's choice from Req-3. Recommended items
  folded: literal addresses and original `Host`, the refusal matrix, the
  conditional `*.localhost` claim, the peer-identity residual, the locked
  re-read for the leave-untouched target, and a posted body. 
- **Verdict:** `approved` (confirmation pass, 2026-09-30, independent
  agent): Req-1 to Req-8 and R1 to R6 resolved, no new Critical or Required
  finding. Advisory wording on `Host`, the trailing dot, and schema 1
  acceptance folded.

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
