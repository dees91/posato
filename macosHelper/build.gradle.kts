import app.posato.buildlogic.PosatoVersion
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.Sync

plugins {
    base
}

val swiftScratchDirectory = layout.buildDirectory.dir("swift")
val helperBundleDirectory = layout.buildDirectory.dir("bundle/PosatoMacOSHelper.app")
val posatoMarketingVersion = PosatoVersion.marketingVersion(rootProject.file("Version.xcconfig"))
val posatoBuildNumber = PosatoVersion.developmentBuildNumber(providers.gradleProperty("posatoMacOsBuildNumber").orNull)

val buildSwiftRelease by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the arm64 macOS helper and proxy-settings daemon."
    inputs.files(fileTree("Sources"), "Package.swift")
    outputs.dir(swiftScratchDirectory)

    commandLine(
        "/usr/bin/xcrun",
        "swift",
        "build",
        "--configuration",
        "release",
        "--scratch-path",
        swiftScratchDirectory.get().asFile.absolutePath,
        "-Xswiftc",
        "-warnings-as-errors",
    )
}

val assembleHelperBundle by tasks.registering(Sync::class) {
    group = "build"
    description = "Assembles the unsigned nested macOS helper application."
    dependsOn(buildSwiftRelease)
    inputs.property("posatoMarketingVersion", posatoMarketingVersion)
    inputs.property("posatoBuildNumber", posatoBuildNumber)

    into(helperBundleDirectory)
    from("Resources/HelperInfo.plist") {
        into("Contents")
        rename { "Info.plist" }
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "POSATO_MARKETING_VERSION" to posatoMarketingVersion,
                "POSATO_BUILD_NUMBER" to posatoBuildNumber,
            ),
        )
    }
    from("Resources/app.posato.macos.proxy-settings.plist") {
        into("Contents/Library/LaunchDaemons")
    }
    from(swiftScratchDirectory.map { it.file("release/PosatoMacOSHelper") }) {
        into("Contents/MacOS")
    }
    from(swiftScratchDirectory.map { it.file("release/PosatoProxySettingsDaemon") }) {
        into("Contents/Resources")
    }
}

tasks.register<Exec>("swiftFormatCheck") {
    group = "verification"
    description = "Checks formatting for the native macOS helper sources."
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
    description = "Runs SwiftLint for the native macOS helper sources."
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
    description = "Runs native macOS helper tests."
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
    )
}

tasks.named("check") {
    dependsOn("swiftFormatCheck", "swiftLintCheck", "swiftTest")
}
