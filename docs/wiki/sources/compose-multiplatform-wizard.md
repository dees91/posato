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

`user-confirmed` (2026-08-25): generation occurs in an isolated temporary
directory. A reviewed subset is then adapted into the repository; the archive
must not be expanded over the checkout. This prevents case-insensitive
replacement of the existing `README.md`, replacement of repository
instructions, and accidental acceptance of generated branding or identifiers.

The accepted wizard configuration is:

| Setting | Accepted value | Reason |
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

The generated module is renamed from `sharedUI` to `shared`. Generated sample
UI, theme, font, links, icons, comments, unused packaging, and README content
are removed or replaced. Exact bundle identifiers use `app.posato.ios` and
`app.posato.macos` rather than generated suffixes. The complete accepted import
boundary is authoritative in
[ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md).

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

## Foundation import observation

`observed` (2026-08-26): the supplied Wizard archive has SHA-256
`640c003612495bad53d17807dbb8bc1eb4a3ef320dcda113cb4510ad4f9e92f1`.
It contained Android, Web, sample UI, icons, optional libraries, and a local
properties file in addition to the Apple hosts. The import retained and
adapted only the wrapper conventions and Xcode-host skeleton. Posato owns the
fresh `:shared` and `:desktopApp` definitions, shared shell, Metro graphs, test,
identifiers, versions, and deployment settings. No PoC source was copied.

`observed` (2026-08-26): Compose Multiplatform 1.12.0 with Skiko 0.150.1 and
Compose Multiplatform 1.11.1 with Skiko 0.144.6 both supplied
`libicu.icudtl_dat.o` for the arm64 iOS Simulator with `minos 18.5`. Linking
either at Posato's accepted iOS 18.0 target produced an external-object linker
warning despite the compatibility guide's broader iOS support claim. Stable
Compose Multiplatform 1.10.3 uses Skiko 0.9.37.4 whose equivalent object
declares `minos 17.2`; it links without that warning at iOS 18.0. Posato
therefore pins Compose 1.10.3 for PR #1 rather than raising the accepted
deployment target or suppressing the warning.

`observed` (2026-08-26): Gradle 9.5.0, Kotlin 2.4.10, Compose Multiplatform
1.10.3, Metro 1.4.2, JDK 21, JVM 17 bytecode, and local Xcode 26.6 passed the
shared UI test, desktop and iOS graph compilation, an unsigned iOS Simulator
build and launch, and a macOS application-image build and launch. Both hosts
rendered the exact four-line `DESIGN.md` shell. The Xcode result is bounded
local evidence because Kotlin 2.4.10 documents support only through Xcode
26.4.

`observed` (2026-08-26): the initial runtime guard alone let Android Studio
select its bundled JBR 25 as the project daemon and then fail before the
maintainer could persist JDK 21 through the IDE. The checked-in Gradle daemon
JVM criteria now pins Eclipse Temurin 21 and records Foojay provisioning URLs.
The Gradle-owned Foojay resolver convention plugin 1.0.0 generates those URLs;
it is a settings-only Apache-2.0 build dependency with no application runtime
role. A build launched by Android Studio's JBR 25 selected a Temurin 21 daemon
and passed the shared tests, both iOS compilations, and macOS application-image
packaging. Compose Desktop rejected Homebrew's JDK distribution for packaging,
so Posato retains the vendor criterion rather than disabling that safeguard.

## Evidence limits

The wizard repository publishes no release tags at the reviewed date, and its
`master` branch changes independently of Posato. Displayed versions and output
must therefore be re-audited at generation time. Posato pins its own wrapper,
plugins, libraries, and toolchain after generation and records the exact
wizard revision or archive hash used for provenance.
