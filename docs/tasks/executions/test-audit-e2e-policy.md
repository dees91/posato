# Execution: TEST-AUDIT

- **Brief:** [TEST-AUDIT](../specifications/test-audit-e2e-policy.md)
- **Status:** done
- **Review tier:** Standard
- **Branch:** chore/test-audit-e2e-policy
- **Updated:** 2026-09-26

## Plan

1. Record baseline checks and inspect tests in three read-only audit lanes.
2. Align the standing testing rules and delete evidenced redundant cases.
3. Run retained proof, review coverage independently, and record the result.

## Result

Removed 30 test declarations and 472 lines of test/support code. Production
sources, build configuration, and quality-gate requirements are unchanged.
Three read-only agents inspected all assigned test bodies across shared,
native applications, tooling, the prototype, and video. Candidate owners,
callers, history, and retained proof were checked individually; retention
reasons are grouped by contract, without claiming mutation testing.
AGENTS.md, the quality contract, driver guidance, and wiki now agree on E2E
preference, repeatable artifacts, and failure-first isolated tests.

## Deletion evidence

The following candidate evidence was recorded before editing. File names below
are unique within their named module. Every candidate is low-risk and unlocks
only test-code cleanup; no production path becomes dead.

| Test file and exact case | Actual signal and remaining proof | Owner, callers, and history |
| --- | --- | --- |
| shared `OnboardingUiStateTest`: `given every sync status when messaged then each maps to a distinct string`; `given action required when messaged with reasons then each reason maps to a distinct string` | Resource identity uniqueness, not correct rendered messages; swapped copy passes. No accepted resource-identity contract. Keep capacity/convergence behavior tests; E2E sync recipes cover selected visible outcomes, not every message. Remove three imports. | `SyncStatus.message`, called by onboarding and iCloud options; introduced `af5ef0d` and `62f69f4`. |
| shared `DesktopBootstrapCompositionTest`: `given the real desktop graph when created then onboarding dependencies resolve without touching providers` | Checks the factory's declared return type without resolving onboarding. Neighboring singleton, non-ready-sync, and dispatcher cases construct the same graph with the same throwing helper fake. | Desktop application graph called by desktop Main; introduced `af5ef0d`. |
| shared `IosBootstrapKeychainAdapterTest`: `given forwarded bytes when compared then copies match the originals` | Binding forwarding, strictly repeated by `given a read when executed then the binding and account are forwarded`, which also checks account and result. | `IosBootstrapKeychainAdapter.readItem`, reached by bootstrap item checks through the iOS graph; both introduced `f3172e7`. |
| desktopApp `MacOsBrowserDomainRedactionTest`: `given a synthetic privacy canary when rendered then helper types stay redacted` | Host canary assertion already in `BrowserDomainConfigureProtocolTest`; port privacy in `MacOsBrowserDomainEnforcerTest`. Other canaries never enter input. Delete file. | Browser configure payload and enforcer, called by helper client/session enforcement; introduced `0792872`. |
| desktopApp `MacOsApplicationRedactionTest`: `given a synthetic canary when rendered then helper types stay redacted` | Payload canary already checked more strongly by `ApplicationEnforcementProtocolTest`; other values never receive it. Delete file. | Application enforcement payload/enforcer, called by helper client/session enforcement; introduced `9687027`. |
| desktopApp `MacOsApplicationPhysicalHarnessTest` and `MacOsBrowserDomainPhysicalHarnessTest`: `given the physical gate when unset then the maintainer checklist is skipped` in each | Returns without assertions or writes a checklist and checks the file exists. No production contract; adjacent real physical cases do not read that file. Remove both private templates. | No production caller; introduced `9687027` and `0792872`. Real harness tests and posato-control session recipes remain. |
| desktopApp `MacOsHelperProtocolTest`: `canonical digest ignores transport metadata` | Compares the same function with identical arguments, varying no metadata. Any deterministic wrong digest passes. Other protocol assertions remain; Swift wire tests do not prove JVM digest bytes. | `canonicalInputDigest`, used by helper-client reconciliation; introduced `d568b4a`. |
| posato-control `DoctorStateTest`: `an unknown condition never claims readiness and never blocks the report`; `one missing condition at error severity makes the report not ready` | Calls a copied test-private predicate, not production readiness. Keep state mapping and JSON cases; remove private `reportOk`. No production regression guard is lost. | Production readiness lives in `DoctorCommand.execute`; copied helper has only these test callers; introduced `f445fa5`. |
| posato-control `ProcessTargetingTest`: `no selector keeps the tracked application` | Same result asserted by `no selector never enumerates processes`, with an additional guard against enumeration. | `ProcessTargeting.resolve`, used by desktop processes/evidence/interaction; both introduced `f445fa5`. |
| posato-provisioning `ProfileDecisionsTest`: `reuses a current profile that already covers this Mac` | Identical effective input and assertion in retained `reads the expiry format App Store Connect actually returns`. | `ProfileDecisions.decide`, called by profiles ensure; both introduced `8d4b922`. Keep later unknown-state/expiry regressions from `1215215`. |
| posato-provisioning `ProvisioningChecksTest`: `never carries a configured or discovered value into the report` | Searches six canaries never supplied to production. No valid privacy proof. Real secret-input tests remain in redaction, key-file, client, certificate, and upload suites, without claiming doctor E2E coverage. Remove six constants. | Doctor gathering reduces input to categorical `ProvisioningFacts`; introduced `4103d5e`. |
| shared `CanonicalBufferLifecycleTest`: `given successful projection hashing when digest completes then its plaintext preimage is cleared`; `given rejected projection hashing when digest completes then its plaintext preimage is cleared`; `given failing projection hashing when digest throws then its plaintext preimage is cleared` | Tests clearing synthetic buffers in a test-only digest helper. Keep production bundle signing/preparation/decoding lifecycle tests and reducer digest comparisons. Remove the unused projection fixture, capturing hash fake, hash constant, and imports. | `canonicalDigest` moved entirely into test support in `79680dc`; originally production in `27851eb`. No production callers remain. |
| iosApp `SuspendedExpiryTests.testMatchingActivityClearsOnlyTheInjectedStore` | Same injected-store clear already checked by `testMatchingActivityWritesClearedRecordAndConsumesPending` after `IOS-006`, plus a never-injected foreign fake. Keeper also checks the cleared record. | `SuspendedExpiryClear`, called by the activity monitor extension; introduced `b8ce997`. The physical device foreign-store test remains. |
| macosSyncCompanion `CloudStoreTests.givenIdenticalAnchorWhenCreatingThenConflictIsReturned`; `givenRaceWhenSavingBundleThenIdenticalIsReconciled` | Strict subsets of retained existing-anchor/no-save and identical-bundle/no-save cases. The alleged race returns before the configured save conflict. | `CloudStore.createAnchor` and `saveBundle`, reached by request handlers; introduced `d2ced23`. No support cleanup. |
| macosSyncCompanion `CloudRecordsTests.givenIdentifierWhenConvertedToTextThenItRoundTrips` | Same-function self-comparison plus canonical check. Retained valid-bundle validation case creates the same identifier, checks canonical form and decodes to independently expected bytes. | `RecordCodec`, used by CloudStore and request handlers; introduced `d2ced23`. |
| macosHelper `BrowserPresentationTests.givenPresentationTypesWhenRenderedThenHostsStayRedacted` | A payload-free enum never receives the asserted host. Keep real selected-URL presentation and proxy request privacy cases. | `BrowserPresentationStatus` and adapter, called by browser-domain session; introduced `0792872`. |
| shared `AppleSyncTest`: `given a failed zone deletion when removing then key and local state stay` | Strict subset of `given a remaining record when removing then retryable is reported and the row is kept`, which also checks retryable status. | `AppleSync.removeWorkspace` / workspace removal, called by sync UI; original `5f7cbcd`, stronger overlap added `03ba1a2`. |
| shared `BootstrapCoordinatorTest`: `given an existing zone when bootstrapping then the zone is never saved` | Same state and assertions as retained crash-after-zone-save case, which additionally checks anchor creation. | Bootstrap coordinator/zone phase, constructed by platform graphs; both introduced `6fc8eaf`. |
| shared `SessionTransitionOwnerTest`: `given a failed displacement bank then replacement apply is withheld` | Same failure, no-apply, and no-ack assertions in `SessionDisplacementRecoveryTest` failed-retain case, followed by restart and real SQLite persistence/order assertions. | Session transition owner and drain, called by SessionViewModel; both introduced `966c786`. |

Unused private `acceptedSequences` and `replicaProjection` in shared
`AppleSyncConvergenceTest` have no callers, including in their introducing
commit `62f69f4`; remove both formatting helpers.

The helper `ProxyChainValidatorTests.givenListenerFailureWhenResolvingThenChainNeverFallsBackDirect`
also provides no listener-failure proof. Its resolver returns a fixed loopback
hop without contacting the stopped listener. Existing exact-loopback and
direct/extra-hop tests cover the validator's actual decisions. Production
callers are browser-domain session preflight and effective-chain checks.
Introduced in `0792872`; `1894641` only adapted async execution. Remove the
case and its fake's unused observation fields; retain the shared blocking
operation helper. Focused validation is the helper Swift suite.

Two empty migration cases are strict subsets of retained seeded migrations:
`SqlSyncLocalPolicyMigrationTest.given an empty version seven database when migrated then policy sync tables exist empty`
and `SqlRemovedWorkspaceMigrationTest.given an empty version eight database when migrated then the tombstone table exists empty`.
The respective seeded cases assert the same empty new tables plus preserved
rows through the same platform database drivers. Migrations `7.sqm` and
`8.sqm` contain unconditional table creation, with no row-dependent branch.
Introduced in `62f69f4` and `8403c14`; later edits only advance downgrade
cleanup. No support cleanup is unlocked. Validate shared JVM and iOS suites.

The shared `SessionViewModelTest` cases
`given an inactive status when entering setup then setup opens` and
`given an active session when requesting early end then confirmation opens`
assert single UI booleans. Retained E2E `session-start-desktop.json` asserts
YOUR NEXT PAUSE after the real setup action; `session-early-end.json` asserts
Ready to return? and completes the end after restart. Both scenarios passed
on a Tart peer VM before deletion. The owner is SessionViewModel, called by
SessionScreen; both tests originated in `eed166d`. Keep race, stale identity,
failed storage, duration, cancellation, and enforcement tests. No support
cleanup is unlocked.

## Review and verification

Independent preservation review examined every diff hunk, the task records,
and retained assertions. No Critical or Required findings remain. The reviewer
also ran five shared JVM suites: SessionViewModel, SessionDisplacementRecovery,
CanonicalBufferLifecycle, SqlSyncLocalPolicyMigration, and
SqlRemovedWorkspaceMigration. All 28 tests passed without failures or skips;
`build/verification/test-audit/reviewer-focused.log` records the command.

- Baseline `./gradlew quality` passed.
- Final gate initially found one leftover blank line and a packaging check
  failure after the signed E2E build. The blank line was removed; focused
  `:desktopApp:verifyMacOsDevelopmentPackaging --stacktrace` rebuilt the staged
  package and passed. The repeated `./gradlew quality` passed in 4m 59s.
- Tart peer E2E passed first-install skip, session setup/start, restart,
  early-end confirmation/completion, and fixture removal. Database reads
  confirmed `ended_early = 1` and zero remaining fixture rows. Run locations, screenshots,
  snapshots, and repeat commands are listed in ignored
  `build/verification/test-audit/README.md`. The peer was destroyed and primary left alone.
- The run showed restrictions requiring attention; it proves session UI and
  persistence, not enforcement. No physical iOS verification is claimed.
- `git diff --check` passed. The maintainer authorized commit and PR publication after verification; merge remains a separate decision.
- Rebased onto `main` `91cb329`, which includes `IOS-006` and `MACOS-012`. `IOS-006` had edited
  `testMatchingActivityClearsOnlyTheInjectedStore`, and the deletion was kept. After `IOS-006`, its
  keeper is `testMatchingActivityWritesClearedRecordAndConsumesPending`. `./gradlew quality` passed
  on the rebased source.
