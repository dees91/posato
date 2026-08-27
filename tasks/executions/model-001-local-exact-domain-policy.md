# Execution: `MODEL-001`

- **Brief:**
  [`../specifications/model-001-local-exact-domain-policy.md`](../specifications/model-001-local-exact-domain-policy.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer; plan and persistence change approved`
- **Branch:** `model-001-sqldelight`
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
- Reduced the cross-runtime contract from 27 lifecycle-focused cases to twelve
  policy behaviors, with descriptive backtick names and real SQLite rollback.
- Required the v1 revision to use SQLite's integer storage class so generated
  `Long` reads cannot coerce text or real values into a fabricated revision.
- Required canonical domains to use SQLite's text storage class and covered
  complete state reads with one standard SQLDelight transaction.
- Applied the canonical-domain budget to the complete stored byte sequence so
  an embedded NUL cannot hide an oversized suffix from the schema constraint.
- Required replacement to validate the previous logical state after the
  revision compare-and-set and before deletion, so detected corruption rolls
  back the transaction instead of silently resetting the replica.
- Classified a missing metadata row as corruption before reporting a revision
  conflict, while preserving conflict behavior for a valid stale revision.
- Corrected the architecture and PoC-reuse synthesis so the deleted draft is
  explicitly superseded rather than retained as production guidance.
- Aligned the repository, ktlint, Detekt, and Android Studio on the accepted
  150-character Kotlin limit and preserved the maintainer-approved expression,
  return, and call-chain layout without introducing a custom lint module.

## Completed-change review

- **Verdict:** `approved, including the latest schema and snapshot corrections`
- **Critical or Required findings:** The first pass found one Required privacy
  defect: default policy and state representations disclosed the domain count
  and policy revision.
- **Resolution:** Both representations are now fully static and redacted. The
  contract test asserts their exact safe outputs, affected verification passed,
  and focused re-review found no remaining Critical or Required finding.
- **Latest correction:** A fresh independent review approved the complete-byte
  length constraint, regenerated baseline, and single cross-runtime NUL
  regression without a Critical or Required finding. The change adds no runtime
  validator, recovery layer, schema attestation, size framework, or variant
  matrix.
- **Latest replacement correction:** A fresh independent review approved the
  post-CAS validation, rollback semantics, extended real-SQLite contract, and
  durable records without a Critical or Required finding. The change adds no
  validator query, recovery layer, schema attestation, or corruption matrix.
- **Latest metadata correction:** A fresh independent review approved the
  shared post-CAS validation, missing-metadata regression, and unchanged stale-
  revision precedence without a Critical or Required finding. The change adds
  no query, validator, trigger, recovery path, or additional test variant.

## Hosted review

- **Reviewed commits:** `a2d871ed2b`, `187e412d3e`, `e596b55cc0`, `b2eb508a0d`,
  `339930072e`, `dc4306ce21`
- **Verdict:** `changes required; correction implemented, follow-up pending`
- **Critical or Required findings:** The reviews found that an `xn--` prefix
  alone trusted a malformed IDNA A-label and that SQLite could store text or
  real revision values which generated `Long` reads then coerced. The latest
  review found that replacement could silently overwrite an already corrupt
  domain row before validating the resulting state.
- **Accepted advisory findings:** A BLOB domain could pass the length constraint
  and be coerced by the generated `String` accessor, while separate revision
  and domain queries could return a state that was never committed. SQLite text
  length also stopped at an embedded NUL and could hide an oversized suffix. A
  missing metadata row could be misreported as a revision conflict even though
  the same state was classified as corruption by a normal read.
- **Resolution:** Cross-runtime regressions cover the malformed A-label and both
  storage-class coercion paths. MODEL-001 now rejects every reserved `??--`
  label pending TARGETS-001 IDNA validation, and the v1 schema requires the
  revision's physical SQLite type to be `integer` and a domain's physical type
  to be `text`. The domain constraint counts the complete stored byte sequence,
  and complete reads use one standard SQLDelight transaction. Replacement now
  validates the previous state inside its existing transaction before deletion;
  failure rolls back the preceding revision change. The same validation now
  runs before conflict classification, so valid stale state remains a conflict
  while missing metadata returns corruption. Focused JVM and iOS verification
  passes; hosted follow-up review remains pending.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Independent high-risk plan review | `pass` | Approved with no Critical or Required finding. |
| Focused persistence verification | `pass` | Twelve real-SQLite tests pass on JVM and the iOS Simulator; SQLDelight migration verification passes. |
| Aggregate `./gradlew quality` | `pass` | Formatting, Detekt, migration verification, both runtime contracts, platform compiles, desktop packaging, and distribution pass after the latest metadata correction. |
| Credential-free iOS host build | `pass` | The CI-equivalent Simulator build succeeds with the static framework and system SQLite linkage. |
| Diff and security self-review | `pass` | No custom lifecycle remnants, personal paths, credentials, raw domain logging, unbounded restore, or SQL interpolation were found. |
| Independent completed-change review | `pass` | Fresh review approved the latest metadata correction with no Critical or Required finding. |
| Kotlin formatting convergence | `pass` | Android Studio preserved the accepted Kotlin diff; `:shared:ktlintCheck` and `:shared:detekt` passed with the 150-character limit. |

## Blockers and accepted risks

- Compatibility with databases produced only by the unmerged custom lifecycle
  is intentionally unsupported; maintainers may delete that development data.
- `inferred`: MODEL-001 rejects reserved `??--` labels, including IDNA A-labels,
  until TARGETS-001 supplies the accepted validation and round-trip boundary.
