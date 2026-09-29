# Execution: Intel Ventura Skiko launch fix

- **Brief:** [intel-ventura-skiko-launch-fix.md](../specifications/intel-ventura-skiko-launch-fix.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Branch:** `feat/macos-intel-ventura`
- **Updated:** 2026-09-29

## Plan

1. Keep the physical-device failure log under ignored `build/verification/` and identify the missing native library.
2. Package the pinned x86-64 Skiko library at the location its loader opens, with a verifier that fails when it is absent or unsigned.
3. Rebuild and notarize the candidate, then install and launch it on the explicitly authorized MacBook Air without changing application data.

## High-risk plan review

- **Verdict:** Approved before implementation. The verifier must assert the exact top-level x86-64 Skiko path; generic scans of existing files cannot catch its absence.
- **Resolution:** Added the explicit presence check and retained the generic architecture and signature checks.

## Result

- The first installed candidate exited while loading the absent top-level `libskiko-macos-x64.dylib`.
- A corrected signed candidate loaded Skiko but the GUI crashed on the macOS 14-only `NSApplication.activate` selector in `WindowChrome.m` on Ventura 13.7.8. The same selector also existed in the updater alert path.
- Copied the signed x86-64 Skiko library from the pinned runtime JAR into `Contents/app`, and used the pre-14 AppKit activation method on macOS 13 in both native leaves.
- The final Developer ID DMG was notarized and installed on the explicitly authorized Intel MacBook Air. The process stayed active after launch through LaunchServices, and the maintainer confirmed a visible window.
- Delivered `Posato-1.2.0-Intel-Ventura-verified-20260929.dmg` and its checksum to the Desktop. SHA-256: `1f94652e0fc1ea6779b0ecdfc83dac354d24fa9420e72b5a07aa299ce9a08631`.

## Completed-change review

- **Verdict:** Approved after correction; no remaining Critical or Required findings.
- **Required finding:** The updater alert path still called the macOS 14-only activation selector.
- **Resolution:** Added a Ventura fallback in `Updater.m`, reran both package checks, and obtained focused independent re-review. The alert path was not exercised on the physical device because doing so would alter the user's update-consent state.

## Verification

- **Tested revision:** `33dd5b7` (the final packaged source tree; committed after the physical run without source changes).
- **Initial state and failure:** The installed evaluation candidate on macOS 13.7.8 exited 1 from `/Applications/Posato.app/Contents/MacOS/Posato` with `UnsatisfiedLinkError` for the absent Skiko library. The subsequent GUI `open -a /Applications/Posato.app` failed with `NSInvalidArgumentException` for `NSApplicationAWT activate`. Raw evidence is ignored under `build/verification/intel-ventura-launch-fix/`.
- **Package checks:** `:desktopApp:verifyMacOsDevelopmentPackaging` and `:desktopApp:verifyMacOsReleasePackaging` passed after the last native correction. The final mounted DMG contained 40 x86-64 Mach-O files with deployment targets no higher than macOS 13 and three bundle minimum versions of 13.0; the candidate update feed remained isolated.
- **Distribution:** `:desktopApp:notarizeMacOsRelease`, `xcrun stapler validate`, and Gatekeeper assessment passed. The transferred DMG's SHA-256 matched the Desktop artifact before installation, and the installed application passed code-signing and Gatekeeper checks on the MacBook.
- **Runtime:** `open -a /Applications/Posato.app` on the physical MacBook left Posato running after 30 seconds with no new launch exception in the inspected log; the maintainer confirmed its window was visible. Existing application data was not removed.
- **Setup observation:** Unified setup initially reached the background helper but did not complete. Ventura received the daemon approval action; Background Task Management first marked it disallowed, then enabled and allowed, while reporting no container item. `launchd` still had no `app.posato.macos.proxy-settings` service. After the maintainer restarted the Mac, Service Management submitted the daemon and the maintainer completed **Finish** with the expected macOS password dialog. Strict signatures passed for the app, helper, and daemon; HTTP/HTTPS proxies stayed disabled while idle. Evidence remains under `build/verification/intel-ventura-launch-fix/`; no registration reset or application-data change was made.
- **Quality:** `./gradlew quality` passed after the last correction. A separately observed fresh `:quality-rules:test` failure in `NativeSafeBacktickNameRuleTest` remains reproducible on unchanged main and is outside this correction.

## Final

- **Status:** `done`
- **Limit:** This proves installation, launch, and maintainer-confirmed setup on one Intel Ventura machine, not full feature coverage. Manual website/application blocking and the updater alert were not exercised on this MacBook. The Intel Ventura support decision remains open.
