# Execution: `IOS-002`

- **Brief:** [Clear Posato-owned restrictions after normal expiry while the iOS app is suspended](../specifications/ios-002-suspended-expiry.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Muse Code (session gray-albedo, 2026-09-05)
- **Reviewer:** pending until assigned; independent plan review required before implementation
- **Branch:** `feature/ios-002-suspended-expiry`
- **Worktree:** `~/Projects/Polyglot/posato-ios-002`
- **Updated:** 2026-09-05

## Plan

1. Resolve the short-session decision in the brief with the maintainer, then
   independent plan review.
2. Extension target, entitlements, shared Swift clear logic, App Group record.
3. Scheduler seam in `iosApp` and the Kotlin `iosMain` adapter with outcomes
   and the reconciliation read.
4. Swift and Kotlin tests, redaction test.
5. Device signing for the extension, physical checklist, `./gradlew quality`,
   completed-change review, record and wiki closeout, pull request.

## High-risk plan review

- **Verdict:** `approve-with-required` (independent reviewer, 2026-09-05)
- **Critical or Required findings:** R1 isolate App Group record from the
  selection store (distinct key, atomic non-destructive write, after-first-
  unlock readable); R2 reconciliation read maps absent/version-mismatch/
  corrupt to unknown, only exact-version match reads expiry-ended; R3 strip
  Apple error text at the boundary, extend the redaction test to every new
  carrier; R4 injectable scheduler seams (center, authorization, clock) so
  Simulator tests run without real scheduling; R5 physical checklist asserts
  AC-01/AC-04 rows with observed-not-promised delay, evidence under ignored
  `build/verification/` only. No Critical findings.
- **Resolution:** all five accepted into the implementation plan below.

## Result

- Extension target `ActivityMonitor` (`app.posato.ios.activitymonitor`,
  Device Activity monitor extension point) added to the Xcode project with
  Family Controls and App Group entitlements, embedded in the app.
- Shared testable Swift file in the app and extension targets: fixed activity
  name, named-store-only clear, versioned App Group record in its own
  `SuspendedExpiry` directory, foreign-activity no-op, no logging.
- Swift scheduler seam (injectable center, authorization, records, calendar)
  plus Kotlin `iosMain` adapter mirroring `IosEnforcement` (six outcomes,
  expired/unknown reconciliation, redacted carrier, no `expect`/`actual`).
- All five plan-review Required findings applied (R1 record isolation, R2
  reconciliation contract, R3 Apple-text stripping plus redaction test, R4
  injectable seams, R5 physical rows below).
- Design corrections from the real SDK interface found during implementation:
  `DeviceActivitySchedule` takes wall-clock components (not `DateInterval`),
  `stopMonitoring` takes the activity list, the center is a value type, and
  the static appex plist needs the executable/version keys.
- Deviation from the brief's letter: the shared Swift file compiles into the
  app target (reached by tests through `@testable import`, avoiding duplicate
  symbols) rather than directly into the test bundle; the store-name literal
  moved into the shared file so the enforcer and extension cannot drift.

## Completed-change review

- **Verdict:** `approve` (independent reviewer, 2026-09-05)
- **Critical or Required findings:** none.
- **Resolution:** one Recommended added (records-nil unavailable test);
  two Optionals declined (vacuous-assertion keep, async-main keep matching
  `IOS-001`); affected suite re-run green. No re-review needed.
- **Device-test addition review (Standard):** `approve`, no Critical or
  Required. Recommended applied (freshness bound on `clearedAt`);
  shield-comment Optional applied; strict-failure Optional declined so a
  missing profile fails loudly instead of skipping.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Kotlin `iosSimulatorArm64Test` enforcement | pass | 8 tests, 0 failures (4 new + 4 existing) |
| Xcode Simulator suite | pass | `TEST SUCCEEDED`, 67 passed, 0 failed, 3 skipped (pre-existing device-gated IOS-001 tests) |
| `./gradlew quality` after last change | pass | `BUILD SUCCESSFUL` |
| CI credential-free builds (Debug sim, Debug device, Release sim) | pass | three `BUILD SUCCEEDED` |
| `git diff --check`, suppression and private-data scans | pass | clean; no `Suppress`; synthetic fixtures only |
| Physical iPhone checklist | pass | device tests pass; delay observed, see below |

## Physical checklist (passed 2026-09-07, wired iPhone, synthetic fixtures)

| Row | State | Evidence |
| --- | --- | --- |
| Signed Debug device build, app plus embedded extension | pass | `BUILD SUCCEEDED`; exact managed profiles exist |
| AC-01 extension clear after force-quit | pass | window ended 09:29:29, store empty at 09:33 verify; app force-quit throughout |
| AC-02 foreign store untouched on device | pass | foreign restriction intact at verify, then cleaned by the test |
| Cancelled schedule never fires | pass | cancel stuck, pending removed, restriction retained; final record names the expiry session only |
| Reopen reads ended (AC-04) | pass | reconciliation `.expired` with session id and fresh `clearedAt` |
| Callback delay | observed | cleared within ~4 min after interval end; exact fire time unknown, never promised |
| Reboot inside interval | not attempted | personal phone; left as the brief's open observation |

## Blockers and accepted risks

- Decided 2026-09-05 (maintainer): sub-15-minute sessions report
  `below-platform-minimum`; foreground expiry covers them (`SESSION-002`).
- The `APPLE-002` profile unblocked packaging as its agent reported: the
  installed profile carries Family Controls and the App Group, and the signed
  device build passes once Xcode may contact the portal. No portal step taken.
- No verify-posato feature file: no user path exists until `SESSION-002`.

## Final

- **Status:** `active`
- **Outcome:** pending
