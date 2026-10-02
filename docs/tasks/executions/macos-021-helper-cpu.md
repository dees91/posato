# Execution: `MACOS-021`

- **Brief:** [`macos-021-helper-cpu.md`](../specifications/macos-021-helper-cpu.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude Code
- **Reviewer:** independent Claude Code agent (completed change, one pass)
- **Branch:** `fix/macos-021-helper-cpu`
- **Updated:** 2026-10-02

## Cause

`observed` on 2026-10-02:

- **The spike recurs.** It is not a one-off load on the host. The
  maintainer's own Mac, on release 1.2.0 (27) during a session, showed the
  normal-user helper at 100% of one core: 43 minutes of CPU in 44 minutes.
  One thread was spinning, about a third of it in system time, while the
  helper relayed seven browser tunnels. This was read with `ps` and `lsof`
  only; the installed copy was not touched.
- **`DirectTCPConnection` never suspends its dispatch sources.** It resumes
  a read source and a write source for the upstream socket once and keeps
  both running. A connected socket is almost always writable, so the write
  source fires again at once after a handler that finds nothing to send.
  The read source does the same while bytes wait and no reader is pending.
  Every open upstream connection therefore keeps the proxy queue busy. The
  code is the same in `v1.2.0` and on `main`.
- **System services start it without any browsing.** In a fresh Tart clone
  the first session already relayed four to nine tunnels from system
  services to Apple and CDN hosts on port 443. That explains every earlier
  observation:
  - the `MACOS-012` clone burned CPU until those connections closed;
  - the next session in that clone, with no connections open, used 0.2%;
  - a Mac with long-lived browser connections never stops.

## Plan

1. Reproduce on `main` in a fresh clone (`vm onboard`, a set with
   `example.com`, a 60-minute session, `observe`, `resources`).
2. Run each source only while it has work: suspend the write source when
   nothing waits to be sent and the read source when no read is pending.
   Resume a suspended source before cancelling it, because releasing a
   suspended source crashes libdispatch.
3. Repeat the same protocol in a new fresh clone. Prove that relaying under
   load still works: a large download, a slow reader, an upload, and plain
   HTTP.

## Result

- `DirectTCPConnection` tracks whether each source is suspended and updates
  it after every read or write attempt and on configure. `cancel()` resumes
  before it cancels. The static socket helpers moved into an extension in
  the same file to keep the class under the SwiftLint type body limit.
- No new isolated test. The E2E `resources` measurement shows the failure
  directly. An isolated CPU-time test would be timing-sensitive and, written
  now, would follow the implementation.
- `user-confirmed` (2026-10-02): the fix ships in 1.3; there is no 1.2.1.
  `DOCS-004` should name the fix in the 1.3 release notes.

## Completed-change review

- **Verdict:** `approved`.
- **Critical or Required findings:** none. The reviewer confirmed that every
  call runs on the proxy's serial queue and that no suspend or resume goes
  unbalanced, with nested completions and cancellation included.
- **Resolution:** two Recommended findings applied. The load run now drives
  both EAGAIN resume paths. The wiki claims were checked against the
  600-second result.

## Verification

Line `primary`, a fresh clone for each column, the same protocol, with
evidence in the ignored `build/verification/macos-021/`.

| Check run | Result | Evidence |
| --- | --- | --- |
| Before, `main` 7e67da6: `resources --seconds 60` right after `observe` | fail | helper 59.5 s of CPU in 60 s; 4 tunnels open at minute 5 |
| Before: `resources --seconds 600`, next 10 minutes | fail | helper 303.0 s, about 50% of a core, falling as tunnels closed |
| After: `observe` `http://example.com` blocked, `https://www.wikipedia.org` allowed | pass | `paused` and `loaded` |
| After: `resources --seconds 60` | pass | helper 0.19 s with 9 tunnels open |
| After: `resources --seconds 600`, next 10 minutes | pass | helper 2.0 s, 0.3% of a core |
| After: 16 MiB upload through a loopback `CONNECT` to a server reading 16 KiB every 5 ms | pass | all bytes, SHA-256 equal; the send buffer must fill, so the write source resumes after EAGAIN |
| After: 32 MiB download from a server writing 64 KiB every 2 ms to a client reading 32 KiB every 3 ms | pass | all bytes, SHA-256 equal; read EAGAIN and client backpressure |
| After: 20 MB through an internet tunnel and 5 MB over plain HTTP | pass | `200` and full size |
| After: helper CPU across the load run, then 30 s idle | pass | 0.45 s for about 70 MB relayed, then 0.01 s |
| `:macosHelper:swiftTest`, `swiftFormatCheck`, `swiftLintCheck` | pass | 227 tests |

## Blockers and accepted risks

- Activity Monitor on the maintainer's Mac also labelled the helper "Not
  Responding". That label is `open`: no check here shows whether it follows
  from the spin or from a helper that loads AppKit without an event loop.

## Final

- **Status:** `done`
- **Outcome:** met. `AC-01`: the first 15 minutes of a first session were
  measured before and after, with a blocked and an unrelated site checked.
  `AC-02`: the spike recurred, its cause is named, and the fix keeps the
  helper under 1% of a core in the same protocol.
