# Build variants of the Mac application

A map of the Gradle tasks that build each macOS package and the checks that
guard them, so a change to signing, channels, architectures, or the
verification seams starts from the right lines. `desktopApp/build.gradle.kts`
is long; the pointers below name where each piece lives. Release steps are in
[releasing](releasing.md); signing material is in
[Apple provisioning](apple-provisioning.md).

## Properties that choose the variant

| Property | Effect | Where |
| --- | --- | --- |
| `posatoMacOsArchitecture=x86_64` | Intel build: x86-64 Temurin runtime and native leaves, `appcast-intel.xml`. Needs Rosetta 2 on the host; configuration refuses without it. | `desktopApp/build.gradle.kts:1363`, `desktopApp/build.gradle.kts:1423`, `desktopApp/build.gradle.kts:1428` |
| `posatoMacOsUpdateChannel=release\|candidate` (+ `posatoMacOsUpdateFeedUrl`, `posatoMacOsUpdatePublicKey` for candidates) | Picks the feed URL and key written into `Info.plist`; a Developer ID build refuses without a channel. | `desktopApp/build.gradle.kts:1593`, `desktopApp/build.gradle.kts:1815` |
| `posatoMacOsAllowRosetta=true` | Verification-only switch that lets the Intel build run under Rosetta in an arm64 guest; refused on the release channel and on arm64. | `desktopApp/build.gradle.kts:1600` |
| `posatoMacOsVerificationSeams=true` | Compiles the SYNC-021 seams into the companion and adds their `Info.plist` key; configuration refuses any channel, any release identity, and any development identity other than `-` or `Apple Development:`. | `desktopApp/build.gradle.kts:1608`, `desktopApp/build.gradle.kts:1622`, `macosSyncCompanion/build.gradle.kts:21` |
| `posatoMacOsSigningIdentity` | Development signing identity (`-` for ad hoc). | `desktopApp/build.gradle.kts:1720` |
| `posatoMacOsReleaseSigningIdentity`, `posatoMacOsSyncDeveloperIdProfile` | Developer ID signing of the release package and its companion. | `desktopApp/build.gradle.kts:1837` |
| `posatoMacOsBuildNumber`, `posatoMacOsPreviousBuildNumber`, `posatoMacOsReleaseNotes` | Build number, the floor a release must exceed, and the signed feed notes. | `desktopApp/build.gradle.kts:1911` |

## Tasks by variant

- **Development package** (what `posato-control build -t desktop` stages):
  `createDistributable` → `embedMacOsSyncCompanion` (`desktopApp/build.gradle.kts:1688`)
  → `signMacOsDevelopmentPackage` (`desktopApp/build.gradle.kts:1720`) →
  `stageMacOsDevelopmentPackage` (`desktopApp/build.gradle.kts:1763`) →
  `verifyMacOsDevelopmentPackaging` (`desktopApp/build.gradle.kts:1772`).
- **Release or candidate package:** `checkMacOsUpdateChannel`
  (`desktopApp/build.gradle.kts:1815`) → `stageMacOsReleasePackage` (`desktopApp/build.gradle.kts:1826`) →
  `signMacOsReleasePackage` (`desktopApp/build.gradle.kts:1837`) →
  `verifyMacOsReleasePackaging` (`desktopApp/build.gradle.kts:1860`, `release=true`, seams
  fixed to `false`) → `notarizeMacOsReleaseApplication`
  (`desktopApp/build.gradle.kts:1879`) → `packageMacOsReleaseDmg` (`desktopApp/build.gradle.kts:1890`) →
  `notarizeMacOsRelease` (`desktopApp/build.gradle.kts:1900`) → `generateMacOsUpdateFeed`
  (`desktopApp/build.gradle.kts:1911`).
- **Intel:** the same tasks with `posatoMacOsArchitecture=x86_64`, plus
  `checkIntelNativeLeaves` (`desktopApp/build.gradle.kts:1547`) in `quality`.
- **Companion:** one Swift build, `buildSwiftRelease` (`macosSyncCompanion/build.gradle.kts:23`),
  embedded by both the development and the release package; the seams
  property is its input. `swiftBuildIntel` (`macosSyncCompanion/build.gradle.kts:135`) and `swiftTest`
  (`macosSyncCompanion/build.gradle.kts:115`) cover Intel and the seam code in `quality`.

## Checks that guard a package

- `VerifyMacOsDevelopmentPackaging` (`desktopApp/build.gradle.kts:179`) serves both the
  development and the release check. Its seam scan runs first
  (`desktopApp/build.gradle.kts:225`); it then checks signing, versions, icon, runtime, and
  the Rosetta switch (`desktopApp/build.gradle.kts:347`).
- The DMG reader in `generateMacOsUpdateFeed` scans the mounted image for the
  seams first, on every channel (`desktopApp/build.gradle.kts:1278`).
- A task graph that holds a Developer ID task refuses the seams property
  (`desktopApp/build.gradle.kts:1958`).
- The x86-64 configuration refuses a host without Rosetta 2
  (`desktopApp/build.gradle.kts:1423`); `posato-control doctor` reports it as `host.rosetta`.
