# Execution: `TARGETS-003`

- **Brief:**
  [`../specifications/targets-003-macos-application-mapping.md`](../specifications/targets-003-macos-application-mapping.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer`
- **Branch:** `feature/targets-003-macos-application-mapping`
- **Updated:** `2026-08-31`

## Plan

1. Define one redacted common mapping contract, inject platform bindings
   through the existing Metro graphs, and extend target state/UI without
   coupling mapping availability to semantic policy persistence.
2. Add a separate desktop SQLDelight mapping store with bounded validation,
   atomic multi-add, idempotent removal, owner-only filesystem permissions, an
   injected IO dispatcher for all database and helper work, and no synchronized
   or diagnostic representation.
3. Extend the existing signed helper with a capability-negotiated AppKit picker,
   strict Security-framework identity extraction, and a bounded response that
   is never forwarded to the root daemon. Validate the 30-minute selection
   deadline only on the parent/helper pipe while retaining the 120-second
   lifecycle and XPC limit.
4. Add focused common, database, protocol, and helper coverage; update accepted
   design, architecture, provenance, and wiki authorities; then run platform
   verification and independent completed-change review.

TARGETS-003 owns the shared target mapping contract/state/UI, desktop mapping
database and adapter, helper picker operation, ADR 0004 amendment, `DESIGN.md`,
its task records, and relevant macOS/provenance wiki sections. SYNC-002 owns its
sync package and records. Metro graph files, the root quality task, and wiki log
are serialized integration points; actual paths are compared before each write
and before the pull request.

## High-risk plan review

- **Verdict:** `approved after correction`
- **Critical or Required findings:** Keep blocking helper and JDBC work off the
  UI thread; establish and verify owner-only mapping-store permissions; and do
  not broaden the daemon's 120-second protocol bound for the picker.
- **Resolution:** The brief and plan now inject an IO dispatcher across the
  complete desktop adapter, require owner-only directory/database/sidecar
  permissions with fail-closed tests, and define operation- and transport-
  specific deadlines. Parent/helper negotiation requires capability bits `1|2`,
  while helper/daemon XPC keeps bit `1` and rejects picker capability/operation;
  missing-bit and unchanged-daemon negotiation tests are explicit. The
  independent reviewer confirmed that no Critical or Required finding remains.

## Result

- Added a redacted shared mapping contract and independently loaded UI state;
  macOS can choose and remove applications while iOS remains truthful and
  non-interactive.
- Added a separate macOS SQLDelight database with atomic batches, stable
  requirement hashes, restart persistence, corruption detection, and
  owner-only files. Database and helper work run on an injected IO dispatcher.
- Added capability bit `2` and pipe operation `10` for the normal-user AppKit
  picker. Strict all-architecture, non-ad-hoc signature validation remains in
  that helper; selection is rejected by daemon decoding and never reaches XPC.
  The dedicated picker client retires its helper after each result so AppKit
  cannot return to a blocking pipe read while it remains the active process.
- Added shutdown cleanup for an in-flight picker. The JVM shutdown hook closes
  the mapping adapter, the client interrupts the active request without waiting
  on its monitor, and the helper cancels the AppKit panel before exiting.
- Removed all detekt suppressions introduced by this task. Application mapping
  UI, state conversion, and ViewModel operations now occupy focused files, and
  corrupt mapping validation shares one unchanged private failure path.
- Deferred default helper-path discovery until first use, preserving packaged
  signature verification while allowing the documented Gradle desktop shell to
  start outside an application bundle. Mapping load failures now render only
  their retryable notice rather than contradictory confirmed-empty copy.
- Updated the accepted design, ADR amendment, maintained wiki synthesis, and
  PoC provenance without copying feasibility code or machine state.

## Completed-change review

- **Verdict:** `approved after correction`
- **Critical or Required findings:** No Critical finding. Required corrections
  covered transactional failure reporting, Swift sensitive-carrier redaction,
  complete preview states, and physical-Mac verification.
- **Resolution:** Selection and removal now keep mutation plus fallible
  verification inside one transaction; injected in-transaction permission
  failures prove rollback after reopen. Swift normal and reflective strings are
  redacted with synthetic-secret tests. The shared provider now covers every
  mapping rendering branch through both existing previews. The reviewer
  confirmed these three findings resolved. Maintainer-observed physical-Mac
  verification then covered the remaining gate. The final review required one
  categorical privacy wording correction in the execution evidence and
  approved the change after that correction. A follow-up independent review
  approved the active-picker shutdown and loading-state corrections with no
  Critical, Required, Recommended, or Optional finding. The final suppression-
  removal review also approved the focused file split and preserved behavior
  with no finding at any severity.
  A final follow-up review approved lazy helper discovery and load-failure
  rendering with no finding at any severity.
- **Advisory findings:** Sidecar permission coverage should create controlled
  sidecars instead of conditionally checking only artifacts SQLite happens to
  leave behind; this does not expand the current scope automatically.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Plan review | `pass` | Approved after the IO, owner-only storage, and capability/deadline corrections. |
| Focused Kotlin and Swift tests | `pass` | Shared state/contract, real SQLite restart/rollback/corruption/permissions/dispatcher, protocol, helper, and daemon-rejection coverage pass. |
| SQLDelight migration verification | `pass` | Desktop schema baseline `1.db` matches the current create statements. |
| Aggregate quality | `pass` | `./gradlew quality --rerun-tasks` completed after all review corrections, executing all 112 tasks including iOS tests, packaging, lint, analysis, and migration checks. |
| Gradle desktop shell | `pass` | `./gradlew :desktopApp:run` reached a live UI process outside an application bundle without eager helper discovery and terminated cleanly from the invoking terminal. |
| Credential-free iOS host build | `pass` | Generic iOS Simulator `xcodebuild` completed with signing disabled. |
| Diff and privacy checks | `pass` | `git diff --check` and the scoped sensitive-data/path scan found no introduced personal path, credential, key, or token material. |
| Physical-Mac application picker | `pass` | Maintainer-observed multi-select, cancel and reopen, non-ad-hoc and self rejection, restart persistence, individual removal, retained mapping cleanup, keyboard operation, VoiceOver navigation, and quitting Posato with an active picker passed on one Apple silicon Mac. Initial runs found and corrected accessory-policy handling, the default picker directory, one-shot AppKit helper lifecycle, and active-picker shutdown cleanup. |
| Independent completed-change review | `pass` | Final review approved the one-shot picker lifecycle and packaging boundary after one Required privacy wording correction. Follow-up reviews approved the active-picker shutdown, loading-state, suppression-removal, lazy helper-discovery, and load-failure-rendering corrections with no finding at any severity. |

## Blockers and accepted risks

- Initial physical execution observed that an already-accessory `LSUIElement`
  helper can return `false` from `setActivationPolicy(.accessory)`; treating
  that return as picker rejection prevented presentation. The helper now sets
  the idempotent policy without interpreting an unchanged value as failure.
- Physical execution also observed that leaving the dedicated picker helper
  alive after `NSOpenPanel.runModal()` returned caused beachballs and blocked
  later system panels because its main thread resumed a blocking pipe read.
  The client now terminates the picker helper after every decoded result;
  repeated cancel and reopen, Posato, unrelated system applications, and later
  panels remained responsive after correction.
- Physical shutdown testing observed that force-terminating a picker helper can
  leave its out-of-process AppKit panel visible. Posato now invokes mapping
  cleanup from a JVM shutdown hook; the helper handles termination by cancelling
  the panel, while the client retains a one-second forced-exit fallback. Quitting
  Posato then closed both windows together, left no child process, and preserved
  later cancel and reopen behavior.
- The physical flow used a locally development-signed gate artifact. The
  repository packaging pipeline still produces a JVM runtime whose nested
  signing and hardened-runtime JIT configuration cannot launch unchanged on
  this Mac. The local bottom-up re-signing workaround is physical feature
  evidence only, not release-signing, notarization, or distribution evidence.
- Hosted CI remains paused through 2026-09-05. Aggregate quality and the iOS
  host build pass locally; rerun affected verification after any correction
  caused by the physical checklist.

## Final

- **Status:** `complete`
- **Outcome:** implementation, affected verification, physical-Mac gate, and independent review are complete; the branch is ready for push and pull request
