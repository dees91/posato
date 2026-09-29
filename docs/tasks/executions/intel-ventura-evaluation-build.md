# Execution: Intel Ventura evaluation build

- **Brief:** [intel-ventura-evaluation-build.md](../specifications/intel-ventura-evaluation-build.md)
- **Status:** `done`; the first artifact was superseded after physical evaluation
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** Independent plan and completed-change review completed
- **Branch:** `feat/macos-intel-ventura`
- **Updated:** 2026-09-29

## Plan

1. Add an opt-in x86-64/macOS 13 packaging variant in the dedicated worktree while preserving the arm64 default.
2. Compile all native components for x86-64, package with a verified x86-64 Temurin 21 runtime, and check architecture and deployment targets across the app.
3. Use an isolated candidate update feed, verify its packaged settings, sign and notarize a uniquely named evaluation DMG, verify Gatekeeper, then copy it and its checksum to the Desktop.

## High-risk plan review

- **Verdict:** `approved after correction`
- **Critical or Required findings:** The Developer ID path could embed the stable arm64/macOS 15 update feed.
- **Resolution:** Added candidate-feed isolation to the plan, acceptance criteria, and packaged-settings check.

## Result

- Added an opt-in x86-64/macOS 13 packaging variant and retained the arm64/macOS 15 default.
- Built native leaves, the Swift helper, daemon, and sync companion for x86-64. The candidate embeds an x86-64 Temurin 21 runtime and x86-64 native libraries.
- Signed and notarized the candidate application and DMG with an isolated, loopback-only Sparkle feed and automatic checks disabled.
- Delivered `Posato-1.2.0-Intel-Ventura-evaluation-20260929.dmg` and its SHA-256 file to the Desktop without replacing an existing file. SHA-256: `90cf27ca579017e4db0a4365e41dd11842ab640bf66d87d423154aeae5badaab`.
- The first packaging scan exposed a stale arm64 Java runtime after changing targets. The runtime-image task now tracks the selected JDK; the package verifier rejects Mach-O files that lack the requested architecture.

## Completed-change review

- **Verdict:** `approved after correction`
- **Critical or Required findings:** Native build tasks initially did not track target inputs. A later artifact scan found a stale arm64 runtime in the x86-64 candidate.
- **Resolution:** Declared target and JDK inputs for native tasks and the runtime-image task, added the package architecture gate, reran both architecture paths, and independently reviewed the correction. No Critical or Required findings remain. The final DMG was scanned separately for each Mach-O deployment target.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| arm64 development package after target switch | Passed | `:desktopApp:verifyMacOsDevelopmentPackaging` |
| x86-64 release package after target switch | Passed; runtime image and distributable rebuilt | `:desktopApp:verifyMacOsReleasePackaging` |
| Notarized candidate DMG | Passed | `:desktopApp:notarizeMacOsRelease`; ignored `build/verification/intel-ventura-20260929/` |
| Mounted final DMG scan | Passed: 39/39 Mach-O files include x86-64 and target at most macOS 13; three bundle minimum versions are 13.0; candidate feed settings match | Ignored `build/verification/intel-ventura-20260929/` |
| Stapled ticket and Gatekeeper | Passed | `xcrun stapler validate` on DMG; `spctl --assess --type execute` on mounted app |
| Quality gate | `./gradlew quality` passed after the formatting correction. A separate fresh `:quality-rules:test` fails in `NativeSafeBacktickNameRuleTest` on both this branch and unchanged main; the cached test result does not resolve that baseline defect | Ignored `build/verification/intel-ventura-20260929/` |
| Tart smoke launch | Blocked before application launch | `posato-control vm install --line primary --dmg ...` returned `VM_UNAVAILABLE` because the created primary VM stopped immediately |

## Blockers and accepted risks

- Physical evaluation later found that this artifact exited before showing a window. The launch faults and replacement artifact are recorded in [intel-ventura-skiko-launch-fix.md](intel-ventura-skiko-launch-fix.md). No Posato build was run or installed on the maintainer's host Mac during this initial task.
- The Tart primary VM stopped immediately after creation and again after boot. Its environment must be repaired before an unattended smoke run can proceed.
- The fresh quality-rule test failure also reproduces on unchanged main. It is outside the Intel packaging change and must be repaired before integrating this branch.

## Final

- **Status:** `done`
- **Outcome:** The initial notarized x86-64/macOS 13 DMG established packaging feasibility but failed at runtime and was superseded by the launch correction. Production support remains open under `MACOS-015`.
