# Execution: `IOS-001`

- **Brief:** [Apply and clear only Posato-owned iOS restrictions](../specifications/ios-001-enforcement.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Muse Code (session `waxen-apogee`)
- **Reviewer:** independent plan reviewer; independent completed-change reviewers; independent GitHub reviewer
- **Branch:** `feature/ios-001-enforcement`
- **Worktree:** `~/Projects/Polyglot/posato-ios-001`
- **Updated:** 2026-09-05

## Plan

1. Obtain the independent plan review. The brief's decisions are accepted:
   the named store, exact `WebDomain` matching, websites-only apply, and the
   App Group migration here.
2. Define the public Kotlin `iosMain` provider interface (canonical domain
   strings, mapping identifiers, platform-neutral outcomes) and the internal
   adapter with redacted carriers; cover it with `iosTest` contract and
   redaction tests over a fake provider.
3. Implement the Swift provider: named store ownership, token lookup by
   mapping identifier from the selection store, atomic apply with
   verification, idempotent clear on the owned store only, authorization
   gating through an injectable seam.
4. Move the selection store to the App Group container with
   after-first-unlock protection, add the App Group entitlement, and prove
   the migration with a Swift test and the existing mapping flows.
5. Run Swift tests on the Simulator and the physical iPhone, record the
   manual checklist, run `./gradlew quality`, complete the independent
   completed-change review, rerun affected checks, update the iOS
   enforcement wiki page, and close this record with the single wiki-log
   entry in the closeout commit.

## High-risk plan review

- **Verdict:** changes required (independent review, 2026-09-04)
- **Critical findings:** none
- **Required findings (6):** wrong App Group identifier, undefined
  atomicity, missing post-revoke fallback, missing migration failure
  semantics, missing AC-02 foreign-store test, incomplete outcome
  mapping.
- **Resolution:** brief corrected for all six; apps-only mirror rule
  flagged for maintainer confirmation at merge (since approved).
- **Re-confirmation:** approved on 2026-09-04, no remaining Required items;
  implementation authorized. Scope check: diff touches only the two task
  files; no write-surface expansion.

## Result

- Kotlin `iosMain` `feature/enforcement`: provider seam, nine outcomes,
  redacted request carrier, suspend facade with empty-set refusal.
- Swift `IosManagedSettingsEnforcer` on the named `app.posato.session`
  store with validate-before-write, verify, rollback, idempotent clear,
  and injectable store/authorization/capability seams.
- `ApplicationMappingsStore.liveMigrated` moves the selection store to
  `group.app.posato.ios.session` (copy-verify-delete, corrupt source
  refused, re-run safe); App Group entitlement added to Debug.
- Material deviation: `clear(handler:)` label on the new Kotlin interface
  only, because `clear(completion:)` collides with the existing mappings
  provider at the ObjC selector level and renames its Swift requirement.
  Existing Swift and all `feature/targets` sources are untouched.
- Wiki `ios-enforcement` gains the implementation synthesis; device
  observations stay open.

## Completed-change review

- **Verdict:** request changes (independent review, 2026-09-04)
- **Critical findings:** none
- **Required findings (2):** failed group verify left the group file
  behind and poisoned re-runs; the `restricted` refusal path had no test
  proving it writes nothing.
- **Resolution:** verify failure now removes the group file before
  throwing, so the next run retries from the intact private source;
  authorization mapping extracted into the testable
  `EnforcementAuthorization` type (the platform status enum has no
  restricted case, so the seam now takes it and `.restricted` is covered
  end to end with `writes == 0`). Advisory findings declined: the Kotlin
  clear test stays at the outcomes clear can produce, and the
  `clear(handler:)` rationale plus the apps-only device step stay in this
  record rather than expanding the brief.
- **Re-review:** approved on 2026-09-04, no remaining Required items.
  The verify-failure cleanup retries from the intact private source, and
  the authorization seam covers `.restricted` end to end. One non-blocking
  note recorded for a future task: the mappings provider still compares
  raw status to `.approved` without the `approvedWithDataAccess`
  treatment; untouched by this diff.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` after last change | pass | `BUILD SUCCESSFUL`, `diff --check` clean |
| Kotlin `iosSimulatorArm64Test` enforcement | pass | 4 tests, 0 failures |
| Xcode Simulator suite | pass | `TEST SUCCEEDED`, 50 passed, 1 expected skip (device-only cycle) |
| Privacy/suppression/log scans | pass | synthetic domains only, no `Suppress`, no logging of values |
| Physical iPhone device run + manual checklist | pass | 54 passed, 0 skipped, 0 failed; `AC-01`/`AC-02`/`AC-04` manual steps all pass (see device gate) |

## Device gate (cleared 2026-09-04)

- Signed build with the ephemeral team parameter; driver drove the app
  to Applications, maintainer authorized and picked one application.
- Device XCTest 54 passed, 0 skipped; manual checkpoint all pass
  (Safari block, app shield, unselected usable, clear restores,
  revoke-then-clear empty). Phone left revoked, restriction-free.
- Independent GitHub review (P2 only, no P1): 3 accepted (re-migrate over empty
  group, best-effort post-copy cleanup, explicit fake read + shield
  rollback test), 3 declined (nil-container fallback, layered
  refusal precedence, unmeasured caching). Verified, pushed, replied.

## Blockers and accepted risks

- None remaining. The `approvedWithDataAccess` note stays a
  future-task item outside this diff.

## Final

- **Status:** `done`
- **Outcome:** all acceptance criteria verified; apps-only rule
  maintainer-approved (2026-09-04). Awaiting merge decision.
