# `MACOS-024`: Keep loopback connections off the session proxy

- **Review tier:** High-risk
- **Tier reason:** Changes what the privileged daemon writes to and restores
  in SystemConfiguration (ADR 0004) and the proxy routing contract
  (ADR 0005).
- **Dependencies:** `MACOS-022` (merged in #107); release 1.3, wave 2. Runs
  after `MACOS-020` (merged in #116), which changed the same ownership
  engine, and apart from `MACOS-021`.
- **Integration group:** PR-MAC-LOOPBACK-EXCEPTIONS
- **Authority:** [Release roadmap](../release-roadmap.md) revision 14, row
  `MACOS-024`; idea 23; maintainer named the row on 2026-09-30.

## Outcome

During a session, connections to `localhost`, `127.0.0.1`, and `::1`
succeed: clients that honor the proxy exceptions connect directly, and
clients that ignore them go through a loopback-only relay in Posato's
listener. The proxy exceptions return to exactly their previous value when
the session's proxy is restored.

## Boundaries

- Report (`user-confirmed`, 2026-09-30): local Codex MCP connections
  received an empty response and `codex_tui failed to start` during a
  session; a direct connection and `NO_PROXY` worked. `observed`: the helper
  leaves the proxy exceptions untouched, the listener forwards HTTP only to
  port 80 and `CONNECT` only to port 443, and Codex 0.159.2 reads no
  exceptions list.
- **Exceptions.** Add the three loopback entries to the exceptions of the
  service Posato applies to, keep every existing entry and its order, and
  add no duplicate. Record only digests and the appended suffix with the
  owned baseline and restore the list exactly, including after a crash and
  a missing service (`MACOS-020`). An update is blocked while Posato owns
  settings, so update compatibility means a version 1 record read by the new
  daemon and is unit-tested only.
- **Relay.** Relay absolute-form HTTP and `CONNECT` to the three exact hosts
  on any port except the listener's own, with no DNS or hosts-file lookup, a
  cap of 32 loopback relay pairs, and no idle timeout once the stream is
  established.
- A selected website can never become an exception or a loopback relay; the
  ADR 0005 post-Apply chain check still proves that every selected exact
  domain routes only through Posato.
- Propose the ADR 0004 and ADR 0005 amendment before implementation.
- Non-goals: other private ranges, other loopback addresses, `*.localhost`
  and `*.local`, per-application bypass, and iOS.

## Acceptance

- `AC-01` — A Tart run on `main` reproduces the failure with a probe on
  Codex's HTTP stack that ignores the exceptions list: a request to a local
  HTTP server on `127.0.0.1` on a port other than 80 fails during a session,
  also when the baseline exceptions already contain the three loopback
  entries.
- `AC-02` — With the change, the same probe reaches `127.0.0.1`,
  `localhost`, and `[::1]` during the session, a posted body arrives, and a
  stream held past 30 seconds survives; the listener's own port stays
  refused; a selected website stays blocked and an unrelated one loads.
- `AC-03` — Existing exceptions (for example `*.local`) are kept during the
  session and the exact previous list returns after early end, natural end,
  a killed session helper, and a killed daemon.
- `AC-04` — The ADR 0004 and ADR 0005 amendment is accepted by the
  maintainer.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Tart (`--vm primary`), before and after: a loopback HTTP server and the
  reqwest probe copied into the guest, the per-service exceptions list read
  and set through the `posato-control` extension, `scutil --proxy` during
  and after the session, a killed helper and daemon, and a blocked and an
  unrelated website.

## Decisions or blockers

- Decisions D1 to D7 are recorded in the
  [execution record](../executions/macos-024-loopback-exceptions.md);
  the amendment was accepted on 2026-09-30 (`AC-04`).
