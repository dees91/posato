# Execution: `MACOS-020`

- **Brief:** [`macos-020-missing-network-service.md`](../specifications/macos-020-missing-network-service.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent Claude Code agents (plan and completed change)
- **Branch:** `fix/macos-020-missing-network-service`
- **Updated:** 2026-09-30

## Cause

`inferred` from code, to be confirmed by `AC-01`:

- `SystemProxyConfiguration.proxyProtocol` throws `protocolConfiguration`
  when `SCNetworkServiceCopy` finds no service for the recorded identifier.
- `ProxyOwnershipEngine.maintain`, `restore`, and `reconcile` read that
  snapshot before writing any phase, so a deleted recorded service leaves the
  record at `applied` forever. The daemon's lease loop retries the failing
  restore every second.
- The session helper exits when its renewal fails, which stops the listener
  and application termination. The application's 15-second status poll then
  respawns the helper, and the daemon's `status` answers from the stale
  record: `applied`. The poll keeps **Restrictions active**.
- At session end, restore fails with `recoveryRequired`, so the application
  shows `CLEAR_FAILED` ("Restrictions may still apply"), and every later
  apply conflicts with the stale record.

The application already maps a `status` of `idle` during an active session
to `APPLY_FAILED` ("Restrictions need attention" with Retry, no automatic
reapply on the desktop). The fix therefore belongs in the daemon's ownership
engine; no Kotlin change is planned.

## Plan

1. Reproduce `M5` on `main` in a fresh `primary` Tart clone (`AC-01`).
   Extend `posato-control vm network` with named-service operations that
   create a service on the guest's interface, enable or disable one service,
   and remove it, using the guest administrator password as the existing
   `--state` form does. Drive: start a session that blocks a website; create
   a second service and disable the original so the second becomes primary;
   observe **Restrictions need attention**; Retry so the proxy applies to the
   second service; remove the second service and enable the original; wait
   past two status polls. Assert the stale **Restrictions active** badge,
   `scutil --proxy` without the loopback proxy, and after ending the session
   **Restrictions may still apply** plus a failing next start.
2. Write the failing engine regressions (Swift Testing, in-memory
   configuration) before changing the engine. Isolated coverage is kept
   because E2E cannot reliably produce a recorded service that exists but
   whose proxy protocol cannot be read, and the credible failure is a fix
   that deletes the record in that case too:
   - recorded service absent: `restore` and `reconcile` return `idle` and
     remove the record for `prepared`, `applied`, `restorePending`, and
     `recoveryRequired` records, `maintain` does so for an `applied` record,
     and a new `apply` then succeeds. E2E reaches only the `applied` phase
     with a primary service present; the other phases and absence with no
     primary service cannot be produced reliably in a VM;
   - recorded service present but unreadable (the fake throws
     `SystemProxyConfigurationFailure.protocolConfiguration`): the record
     stays and the error still surfaces. The credible regression is a fix
     that deletes the record on any read failure.
3. Implement:
   - `ProxyConfigurationAccess` reports an absent recorded service as its own
     outcome. `SystemProxyConfiguration` confirms absence from the raw
     preferences under the exclusive `SCPreferences` lock, taken before any
     read and followed by `SCPreferencesSynchronize`: no
     `/NetworkServices/<id>` value and no `/Sets/*/Network/Service/<id>`
     link. `kSCStatusNoKey` from `SCNetworkServiceCopy` is not used as the
     discriminator (`observed` in configd `SCNetworkServiceCopy`: it also
     returns that status for a service without an `Interface` entity or a
     PPTP service, which can still hold a `Proxies` dictionary). A failed
     lock stays a transient error and never counts as absence.
   - The dynamic store must not still carry the applied tuple: if
     `Setup:/Network/Service/<id>/Proxies` exists with the Posato-applied
     tuple, the record is kept and the lease loop retries.
   - The lock does not cover `persistence.remove()`; this is acceptable
     because service identifiers are generated UUIDs that are not reused.
   - The engine's shared snapshot read treats a confirmed absent recorded
     service as "no Posato-owned tuple remains": it removes the record and
     returns `idle`. It never touches another service and never restores a
     baseline onto another service.
   - Entry points: `restore` and `reconcile` (daemon start, Enable, Repair,
     lease-expiry cleanup, connection invalidation) clear the stale record;
     Enable and Repair, which failed permanently before, now recover.
     `maintain` reaches it for an `applied` record while a primary service
     exists; other cases reach it through the lease loop's `restore`.
     `apply` and `verifyApplyOwnership` keep their conflict behavior; the
     stale record is cleared first by daemon start or the lease loop.
4. Rerun the `M5` drive on the change (`AC-02`, `AC-03`): after removal and
   re-enable, the next poll shows **Restrictions need attention** with Retry;
   Retry or a new session applies on the original service and blocks the
   website; ending leaves `scutil --proxy` clean.
5. Upgrade recovery: install the fixed build over the stuck state the
   `AC-01` run on `main` leaves behind and confirm that the daemon's start-up
   reconcile clears the record with no manual step (the path for people
   already stuck on 1.2).
6. Regression drive of the normal paths (`AC-04`): single-service start,
   block, early end, and Retry after a disable/re-enable of the original
   service.
7. Quality: `swift test` for `macosHelper`, the aggregate quality gate, and
   `posato-control` tests for the new command.
8. ADR 0004 records no direct `Applied`/`Prepared` -> `Idle` transition and
   ADR 0005 routes an unreconcilable service to `recoveryRequired`.
   `user-confirmed` (2026-09-30): ADR 0004's ownership section gains one
   clarifying sentence in this pull request: a recorded service that
   SystemConfiguration preferences confirm absent holds no Posato-owned
   tuple, and its state is deleted without restoring a baseline elsewhere.
   Record the conclusion in
   `docs/wiki/topics/macos-enforcement.md` (replace the open question) and one
   `docs/wiki/log.md` entry at closeout.

## High-risk plan review

- **Verdict:** changes-required (independent agent, 2026-09-30)
- **Critical or Required findings:** (1) `kSCStatusNoKey` does not prove
  that the service is gone; (2) the ADR wording conflicts with the "no
  amendment" conclusion and needs a maintainer decision.
- **Resolution:** (1) absence confirmed from raw preferences paths under the
  lock, plus the dynamic-store check (step 3); (2) maintainer chose the
  ADR 0004 clarification (step 8). Recommended findings on per-phase tests, entry points, and
  upgrade recovery adopted (steps 2, 3, 5).

## Result

- `observed`: the cause above is confirmed; `AC-01` reproduced every symptom
  on `main` code in Tart.
- `ProxyOwnershipEngine` reads the recorded service through one path; when
  that read fails and `serviceIsConfirmedAbsent` holds, `restore`,
  `reconcile`, and `maintain` delete the record and return `idle`. Every other
  read failure still surfaces. `SystemProxyConfiguration` confirms absence
  from the raw preferences under the lock and from the dynamic store, stricter
  than the plan: any `Setup:` proxy entity for the service, not only the
  applied tuple, keeps the record.
- No Kotlin change: the existing poll turns the daemon's `idle` into
  **Restrictions need attention** with Retry.
- `posato-control vm network` gained `--service <name> --action
  create|enable|disable|remove [--device]`.
- ADR 0004 gained the accepted clarification; the wiki topic records the
  result and the upgrade finding.
- Upgrade finding (`inferred`, recorded in the wiki as `open`): a Mac already
  stuck on 1.2 keeps its stuck daemon until a restart, and in-app update
  admission likely refuses on the stale `applied` status (`FOREIGN_LEASE`).
  Handling that for existing 1.2 installs is outside this brief.

## Completed-change review

- **Verdict:** approved (independent agent, 2026-09-30)
- **Critical or Required findings:** none
- **Resolution:** adopted: missing `Sets` counts as a link; a failing
  absence check keeps the original read error; the `kSCStatusNoKey` note is
  its own `observed` wiki bullet; the Duplicate Service edge case is an
  `open` wiki item. Verification was rerun afterwards.
- **Advisory findings:** `--device` is ignored outside `--action create`;
  left as is.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `AC-01` on `main` code: session, second service, Retry, remove it, re-enable Ethernet | pass (reproduced) | **Restrictions active** with no proxy and `example.com` loading (run `20260930-111844-61eb`); end showed **Restrictions may still apply**; the next start kept it and the site loaded |
| Engine regressions before the fix (`swift test --filter "ServiceRemoved\|ServiceUnreadable"`) | fail as expected | 5 issues for the absent service; the unreadable guard passed |
| `swift test` in `macosHelper` after the fix | pass | 201 tests |
| Upgrade over the stuck state: fixed package synced, VM restarted, **Resume restrictions** | pass | stuck daemon kept running until the restart; afterwards Resume applied and `observe` returned `paused` |
| `AC-02` on the fix, twice | pass | **Restrictions need attention** with Retry after the removal (run `20260930-112843-bf0a`); the daemon exited idle |
| `AC-03` on the fix: end without Retry, then a new session | pass | no clear failure, `scutil --proxy` without HTTP(S) proxies, site `loaded`; the next session showed **Restrictions active** and `observe` returned `paused` |
| `AC-04`: single-service start, block, end, and Retry after a service change | pass | start and end three times, Retry twice, each with the matching `observe` result |
| Final build after review corrections: full `M5` drive, end without Retry, next session (fresh restart of the same guest) | pass | same results as above; the daemon had exited idle after the removal (run `20260930-115347-248e`) |
| `./gradlew quality` after the last correction | pass | first runs caught SwiftLint length limits and ktlint wrapping, fixed in source |

## Blockers and accepted risks

- A Mac already stuck on 1.2 recovers only once the fixed daemon runs, which
  needs a restart; in-app update from that state is likely refused
  (`inferred`). `user-confirmed` (2026-09-30): the 1.3 release notes
  (`DOCS-004`) carry a manual-download-and-restart hint; no code change.

## Final

- **Status:** `done`
- **Outcome:** met: `AC-01` to `AC-04` pass in Tart.
