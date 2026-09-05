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
 * The local Apple tools this command surface depends on.
 *
 * The seam exists so the code that installs a profile, reads the keychain, or registers a device can be tested
 * against recorded tool output. Those are the file-writing and boundary paths the quality contract asks for tests
 * on, and none of them is reachable when the only implementation spawns a real process.
 */
fun interface CommandRunner {
    fun run(command: List<String>): ProcessOutput
}

/**
 * Runs a command with a deadline.
 *
 * Output is captured through temporary files rather than pipes so a command that writes more than a pipe buffer
 * cannot deadlock against a process this tool is already waiting on.
 */
class Subprocess(
    private val transcript: Transcript,
    private val timeout: Duration,
) : CommandRunner {
    override fun run(command: List<String>): ProcessOutput {
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
            process.outputStream.close()
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
