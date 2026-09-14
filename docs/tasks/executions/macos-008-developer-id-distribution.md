# Execution: `MACOS-008`

- **Brief:** [macos-008-developer-id-distribution.md](../specifications/macos-008-developer-id-distribution.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** pending
- **Branch:** `feature/macos-008-developer-id`
- **Updated:** 2026-09-14

## Decisions

`user-confirmed` 2026-09-14: the container is a signed, notarized, and stapled DMG; the release runtime is Eclipse Temurin 21; updates are a manual download of a newer notarized build installed over the old one, with no updater (ADR 0004). The maintainer creates the Developer ID Application certificate.

Proposed, awaiting plan review and maintainer acceptance: the macOS build number is a positive integer passed as `posatoMacOsBuildNumber` when a release is built, higher than the previous candidate and never tracked; development packaging defaults to `1`.

## Starting observations

`observed` at `e5bd3dc`:

- `SignMacOsDevelopmentPackage` signs inside-out with `--options runtime --timestamp=none`. Application entitlements are JIT-only under Apple Development; the companion gets the template entitlements plus an embedded development profile.
- Helper and daemon peers require `anchor apple generic`, the exact identifier, and the team OU (`CodeSigning.swift`); both JVM verifiers compare only identifier and team. `inferred`: a Developer ID signature from the same team satisfies all of them. AC-04 proves it.
- `1.0.0` is hard-coded in two Compose `packageVersion` values and in the helper and companion `Info.plist` (`CFBundleVersion` `1`). About reads `jpackage.app-version`.
- No Compose `javaHome` is set, so jpackage uses the Gradle daemon JVM, which `gradle/gradle-daemon-jvm.properties` pins to Adoptium 21. The bundled runtime is 21.0.12.1 with `legal/`, and its `release` file has no `IMPLEMENTOR`.
- Unsigned x86_64 Mach-O files sit inside jars: `libskiko-macos-x64.dylib` in the arm64 skiko runtime jar and `org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib`. The notary service inspects archives.
- `quality` depends on the ad-hoc `verifyMacOsDevelopmentPackaging`, so release tasks stay outside `quality`.
- `posato-control` launches the staged `development-package` executable directly, bypassing LaunchServices and Gatekeeper. It cannot open a quarantined copy.
- This Mac has only an Apple Development identity. `posato.asc.*` keys exist; `posato-provisioning` creates development profiles only.

## Plan

1. **Version (AC-02).** Add a root `Version.xcconfig` byte-identical to `IOS-003`'s (`MARKETING_VERSION = 1.0.0` and a newline). `desktopApp` parses it strictly: exactly one line matching `MARKETING_VERSION = <major>.<minor>.<patch>`, and anything else fails the build. The value feeds both Compose package versions; the helper and companion bundle tasks substitute it into their `Info.plist`, together with the build number (Compose `packageBuildVersion`).
2. **Release runtime (AC-03).** Set Compose `javaHome` from a Java 21 toolchain launcher with vendor `ADOPTIUM`, so the bundled runtime no longer depends on the daemon JVM. Name Eclipse Temurin 21 in the OpenJDK row of `THIRD_PARTY_NOTICES.md`.
3. **Bundled notices (AC-03).** Copy the root `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` into `Contents/Resources/legal/` before signing, next to the Compose resources the licenses screen reads. The runtime keeps its own `legal/`.
4. **Release staging and signing.** Stage the embedded distributable into a separate `release-package/Posato.app`, so `quality` restaging cannot clobber a candidate.
   - Remove the non-arm64 Mach-O entries from the staged jars; the package is arm64-only.
   - Extend the existing signing task with a release mode rather than copying it: Developer ID identity, `--timestamp`, the same JIT-only application entitlements, and companion entitlements from the template plus `com.apple.developer.icloud-container-environment` = `Production`, with the Developer ID profile embedded.
   - The profile path comes from a new untracked Gradle property and never enters Git.
5. **Release verification (AC-01..03).** Add a release mode to the packaging verifier. It checks that:
   - every signed item has a `Developer ID Application` leaf authority, one team, a secure `Timestamp=`, and the hardened-runtime flag;
   - no item carries `get-task-allow`;
   - the companion targets Production;
   - no jar contains an unsigned Mach-O;
   - application, helper, and companion versions equal `Version.xcconfig`;
   - `Contents/Resources/legal/` matches the root files byte-for-byte, and the runtime `legal/` is present.
6. **Notarize and staple (AC-01).** One release task, run by hand and never in `quality`:
   - `ditto` zip the verified app, submit it with `xcrun notarytool submit --wait` using the `posato.asc.*` key, and staple the app;
   - build the DMG from the stapled app, sign it with `--timestamp`, submit it, and staple it;
   - run `stapler validate` on both, `spctl --assess --type execute` on the app (expect `Notarized Developer ID`), and `spctl --assess --type open --context context:primary-signature` on the DMG;
   - on `Invalid`, save `notarytool log` under `build/` and fail.
   Two submissions keep the first launch of the copied app working offline. Submission IDs stay under `build/`.
7. **Maintainer resources.** Guide the maintainer one step at a time:
   - the Developer ID Application certificate, which is in progress;
   - a Developer ID provisioning profile named `Posato macOS Sync Developer ID` for `app.posato.macos.sync`, kept under `~/Library/Developer/Posato`.
   No new provisioning tooling.
8. **Export compliance.** Record an `inferred` assessment without a legal opinion: a download outside the App Store has no Apple declaration, and the classification and any self-classification filing stay with the maintainer.
9. **Physical acceptance (AC-04), attended.** The driver cannot open a quarantined copy, so the maintainer performs the Finder steps and the agent collects evidence under `build/verification/`.
   - Remove the development build through the supported in-app removal.
   - Quarantine a copy of candidate 1's DMG, open it, drag Posato to `/Applications`, and open it.
   - Complete helper setup and block the MVP-001 website and application scenarios. Sync stays off, because Production CloudKit belongs to `SYNC-017`.
   - Install candidate 2 (build number plus one) over it and confirm one `app.posato.macos.proxy-settings` background item for the `/Applications` bundle.
   - Remove Posato through the supported path and confirm no registration or authorization right remains.
   - Evidence: `spctl`, `sfltool dumpbtm`, `launchctl print`, screenshots, and database reads.
10. **Closeout.** Update `docs/development/apple-provisioning.md` with the release command and profile, the release-signing evidence in `docs/wiki/topics/macos-enforcement.md`, and one wiki-log entry.

Write surface: `desktopApp/build.gradle.kts`, the helper and companion bundle tasks and plists, `Version.xcconfig`, `THIRD_PARTY_NOTICES.md`, and the documents above. `IOS-003` adds the identical `Version.xcconfig` independently.

## High-risk plan review

- **Verdict:** pending
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- **Blocker (maintainer):** the Developer ID Application certificate (in progress) and the Developer ID profile for `app.posato.macos.sync`. Signing and submission wait for both and for the plan review.

## Final

- **Status:** pending
- **Outcome:** pending
