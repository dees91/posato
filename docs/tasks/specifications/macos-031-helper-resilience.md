# `MACOS-031`: A pause keeps its restrictions when the Mac is busy for a few seconds

- **Review tier:** High-risk
- **Tier reason:** Changes the helper's lease renewal and the daemon's idle
  exit that [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  bounds, where a wrong change leaves the proxy applied without an owner. A
  plan review precedes implementation.
- **Dependencies:** None.
- **Integration group:** PR-MAC-HELPER-RESILIENCE
- **Authority:** [Release roadmap](../release-roadmap.md) revision 31, row
  `MACOS-031`; the maintainer asked for it on 2026-10-25.
- **Record:** [execution record](../executions/macos-031-helper-resilience.md)

## Outcome

A few seconds of load on the Mac no longer ends a pause's restrictions, and
Session never says they are active while nothing is blocked.

## Boundaries

- Start from idea 55 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md): the daemon
  answered one Status in 5.4 s, a lease renewal with its 5 s deadline failed,
  the helper ended itself (`exit(EXIT_FAILURE)` in `HelperTransport.swift`),
  the daemon restored the proxy and exited while a new helper connected, and
  that helper ended too.
- One late renewal or Status answer does not end the helper; a daemon that
  stays unreachable still makes the helper give up, so restrictions never
  outlive their owner. The lease's purpose in ADR 0004 is kept; any change
  to its timing is an ADR amendment the maintainer accepts.
- The daemon does not exit idle while a helper is connecting to it.
- While restrictions do not apply, Session does not show **Session active**
  as if they did.
- Applying again by itself and the red notice belong to `MACOS-029`.
- Non-goals: the poll cadence, iPhone, Linux, Android.

## Acceptance

- `AC-01` — A regression test that runs in seconds reproduces the root
  cause: during a pause with restrictions applied, it delays the daemon past
  the renewal deadline once. It fails on `main` (the helper ends and the
  proxy is restored) and passes with the fix (the helper and the proxy stay,
  and restrictions still change and clear afterwards). It is written and
  shown failing before any fix.
- `AC-02` — A helper that connects while the daemon is about to exit idle
  gets its answer; a test shows the race on `main` first.
- `AC-03` — In the real application, a pause survives a delayed daemon
  without "Restrictions need attention", and with a daemon that stays
  unreachable Session does not claim restrictions are active.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `AC-01` and `AC-02` in a Tart VM against the real signed helper and
  daemon, as the `MACOS-030` harness did: the released 1.5.1 build first
  (failing), then the fixed development package.
- `AC-03` through `posato-control` in a Tart VM, with `observe` and the
  system proxy state.

## Decisions or blockers

- How the test delays the daemon without a test-only switch in the shipped
  daemon is a question for the plan review.
