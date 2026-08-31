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
- Updated the accepted design, ADR amendment, maintained wiki synthesis, and
  PoC provenance without copying feasibility code or machine state.

## Completed-change review

- **Verdict:** `changes required`
- **Critical or Required findings:** No Critical finding. Required corrections
  covered transactional failure reporting, Swift sensitive-carrier redaction,
  complete preview states, and physical-Mac verification.
- **Resolution:** Selection and removal now keep mutation plus fallible
  verification inside one transaction; injected in-transaction permission
  failures prove rollback after reopen. Swift normal and reflective strings are
  redacted with synthetic-secret tests. The shared provider now covers every
  mapping rendering branch through both existing previews. The reviewer
  confirmed these three findings resolved. Physical-Mac verification remains
  open.
- **Advisory findings:** Sidecar permission coverage should create controlled
  sidecars instead of conditionally checking only artifacts SQLite happens to
  leave behind; this does not expand the current scope automatically.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Plan review | `pass` | Approved after the IO, owner-only storage, and capability/deadline corrections. |
| Focused Kotlin and Swift tests | `pass` | Shared state/contract, real SQLite restart/rollback/corruption/permissions/dispatcher, protocol, helper, and daemon-rejection coverage pass. |
| SQLDelight migration verification | `pass` | Desktop schema baseline `1.db` matches the current create statements. |
| Aggregate quality | `pass` | `./gradlew quality --rerun-tasks` completed after the last production correction, including iOS tests, packaging, lint, analysis, and migration checks. |
| Credential-free iOS host build | `pass` | Generic iOS Simulator `xcodebuild` completed with signing disabled. |
| Diff and privacy checks | `pass` | `git diff --check` and the scoped sensitive-data/path scan found no introduced personal path, credential, key, or token material. |
| Independent completed-change review | `blocked` | Three Required findings were corrected and re-reviewed; the remaining Required finding is the maintainer-observed physical-Mac picker and accessibility checklist. |

## Blockers and accepted risks

- The signed physical-Mac flow still needs maintainer-observed multi-select,
  cancel, invalid/self rejection, restart persistence, removal, retained
  mappings, keyboard operation, and VoiceOver checks. Automated tests do not
  substitute for AppKit, Security, and accessibility observation.
- Hosted CI remains paused through 2026-09-05. Aggregate quality and the iOS
  host build pass locally; rerun affected verification after any correction
  caused by the physical checklist.

## Final

- **Status:** `blocked`
- **Outcome:** implementation and automated verification are complete; physical-Mac verification blocks final review, push, and pull request
