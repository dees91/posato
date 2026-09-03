# Execution: `TARGETS-005`

- **Brief:** [Recover iOS application mappings from stale, refused, and corrupted states](../specifications/targets-005-ios-mapping-hardening.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Grok
- **Reviewer:** independent plan reviewer; independent completed-change reviewer
- **Branch:** `feature/targets-005-ios-mapping-hardening`
- **Updated:** 2026-09-03

## Plan

1. Record the maintainer-accepted unavailability sentence and no-confirmation
   clear, then apply the Required plan-review corrections before product edits.
2. Move the request-generation and presentation guards into
   FamilyControls-free Swift types, attach a generation to every `choose`,
   fail closed when the presenter cannot present, and complete a refused or
   failed presentation with the picker-failure outcome.
3. Keep the valid-selection Clear path and add a corruption disjunct so
   `Clear selection` appears on the corruption notice while choose and remove
   stay blocked; refresh access with `load()` after that clear succeeds.
4. Replace the unavailability copy in `DESIGN.md`, the string resource, the
   previews, and the verification feature map.
5. Run `./gradlew quality`, Simulator XCTest, and the three-configuration build
   matrix, then drive the physical iPhone through the `verify-posato` iOS
   mappings recipe.
6. Complete the independent completed-change review, rerun affected checks,
   update the iOS enforcement wiki page, and close this record with the single
   wiki-log entry in the closeout commit.

## High-risk plan review

- **Verdict:** `approved after corrections`
- **Critical or Required findings:** `canClearApplicationMappings` must keep
  the valid-selection path and add a corruption disjunct (R1); the presentation
  gate must fail closed when the presenter has no window, not only wait on the
  UIKit completion (R2)
- **Resolution:** Valid Clear stays `canMutate && isNotEmpty`; corruption Clear
  is a second disjunct. `canPresent` requires no presented controller and a
  window; UIKit completion only confirms identity. Accepted Recommended items:
  capture corruption before the mutation update and refresh `load()` after that
  clear; add a `LOAD_FAILED` preview sample; ViewModel test that `LOAD_FAILED`
  does not enable Clear.

## Result

- Added FamilyControls-free choose-session generation and a fail-closed
  presentation gate in the existing Swift provider. Stale authorization no
  longer presents or completes a later request; refused presentation returns
  picker failure and releases the adapter mutex.
- Shared Targets UI keeps valid-selection Clear and adds **Clear selection**
  on the corruption notice with no confirmation. A successful corruption clear
  reloads access; a failed one keeps Clear reachable and says the selection
  could not be cleared. Choose and remove stay blocked on corruption.
- Replaced build-terms unavailability with **Choosing apps is not available in
  this version of Posato.**
- No entitlement, App Group, store format, schema, or Kotlin protocol change.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** none
- **Resolution:** Accepted one Recommended item: a failed corruption clear
  keeps Clear reachable. Maintainer-accepted P2 corrections: complete the
  session after picker dismissal, drop the unreachable UIKit `!presented`
  branch and tighten `canPresent`, report a failed corruption clear as
  `CORRUPTED_CLEAR_FAILED`, and have `begin` complete an active session
  instead of dropping it.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | after last correction; includes `shared:iosSimulatorArm64Test` |
| Simulator XCTest | pass | iPhone 17; session, presentation, store, and provider tests, including P2 session-complete-on-begin |
| Unsigned Debug Simulator build | pass | Xcode 26.6 |
| Unsigned Debug device build | pass | Xcode 26.6; Family Controls path compiled |
| Unsigned Release Simulator build | pass | Xcode 26.6 |
| `git diff --check` and private-data scan | pass | no credentials, paths, or tokens |
| Physical iPhone `verify-posato` mappings | pass | authorize, pick, cancel with count retained, restart, clear; ignored `build/verification/runs/` |

## Blockers and accepted risks

- Hosted CI stays manual-only through 2026-09-05; a fresh local
  `./gradlew quality` after the last correction is the merge gate.
- No device, team, or profile value is recorded.

## Final

- **Status:** `done`
- **Outcome:** met
