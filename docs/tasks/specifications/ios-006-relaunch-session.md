# `IOS-006`: Keep an active iPhone session and its restrictions across a relaunch

- **Review tier:** `high-risk`
- **Tier reason:** The fix changes when Device Activity monitoring starts and stops and when the monitor extension clears Posato's Managed Settings store; a wrong guard either lifts restrictions during a session (the defect) or leaves the phone restricted after it ends, and callback timing is owned by Apple.
- **Dependencies:** none; release 1.2, wave R1.2/W1. Builds on the completed `IOS-002` suspended expiry and `SESSION-002` relaunch reconciliation.
- **Integration group:** `PR-IOS-RELAUNCH`, milestone `1.2.0`.
- **Authority:** [release roadmap](../release-roadmap.md) revision 8, the [`IOS-002` brief](ios-002-suspended-expiry.md) and [record](../executions/ios-002-suspended-expiry.md), [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md) (iOS enforcement boundary and minimum App Group state), the [`RELEASE-003` record](../executions/release-003-release-1-1.md) (defect evidence), and the [iOS enforcement topic](../../wiki/topics/ios-enforcement.md).

## Outcome

On the test iPhone, relaunching Posato during an active session, fast or slow and repeatedly, leaves the session active and its website and application restrictions in place until early end or the planned end, and a session whose end passes while the application is suspended is still cleared by the monitor extension.

## Boundaries

- Evidence (`observed`, `RELEASE-003`): 4 of 6 relaunches ended the session as expired and lifted restrictions, every fast XCUITest relaunch among them. Cause (`inferred` from code): relaunch re-applies the same session, which calls `startMonitoring` again for the fixed activity `app.posato.session.expiry`; the resulting interval-end callback clears the named store unconditionally and writes a cleared record for the running session. First confirm or correct this cause on the device before changing code.
- Re-applying the session that is already scheduled must not stop and restart its monitoring. An interval-end callback that arrives before the planned end must not clear the store or record the session as cleared. A new session, an early end, and `IOS-002` expiry keep their current behavior.
- Any change to the App Group record stays versioned, minimal (no tokens, domains, or selections), and readable from version 1 records written by 1.1.
- Write surface: `iosApp/iosApp/SuspendedExpiry*.swift`, `iosApp/ActivityMonitor/**`, `iosApp/iosAppTests/**`, `shared/src/iosMain/**/feature/enforcement/**`, `shared/src/iosTest/**/feature/enforcement/**`, a defaulted `EnforcementPort` capability and the relaunch adoption in `SessionTransitionOwner` with their `commonTest` tests (`user-confirmed`, 2026-09-26), and the wiki topics `ios-enforcement.md` and `cross-device-synchronization.md`; `docs/wiki/log.md` is shared by rebase. No macOS, sync, or session-model change; `MACOS-012` runs in parallel on documents only.
- A relaunch keeps the restriction set the session started with; Paused-items edits made during the session no longer apply on relaunch (`user-confirmed`, 2026-09-26).
- The availability-page limit (`docs/product/limits-and-platforms.md`) stays until `RELEASE-004` publishes the fix; this task hands that edit over.
- Non-goals: `SYNC-020` publication reliability, `IOS-005` reinstall behavior, sessions below the 15-minute Device Activity minimum beyond keeping their foreground expiry.

## Acceptance

- `AC-01` — The cause is recorded with provenance from a device reproduction before the fix.
- `AC-02` — Unit tests cover repeated apply of the same session, an early interval-end callback, a new session replacing an old one, and a version 1 record.
- `AC-03` — On the test iPhone, `session-relaunch-ios.json` passes repeatedly, including fast XCUITest relaunches and a slow relaunch, with the Calculator shield and Safari's "Website Not Allowed" page observed after each.
- `AC-04` — On the test iPhone, the `IOS-002` suspended expiry still clears restrictions after force-quit, and early end still lifts them.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Independent plan review before code, independent completed-change review after.
- Swift and Kotlin unit tests for the guards; the device runs above through `posato-control -t device` with `session-relaunch-ios.json`, `observe-blocking-ios.json`, and `observe-unblocked-ios.json`; evidence in ignored `build/verification/`.
- The standing local `./gradlew quality` merge gate.

## Decisions or blockers

- None known. If the device shows a different cause, the record states it and the plan review repeats before code.
