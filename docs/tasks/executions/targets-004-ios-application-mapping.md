# Execution: `TARGETS-004`

- **Brief:** [Associate opaque device-local iOS application selections](../specifications/targets-004-ios-application-mapping.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending until assigned
- **Branch:** `feature/targets-004-ios-application-mapping`
- **Updated:** 2026-09-02

## Plan

1. Obtain the two maintainer decisions from the brief (display names, token
   store location) and record them here.
2. Define the Kotlin protocol for the Swift adapter, the iOS mappings store,
   and identifier derivation with fakes and JVM/iOS Simulator tests first.
3. Implement the Swift adapter (authorization, picker, token encoding) and
   inject it through `mainViewController(...)`; add the development-only
   Family Controls capability.
4. Persist selections in the app-private versioned store; wire
   `provideApplicationMappings` in `IosApplicationGraph.kt`.
5. Verify on the Simulator (credential-free, `Unavailable`) and on a physical
   iPhone with the manual checklist; complete the independent reviews and one
   hosted pass; close this record.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pending | |
| Credential-free Simulator build and XCTest | pending | |
| Physical iPhone checklist | pending | |

## Blockers and accepted risks

- Two open maintainer decisions and the physical-device requirement; see the
  brief.
