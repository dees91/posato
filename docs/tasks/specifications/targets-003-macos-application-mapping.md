# `TARGETS-003`: Associate device-local macOS applications

- **Review tier:** `high-risk`
- **Tier reason:** The change handles sensitive application identity, durable
  local persistence, native code-signing validation, and authenticated helper
  IPC.
- **Dependencies:** completed `TARGETS-002` and `MACOS-003`
- **Integration group:** `PR-MAC-MAPPING`
- **Authority:** `TARGETS-003` in the accepted MVP roadmap, ADR 0004, ADR 0006,
  the threat model, diagnostics policy, and `DESIGN.md`

## Outcome

A person can choose several signed macOS applications, review and remove their
device-local mappings, and retain those mappings through application-group
rename, removal, and recreation.

## Boundaries

- Keep common code semantic: expose only a redacted mapping identifier and
  display name behind one injected contract. Keep AppKit, Security, paths, and
  designated requirements in the desktop/helper implementation.
- Store mappings in a separate macOS-only SQLDelight database that is never
  synchronized. Create and verify its directory as owner-only and its database
  and SQLite sidecars as owner read/write only; fail closed rather than reset or
  broaden permissions. Never send application identity to the root daemon or
  product diagnostics.
- Reuse the existing signed normal-user helper and bounded pipe protocol for a
  native multi-selection picker and strict non-ad-hoc signature validation.
- Allow valid Apple system and third-party applications, but reject Posato.
  Invalid input or a failed batch preserves every prior mapping.
- Defer iOS selection to TARGETS-004 and process observation, matching, and
  termination to MACOS-005. Add no new process, dependency version, icon model,
  history, search, or enforcement behavior.
- Keep `.research/blocker` read-only. Use revisions `bcdc8ce` and `4c13ade` only
  as feasibility provenance; copy no PoC implementation.

## Acceptance

- `AC-01` — The shared UI loads mappings independently of semantic policy,
  supports multi-select and individual removal on macOS, and remains truthful
  and non-interactive for local application selection on iOS.
- `AC-02` — A successful batch commits atomically and survives restart;
  cancellation, invalid signatures, Posato, capacity, corruption, and storage
  failure preserve the prior valid snapshot.
- `AC-03` — Mapping identity uses a bounded binary designated requirement, not
  a path or bundle identifier, and every sensitive carrier is redacted by
  default.
- `AC-04` — The picker operation is capability-negotiated, bounded, handled
  only by the normal-user helper, and never forwarded over daemon XPC.
- `AC-05` — Existing exact-domain, application-policy, helper-lifecycle, iOS,
  packaging, and migration behavior remains valid.
- `AC-06` — Helper IPC and every mapping-database operation execute on an
  injected IO dispatcher rather than the Compose/ViewModel thread.

## Verification

- Run shared JVM and iOS Simulator state tests, real desktop SQLDelight tests,
  Kotlin/Swift protocol and helper tests, migration verification, packaging,
  aggregate quality, and the credential-free iOS host build.
- Prove dispatcher use and owner-only directory, database, journal, WAL, and
  shared-memory permissions with focused adapter and real-filesystem tests.
- Inspect the signed packaged macOS flow manually for multi-select, cancel,
  rejection, persistence, removal, retained mappings, keyboard, and VoiceOver.
- Verify the shared policy database baseline, private-data scans, concurrent
  SYNC-002 write surfaces, `.research/blocker`, and `git diff --check`.

## Decisions or blockers

- The mapping key is SHA-256 of the exact binary designated requirement. The
  store accepts at most 64 mappings, 256 UTF-8 display-name bytes, and 4096
  requirement bytes per mapping.
- Duplicate valid selections are idempotent; any invalid member rejects the
  complete batch. No blocker is known before implementation.
- The 30-minute selection deadline is valid only on the authenticated parent to
  normal-user-helper pipe. Lifecycle operations and every daemon XPC frame keep
  the existing 120-second maximum; operation `10` is rejected by the daemon
  before payload handling or side effects.
- Parent to helper negotiation offers and requires lifecycle bit `1` plus
  picker bit `2`. Helper to daemon negotiation offers and requires lifecycle
  bit `1` only; XPC never advertises or accepts picker bit `2` or operation
  `10`. Kotlin and Swift tests cover a parent missing bit `2`, a helper welcome
  missing bit `2`, and unchanged daemon compatibility with bit `1`.
