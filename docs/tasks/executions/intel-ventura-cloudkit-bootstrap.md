# Execution: Intel Ventura CloudKit bootstrap timeout

- **Brief:** [Intel Ventura CloudKit bootstrap timeout](../specifications/intel-ventura-cloudkit-bootstrap.md)
- **Status:** `blocked`
- **Review tier:** High-risk

## Plan

1. Preserve filtered CloudKit and companion logs from the failing physical attempt. Verify the installed sync companion signature and entitlements and confirm the failed operation and local timeout without reading workspace contents.
2. Review the bootstrap deadline and companion cancellation contract. Raise only the macOS bootstrap CloudKit deadline to the existing 120-second protocol maximum as a diagnostic candidate; preserve unknown-outcome reconciliation and account gating.
3. Build, sign, and stage the candidate in the dedicated worktree. Before replacing only the authorized MacBook's app, verify no active session, proxy ownership, in-flight bootstrap, or sync companion; quit Posato cleanly and preserve the exact installed signed app/DMG for rollback. Verify the new signature and entitlements, then run one user-triggered Sync with iCloud flow and capture the elapsed time, exact zone/anchor outcome, and redacted native evidence.
4. If the longer deadline succeeds, run applicable quality/package checks and independent completed-change review. If it fails, restore the prior installed signed app and source deadline, recheck account-bound local state and proxy/helper readiness, and document the next identified blocker without touching local data or shipping a speculative timeout change.

## Baseline evidence

- On the 2019 Intel MacBook Air running macOS 13.7.8, the installed candidate showed **Sync with iCloud didn't finish** repeatedly. The signed sync companion started successfully and CloudKit fetched container-specific information, then a private-database zone retrieval remained in progress until the companion was terminated almost exactly 30 seconds later. The system logged the CloudKit request as cancelled after the client disappeared, not a server response. Three attempts showed the same shape.
- The macOS bootstrap CloudKit adapter's default deadline is 30 seconds; the companion protocol permits up to 120 seconds. The Mac's active proxy is disabled and a simple HTTPS request to the iCloud gateway completed, but that does not prove authenticated CloudKit requests will complete.
- Raw logs remain under ignored `build/verification/intel-ventura-launch-fix/`; no account, workspace, Keychain, or app data was modified during diagnosis.

## Independent plan review

- **Verdict:** Approved before implementation after adding the idle/no-in-flight preflight, clean quit, exact signed-app rollback copy, signature/entitlement validation, and installed-app rollback on diagnostic failure. No Critical or Required findings remain. A persisted bootstrap candidate is preserved; the preflight excludes a request currently in flight.

## Diagnostic result

- The one-line 120-second deadline candidate passed `./gradlew quality` and `:desktopApp:notarizeMacOsRelease`. The signed, notarized DMG passed stapling and Gatekeeper checks; its SHA-256 was `8b762878ab84c09d4c120dad233c87c8b8ff8ffd503318de20af8dcb8627c866`. The candidate used build number `20260930` and an isolated loopback update feed.
- On the authorized Intel Mac, the preflight found an idle session, disabled HTTP and HTTPS proxies, and no persisted bootstrap candidate or established workspace. One companion process appeared during the first preflight; installation waited until it exited. The original build `20260929` was moved intact to a rollback location and verified. The diagnostic build passed signature, Gatekeeper, entitlement, minimum-version, and launch checks after installation.
- **The diagnostic sync action could not be driven.** The remote command `osascript -e 'tell application "System Events" to get name of every window of process "Posato"'` did not return within 30 seconds, and the Mac did not expose a screen-sharing endpoint on port 5900. No successful or failed 120-second CloudKit zone/anchor outcome was observed, so the local deadline is a hypothesis, not a confirmed cause or fix.
- The diagnostic app was quit while idle, and the exact prior signed build `20260929` was restored and relaunched. The diagnostic app and DMG were removed from the Mac. Local state remained without an active session, established workspace, or persisted bootstrap candidate; HTTP and HTTPS proxies remained disabled, and the background helper retained its successful exit state. The source deadline was restored to 30 seconds. Application data, Keychain, account settings, and CloudKit records were untouched by the rollback.
- Raw evidence and build logs are ignored under `build/verification/intel-ventura-launch-fix/`. The physical pre-change failure and the signed diagnostic package are reproducible, but the candidate lacks an E2E result. This task remains blocked on an unattended way to drive the Mac's application UI; it must not be presented as a sync correction.

## Follow-up diagnosis

- `observed`: all three cancelled zone requests used CloudKit's default operation configuration. The system reported Utility QoS, inferred discretionary scheduling, and zero recorded request and response bytes before the client exited. The native backend does not set a QoS on `CKFetchRecordZonesOperation` or its other CloudKit operations.
- `source-claim`: Apple's CloudKit documentation says default-priority operations are discretionary and can be scheduled according to battery and network conditions; it shows `.userInitiated` for work whose result the user is waiting for. This makes deferred execution a more specific explanation than a slow zone response, but the cancellation metrics alone do not prove the exact scheduling cause.
- `hypothesis`: keep the 30-second application deadline and set an appropriate foreground QoS for the user-triggered bootstrap operation, then compare the physical request's scheduling, bytes sent, and zone/anchor result. Preserve lower-priority behavior for background exchange. Do not ship this without a completed physical or approved VM verification run.
- `user-confirmed` (2026-09-29): a later **Sync with iCloud** attempt succeeded on the restored build `20260929`, which still uses the 30-second deadline. The intermittent failure's cause remains unverified. The maintainer chose to defer further iCloud investigation; this branch makes no synchronization code change.
