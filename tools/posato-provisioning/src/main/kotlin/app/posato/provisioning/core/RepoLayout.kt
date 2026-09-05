package app.posato.provisioning.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/** The checkout this tool reads its configuration from. It writes nothing inside the repository. */
class RepoLayout(
    val root: Path
) {
    val localProperties: Path = root.resolve("local.properties")

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
            throw ProvisioningException(
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
