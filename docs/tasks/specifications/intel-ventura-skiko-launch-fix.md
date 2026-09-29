# Intel Ventura Skiko launch fix

- **Review tier:** `high-risk`
- **Reason:** The correction changes signed native code inside a notarized macOS application.
- **Dependency:** Intel evaluation candidate `ccf9c02`; `MACOS-015` remains discovery work.
- **Authority:** Maintainer's 2026-09-29 report and explicit request to diagnose and install on the nearby Intel MacBook Air.

## Outcome

The x86-64/macOS 13 evaluation application opens on the maintainer's MacBook Air instead of exiting while loading Skiko.

## Boundaries

- Correct the opt-in Intel packaging path; retain the arm64 production package.
- Preserve existing application data on the MacBook and the isolated candidate update feed.
- Do not claim full Ventura support from one successful launch.

## Acceptance

- The actual launch failure is captured before the fix, and the corrected app opens through LaunchServices on the Intel MacBook Air.
- The package verifier requires and checks the top-level x86-64 Skiko library and its signature.
- The revised DMG passes the package, notarization, Gatekeeper, and quality checks and receives a unique local filename and checksum.
- Runtime evidence and any remaining limitation are recorded without tracking raw device logs or identifiers.

## Verification

- Reproduce the failure on the physical MacBook, then launch the revised package there through the same route.
- Inspect the revised DMG and the installed app for the required x86-64 library, signatures, and Ventura deployment target.
- Run the focused packaging checks and `./gradlew quality` after the last correction.

## Decision or blocker

The maintainer explicitly requested direct work on this MacBook, overriding the repository's default VM-only rule for this evaluation. Use only the application's bundle during installation; retain its existing data.
