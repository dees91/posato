package app.posato.provisioning.core

import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit

private const val TAIL_LINES = 12

data class ProcessOutput(
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
        throw ProvisioningException(code, "$what failed with exit code $exitCode.\n$detail", hint)
    }
}

fun interface Transcript {
    fun record(line: String)
}

/**
 * The local Apple tools this command surface depends on, run with a deadline.
 *
 * Output is captured through temporary files rather than pipes so a command that writes more than a pipe buffer
 * cannot deadlock against a process this tool is already waiting on.
 */
class Subprocess(
    private val transcript: Transcript,
    private val defaultTimeout: Duration,
) {
    fun run(
        command: List<String>,
        timeout: Duration = defaultTimeout,
        stdin: String? = null
    ): ProcessOutput {
        transcript.record("$ " + command.joinToString(" "))
        val builder = ProcessBuilder(command)
        val stdoutFile = Files.createTempFile("posato-provisioning-stdout", ".txt")
        val stderrFile = Files.createTempFile("posato-provisioning-stderr", ".txt")
        try {
            builder.redirectOutput(stdoutFile.toFile())
            builder.redirectError(stderrFile.toFile())
            val process = try {
                builder.start()
            } catch (exception: IOException) {
                throw ProvisioningException(
                    ErrorCode.COMMAND_FAILED,
                    "Could not start ${command.first()}: ${exception.message}",
                    cause = exception,
                )
            }
            process.outputStream.use { stream -> stdin?.let { stream.write(it.toByteArray()) } }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                throw ProvisioningException(ErrorCode.COMMAND_FAILED, "${command.first()} did not finish within ${timeout.toSeconds()} s.")
            }
            val output = ProcessOutput(process.exitValue(), Files.readString(stdoutFile), Files.readString(stderrFile))
            transcript.record("exit ${output.exitCode}")
            return output
        } finally {
            Files.deleteIfExists(stdoutFile)
            Files.deleteIfExists(stderrFile)
        }
    }
}
