# Execution: `MODEL-001`

- **Brief:**
  [`../specifications/model-001-local-exact-domain-policy.md`](../specifications/model-001-local-exact-domain-policy.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer; plan and completed change approved`
- **Branch:** `model-001` staging; clean replacement branch pending
- **Updated:** `2026-08-27`

## Plan

1. Replace the custom database lifecycle, factories, driver decorator, schema
   validation, and close ownership with standard SQLDelight JVM and Native
   drivers initialized from the generated synchronous schema bridge.
2. Bind the platform driver, generated database, and store directly in each
   app-scoped Metro graph; run store operations on an injected named database
   dispatcher and use one standard SQLDelight replacement transaction.
3. Reduce the schema and cross-platform contract tests to the accepted policy,
   restart, rollback, corruption, bounds, redaction, and dispatcher behavior;
   commit the v1 schema baseline and wire migration verification into quality.
4. Correct the durable architecture and PoC-reuse synthesis, then run focused
   and aggregate verification and obtain an independent completed-change review.
5. Publish the verified final tree on a clean branch from current `main`, open a
   replacement pull request, and close the existing draft as superseded.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** `none`
- **Resolution:** AC-03 distinguishes typed stored-data corruption from standard-
  driver initialization failure. The iOS invalid-file test will force the first
  query because Native may defer opening; no production timing wrapper is added.

## Result

- Replaced the custom database lifecycle with the standard JDBC and Native
  SQLDelight drivers, generated schema bridge, `user_version`, and migration
  verification baseline. The schema now contains only revision metadata and
  canonical domains.
- Bound the app-scoped driver, generated database, and ready store directly in
  both Metro graphs. Store reads and atomic replacements use the named database
  dispatcher; JVM uses `Dispatchers.IO`, while iOS uses limited
  `Dispatchers.Default` because the pinned Native API does not expose IO.
- Removed the store factory, driver decorator, schema attestation, corruption
  classifier, recovery protocol, explicit close API, and custom failure states.
- Reduced the cross-runtime contract from 27 lifecycle-focused cases to eight
  policy behaviors, with descriptive backtick names and real SQLite rollback.
- Corrected the architecture and PoC-reuse synthesis so the deleted draft is
  explicitly superseded rather than retained as production guidance.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** The first pass found one Required privacy
  defect: default policy and state representations disclosed the domain count
  and policy revision.
- **Resolution:** Both representations are now fully static and redacted. The
  contract test asserts their exact safe outputs, affected verification passed,
  and focused re-review found no remaining Critical or Required finding.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Independent high-risk plan review | `pass` | Approved with no Critical or Required finding. |
| Focused persistence verification | `pass` | Eight real-SQLite tests pass on JVM and the iOS Simulator; SQLDelight migration verification passes. |
| Aggregate `./gradlew quality` | `pass` | Formatting, Detekt, migration verification, both runtime contracts, platform compiles, desktop packaging, and distribution pass. |
| Credential-free iOS host build | `pass` | The CI-equivalent Simulator build succeeds with the static framework and system SQLite linkage. |
| Diff and security self-review | `pass` | No custom lifecycle remnants, personal paths, credentials, raw domain logging, unbounded restore, or SQL interpolation were found. |
| Independent completed-change review | `pass` | One Required metadata-redaction defect was corrected; focused re-review approved the final change. |

## Blockers and accepted risks

- Compatibility with databases produced only by the unmerged custom lifecycle
  is intentionally unsupported; maintainers may delete that development data.
