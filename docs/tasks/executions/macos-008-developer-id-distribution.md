# Execution: `MACOS-008`

- **Brief:** [macos-008-developer-id-distribution.md](../specifications/macos-008-developer-id-distribution.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent plan-review agent
- **Branch:** `feature/macos-008-developer-id`
- **Updated:** 2026-09-15

## Decisions

`user-confirmed` 2026-09-14 and 2026-09-15:

- **Package.** The release ships as a signed, notarized, and stapled DMG, with an Eclipse Temurin 21 runtime. There is no updater (ADR 0004). The maintainer creates the Developer ID Application certificate.
- **Build number.** A positive integer passed as `posatoMacOsBuildNumber`. The release task has no default and fails without the value. It rises with each candidate and is never tracked. Development packaging keeps `1`.
- **Update.** The only supported update is to quit, replace the app, and open it, with no stop action in the UI.
- **Removal.** The application has no Disable or Remove entry. Supported in-app removal moves to the new roadmap row `MACOS-009`. An actual companion launch under the release signature transfers to `SYNC-017`.
- **ADR 0004.** The clarification in step 10 is reviewed in this PR.

## Starting observations

`observed` at `e5bd3dc`:

- **Signing.** `SignMacOsDevelopmentPackage` signs inside-out, in place, with `--options runtime --timestamp=none`. The task is never up to date. Application entitlements are JIT-only under Apple Development. The companion gets the template entitlements plus an embedded development profile.
- **Peers.** Helper and daemon peers require `anchor apple generic`, the exact identifier, and the team OU (`CodeSigning.swift`). The JVM verifiers compare the identifier and team only. `inferred`: a Developer ID signature from the same team satisfies all of them.
- **Version and icon.** `1.0.0` is hard-coded in two Compose `packageVersion` values and in the helper and companion `Info.plist`, whose `CFBundleVersion` is `1`. About reads `jpackage.app-version`. No `iconFile` is set, so jpackage's default icon ships.
- **Runtime.** Compose has no `javaHome`, so jpackage uses the Gradle daemon JVM, pinned to Adoptium 21 in `gradle/gradle-daemon-jvm.properties`. The bundled runtime is 21.0.12.1 with a `legal/` directory. The jlink `release` file lacks `IMPLEMENTOR`.
- **Notices.** `shared` already copies `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` into Compose resources (`files/legal`) in the shared jar.
- **Jars.** They contain three Mach-O entries: unsigned x86_64 `libskiko-macos-x64.dylib`, unsigned `org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib`, and the signed arm64 SQLite library. The existing walk covers the runtime's 28 Mach-O files and the launcher.
- **Lifecycle.**
  - The helper registers the daemon only when it is not enabled.
  - On quit, the helper reads end-of-input and sends Restore. Closing the window also runs `MacOsHelperClient.close()`, while Cmd-Q likely exits without it. If Restore fails, the daemon restores on disconnect or when the lease expires.
  - The daemon exits with success about one second after it reaches `Idle` with no connections.
  - The launchd plist has `KeepAlive.SuccessfulExit` false and an on-demand Mach service.
- **Companion.** It starts only on a sync exchange. codesign and notarization do not check entitlements against the profile; launch does.
- **Tooling.** `quality` depends on the ad-hoc `verifyMacOsDevelopmentPackaging`. `posato-control` `build` and `launch` target the staged `development-package` and bypass Gatekeeper. This Mac has only an Apple Development identity; `posato.asc.*` keys exist.

## Plan

1. **Version (AC-02).**
   - Add a root `Version.xcconfig` byte-identical to `IOS-003`'s: `MARKETING_VERSION = 1.0.0` and a newline.
   - One strict parser, shared by `desktopApp`, `:macosHelper`, and `:macosSyncCompanion`, accepts exactly that line shape and fails the build otherwise.
   - The version feeds both Compose package versions. The build number feeds `packageBuildVersion`.
   - The helper and companion plist copies substitute both values and declare them as `inputs.property`.
2. **Icon.** Set `macOS.iconFile` to `Config/Posato.icns`.
3. **Release runtime (AC-03).**
   - Set Compose `javaHome` from a Java 21 toolchain with vendor `ADOPTIUM`; development uses it too.
   - The release task checks `IMPLEMENTOR="Eclipse Adoptium"` and `JAVA_VERSION` in that JDK's `release` file and records the version.
   - `THIRD_PARTY_NOTICES.md` names Eclipse Temurin 21.
4. **Release staging and signing.**
   - Stage the embedded distributable into a separate `release-package/Posato.app`, using `mustRunAfter(signMacOsDevelopmentPackage)`.
   - Remove the two non-arm64 Mach-O jar entries in both modes.
   - Give the existing signing task a release mode: Developer ID identity, `--timestamp`, the same JIT-only application entitlements, and companion template entitlements plus `com.apple.developer.icloud-container-environment` set to `Production`.
   - Embed the Developer ID profile.
   - The profile path, the identity, and the `posato.asc.*` values are untracked `@Internal` properties.
5. **Release verification (AC-01–AC-03).** The existing verifier gets a release mode that checks:
   - **Signatures:** every signed item has a `Developer ID Application` leaf, one team, a secure `Timestamp=`, and hardened runtime. No item has `get-task-allow`, and no jar holds an unsigned Mach-O.
   - **Embedded profile** (`security cms -D`):
     - it is a Developer ID profile (`ProvisionsAllDevices`, no `ProvisionedDevices`), unexpired, from the signing team;
     - its `Entitlements` allow every signed companion entitlement, with keychain wildcards expanded;
     - that covers the exact `<team>.app.posato.macos.sync` application identifier, `CloudKit` services, the container, the keychain group, and `Production`.
   - **Versions:** the application, helper, and companion `CFBundleShortVersionString` equals `Version.xcconfig`, and their `CFBundleVersion` equals the build number.
   - **Icon:** `Contents/Resources/Posato.icns` is byte-identical to the Config file.
   - **Notices:** the shared jar's `files/legal` entries match the root files, and the runtime `legal/` exists.
6. **Notarize and staple (AC-01).** A manual release task, never part of `quality`:
   1. Zip the verified app with `ditto`, submit it with `xcrun notarytool submit --wait`, and staple the app.
   2. Build the DMG with a second task of the Compose DMG type; `packageDmg` stays unchanged.
   3. Sign the DMG with `--timestamp`, submit it, and staple it.
   4. Run `stapler validate` on both. `spctl --assess --type execute` on the app must report `Notarized Developer ID`. Run `spctl --assess --type open --context context:primary-signature` on the DMG.
   5. On `Invalid`, save the `notarytool log` under `build/` and fail.

   The second submission gives the dragged-out app its own ticket. Submission IDs stay under `build/`.
7. **Maintainer resources.** One step at a time, with no new tooling:
   - the Developer ID Application certificate (in progress);
   - the Developer ID profile `Posato macOS Sync Developer ID` for `app.posato.macos.sync`, kept under `~/Library/Developer/Posato`.
8. **Export compliance.** Record an `inferred` assessment without a legal opinion. A download outside the App Store has no Apple declaration; classification and any self-classification filing stay with the maintainer.
9. **Physical acceptance (AC-04), attended.**
   - **Driver scope.** The maintainer performs the Finder steps. The agent collects evidence under `build/verification/` from shell reads, screenshots, and `posato-control` snapshots and `db` reads, never `build` or `launch`.
   - **Baseline.** The maintainer follows a one-time checklist:
     1. Quit the development build at `Idle` and delete its bundles.
     2. Run `sudo launchctl bootout system/app.posato.macos.proxy-settings` and `sudo security authorizationdb remove app.posato.macos.proxy.apply`.
     3. Back up and clear the development database with `posato-control --target desktop reset --yes`, so the release build cannot resume a linked workspace against Production. Record the deleted paths, which must include `posato-policy.db`, and the backup directory.

     Then record `sfltool dumpbtm` (including orphaned entries), `launchctl print` reporting the daemon not found, and `scutil --proxy`. `hypothesis`: an orphaned development entry does not block release registration.
   - **Install.** Download candidate 1's DMG through Safari from a local HTTP server, drag Posato to `/Applications`, and open it. Record `xattr -p com.apple.quarantine`, the Gatekeeper prompt, `spctl -a -vvv`, and a non-translocated process path.
   - **Setup and blocking.** Complete helper setup, then block the MVP-001 website and application scenarios. Sync stays off. At the end, `log show --start <baseline time> --predicate 'process == "PosatoMacOSSync"'` returns no entries for the whole run.
   - **Update to candidate 2** (build number plus one), with no UI action: start a session, quit Posato, replace it with a Safari-downloaded candidate 2, and open it.
     - `inferred` from the lifecycle observations: launchd starts the replaced `BundleProgram` on the next helper connection.
     - **Pass criteria:**
       - after quit (method recorded), `scutil --proxy` shows the restored baseline and the old daemon PID is gone;
       - after open, the daemon runs from the same program path with a new PID started after the replacement; `sudo codesign -dvvv <pid>` shows candidate 2's `Timestamp=`, and the bundle reports the new `CFBundleVersion`;
       - the helper reports `ready` and the still-running session blocks again; any repeated approval prompt is resolved by the existing setup screen and recorded;
       - after the session ends, the helper is `Idle`, `scutil --proxy` is restored, and the daemon exits.
   - **Removal** transfers to `MACOS-009`. The **companion launch** transfers to `SYNC-017`; the profile check in step 5 covers entitlement consistency.
10. **Closeout.** Update:
    - `docs/development/apple-provisioning.md` with the release command and profile;
    - `docs/wiki/topics/macos-enforcement.md` with the release-signing evidence;
    - the `TB-08`/`T-13` signing and update review handed over by `RELEASE-001`;
    - ADR 0004, after AC-04 proves the update path. A manual-download update is a quit that restores ownership, with the daemon exiting only at `Idle`. It is followed by a bundle replacement that needs no unregister or re-register while the label and `BundleProgram` stay unchanged. launchd then starts the new daemon on demand, and `ready` is still checked before Apply. A failed restore keeps the Repair path, and "RELEASE-001 concerns" becomes `MACOS-008`;
    - one wiki-log entry.

The write surface is `desktopApp/build.gradle.kts`, the helper and companion bundle tasks and plists, one shared version parser, `Version.xcconfig`, `THIRD_PARTY_NOTICES.md`, ADR 0004, and the documents above.

## High-risk plan review

- **First pass (2026-09-14, `c63472b`): `changes-required`.** The core notarization, entitlement, and two-submission approach was confirmed. Four Required findings were resolved:
  - **R1**, the icon was not wired: steps 2 and 5.
  - **R2**, the update check was insufficient: step 9 and the 2026-09-15 update decision.
  - **R3**, companion profile and launch: step 5 and the `SYNC-017` transfer.
  - **R4**, a silent build-number default: steps 1 and 5.
- **Adopted recommendations from the first pass:** the duplicate notices copy is dropped, and a shared parser, the `IMPLEMENTOR` check, `mustRunAfter`, jar stripping in both modes, Safari quarantine, the baseline, and `TB-08`/`T-13` were added.
- **Second pass (2026-09-15, `6b230c9`): `changes-required`.** R1 and R4 were confirmed resolved.
  - **R2a:** the app has no Disable or Remove entry. Resolved by the update and removal decisions and step 9.
  - **R3a:** the profile check must cover every signed entitlement. Resolved in step 5.
- **Third pass (2026-09-15, `d28b683`): `changes-required`.** R2a and R3a were confirmed, and the update chain holds. R5 (a development database could start the companion against Production) is resolved by baseline step 3 and a whole-run log query. A fourth pass tightened both: an explicit desktop target and a log query instead of `pgrep`. R6 (update criteria could not be observed, and the session is still active on open) is resolved by the step 9 pass criteria.

## Blockers and accepted risks

- **Blocker (maintainer):** the Developer ID Application certificate (in progress) and the Developer ID profile for `app.posato.macos.sync`. Signing and submission wait for both. Result, review, verification, and final sections are added at closeout.
