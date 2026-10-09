import app.posato.buildlogic.PosatoMacOsArchitecture
import app.posato.buildlogic.PosatoVerificationSeams
import app.posato.buildlogic.PosatoVersion
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.Sync

plugins {
    base
}

val swiftScratchDirectory = layout.buildDirectory.dir("swift")
val companionBundleDirectory = layout.buildDirectory.dir("bundle/PosatoMacOSSync.app")
val posatoMarketingVersion = PosatoVersion.marketingVersion(rootProject.file("Version.xcconfig"))
val posatoBuildNumber = PosatoVersion.developmentBuildNumber(providers.gradleProperty("posatoMacOsBuildNumber").orNull)
val macOsArchitecture = PosatoMacOsArchitecture.resolve(providers.gradleProperty(PosatoMacOsArchitecture.PROPERTY).orNull)
val macOsMinimumVersion = PosatoMacOsArchitecture.MINIMUM_SYSTEM_VERSION
val swiftTriple = macOsArchitecture.swiftTriple

// Verification builds compile the ADR 0007 verification-only operations in;
// the desktop build refuses the property together with any Developer ID build.
val verificationSeams = PosatoVerificationSeams.enabled(providers.gradleProperty(PosatoVerificationSeams.PROPERTY).orNull)

val buildSwiftRelease by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the macOS synchronization companion."
    inputs.files(fileTree("Sources"), "Package.swift")
    inputs.property("swiftTriple", swiftTriple)
    inputs.property("verificationSeams", verificationSeams)
    outputs.dir(swiftScratchDirectory)

    val swiftCommand = buildList {
        addAll(
            listOf(
                "/usr/bin/xcrun",
                "swift",
                "build",
                "--configuration",
                "release",
                "--scratch-path",
                swiftScratchDirectory.get().asFile.absolutePath,
                "-Xswiftc",
                "-warnings-as-errors",
                "--triple",
                swiftTriple,
            ),
        )
        if (verificationSeams) addAll(PosatoVerificationSeams.SWIFT_FLAGS)
    }
    commandLine(swiftCommand)
}

val assembleCompanionBundle by tasks.registering(Sync::class) {
    group = "build"
    description = "Assembles the unsigned nested macOS synchronization companion."
    dependsOn(buildSwiftRelease)
    inputs.property("posatoMarketingVersion", posatoMarketingVersion)
    inputs.property("posatoBuildNumber", posatoBuildNumber)
    inputs.property("macOsMinimumVersion", macOsMinimumVersion)

    into(companionBundleDirectory)
    from("Resources/Info.plist") {
        into("Contents")
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "POSATO_MARKETING_VERSION" to posatoMarketingVersion,
                "POSATO_BUILD_NUMBER" to posatoBuildNumber,
                "POSATO_MINIMUM_SYSTEM_VERSION" to macOsMinimumVersion,
            ),
        )
    }
    from(swiftScratchDirectory.map { it.file("release/PosatoMacOSSync") }) {
        into("Contents/MacOS")
    }
}

tasks.register<Exec>("swiftFormatCheck") {
    group = "verification"
    description = "Checks formatting for the native macOS synchronization companion."
    commandLine(
        "/usr/bin/xcrun",
        "swift",
        "format",
        "lint",
        "--recursive",
        "--strict",
        "Package.swift",
        "Sources",
        "Tests",
    )
}

tasks.register<Exec>("swiftLintCheck") {
    group = "verification"
    description = "Runs SwiftLint for the native macOS synchronization companion."
    inputs.files(
        fileTree("Sources"),
        fileTree("Tests"),
        "Package.swift",
        "Package.resolved",
        ".swiftlint.yml",
    )

    commandLine(
        "/usr/bin/xcrun",
        "swift",
        "package",
        "--package-path",
        projectDir.absolutePath,
        "plugin",
        "--allow-writing-to-package-directory",
        "swiftlint",
    )
}

tasks.register<Exec>("swiftTest") {
    group = "verification"
    description = "Runs native macOS synchronization companion tests."
    inputs.files(fileTree("Sources"), fileTree("Tests"), "Package.swift")
    outputs.dir(layout.buildDirectory.dir("swift-tests"))

    commandLine(
        "/usr/bin/xcrun",
        "swift",
        "test",
        "--scratch-path",
        layout.buildDirectory.dir("swift-tests").get().asFile.absolutePath,
        "-Xswiftc",
        "-warnings-as-errors",
        // The tests cover the verification-only refusals, so they always
        // compile them; no shipped binary comes from this scratch path.
        *PosatoVerificationSeams.SWIFT_FLAGS.toTypedArray(),
    )
}

tasks.register<Exec>("swiftBuildIntel") {
    group = "verification"
    description = "Compiles the synchronization companion for x86-64 on the macOS minimum."
    inputs.files(fileTree("Sources"), "Package.swift")
    outputs.dir(layout.buildDirectory.dir("swift-x86_64"))

    commandLine(
        "/usr/bin/xcrun",
        "swift",
        "build",
        "--configuration",
        "release",
        "--scratch-path",
        layout.buildDirectory.dir("swift-x86_64").get().asFile.absolutePath,
        "--triple",
        PosatoMacOsArchitecture.X86_64.swiftTriple,
        "-Xswiftc",
        "-warnings-as-errors",
    )
}

tasks.named("check") {
    dependsOn("swiftFormatCheck", "swiftLintCheck", "swiftTest", "swiftBuildIntel")
}
