package app.posato.control.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class RepoLayout(
    val root: Path
) {
    val gradlew: Path = root.resolve("gradlew")
    val localProperties: Path = root.resolve("local.properties")
    val verificationDirectory: Path = root.resolve("build").resolve("verification")
    val runsDirectory: Path = verificationDirectory.resolve("runs")
    val latestRunLink: Path = verificationDirectory.resolve("latest")
    val stateFile: Path = verificationDirectory.resolve("state.json")
    val nativeDirectory: Path = verificationDirectory.resolve("native")
    val driverDirectory: Path = verificationDirectory.resolve("driver")
    val toolDirectory: Path = root.resolve("tools").resolve("posato-control")
    val accessibilityBridgeSource: Path = toolDirectory.resolve("native").resolve("macos").resolve("PosatoAxBridge.swift")
    val accessibilityBridgeBinary: Path = nativeDirectory.resolve("posato-ax-bridge")
    val iosProject: Path = root.resolve("iosApp").resolve("iosApp.xcodeproj")
    val driverProject: Path = toolDirectory.resolve("ios-driver").resolve("PosatoDriver.xcodeproj")
    val stagedDesktopApplication: Path = root
        .resolve("desktopApp")
        .resolve("build")
        .resolve("compose")
        .resolve("binaries")
        .resolve("main")
        .resolve("development-package")
        .resolve("Posato.app")

    fun derivedData(name: String): Path = verificationDirectory.resolve("derived-data").resolve(name)

    fun relativize(path: Path): String = if (path.startsWith(root)) root.relativize(path).toString() else path.toString()

    companion object {
        private const val ROOT_MARKER = "rootProject.name = \"Posato\""

        fun discover(start: Path = Path.of("").toAbsolutePath()): RepoLayout {
            System.getenv("POSATO_REPO_ROOT")?.let { override ->
                val candidate = Path.of(override).toAbsolutePath()
                if (isRoot(candidate)) return RepoLayout(candidate)
            }
            var current: Path? = start
            while (current != null) {
                if (isRoot(current)) return RepoLayout(current)
                current = current.parent
            }
            throw ControlException(
                ErrorCode.COMMAND_FAILED,
                "The Posato repository root was not found above $start.",
                "Run the tool from inside the repository checkout or set POSATO_REPO_ROOT.",
            )
        }

        private fun isRoot(candidate: Path): Boolean {
            val settings = candidate.resolve("settings.gradle.kts")
            return settings.exists() && Files.isRegularFile(settings) && settings.readText().contains(ROOT_MARKER)
        }
    }
}
