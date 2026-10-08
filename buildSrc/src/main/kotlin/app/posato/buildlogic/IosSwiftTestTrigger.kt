package app.posato.buildlogic

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Tells whether `quality` needs the native Swift XCTest suites: it returns why they run, or nothing when no file
 * they guard differs from the merge base with [Parameters.baseRef], counting uncommitted and untracked files. Any
 * failure to compare, such as a missing base or Git, runs them.
 */
abstract class IosSwiftTestTrigger : ValueSource<String, IosSwiftTestTrigger.Parameters> {
    interface Parameters : ValueSourceParameters {
        val repositoryDirectory: DirectoryProperty
        val baseRef: Property<String>
        val guardedPaths: ListProperty<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String? {
        val baseRef = parameters.baseRef.get()
        val base = git("merge-base", "HEAD", baseRef)?.trim()
            ?: return "no merge base with $baseRef"
        // Without rename detection a moved file also lists its old path; -z keeps unusual paths unquoted.
        val changed = listOf(
            git("diff", "--name-only", "--no-renames", "-z", base) ?: return "git diff against $baseRef failed",
            git("ls-files", "--others", "--exclude-standard", "-z") ?: return "git ls-files failed",
        ).flatMap { it.split('\u0000') }.filter { it.isNotEmpty() }
        val guarded = parameters.guardedPaths.get()
        val match = changed.firstOrNull { path -> guarded.any { path == it || path.startsWith(it) } }
        return match?.let { "$it differs from $baseRef" }
    }

    private fun git(vararg arguments: String): String? {
        val output = ByteArrayOutputStream()
        val result = runCatching {
            execOperations.exec {
                workingDir = parameters.repositoryDirectory.get().asFile
                commandLine("git", *arguments)
                standardOutput = output
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
        }.getOrNull()
        return output.toString(Charsets.UTF_8).takeIf { result?.exitValue == 0 }
    }
}
