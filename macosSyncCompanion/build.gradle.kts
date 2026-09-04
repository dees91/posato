import org.gradle.api.tasks.Sync

plugins {
    base
}

val swiftScratchDirectory = layout.buildDirectory.dir("swift")
val companionBundleDirectory = layout.buildDirectory.dir("bundle/PosatoMacOSSync.app")

val buildSwiftRelease by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the arm64 macOS synchronization companion."
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

val assembleCompanionBundle by tasks.registering(Sync::class) {
    group = "build"
    description = "Assembles the unsigned nested macOS synchronization companion."
    dependsOn(buildSwiftRelease)

    into(companionBundleDirectory)
    from("Resources/Info.plist") {
        into("Contents")
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
    )
}

tasks.named("check") {
    dependsOn("swiftFormatCheck", "swiftLintCheck", "swiftTest")
}
