# Execution: `TARGETS-005`

- **Brief:** [Recover iOS application mappings from stale, refused, and corrupted states](../specifications/targets-005-ios-mapping-hardening.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending until assigned
- **Branch:** `feature/targets-005-ios-mapping-hardening`
- **Updated:** 2026-09-03

## Plan

1. Obtain the independent plan review and the maintainer's decision on the
   unavailability sentence and the corrupted-store confirmation.
2. Move the request-generation and presentation guards into
   FamilyControls-free Swift types, attach a generation to every `choose`,
   present only when nothing is presented, and complete a refused or failed
   presentation with the picker-failure outcome through the presentation
   completion.
3. Expose `Clear selection` in the corrupted-mappings state of the shared
   Targets UI while choose and remove stay blocked; add ViewModel coverage on
   JVM and iOS Simulator.
4. Replace the unavailability copy in `DESIGN.md`, the string resource, the
   previews, and the verification feature map.
5. Run `./gradlew quality`, Simulator XCTest, and the three-configuration build
   matrix, then drive the physical iPhone through the `verify-posato` iOS
   mappings recipe.
6. Complete the independent completed-change review, rerun affected checks,
   update the iOS enforcement wiki page, and close this record with the single
   wiki-log entry in the closeout commit.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- pending

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- `AC-05` needs the maintainer's development-signed iPhone; no device, team, or
  profile value may be recorded.
- Hosted CI stays manual-only through 2026-09-05; a fresh local
  `./gradlew quality` after the last correction is the merge gate.

## Final

- **Status:** `active`
- **Outcome:** pending
