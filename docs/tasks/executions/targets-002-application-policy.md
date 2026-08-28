# Execution: `TARGETS-002`

- **Brief:**
  [`../specifications/targets-002-application-policy.md`](../specifications/targets-002-application-policy.md)
- **Status:** `complete`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer`
- **Branch:** `feature/targets-002-application-group`
- **Updated:** `2026-08-28`

## Plan

1. Extend the semantic aggregate with one validated, redacted application
   policy name behind the smallest compile-time Unicode-normalization seam.
2. Add the v1-to-v2 SQLDelight migration and atomically persist exact domains
   plus the optional semantic policy without local application identifiers.
3. Extend the existing state holder and shared screen with application-group
   add, edit, cancel, remove, and truthful mapping-required presentation.
4. Add focused cross-platform regression coverage, run aggregate and platform
   verification, complete the independent review, and close durable records.

TARGETS-002 owns shared target domain, data, UI, composition, SQLDelight schema
and migration sources; its task records; `DESIGN.md`; and the relevant
architecture-direction and PoC-reuse wiki sections. It excludes MACOS-003 root
and desktop Gradle or packaging sources, desktop native-client and helper
sources, ADR 0004, and the macOS-enforcement wiki topic. `docs/wiki/log.md` is a
known shared append-only integration point. If either task enters the other's
surface, implementation stops until the writes are serialized. Final review
compares actual changed paths in both worktrees.

## High-risk plan review

- **Verdict:** `approved after correction`
- **Critical or Required findings:** Record TARGETS-001 as the consumed
  implementation baseline, freeze concurrent write surfaces, and define control
  characters portably rather than through platform character categories.
- **Resolution:** The records now identify TARGETS-001, make TARGETS-002 and
  MACOS-003 write surfaces and serialization explicit, define `Cc` as common
  C0/C1 ranges, require their tests, protect `1.db`, and compare actual paths.
- **Hosted-correction plan review:** Approved with no Critical or Required
  findings. The correction reuses one mutation gate, adds only the three
  missing preview states, and extends one existing conflict test.

## Result

- Added one optional, normalized semantic application-group name to the target
  aggregate without persisting platform application identifiers.
- Added an atomic SQLDelight v1-to-v2 migration and aggregate compare-and-set
  persistence while preserving the checked-in v1 migration baseline.
- Replaced the domain-only presentation with one shared Paused items screen
  covering application-group and exact-domain management, including truthful
  device-mapping-required copy and no placeholder picker action.
- Added focused domain, persistence, migration, conflict, cancellation, and
  redaction coverage across JVM and iOS simulator targets.
- Restored the complete preview matrix and kept stale policy mutations disabled
  until a conflict, corruption, or load failure is successfully reloaded.

## Completed-change review

- **Verdict:** `approved after correction`
- **Critical or Required findings:** Use multibyte values at the UTF-8 byte
  boundary; retain the complete TARGETS-001 domain ViewModel regression suite;
  and complete proportionate macOS and iOS Simulator runtime inspection.
- **Resolution:** Boundary coverage now distinguishes bytes from characters.
  Domain duplicate, invalid-edit preservation, both conflict reconciliation
  branches, editor cancellation, read retry, and persistence cancellation tests
  were restored alongside an application-editor cancellation test. The packaged
  macOS app and the installed iOS Simulator build were inspected in empty and
  populated states, including the mapping-required state, website regression,
  scrolling at the simulator's large text size, and all visible edit/remove
  controls. Add, edit, cancel, remove, invalid-edit preservation, and scrolling
  behavior are additionally exercised by the shared JVM and iOS test suites.
  The independent reviewer confirmed that no Critical or Required finding
  remains. Filename alignment was retained as advisory cleanup and did not
  expand this task.
- **Hosted correction:** The final hosted review identified one Required
  preview-matrix regression and one P2 conflict-recovery defect accepted by the
  maintainer. The missing website-validation, website-saving, and loaded-
  corruption previews were restored, and the existing mutation gate now keeps
  controls disabled until reload while preserving retry after `SAVE_FAILED`.
  A focused independent completed-change review approved the correction with no
  remaining findings.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Plan review | `pass` | Approved after all Required findings were resolved. |
| Conflict-recovery regression | `pass` | The focused JVM test failed against the old behavior when opening the application editor cleared a revision conflict, then passed after the shared mutation gate was corrected. |
| `./gradlew quality` | `pass` | All 92 tasks passed after the hosted corrections, including JVM and iOS tests, Android preview compilation, Apple compilation, SQLDelight migration verification, static analysis, formatting, and desktop packaging. |
| Credential-free iOS build | `pass` | `xcodebuild` completed with `CODE_SIGNING_ALLOWED=NO` and `CODE_SIGNING_REQUIRED=NO`. |
| Desktop package smoke check | `pass` | The packaged application launched and rendered the empty application-group and website editors. |
| macOS and iOS Simulator inspection | `pass` | Empty and populated group/domain states rendered on both Apple form factors; mapping-required copy, edit/remove controls, website rows, and the scrollable large-text layout remained visible and truthful. Shared interaction tests cover add/edit/cancel/remove and invalid-edit preservation on JVM and iOS. |
| v1 migration baseline | `pass` | `git diff --exit-code -- shared/src/commonMain/sqldelight/databases/1.db` reported no change. |
| Patch hygiene | `pass` | `git diff --check` reported no errors. |
| Concurrent write surfaces | `pass` | MACOS-003 changes remain limited to its declared Gradle, desktop native-client/test, helper, and task-record paths; no TARGETS-002 source overlap was found. |

## Blockers and accepted risks

- No blocker is known.
- This correction leaves `docs/wiki/log.md` unchanged under the maintainer's
  explicit collision-avoidance decision for the concurrent MACOS-003 worktree;
  its execution record and maintained architecture topic carry the update.

## Final

- **Status:** `complete`
- **Outcome:** implementation, verification, and independent review are complete
