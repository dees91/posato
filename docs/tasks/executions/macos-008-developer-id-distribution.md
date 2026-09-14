# Execution: `MACOS-008`

- **Brief:** [macos-008-developer-id-distribution.md](../specifications/macos-008-developer-id-distribution.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent plan-review agent
- **Branch:** `feature/macos-008-developer-id`
- **Updated:** 2026-09-14

## Decisions

`user-confirmed` 2026-09-14:
- The package is a signed, notarized, and stapled DMG.
- The release runtime is Eclipse Temurin 21.
- Updates are a manual download of a newer notarized build installed over the old one. There is no updater (ADR 0004).
- The maintainer creates the Developer ID Application certificate.

Proposed, awaiting maintainer acceptance:
- **Build number.** A positive integer passed as `posatoMacOsBuildNumber`. The release task has no default and fails without it. Each candidate uses a higher number, and the number is never tracked. Development packaging keeps `1`.
- **Update path for AC-04.** In-app Disable, quit, replace, open, then setup.
- **Companion launch.** A companion launch under the release signature is either proven here or transferred to `SYNC-017` (see step 9).

## Starting observations

`observed` at `e5bd3dc`:

- **Signing.** `SignMacOsDevelopmentPackage` signs inside-out with `--options runtime --timestamp=none`. It signs the application in place, and the task is never up to date. Under Apple Development the application entitlements are JIT-only. The companion gets the template entitlements plus an embedded development profile.
- **Peer checks.** Helper and daemon peers require `anchor apple generic`, the exact identifier, and the team OU (`CodeSigning.swift`). Both JVM verifiers compare identifier and team only. `inferred`: a Developer ID signature from the same team satisfies all three; AC-04 proves it.
- **Version.** `1.0.0` is hard-coded in two Compose `packageVersion` values and in the helper and companion `Info.plist` (`CFBundleVersion` `1`). About reads `jpackage.app-version`.
- **Icon.** No `iconFile` is set, so the bundle ships jpackage's default icon instead of `desktopApp/Config/Posato.icns`.
- **Runtime.** Compose has no `javaHome`, so jpackage uses the Gradle daemon JVM, which `gradle/gradle-daemon-jvm.properties` pins to Adoptium 21. The bundled runtime is 21.0.12.1 with `legal/`. jlink writes its `release` file without `IMPLEMENTOR`.
- **Notices.** `shared` already copies `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` into Compose resources (`files/legal`) inside the shared jar, which the licenses screen reads.
- **Mach-O inside jars.** Exactly three entries: unsigned x86_64 `libskiko-macos-x64.dylib`, unsigned `org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib`, and the arm64 SQLite library that is already signed. The runtime's 28 Mach-O files and the launcher are covered by the existing walk.
- **Helper registration.** The helper registers the daemon only when Service Management reports it as not enabled. Replacing the bundle therefore neither re-registers nor restarts a running daemon.
- **Companion launch.** The companion starts only on a sync exchange. codesign and notarization do not check signed entitlements against the embedded profile; launch does.
- **Tooling limits.** `quality` depends on the ad-hoc `verifyMacOsDevelopmentPackaging`. `posato-control` `build` and `launch` target the staged `development-package` and bypass Gatekeeper.
- **This Mac.** Only an Apple Development identity is present. `posato.asc.*` keys exist.

## Plan

1. **Version (AC-02).**
   - Add a root `Version.xcconfig` byte-identical to `IOS-003`'s (`MARKETING_VERSION = 1.0.0` plus a newline).
   - One strict parser, shared by `desktopApp`, `:macosHelper`, and `:macosSyncCompanion`, accepts exactly one line `MARKETING_VERSION = <major>.<minor>.<patch>`. Anything else fails the build.
   - The version feeds both Compose package versions.
   - The helper and companion plist copies substitute the version and the build number, and declare both as `inputs.property`.
   - The build number feeds Compose `packageBuildVersion`.
2. **Icon.** Set `macOS.iconFile` to `Config/Posato.icns`.
3. **Release runtime (AC-03).**
   - Set Compose `javaHome` from a Java 21 toolchain launcher with vendor `ADOPTIUM`. The development package uses the same JDK.
   - The release task checks `IMPLEMENTOR="Eclipse Adoptium"` and `JAVA_VERSION` in that JDK's `release` file and records the version.
   - `THIRD_PARTY_NOTICES.md` names Eclipse Temurin 21 in its OpenJDK row.
4. **Release staging and signing.**
   - Stage the embedded distributable into a separate `release-package/Posato.app`, with `mustRunAfter(signMacOsDevelopmentPackage)`.
   - Remove the two non-arm64 Mach-O jar entries in both modes. The package is arm64-only, so no mode-specific branch is needed.
   - Extend the existing signing task with a release mode rather than copying it: Developer ID identity, `--timestamp`, the same JIT-only application entitlements, and companion entitlements from the template plus `com.apple.developer.icloud-container-environment` = `Production`.
   - The Developer ID profile is embedded. Its path, the identity, and the `posato.asc.*` values are untracked `@Internal` properties.
5. **Release verification (AC-01–AC-03).** Add a release mode to the existing packaging verifier. It checks:
   - **Signatures:** every signed item has a `Developer ID Application` leaf, one team, a secure `Timestamp=`, and the hardened-runtime flag. No item has `get-task-allow`, and no jar holds an unsigned Mach-O.
   - **Embedded profile,** decoded with `security cms -D`: a Developer ID profile (`ProvisionsAllDevices`, no `ProvisionedDevices`), not expired, with the signing team, and with entitlements covering the signed container identifier, keychain group, and the `Production` environment.
   - **Versions:** application, helper, and companion `CFBundleShortVersionString` equal `Version.xcconfig`, and their `CFBundleVersion` equals the build number.
   - **Icon:** `Contents/Resources/Posato.icns` is byte-identical to the Config file.
   - **Notices:** the shared jar's `files/legal` entries match the root files byte-for-byte, and the runtime's `legal/` is present.
6. **Notarize and staple (AC-01).** One release task, run by hand and never in `quality`:
   1. `ditto`-zip the verified app, submit it with `xcrun notarytool submit --wait`, and staple the app.
   2. Build the DMG from the stapled app with the existing Compose DMG task pointed at the release directory.
   3. Sign the DMG with `--timestamp`, submit it, and staple it.
   4. Run `stapler validate` on both. `spctl --assess --type execute` on the app must report `Notarized Developer ID`; also run `spctl --assess --type open --context context:primary-signature` on the DMG.
   5. On `Invalid`, save `notarytool log` under `build/` and fail.

   The second submission gives the dragged-out app its own stapled ticket instead of an online lookup. Submission IDs stay under `build/`.
7. **Maintainer resources.** Guide the maintainer one step at a time:
   - the Developer ID Application certificate, in progress;
   - a Developer ID profile `Posato macOS Sync Developer ID` for `app.posato.macos.sync`, kept under `~/Library/Developer/Posato`.

   No new provisioning tooling.
8. **Export compliance.** Record an `inferred` assessment without a legal opinion: a download outside the App Store has no Apple declaration, and the classification and any self-classification filing stay with the maintainer.
9. **Physical acceptance (AC-04), attended.**
   - **Driver scope.** The maintainer performs the Finder steps. The agent collects evidence under `build/verification/` with shell reads, screenshots, and `posato-control` accessibility snapshots and `db` reads only; never `build` or `launch`.
   - **Baseline.** Remove the development build through supported in-app removal. Then record `sfltool dumpbtm`, `launchctl print system/app.posato.macos.proxy-settings` (not found), and `scutil --proxy`.
   - **Install.** Download candidate 1's DMG through Safari from a local HTTP server. Open it, drag Posato to `/Applications`, and open it. Record `xattr -p com.apple.quarantine`, the Gatekeeper first-open prompt, `spctl -a -vvv`, and a non-translocated process path.
   - **Setup and blocking.** Complete helper setup, then block the MVP-001 website and application scenarios. Sync stays off.
   - **Update to candidate 2** (build number plus one), per ADR 0004:
     1. End any session and confirm ownership `Idle`.
     2. Disable in the app, and quit.
     3. Replace the app from a Safari-downloaded candidate 2 and open it.
     4. Complete setup.

     Record daemon and helper PIDs, start times, and program paths before and after, plus the running bundle's `CFBundleVersion`, helper `ready`/`Idle`, and a repeated website and application block.
   - **Removal.** Remove Posato through the supported path. It passes when `security authorizationdb read` of the Posato right fails, `launchctl print` reports the daemon not found, and `scutil --proxy` shows no Posato tuple.
   - **Companion.** The release verifier's profile check covers entitlement consistency. An actual companion launch under the release signature needs a sync exchange against Production CloudKit. Unless the maintainer names a safe trigger, it transfers to `SYNC-017` as a recorded, maintainer-accepted item.
10. **Closeout.** Update:
    - `docs/development/apple-provisioning.md` with the release command and the profile;
    - `docs/wiki/topics/macos-enforcement.md` with the release-signing evidence;
    - the outcome of the `TB-08`/`T-13` signing and update review handed over by `RELEASE-001`;
    - one wiki-log entry.

Write surface: `desktopApp/build.gradle.kts`, the helper and companion bundle tasks and plists, one shared version parser, `Version.xcconfig`, `THIRD_PARTY_NOTICES.md`, and the documents above. `IOS-003` adds the identical `Version.xcconfig` independently.

## High-risk plan review

- **Verdict:** `changes-required` (2026-09-14, plan at `c63472b`). The core notarization, entitlement, and two-submission approach was confirmed.
- **Required findings:**
  - R1: the icon is not wired.
  - R2: the AC-04 update check proved neither re-registration nor that the new code runs, and it ignored the ADR 0004 ownership precondition.
  - R3: nothing proves the companion's profile and entitlement consistency or its launch under Developer ID.
  - R4: a release could silently default the build number to `1`.
- **Resolution:**
  - R1: steps 2 and 5.
  - R2: the explicit update sequence and evidence in step 9.
  - R3: the embedded-profile verification in step 5, plus the companion-launch decision in step 9.
  - R4: no release default, plus the `CFBundleVersion` checks.
- **Recommended findings adopted:** the duplicate notices copy is dropped; one shared parser with task inputs; the JDK `IMPLEMENTOR` check; `mustRunAfter`; stripping jars in both modes; Safari quarantine, baseline, and removal criteria; `TB-08`/`T-13` closeout.

## Blockers and accepted risks

- **Blocker (maintainer):** the Developer ID Application certificate (in progress) and the Developer ID profile for `app.posato.macos.sync`. Signing and submission wait for both.

Result, completed-change review, verification, and final sections are added at closeout.
