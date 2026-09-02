# `QUALITY-002`: Drive, inspect, and reset the Posato apps from one agent-facing CLI

- **Review tier:** `standard`
- **Tier reason:** The change adds on-demand developer tooling: a new Gradle
  module, a single-file Swift accessibility bridge, and a separate XCUITest
  driver project. It changes no product source, entitlement, signing
  configuration, helper, or schema, and the aggregate gate gains only static
  checks and unit tests for the tool itself.
- **Dependencies:** completed `FOUNDATION-001`, `MACOS-006`
- **Integration group:** `PR-VERIFICATION-DRIVER`
- **Authority:** [MVP roadmap revision 6](../mvp-roadmap.md) row
  `QUALITY-002` and the maintainer's explicit tooling request on 2026-09-02

## Outcome

A coding agent builds, launches, drives, inspects, screenshots, and resets the
macOS application and the iOS application on the Simulator and on the
connected iPhone through one local CLI that returns machine-readable JSON, so
the agent can verify its own change without a maintainer in the loop.

## Boundaries

- Add the Kotlin/JVM module `:posato-control` under `tools/posato-control`
  with lifecycle, inspection, interaction, evidence, state, and cleanup
  commands for the `desktop`, `simulator`, and `device` targets; a
  single-file Swift accessibility bridge for macOS; and a separate Xcode
  driver project whose UI-testing bundle drives the installed iOS application
  by bundle identifier.
- Reuse the existing packaging chain and run recipes. Do not change any
  product module, `iosApp/`, `shared/`, `desktopApp/`, `macosHelper/`,
  entitlement, signing, or helper. Product `testTag`s are a follow-up after
  `TARGETS-004` merges.
- The tool is a platform-build and manual-inspection driver, not an automated
  UI test suite. Nothing it drives runs in `./gradlew quality` or CI; the
  aggregate gate gains only the module's ktlint, Detekt, unit tests, and
  Swift format check.
- Signing identities, team identifiers, device identifiers, and personal
  paths live only in ignored local configuration; the CLI reports their
  presence, never their values. All artifacts stay under the ignored
  `build/verification/` directory, and tracked evidence remains categorical.
- Destructive commands support `--dry-run`, require explicit confirmation,
  act only on state the tool created or the application's own data files,
  and never touch `/Library/Application Support/Posato`.
- Write surface: `tools/posato-control/**`, `settings.gradle.kts`,
  `gradle/libs.versions.toml`, the root `build.gradle.kts` `quality` list,
  `.gitignore`, `docs/development/README.md`, this brief and its record, the
  roadmap row, one wiki-log entry, and one wiki observation.
  `.research/blocker` stays read-only evidence.

## Acceptance

- `AC-01` — `doctor`, `build`, `install`, `launch`, `status`, `screenshot`,
  `logs`, `terminate`, and `reset` succeed on the desktop, the Simulator, and
  the connected iPhone, each returning the documented JSON envelope and exit
  code.
- `AC-02` — The `add-website` scenario passes on all three targets from the
  same scenario file, the resulting row is visible in the application, and
  the desktop and Simulator local databases confirm it.
- `AC-03` — `reset` restores the empty state on every target, its
  `--dry-run` lists exactly the files it would delete, and it refuses without
  confirmation.
- `AC-04` — `./gradlew quality` passes with the module's checks included and
  no new suppression; the driver, bridge, and application builds run only on
  demand.
- `AC-05` — No signing identity, team identifier, device identifier,
  screenshot, or personal path enters tracked files.

## Verification

- `./gradlew :posato-control:test :posato-control:detekt
  :posato-control:ktlintCheck :posato-control:swiftFormatCheck`, then
  `./gradlew quality`.
- The end-to-end command sequence per target recorded in the execution
  record, including the negative paths for a missing permission, a missing
  element, an unconfirmed reset, and an unsupported target.
- `git diff --check` and a scan of the tracked diff for identifiers and
  personal paths.
- One independent completed-change review with evidence, then at most one
  hosted pass under the `AGENTS.md` budget.

## Decisions or blockers

- Kotlin/JVM orchestrator with thin Swift helpers (maintainer decision on
  2026-09-02) so later Android and Linux backends reuse the same CLI.
- The iOS driver lives outside `iosApp.xcodeproj` to keep product signing
  untouched and to stay clear of the open `TARGETS-004` write surface.
- Escalate to High-risk before implementation if the device driver needs any
  entitlement or explicit capability.
