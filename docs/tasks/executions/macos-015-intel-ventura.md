# Execution: MACOS-015 Posato on Intel Macs running macOS 13 Ventura

- **Brief:** [macos-015-intel-ventura.md](../specifications/macos-015-intel-ventura.md)
- **Status:** `active`; the evaluation below precedes the row's plan
- **Review tier:** High-risk
- **Branch:** `feat/macos-intel-ventura`

## Evaluation before the row (2026-09-29)

The maintainer asked for a direct evaluation on the 2019 MacBook Air before
release 1.3 was composed. It ran as four High-risk steps, each with an
independent plan review and, where code changed, a completed-change review.
They overrode the VM-only rule for this evaluation only, at the maintainer's
explicit request. They are history, not the row's plan.

1. **Evaluation build.** An opt-in `-PposatoIntelEvaluation=true` variant
   builds x86-64/macOS 13 native leaves, the Swift helper, daemon, and sync
   companion, and embeds an x86-64 Temurin 21 runtime. The arm64/macOS 15
   default remains. The candidate uses an isolated loopback Sparkle feed with
   automatic checks off; the plan review's Required finding was that the
   Developer ID path could embed the stable arm64 feed. The completed-change
   review found untracked native-task inputs and a stale arm64 runtime; the
   runtime-image task now tracks the JDK, and the package verifier rejects
   Mach-O files without the requested architecture.
2. **Launch fix.** The first physical launch on macOS 13.7.8 exited on the
   missing top-level `libskiko-macos-x64.dylib`, then crashed on the macOS
   14-only `NSApplication.activate` selector. The package now carries the
   signed x86-64 Skiko library at the loader's path, with an explicit
   verifier check, and `WindowChrome.m` and `Updater.m` fall back to the
   pre-14 activation call. The final notarized DMG launched with a visible
   window (`user-confirmed`).
3. **Helper registration.** Unified setup first stopped: Background Task
   Management recorded the daemon as enabled and allowed, but `launchd` had
   no Posato service. After a maintainer-approved restart, Service
   Management submitted the daemon, and **Finish** completed setup with the
   administrator dialog. No Background Items reset, unregister, or data
   change occurred. The cause of the missed submission remains `open`.
4. **CloudKit bootstrap.** Three attempts ended when Posato's 30-second
   companion deadline cancelled a pending private-zone fetch. The requests
   had default discretionary QoS and no recorded bytes (`hypothesis`:
   deferred scheduling). A 120-second diagnostic build could not be driven
   remotely: System Events timed out and screen sharing was closed. The
   previous build and deadline were restored. A later manual sync succeeded
   on the 30-second build (`user-confirmed`); the maintainer deferred
   further investigation.

## Evaluation evidence

- **Package:** `:desktopApp:verifyMacOsDevelopmentPackaging` and
  `:desktopApp:verifyMacOsReleasePackaging` pass for both architectures. The
  final DMG has 40 x86-64 Mach-O files with deployment targets at most
  macOS 13 and three bundle minimum versions of 13.0. Notarization,
  stapling, and Gatekeeper pass.
- **Quality:** `./gradlew quality` passed after the last evaluation change.
  A fresh `:quality-rules:test` failure in `NativeSafeBacktickNameRuleTest`
  also reproduced on unchanged `main` at the time.
- **Runtime:** launch and setup on one Intel Ventura Mac only. Blocking,
  schedules, sync with a peer, and the updater alert were not exercised.
- Raw evidence stays under ignored `build/verification/intel-ventura-*`.

## Plan

Written when implementation starts, then reviewed by a different agent
before any further implementation, as the High-risk tier requires.
