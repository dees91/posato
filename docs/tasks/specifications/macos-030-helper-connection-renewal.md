# `MACOS-030`: The Mac keeps talking to its helper for as long as Posato runs

- **Review tier:** High-risk
- **Tier reason:** Changes the parent-helper pipe and the helper's daemon
  connections that [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  bounds, and the update admission of
  [ADR 0008](../../decisions/0008-macos-update-delivery.md). A plan
  review precedes implementation.
- **Dependencies:** None.
- **Integration group:** PR-MAC-HELPER-CONNECTION
- **Authority:** [Release roadmap](../release-roadmap.md) revision 29, row
  `MACOS-030`; the maintainer asked for the complete fix on 2026-10-23.
- **Record:** [execution record](../executions/macos-030-helper-connection-renewal.md)

## Outcome

A pause, a schedule, and This Mac keep working however many operations
Posato sends to its helper, restrictions end when their pause ends, and an
update is refused only while restrictions are held.

## Boundaries

- Start from idea 54 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md): after 256
  operations on one helper process, every request fails before it is sent
  and the process is never replaced.
- Keep ADR 0004's bound of 256 operations per connection, its strictly
  increasing sequences, and its signing requirements; a connection is
  renewed, never extended. The maintainer prefers no ADR change unless one is
  necessary (2026-10-23).
- A renewal during a pause keeps the helper process, its proxy relay, its
  lease, and the daemon connection that owns the applied mutation.
- Update admission keeps refusing during a manual pause and while a
  scheduled pause holds restrictions; ADR 0008's cleanup is not relaxed.
- Non-goals: the poll cadence, the iPhone, Linux, Android, and helper or
  daemon logging.

## Acceptance

- `AC-01` — A test that sends more than 256 operations to the real helper in
  a Tart VM, with restrictions applied, fails on `main` and passes with the
  fix; afterwards restrictions still apply, change, and clear.
- `AC-02` — A manual pause in the real application lasts beyond 64 minutes
  without "Restrictions need attention", and restrictions end with it.
- `AC-03` — With a schedule running that holds no restrictions, **Check for
  Updates** installs the update; while a pause holds restrictions it is
  refused with wording that fits manual and scheduled pauses.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `AC-01` through the operation-count harness in a Tart VM against the
  released 1.5.0 helper (before) and the fixed development package (after).
- `AC-02` as a two-hour Tart session with `observe` and the proxy state.
- `AC-03` through `SqlUpdateMaintenanceStoreTest` and
  `MaintenanceAdmissionTest`, each failing first: the admission rule and the
  replace permit are state that no update run can reach on demand, and the
  Sparkle path itself is unchanged and runs again in the next release row.
  Amended 2026-10-23 by the implementer under the maintainer's delegation
  (`agent-delegated`) from a candidate-feed run in Tart.

## Decisions or blockers

- None.
