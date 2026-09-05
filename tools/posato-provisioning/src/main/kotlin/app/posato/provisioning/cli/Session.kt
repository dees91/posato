package app.posato.provisioning.cli

import app.posato.provisioning.core.ConfigurationKey
import app.posato.provisioning.core.LocalConfiguration
import app.posato.provisioning.core.Redaction
import app.posato.provisioning.core.RepoLayout
import app.posato.provisioning.core.Subprocess
import app.posato.provisioning.core.Transcript
import app.posato.provisioning.core.UserPaths
import java.time.Duration

private val HELPER_TIMEOUT: Duration = Duration.ofSeconds(60)

/**
 * Everything a command needs, built once per run.
 *
 * The redaction registry is seeded from configuration before any helper can run, so no transcript line can carry a
 * configured value even when the very first command fails.
 */
class Session(
    private val options: GlobalOptions,
    val layout: RepoLayout = RepoLayout.discover(),
    val userPaths: UserPaths = UserPaths.discover(),
) {
    val configuration: LocalConfiguration = LocalConfiguration.load(layout)
    val redaction: Redaction = Redaction().apply {
        ConfigurationKey.entries.forEach { key -> register(configuration.value(key)) }
    }

    private val transcript = Transcript { line -> if (options.verbose) System.err.println(redaction.redact(line)) }

    val subprocess: Subprocess = Subprocess(transcript, HELPER_TIMEOUT)

    fun log(line: String) = transcript.record(line)
}
