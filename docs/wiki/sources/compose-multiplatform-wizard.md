# Compose Multiplatform Wizard Audit

## Source and scope

- **Reviewed:** 2026-08-25
- **Provenance:** `observed`
- **Wizard:** [Compose Multiplatform Wizard](https://terrakok.github.io/Compose-Multiplatform-Wizard/)
- **Source:** [terrakok/Compose-Multiplatform-Wizard](https://github.com/terrakok/Compose-Multiplatform-Wizard)
- **Reviewed source revision:** `e639d668a45f04c2ec11b382c1ccb745b185271f`
- **License:** MIT in the reviewed source repository

This audit evaluates the wizard as an input to the Posato production skeleton.
It does not accept generated source unchanged, make the external repository a
build dependency, or replace repository-owned architecture and quality rules.

## Observed generator baseline

At the reviewed revision, the wizard displayed Kotlin 2.4.10, Compose
Multiplatform 1.12.0, Gradle 9.7.1, and Metro 1.4.2. Android Gradle Plugin 9.1.1
was also displayed, but it is not added when Android is not selected.

For an iOS and Desktop selection, the generated topology contains:

- one KMP and Compose module named `sharedUI` with `commonMain`, `commonTest`,
  `iosMain`, and `jvmMain` source sets;
- one JVM `desktopApp` entry-point module that depends on `sharedUI`; and
- one Xcode `iosApp` host that embeds and signs a static Kotlin framework.

The wizard can add a sample UI test, a generated `AGENTS.MD`, platform icons,
sample UI and theme source, and optional third-party dependencies. It also
generates a root `README.MD` and applies `.desktopApp` and `.iosApp` suffixes to
the supplied project ID.

## Posato import boundary

`user-confirmed`: the maintainer wants to use the wizard to create the initial
application skeleton.

`inferred`: generation must occur in an isolated temporary directory. A
reviewed subset is then adapted into the repository; the archive must not be
expanded over the checkout. This prevents case-insensitive replacement of the
existing `README.md`, replacement of repository instructions, and accidental
acceptance of generated branding or identifiers.

The proposed wizard configuration is:

| Setting | Proposed value | Reason |
| --- | --- | --- |
| Project name | `Posato` | Accepted public and display name |
| Project ID | `app.posato` | Accepted reverse-DNS root; final bundle IDs are corrected after generation |
| iOS | Selected | Accepted MVP platform |
| Desktop | Selected | Accepted macOS application host |
| Android | Not selected | Later platform; outside the Apple MVP |
| Web | Not selected | Outside the accepted MVP |
| Add sample tests | Not selected | Posato adds contract-specific tests rather than retaining the click-counter sample |
| Add `AGENTS.MD` | Not selected | The repository already owns authoritative agent instructions |
| Metro | Selected | `user-confirmed` Gate 4 DI choice |
| Other optional dependencies | Not selected | Dependencies enter only with the vertical slice that uses them |

After Gate 4 acceptance, the generated module is renamed from `sharedUI` to the
accepted module name. Generated sample UI, theme, font, links, icons, comments,
platform packaging, and README content are removed or replaced. Exact bundle
identifiers use the accepted `app.posato.<platform>` pattern rather than
generated suffixes.

## Current compatibility checks

- `source-claim`: the current
  [Compose compatibility guide](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html)
  lists Compose Multiplatform 1.12.0 support from iOS 14 and macOS 13 arm64,
  requires JDK 17 or later for desktop packaging, and requires the Compose
  compiler plugin version to match the Kotlin Multiplatform plugin.
- `source-claim`: the current
  [Metro compatibility table](https://zacsweers.github.io/metro/latest/compatibility/)
  includes Kotlin 2.4.10 in Metro's supported compiler window.
- `source-claim`: the current
  [Metro multiplatform guide](https://zacsweers.github.io/metro/latest/multiplatform/)
  supports Apple and JVM targets and requires a platform-specific final
  dependency graph when common and platform source sets both contribute
  bindings.

## Evidence limits

The wizard repository publishes no release tags at the reviewed date, and its
`master` branch changes independently of Posato. Displayed versions and output
must therefore be re-audited at generation time. Posato pins its own wrapper,
plugins, libraries, and toolchain after generation and records the exact
wizard revision or archive hash used for provenance.
