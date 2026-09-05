package app.posato.provisioning.local

import app.posato.provisioning.core.CommandRunner
import app.posato.provisioning.core.ProcessOutput

/** Replays recorded tool output so the file-writing paths run without a keychain, a network, or a real profile. */
class RecordingRunner(
    private val reply: (List<String>) -> ProcessOutput
) : CommandRunner {
    val commands = mutableListOf<List<String>>()

    override fun run(command: List<String>): ProcessOutput {
        commands.add(command)
        return reply(command)
    }

    companion object {
        fun succeeding(stdout: String): RecordingRunner = RecordingRunner { ProcessOutput(0, stdout, "") }

        fun failing(stderr: String): RecordingRunner = RecordingRunner { ProcessOutput(1, "", stderr) }
    }
}
