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

During a session, connections to `localhost`, `127.0.0.1`, and `::1` go
directly instead of through Posato's proxy, and the proxy exceptions return
to exactly their previous value when the session's proxy is restored.

## Boundaries

- Report (`user-confirmed`, 2026-09-30): local Codex MCP connections
  received an empty response and `codex_tui failed to start` during a
  session; a direct connection and `NO_PROXY` worked. `observed` in code: the
  helper leaves the proxy exceptions untouched, and ADR 0005 rejects every
  loopback authority except the pause page.
- Add the three loopback entries to the exceptions of each service Posato
  applies to, keep every existing entry and its order, and add no duplicate.
  Record the previous exceptions with the rest of the owned baseline and
  restore them exactly, including after a crash, a missing service
  (`MACOS-020`), and an update.
- A selected website can never become an exception; the ADR 0005 post-Apply
  chain check still proves that every selected exact domain routes only
  through Posato.
- Propose the ADR 0004 and ADR 0005 amendment before implementation.
- Non-goals: other private ranges or `*.local`, per-application bypass, and
  iOS.

## Acceptance

- `AC-01` — A Tart run on `main` reproduces the failure: a local HTTP
  server on `127.0.0.1` behind the system proxy fails during a session.
- `AC-02` — With the change, the same request and one to `localhost` and
  `[::1]` succeed during the session, while a selected website stays
  blocked and an unrelated one loads.
- `AC-03` — Existing exceptions (for example `*.local`) are kept during the
  session and the exact previous list returns after early end, natural end,
  and restore after a helper crash.
- `AC-04` — The ADR 0004 and ADR 0005 amendment is accepted by the maintainer.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Tart (`--vm primary`), before and after: a loopback HTTP server reached by
  a client that follows system proxy settings, the proxy exceptions read
  with `scutil --proxy` during and after the session, and a blocked and an
  unrelated website.

## Decisions or blockers

- None known beyond the amendment in `AC-04`.
