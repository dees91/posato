# PR #1: Establish the production application skeleton

- **Review tier:** `standard`
- **Tier reason:** The change introduces production build, target, test, and CI
  configuration without changing a security boundary or performing a release.
- **Dependencies:** `APPLE-001`
- **Integration group:** `PR #1`
- **Authority:** `FOUNDATION-001`, `QUALITY-001`, and `CI-001` in the accepted
  MVP roadmap

## Outcome

The Apple-only Posato skeleton renders the accepted shared Compose shell on
macOS and iOS, exposes one local aggregate quality gate, and verifies the same
credential-free boundary in CI.

## Boundaries

- Add only `:shared`, `:desktopApp`, and the `iosApp` Xcode host with
  `app.posato.macos` and `app.posato.ios` identifiers.
- Sanitize the reviewed Compose Multiplatform Wizard archive rather than
  importing it over the repository or adopting its optional targets and sample.
- Use the architecture, design, dependency, quality, and provenance boundaries
  accepted for PR #1.
- Do not add Android, Web, enforcement, synchronization, persistence,
  navigation, state-holder, helper, extension, signing, or release behavior.

## Acceptance

- `AC-01` — The macOS application runs the exact four-line shell from
  `DESIGN.md`, and the iOS Simulator application builds and renders the same
  shared composable.
- `AC-02` — `:shared` has one common graph contract and final validated Metro
  graphs for iOS and desktop. The static shell introduces no business behavior
  that warrants an automated test.
- `AC-03` — Stable compatible Kotlin, Compose Multiplatform, Gradle, Metro,
  JDK, JVM bytecode, Xcode, and deployment versions are selected and pinned.
- `AC-04` — One documented local command runs formatting, analysis, warning,
  test, build, and report checks applicable to the introduced surfaces.
- `AC-05` — Credential-free CI runs the aggregate gate and no imported file
  contains private signing, device, path, or account data.

## Verification

- Compile both Metro platform graphs and run tests only for behavior-bearing
  logic introduced by the increment.
- Build and launch the macOS shell, then build and inspect the iOS Simulator
  shell without code signing.
- Run the aggregate local quality command and its CI workflow from a clean
  checkout-equivalent state.
- Compare the retained topology with the wizard archive identified in the
  execution record and review the final integrated change once.

## Decisions or blockers

- Exact quality commands and CI jobs remain owned by `QUALITY-001` and
  `CI-001` inside this shared execution cycle.
