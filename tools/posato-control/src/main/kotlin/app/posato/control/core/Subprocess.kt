package app.posato.control.core

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

data class ProcessOutput(
    val command: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val succeeded: Boolean
        get() = exitCode == 0

    fun requireSuccess(
        code: ErrorCode,
        what: String,
        hint: String? = null
    ): ProcessOutput {
        if (succeeded) return this
        val detail = stderr.trim().ifEmpty { stdout.trim() }.lines().takeLast(TAIL_LINES).joinToString("\n")
        throw ControlException(code, "$what failed with exit code $exitCode.\n$detail", hint)
    }

    private companion object {
        const val TAIL_LINES = 12
    }
}

fun interface Transcript {
    fun record(line: String)
}

class Subprocess(
    private val transcript: Transcript,
    private val defaultTimeout: Duration,
) {
    fun run(
        command: List<String>,
        workingDirectory: Path? = null,
        environment: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
        stdin: String? = null,
    ): ProcessOutput {
        transcript.record("$ " + command.joinToString(" "))
        val builder = ProcessBuilder(command)
        workingDirectory?.let { builder.directory(it.toFile()) }
        builder.environment().putAll(environment)
        val stdoutFile = Files.createTempFile("posato-control-stdout", ".txt")
        val stderrFile = Files.createTempFile("posato-control-stderr", ".txt")
        try {
            builder.redirectOutput(stdoutFile.toFile())
            builder.redirectError(stderrFile.toFile())
            val process = try {
                builder.start()
            } catch (exception: IOException) {
                throw ControlException(ErrorCode.COMMAND_FAILED, "Could not start ${command.first()}: ${exception.message}", cause = exception)
            }
            process.outputStream.use { stream -> stdin?.let { stream.write(it.toByteArray()) } }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                throw ControlException(ErrorCode.COMMAND_FAILED, "${command.first()} did not finish within ${timeout.toSeconds()} s.")
            }
            val output = ProcessOutput(command, process.exitValue(), Files.readString(stdoutFile), Files.readString(stderrFile))
            transcript.record("exit ${output.exitCode}")
            if (output.stderr.isNotBlank()) transcript.record(output.stderr.trimEnd())
            return output
        } finally {
            Files.deleteIfExists(stdoutFile)
            Files.deleteIfExists(stderrFile)
        }
    }

    fun captureFor(
        command: List<String>,
        duration: Duration
    ): String {
        val logFile = Files.createTempFile("posato-control-capture", ".log")
        try {
            val process = startDetached(command, logFile)
            if (!process.waitFor(duration.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy()
                if (!process.waitFor(DESTROY_GRACE_MS, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            }
            return Files.readString(logFile)
        } finally {
            Files.deleteIfExists(logFile)
        }
    }

    fun startDetached(
        command: List<String>,
        logFile: Path,
        workingDirectory: Path? = null,
        environment: Map<String, String> = emptyMap(),
    ): Process {
        transcript.record("$ " + command.joinToString(" ") + " > $logFile 2>&1 &")
        Files.createDirectories(logFile.parent)
        val builder = ProcessBuilder(command)
        workingDirectory?.let { builder.directory(it.toFile()) }
        builder.environment().putAll(environment)
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
        builder.redirectErrorStream(true)
        return try {
            builder.start()
        } catch (exception: IOException) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "Could not start ${command.first()}: ${exception.message}", cause = exception)
        }
    }

    private companion object {
        const val DESTROY_GRACE_MS = 2_000L
    }
}
