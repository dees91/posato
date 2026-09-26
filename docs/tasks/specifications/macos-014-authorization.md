# `MACOS-014`: Reduce repeated administrator prompts on the Mac

- **Review tier:** `high-risk`
- **Tier reason:** The task replaces the one-use Apply authorization of [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md) with a standing grant for a system-wide proxy change. It touches the root daemon, the Authorization Services rule, the privileged IPC contract, and removal. The roadmap requires an independent security review of the ADR 0004 revision before implementation, and the plan and the completed change are reviewed independently.
- **Dependencies:** `MACOS-012` and `MACOS-013` (ADR 0009, merged); release 1.2, wave R1.2/W2. It shares the enforcement and This Mac surfaces with `NOTIFY-001`, so it is serialized with that task.
- **Integration group:** `PR-MAC-AUTHORIZATION`, milestone `1.2.0`.
- **Authority:** [release roadmap](../release-roadmap.md); [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md) (authorization of Apply and cleanup, installation, removal); [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md) (the `MACOS-014` constraints); the [threat model](../../security/apple-mvp-threat-model.md) (`TB-04`, `T-07`, `T-08`, `T-15`, `R-02`); [`DESIGN.md`](../../../DESIGN.md) (This Mac); [`PRIVACY.md`](../../../PRIVACY.md).

## Outcome

After a one-time administrator opt-in, a person who starts or resumes a session on this Mac no longer gets an administrator prompt each time. The opt-in has a clear way to take it back, and the privileged daemon still accepts only authenticated, narrowly scoped requests from the signed Posato helper.

## Boundaries

- Write the ADR 0004 revision first. It must state what the opt-in grants, to which user and signed peers, and for which operations. It must cover how the grant is stored, verified, and revoked (a switch in This Mac, and **Remove from this Mac**), and what still fails closed. An independent security review of that revision must pass before any code.
- Keep the daemon's allowlist, fixed proxy values, durable ownership, lease, reconciliation, and cleanup unchanged. The grant authorizes Apply only. Restore and cleanup already need no bearer material.
- Keep the ADR 0009 rule: the menu, a login launch, and a synchronized session never apply silently unless this task's security review explicitly accepts that case. The default keeps Apply behind the person's own action.
- Non-goals: administrator resistance, App Sandbox (`MACOS-019`), notifications (`NOTIFY-001`), schedules (`SCHEDULE-001`), iOS.

## Acceptance

- `AC-01` — The ADR 0004 revision is written out and has passed an independent security review. It names the threats it adds and how they are handled, including another local user, a compromised non-admin process, replay, grant theft, and stale grants after removal or reinstallation.
- `AC-02` — After the opt-in, starting a session and resuming after relaunch, wake, or a login launch apply without an administrator prompt when the person acts. Without the opt-in, behavior is unchanged.
- `AC-03` — Turning the opt-in off and **Remove from this Mac** both revoke the grant. The next Apply then prompts or fails closed.
- `AC-04` — The failure modes E2E cannot reach have isolated tests written failing first: grant parsing and binding, revocation, and wrong peer or user.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- E2E in Tart clones through `posato-control` with `vm prompt admin`:
  - the opt-in;
  - a session start with no prompt;
  - resume after relaunch and after a login launch;
  - revocation by the switch and by removal, with the next Apply prompting;
  - `observe --expect blocked` and `--expect allowed`.
- Isolated daemon and helper tests for the AC-04 failures, and the complete `./gradlew quality` after the last correction.

## Decisions or blockers

- `open` (maintainer): the grant mechanism. Options: a standing grant recorded by the root daemon for the enabling user; an Authorization Services rule with a longer credential lifetime; or authenticating as the session user instead of an administrator.
- `open` (maintainer): the default and placement of the opt-in (This Mac options, off by default is proposed), and which resume paths it covers.
- The security review of the ADR 0004 revision gates implementation.
