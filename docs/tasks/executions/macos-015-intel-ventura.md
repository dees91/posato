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
  also reproduced on unchanged `main` at the time; it must be green before
  this branch integrates.
- **Runtime:** launch and setup on one Intel Ventura Mac only. Blocking,
  schedules, sync with a peer, and the updater alert were not exercised.
- **Tart smoke launch:** blocked; `vm install --line primary` returned
  `VM_UNAVAILABLE` because the primary VM stopped at once. Recheck before AC-03.
- **Tested revision:** `33dd5b7`, based on `b4db95d`, before `MACOS-022`
  reworked the helper and before `SCHEDULE-005`. The rebased head has not been
  built or verified for x86-64.
- The helper-recovery plan review ruled that any `sfltool resetbtm` needs a
  fresh maintainer decision and a full inventory of other applications'
  background items; none was run.
- Raw evidence stays under ignored `build/verification/intel-ventura-*`.

## Brief review (2026-09-29)

An independent review of the opened brief found no Critical and seven
Required findings: the feed layout belongs to an ADR 0008 revision, only
`RELEASE-005` publishes, Rosetta needs a decision, the dedicated-Mac gate and
its unattended prerequisites were incomplete, setup from a clean state was
missing, the flow did not match the arm64 flow, and macOS 13 compatibility
needed a lasting gate. All are folded into the brief.

## Plan

Written 2026-09-30 on `3e19215`. A different agent reviews it before any
further implementation. Steps run in order; each code step ends with
`./gradlew quality` green.

0. **Feasibility first.** Create the `ventura` golden VM (step 8 recipe),
   install Rosetta, install the existing x86-64 evaluation package, and
   through `posato-control` show a visible window and take an accessibility
   snapshot; also start `jwebserver` from the shared JDK in the guest. If
   any part fails, stop and report the failing command as a blocker.

1. **Decisions first (`AC-01`).** Amend ADR 0003 (x86-64 on macOS 13+,
   Rosetta refusal, arm64 minimum 13.0 under a recorded maintainer exception
   to per-version evidence, support horizon and one-release removal notice),
   ADR 0008 (per-architecture feeds, shared `CFBundleVersion`, candidate
   feeds per architecture), and `TB-08`/`T-13` in the threat model. Prose
   only; nothing else depends on wording beyond the brief.
2. **One architecture property.** Replace `posatoIntelEvaluation` with
   `posatoMacOsArchitecture=arm64|x86_64` (default `arm64`). Both
   architectures use minimum 13.0: clang `-target <arch>-apple-macos13.0`
   with `-Werror` and `-Wunguarded-availability-new`, an explicit Swift
   triple for both, and all three `LSMinimumSystemVersion` values.
   Target-built native libraries go to the architecture-neutral `macos/`
   resource directory, because Compose packages only the build host's
   `macos-<arch>` directory; the verifier keeps its presence checks. The
   prototype app stays at 15.0; it ships nowhere.
3. **Pinned x86-64 runtime.** A download task fetches the Temurin x86-64 JDK
   for the exact version of the resolved arm64 toolchain, verified by a
   SHA-256 in the version catalog (as Sparkle is), into the Gradle cache.
   The SHA-256 sits beside Sparkle's pin. `posatoMacOsJavaHome` is removed.
   Only x86-64 packaging and release tasks fail when the arm64 toolchain's
   `JAVA_VERSION` differs from the pin, with a message on bumping both; arm64
   builds and `quality` never download it. x86-64 `jlink` and `jpackage` run
   under Rosetta on the build host, a contributor prerequisite that
   `apple-provisioning.md` states.
4. **Package checks.** `embedSparkleFramework` thins the pinned universal
   Sparkle to the target architecture before signing. The verifier then
   requires thin Mach-O files of exactly the requested architecture
   (`lipo -archs` equals, not contains) and a minimum of at most 13.0 on
   every Mach-O, read from `LC_BUILD_VERSION` or `LC_VERSION_MIN_MACOSX`.
5. **Rosetta refusal.** A JNI call in `WindowChrome.m` reads
   `sysctl.proc_translated` before `installLaunchProbe()` in `main()`. It
   refuses only when the call succeeds and returns 1; any error, as on a
   real Intel CPU, means run (an unverified path). Under translation it
   runs a synchronous modal alert on the main queue with the regular
   activation policy, opens the latest-release page on **Download**, and
   quits before the instance lock, helper client, or graph start. The
   verification switch `-PposatoMacOsAllowRosetta=true` (x86-64 only) writes
   an Info.plist key under which the app logs the detected translation and
   continues. The check is tied to the update channel, not the signing
   path: configuration refuses the switch with the `release` channel, the
   verifier rejects the key in a `release`-channel package, and
   `generateMacOsUpdateFeed` rejects it in the mounted DMG. The helper,
   daemon, and companion do not check, since only the app starts them. The
   ADR 0003 revision states the effect of an x86-64 install over an arm64
   one on Apple silicon: the login item shows the refusal at each login and
   **Download** recovers.
6. **Feeds.** `UpdateChannel` × architecture: release arm64 `appcast.xml`,
   release x86-64 `appcast-intel.xml`, candidate `appcast-test.xml` and
   `appcast-intel-test.xml`. The validator requires minimum 13.0, the
   `arm64` hardware requirement only on arm64 feeds, a mounted app of
   exactly the feed's architecture, and the feed name matching the
   embedded `SUFeedURL`. Assets are `Posato-<v>.dmg` and
   `Posato-<v>-intel.dmg`. Both feeds of a release are generated from the
   draft before either is published, each floor is the highest build in
   either published feed, and generating the second feed requires the
   first DMG's `CFBundleVersion`, so both builds share one. The ADR 0008
   revision states that every stable release from 1.3 on carries both
   feeds and what the last Intel release does. Failing cases added first to
   the existing inline regression contracts in `PosatoUpdateFeed.kt` and
   `PosatoPublishedFeed.kt` cover the channel mapping and each validator
   rejection: feed separation is a security boundary that a VM run shows
   only for the one path it takes.
7. **Lasting macOS 13 gate.** `quality` compiles both ObjC leaves for
   `x86_64-apple-macos13.0` (the arm64 default already targets 13.0) and
   builds both Swift packages with the x86-64 triple, using the arm64 JDK's
   JNI headers so nothing is downloaded.
8. **`ventura` VM line.** `posato-control` gets `VmLine.VENTURA`
   (`posato.vm.venturaGolden`), `build -t desktop --arch x86_64
   [--allow-rosetta]`, and an AX bridge built for `arm64-apple-macos13.0`.
   The golden VM follows the legacy-line recipe with the newest macOS 13
   restore image, plus `softwareupdate --install-rosetta
   --agree-to-license` and a `posato-provisioning` registration. It has no
   Apple Account: sign-in in a guest needs macOS 15 or later (see `C1`). Ventura's System Settings layout will need new
   `vm prompt` calibration; every dialog stays driver-answered.
   `unattended-verification.md`, the driver README, and the verify-posato
   skill describe the line.
9. **Candidates.** Build and notarize: arm64 candidate, x86-64 candidates N
   and N+1 with the switch on the Intel test feed, and the x86-64 release
   build (no switch) for the refusal check and package checks. The test
   feed is served per `D1`.
10. **Verification (`AC-02`, `AC-03`).** On a fresh `ventura` clone, from a
    clean state: the macOS 15 flow of the availability page, the pause page,
    the application picker, sync with a `peer` clone, helper removal, and
    the update N to N+1. Setup runs without a restart; if the daemon
    submission is missed again, investigate without `sfltool resetbtm` and,
    if unresolved, record it as a named risk for the availability page. The
    arm64 candidate runs the same flow on the `primary` line and, per `D3`,
    installation and setup on `ventura`. The x86-64
    release build shows the refusal and exits on `ventura`. Package checks
    and feed contents for both architectures.
11. **Availability page (`AC-04`)** and closeout: Intel on macOS 13 with
    evidence "under Rosetta in a virtual machine, not on Intel hardware",
    arm64 from macOS 13 under the exception, the end of Apple's macOS 13
    security updates, the named paths that differ between Rosetta and an
    Intel CPU (the native `proc_translated` branch, sync per `C1`), and the
    removal of the `Planned` row; the every-release toolchain check in the
    release procedure; `apple-provisioning.md` minimum statement; wiki topic and
    one log entry; roadmap mirror. README and `posato.app` copy stay with
    `DOCS-004`.

### Plan review (2026-09-30)

A different agent found one Critical, eight Required, and several
Recommended findings. R1-R8 and the Recommended alert, load-command,
build-host, horizon, and cross-install items are folded above; `C1` needs
a brief amendment.

### Open decisions for the maintainer

- `C1` `AC-02` requires sync with a Tart peer, but the `ventura` guest
  cannot sign in to an Apple Account (`source-claim`, Apple Support 120468:
  guests need macOS 15 or later). Proposed amendment: the x86-64
  verification build runs the sync check under Rosetta on the `legacy`
  line (macOS 15.6.1) against a `peer` clone; everything else runs on
  `ventura`; sync on macOS 13 is named unverified on the availability page.

- `D1` Intel test feed hosting. Recommended: serve it inside the guest on
  `127.0.0.1` with `jwebserver` from the driver's shared JDK, so nothing is
  published; the candidate then carries `NSAllowsLocalNetworking`, as the
  MACOS-011 loopback candidates did. Alternative: a temporary public GitHub
  prerelease, never latest, deleted afterwards (MACOS-011 `D1`).
- `D2` Refusal copy: title "This version is for Intel Macs", body "This Mac
  has Apple silicon. Download Posato for Apple silicon.", buttons **Download** and **Quit**.
- `D3` Whether the arm64 candidate also gets an install-and-launch smoke on
  `ventura`. Recommended: yes, through setup with helper registration,
  where the evaluation failed on macOS 13; it does not replace the
  exception.
