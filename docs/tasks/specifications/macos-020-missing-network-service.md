# `MACOS-020`: Keep a session truthful when its network service disappears

- **Review tier:** High-risk
- **Tier reason:** Changes how the privileged daemon reconciles its proxy
  ownership record and restores system proxy settings (ADR 0004, ADR 0005).
- **Dependencies:** `MACOS-022` (merged in #107); release 1.3, wave 2.
  Implementation runs after or strictly apart from `MACOS-021`, which also
  touches the helper and proxy.
- **Integration group:** PR-MAC-PROXY-RECOVERY
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `MACOS-020`; maintainer named the row on 2026-09-29.

## Outcome

When the network service that holds Posato's proxy settings disappears
during a session, Posato stops claiming **Restrictions active**, says that
restrictions need attention, and a later session can apply again.

## Boundaries

- `observed` in `QUALITY-010` `M5` (macOS 26 guest, reproduced on a fresh
  clone): after Retry applied the proxy to a second network service,
  deleting that service and re-enabling the original left **Restrictions
  active** with no proxy and no application termination. The ownership record
  still named the deleted service, so every later start reported that
  restrictions may still apply.
- Reconcile a recorded service that no longer exists: clear or rewrite that
  record, and never touch a service Posato does not own or restore a
  baseline Posato did not record.
- Keep the ADR 0004 ownership and restore contract and the ADR 0005
  network-transition policy; propose an amendment if the fix needs one.
- Non-goals: new supported network topologies, VPN or Private Relay
  coexistence (`MACOS-018`), and iOS.

## Acceptance

- `AC-01` — The `M5` reproduction fails on `main`, driven in Tart.
- `AC-02` — With the change, the session shows that restrictions need
  attention instead of **Restrictions active** once the owned service is gone.
- `AC-03` — After that session ends, the ownership record no longer names the
  missing service, and the next session applies and blocks a website.
- `AC-04` — The normal single-service start, end, and Retry paths behave as
  on `main`.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- The `M5` scenario in a fresh Tart clone (`--vm primary`): a second network
  service, Retry, deletion, re-enable, then a new session with an observed
  block, before and after the change.

## Decisions or blockers

- None known. If reconciliation needs an ADR 0004 amendment, it goes to the
  maintainer before implementation.
