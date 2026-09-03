package app.posato.control.core

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.random.Random

class RunContext(
    val layout: RepoLayout,
    val configuration: LocalConfiguration,
    val runId: String,
    val artifactsRoot: Path,
    val verbose: Boolean,
    timeout: Duration,
) {
    val runDirectory: Path = artifactsRoot.resolve(runId)
    private val transcriptFile: Path = layout.verificationDirectory.resolve("transcript.log")
    private val recorded = mutableListOf<String>()

    val artifacts: List<String>
        get() = recorded.toList()

    val subprocess: Subprocess = Subprocess(::log, timeout)

    private val secrets: List<String> = ConfigurationKey.entries.mapNotNull { key -> configuration.value(key) }.filter {
        it.length >=
            MIN_SECRET_LENGTH
    }

    fun log(line: String) {
        val redacted = secrets.fold(line) { current, secret -> current.replace(secret, "<redacted>") }
        if (verbose) System.err.println(redacted)
        try {
            Files.createDirectories(transcriptFile.parent)
            Files.writeString(transcriptFile, "[$runId] $redacted\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (_: IOException) {
            // The transcript is best-effort evidence; a failure to write it never fails the command.
        }
    }

    fun artifactPath(vararg segments: String): Path {
        val path = segments.fold(runDirectory) { current, segment -> current.resolve(segment) }
        Files.createDirectories(path.parent)
        return path
    }

    fun recordArtifact(path: Path): Path {
        recorded.add(layout.relativize(path))
        return path
    }

    fun updateLatestLink() {
        if (!runDirectory.exists()) return
        try {
            val link = layout.latestRunLink
            if (Files.isSymbolicLink(link) || link.exists()) Files.delete(link)
            Files.createSymbolicLink(link, link.parent.relativize(runDirectory))
        } catch (_: IOException) {
            // A missing convenience link is not a failure.
        }
    }

    companion object {
        private const val SUFFIX_BOUND = 0x10000
        private const val MIN_SECRET_LENGTH = 4
        private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun newRunId(now: LocalDateTime = LocalDateTime.now()): String =
            now.format(formatter) + "-" + Random.nextInt(SUFFIX_BOUND).toString(RADIX_HEX).padStart(HEX_WIDTH, '0')

        private const val RADIX_HEX = 16
        private const val HEX_WIDTH = 4
    }
}
