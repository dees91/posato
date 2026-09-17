# Execution: `MACOS-008`

- **Brief:** [macos-008-developer-id-distribution.md](../specifications/macos-008-developer-id-distribution.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent review agents (plan and completed change)
- **Branch:** `feature/macos-008-developer-id`
- **Updated:** 2026-09-15

## Decisions

`user-confirmed` 2026-09-14 and 2026-09-15:

- **Release format.**
  - **Package:** a signed, notarized, and stapled DMG with the Eclipse Temurin 21 runtime. There is no updater (ADR 0004).
  - **Build number:** a positive integer passed as `posatoMacOsBuildNumber`. Release tasks fail without it, it is never tracked, and development packaging keeps `1`.
- **Certificate.** The Developer ID Application certificate comes from the G2 Sub-CA and is valid until 2031. The maintainer created it in the portal from a CSR, because Xcode issued a previous-Sub-CA certificate that expires on 2027-02-01. Release signing therefore passes the G2 identity by SHA-1 hash.
- **Update.** The supported update is quit, replace, and open, with no stop action in the UI. The ADR 0004 clarification is recorded in this PR.
- **Transfers.** Supported in-app removal moves to the new roadmap row `MACOS-009` (revision 18). A companion launch under the release signature moves to `SYNC-017`.
- **Review follow-up.** App Store Connect credentials are read from `-P`, then `POSATO_ASC_*`, then `local.properties` (review Recommended 1).
- **Scope additions (defects found by AC-04):**
  - Check again did not install a missing helper rule after background approval.
  - Open System Settings opened the browser.

## Plan (approved after seven plan-review passes, final at `a1881dd`)

1. **Version.** One strict `Version.xcconfig` parser in buildSrc, with an in-code regression contract. The version and build number feed Compose and the helper and companion plists.
2. **Icon and runtime.** Wire `Config/Posato.icns`. Take `javaHome` from the Adoptium 21 toolchain and name Temurin 21 in the notices.
3. **Signing.** Strip the non-arm64 Mach-O jar entries in both modes. A release mode on the signing task adds the Developer ID identity, `--timestamp`, the Production CloudKit environment, and the embedded Developer ID profile.
4. **Verification.** A release mode on the verifier checks:
   - authority, timestamp, and hardened runtime;
   - that the embedded profile allows every signed entitlement;
   - versions, the icon, notices, the Temurin `release` file, and absence of unsigned archived Mach-O files.
5. **Notarization.** Notarize and staple the app, build the DMG, then sign, notarize, and staple the DMG. Assess both with `stapler` and `spctl`. The tasks are manual, never part of `quality`, and compatible with the configuration cache.
6. **Physical AC-04.** An attended run on quarantined Safari downloads, with the baseline, blocking, update, and companion-log criteria agreed in plan review.

## High-risk plan review

Seven passes, 2026-09-14 to 2026-09-15. The final verdict was `approved` at `a1881dd`. Required findings and resolutions:

| Finding | Resolution |
| --- | --- |
| R1: icon not wired | `iconFile` plus a byte check in the verifier |
| R2/R2a: update proof insufficient; no in-app Disable or Remove | Decided update path, `MACOS-009` transfer, explicit update pass criteria |
| R3/R3a: companion profile and launch unproven | Profile must allow every signed entitlement; launch moves to `SYNC-017` |
| R4: silent build-number default | No release default; `CFBundleVersion` checks |
| R5, R5b–R5f: development data could start the companion; the absence proof was weak | Desktop reset with backup; process-level `/usr/bin/log` query that excludes the log tool |
| R6: update criteria not observable | Same path, new PID, running code timestamp, Idle only after the session ends |

## Result

- **Release path.** The chain is `stageMacOsReleasePackage` → `signMacOsReleasePackage` → `verifyMacOsReleasePackaging` → `notarizeMacOsReleaseApplication` → `packageMacOsReleaseDmg` → `notarizeMacOsRelease`. The command is documented in `docs/development/apple-provisioning.md`.
- **Defect fixes.**
  - `DesktopMacHelperState.recheck` runs Enable once when status reports `RuleRepair`. Enable installs a missing rule and never overwrites a changed one.
  - `MacOsSystemSettings` opens only `x-apple.systempreferences:` links through `/usr/bin/open`.
- **Deviations from the plan.**
  - Candidates 1–2 proved AC-01 but exposed the setup defect. After the fix, AC-04 ran on candidates 3–4.
  - The companion-log criterion was refined. The combined query also matched 35 `com.apple.fsevents.matching` install events and 4 kernel sandbox reports of GamePolicyAgent reading `PosatoMacOSSync.app` metadata. The process-only query returned 0 entries.
  - After reopening during an active session, restrictions need the existing Resume with administrator authentication. That is prior session behavior, not an update step.
- **Export compliance.** `inferred`, not a legal opinion: a Developer ID download has no Apple encryption declaration. Posato uses standard cryptography to protect user data; any U.S. export classification and self-classification filing stay with the maintainer.
- **`TB-08`/`T-13` handover.**
  - **Signing chain:** Developer ID G2 with a secure timestamp and hardened runtime on every nested item. Entitlements are minimal: JIT for the app, and CloudKit, keychain, and Production for the companion. The profile is verified as a superset of the signed entitlements, and notarization plus Gatekeeper assessment cover both artifacts.
  - **Credentials:** kept outside Git.
  - **Update trust:** a quarantined, notarized download installed by the user, replacing the whole bundle.
  - **Residual:** there is no automatic security update, the companion's Production launch is unverified (`SYNC-017`), and removal belongs to `MACOS-009`.

## Completed-change review

- **Implementation `6a47e4e..e1bf3cb`:** `approved`, no Critical or Required findings. Recommended 1 was adopted. Optional 2 and 3 were declined as advisory. Optional 4 is covered by recording `JAVA_VERSION` `21.0.12.1` here.
- **Corrections and closeout:** credential fallback, both defect fixes, the ADR 0004 clarification, and the closeout documents. The first pass was `changes-required`:
  - **Required 1:** Check again could send Enable during an applied or recovery phase, which restores the proxy mid-session. Fixed by requiring ownership `Idle`, with a test.
  - **Required 2:** this record had dropped the plan-review findings. The table is restored.
  - **Optional 3 and 4:** the credential error message and blank-value fall-through, both fixed.
  - Re-check: `approved`, with no Critical or Required findings.
- **Hosted review at `52d5c68`:** no P1. One P2 was accepted by the maintainer. The build-number guard ran in `doFirst`, which Gradle skips when staging is up to date. The validated number is now a task input, and the omitted-number sequence fails.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass on the final tree, rebased onto `465b2e1` after the last correction; no build-script warnings | local run |
| `DesktopMacHelperStateTest`, `MacOsSystemSettingsTest`, detekt, ktlint | pass (21/21 and 2/2) | local run |
| Development packaging after jar stripping | pass; only the arm64 SQLite dylib remains archived | `verifyMacOsDevelopmentPackaging` |
| Release fail-closed paths | missing identity or build number stops with a clear message | local run |
| Build number after up-to-date staging | stage with `=1` passes; stage with no number fails on the release guard; `=1` again is `UP-TO-DATE` | local run |
| Configuration cache | `notarizeMacOsRelease --dry-run` stores the entry on the final tree | local run |
| Runtime | source JDK `IMPLEMENTOR="Eclipse Adoptium"`, `JAVA_VERSION` `21.0.12.1` | verifier |
| AC-01 candidates 1–4 | app and DMG `Accepted`, stapled, `spctl`: `Notarized Developer ID`; deep strict pass | `build/verification/macos-008-release-20260915T094241`, `…T120226` |
| AC-02 | app, helper, and companion versions `1.0.0` with build numbers 1–4 | verifier and plist reads |
| AC-03 | notices match in the shared jar; runtime `legal/` present | verifier |
| Credential fallback | real app submission without `-PposatoAsc*`: `Accepted` | local run |
| AC-04 baseline | daemon not found, rule absent, proxy off, no copies, database reset with backup, no Posato background items | `…T120226/baseline-*` |
| AC-04 install | Safari quarantine `0083`, Gatekeeper prompt, not translocated | `candidate-3/*` |
| AC-04 setup with fix | background item off → approval required → on → Check again installed the rule → helper enabled | `candidate-3/approval-required.txt`, `check-again-ready.txt` |
| AC-04 blocking | `user-confirmed`: Safari and Chrome block `example.com`/`example.net`, `example.org` loads; helper quit Chess, Calculator untouched | `attended-observations.txt`, `helper-quit-events.txt` |
| AC-04 update 3 → 4 | Cmd-Q restored the proxy; old daemon exited 0; Finder Replace; reopened; after Resume a new daemon PID from the same path runs build 4 code (`Timestamp` 12:07:58); blocking repeated | `candidate-4/*` |
| AC-04 end | End session early: proxy off, daemon exited 0, sites and Chess open | `candidate-4/after-end.txt` |
| Companion | process `PosatoMacOSSync`: 0 entries since baseline | `companion-log-query-refined.txt` |
| Settings link | `user-confirmed`: `/usr/bin/open` of the Login Items link opened System Settings rather than the browser | attended |

## Blockers and accepted risks

- **Launchd metadata.** Launchd keeps the registration's `parent bundle version = 3` after the update, while candidate 4 code runs. It is metadata only and does not change behavior.
- **Previous certificate.** The previous-Sub-CA Developer ID certificate remains in the keychain until 2027-02-01. It is unused; identity selection uses the G2 hash.
- **Tests and CI.** Physical AC-04 depends on attended steps. Hosted CI stays disabled.

## Final

- **Status:** `done`
- **Outcome:** met.
  - AC-01 to AC-03 pass on notarized candidates.
  - AC-04 passes as amended (quit, replace, and open; removal belongs to `MACOS-009`).
  - Two onboarding defects found physically were fixed and reviewed.
  - A companion launch in Production remains with `SYNC-017`.
