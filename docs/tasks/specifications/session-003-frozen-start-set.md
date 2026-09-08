# `SESSION-003`: Keep the active session's frozen start set truthful across a relaunch

- **Review tier:** `standard`
- **Tier reason:** Local, reversible persistence change behind an existing
  screen. It adds one schema migration and one durable field, so it needs a
  record and one independent completed-change review, but no new privilege,
  process, or platform boundary.
- **Dependencies:** completed `SESSION-001` (session table, store, terminal
  marker), `SESSION-002` (frozen set, enforcement coordinator, active summary),
  `TARGETS-001`, `TARGETS-003`, `TARGETS-004` (the selected items)
- **Integration group:** `PR-SESSION-FROZEN-SET`
- **Authority:** MVP roadmap revision 11 (`SESSION-003`, wave P3/W3.5a),
  recorded as follow-up `R5` in the
  [`SESSION-002` execution record](../executions/session-002-local-session.md),
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  ("a session operation does not embed or freeze a policy snapshot" governs the
  wire operation; `SESSION-001` owns the local session row and `SYNC-012`
  reconciles from it),
  the threat model `A-02` (no usage history) and `A-03` (opaque mappings stay
  in app-private or platform-protected storage and are never synchronized),
  and `DESIGN.md` "Session" (the active surface states what is actually paused)

## Outcome

After the application is relaunched during an active session, the active
summary shows the set that was frozen when that session started, not the
current Paused-items policy, so the next-pause copy is true for the whole
session.

## Boundaries

- Persist the frozen start set with the session row it belongs to, in the
  app-private database, written in the same transaction that starts the
  session and cleared with it. Add the next SQLDelight migration; the schema is
  at version 5 today.
- The coordinator reads the persisted set on `settle` instead of re-deriving it
  from live targets. The three `reconcile` branches and the apply-failure path
  are the sites that re-derive today.
- Non-goal: session history. Exactly one session's set exists at a time, with
  no observed timestamps and no accumulation.
- Non-goal: the wire format. The persisted set is local only. It is never
  authored into a `session-start` operation, never synchronized, never
  diagnosed, and never used for policy resolution, which stays current-state
  based per ADR 0006.
- Non-goal: changing how enforcement chooses what to apply on Resume or on the
  silent iOS re-converge. Per decision `D2` it keeps applying the current set;
  only the copy changes.
- This task owns `shared/**/feature/session/**`, the session SQLDelight schema
  and its migration. It must not touch `feature/sync/**`, the dependency
  injection graphs, `tools/posato-control/**`, or the enforcement adapters,
  which belong to `SYNC-009` and `QUALITY-005` in this wave.

## Acceptance

- `AC-01` — Starting a session persists the frozen set atomically with the
  session row; ending early, expiry, and starting a new session leave no stale
  set behind.
- `AC-02` — A relaunch during an active session shows the persisted frozen set
  in the active summary and in the Selected items panel, while later
  Paused-items edits change neither until the next session.
- `AC-03` — The migration preserves existing sessions, policies, mappings, and
  replica rows; an active session created before the upgrade shows a truthful
  fallback rather than an invented set.
- `AC-04` — No persisted set value appears in any `toString()`, log,
  diagnostic, or committed evidence, and nothing is written outside the
  app-private database.

## Verification

- `commonTest` over the store and the coordinator: freeze on start, read back
  after a simulated relaunch, no leak into the next session, and the
  pre-upgrade fallback.
- A focused migration check that an existing database at the current version
  upgrades with its session, policy, and replica rows intact. Automated
  migration verification is currently off in the build, so this check is
  explicit rather than inherited.
- Driver row on the desktop target: start a session with a known set, edit
  Paused items, relaunch, and read the summary. Evidence under the ignored
  `build/verification/`.
- `./gradlew quality` with no new suppression.

## Decisions or blockers

- `D1` decided (`user-confirmed`, 2026-09-08): the persisted set contains the
  exact domains and the application count, mirroring what the summary shows.
  The 64-hex opaque mapping identifiers are not copied out of their own
  app-private store into the shared database, so the `A-03` boundary is
  unchanged and the threat model needs no amendment.
- `D2` decided (`user-confirmed`, 2026-09-08): Resume on macOS and the silent
  iOS re-converge keep applying the current set, as `SESSION-002` accepted. The
  copy is corrected for the post-relaunch case so it no longer promises that
  Paused-items edits wait for the next pause when they do not. Enforcement
  therefore needs no mapping identifiers from the persisted set.
- Consequence to keep visible in the review: after a relaunch the summary shows
  the frozen set while enforcement applies the current set. Both statements are
  true, and the corrected copy must make that difference legible rather than
  hide it.
