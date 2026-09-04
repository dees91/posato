# Execution: `IOS-001`

- **Brief:** [Apply and clear only Posato-owned iOS restrictions](../specifications/ios-001-enforcement.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending
- **Branch:** `feature/ios-001-enforcement`
- **Worktree:** `~/Projects/Polyglot/posato-ios-001`
- **Updated:** 2026-09-04

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
- **Required findings (6):** wrong App Group identifier (`group.app.posato`
  instead of the APPLE-001 `group.app.posato.ios.session`); atomic set
  undefined; post-revoke clear fallback missing; migration failure
  semantics missing; AC-02 foreign-store test missing; outcome mapping
  incomplete (auth states, apps-only case).
- **Resolution:** brief corrected for all six (identifier, validate-before-
  write with rollback, post-revoke platform-failure fallback, copy-verify-
  delete migration with corrupt-source refusal, two-store plus injected-
  failure plus corrupt-migration tests, explicit outcome mapping with the
  apps-only mirror rule flagged for maintainer confirmation at merge).
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

- Signed device build with the team from ignored `local.properties`
  passed as an ephemeral command parameter; no signing value recorded.
- Driver (`posato-control`) installed, launched, and drove the app to
  the Applications section; the maintainer tapped through the system
  authorization and picked one application in the native picker.
- Full device XCTest: 54 passed, 0 skipped — real-store apply/clear
  with the stored selection, foreign-store isolation, migration
  read-back, and the existing mapping flows.
- Manual checkpoint (150 s hold, auto-clear): Safari shows the system
  blocked presentation for the paused domain, the selected application
  shows the system shield, unselected controls stay usable — all pass.
- After clear: paused domain and application usable again — pass.
- After revoking Screen Time authorization: clear runs, owned store
  empty — pass. Phone left with authorization revoked and no Posato
  restrictions; the stored selection remains for later flows.

## Blockers and accepted risks

- None remaining. The `approvedWithDataAccess` note from re-review stays
  a future-task item outside this diff.

## Final

- **Status:** `done`
- **Outcome:** all acceptance criteria verified; pull request ready for
  maintainer merge decision. The apps-only mirror rule is
  maintainer-approved (2026-09-04).
