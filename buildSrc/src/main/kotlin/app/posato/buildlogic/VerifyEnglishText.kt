package app.posato.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Rejects Polish letters in the repository's own text. AGENTS.md asks for English everywhere, and agents twice quoted
 * the maintainer's Polish orders in execution records, which only a completed-change review caught (release 1.4
 * retro). A file that must keep such a letter, such as a third-party copyright holder's name, is listed with a reason.
 */
abstract class VerifyEnglishText : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val textFiles: ConfigurableFileCollection

    /** Repository-relative path to the reason it may contain Polish letters. */
    @get:Input
    abstract val exceptions: MapProperty<String, String>

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        check(POLISH.containsMatchIn("Za\u017c\u00f3\u0142\u0107") && !POLISH.containsMatchIn("Pause. Then choose.")) {
            "The Polish-letter pattern no longer detects its own fixture."
        }
        val root = repositoryDirectory.get().asFile
        val allowed = exceptions.get()
        val findings = textFiles.files
            .map { it to it.relativeTo(root).invariantSeparatorsPath }
            .filter { (_, path) -> path !in allowed }
            .sortedBy { (_, path) -> path }
            .flatMap { (file, path) ->
                file.readLines().withIndex().filter { (_, line) -> POLISH.containsMatchIn(line) }.map { (index, _) -> "$path:${index + 1}" }
            }
        if (findings.isNotEmpty()) {
            throw GradleException(
                findings.joinToString(prefix = "Polish letters found; write repository content in English:\n", separator = "\n") { "- $it" } +
                    "\nTranslate quoted orders and mark them as a translation. A file that must keep such a letter is listed " +
                    "in verifyEnglishText's exceptions with its reason.",
            )
        }
    }

    private companion object {
        // Escaped, so this file passes its own check.
        val POLISH = Regex("[\u0105\u0107\u0119\u0142\u0144\u00f3\u015b\u017a\u017c\u0104\u0106\u0118\u0141\u0143\u00d3\u015a\u0179\u017b]")
    }
}
