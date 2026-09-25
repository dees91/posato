package app.posato.provisioning.cli

import app.posato.provisioning.asc.AscClient
import app.posato.provisioning.asc.AscHttp
import app.posato.provisioning.asc.JdkUploadTransport
import app.posato.provisioning.asc.PrivateKeyFile
import app.posato.provisioning.asc.ReviewClient
import app.posato.provisioning.asc.ScreenshotClient
import app.posato.provisioning.asc.ScreenshotUploader
import app.posato.provisioning.asc.Sleeper
import app.posato.provisioning.asc.StoreClient
import app.posato.provisioning.asc.TokenSource
import app.posato.provisioning.core.ConfigurationKey
import app.posato.provisioning.core.LocalConfiguration
import app.posato.provisioning.core.Redaction
import app.posato.provisioning.core.RepoLayout
import app.posato.provisioning.core.Subprocess
import app.posato.provisioning.core.Transcript
import app.posato.provisioning.core.UserPaths
import app.posato.provisioning.local.KeychainReader
import app.posato.provisioning.local.LocalDeviceReader
import app.posato.provisioning.store.ScreenshotReplacement
import app.posato.provisioning.store.StoreServices
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

private val HELPER_TIMEOUT: Duration = Duration.ofSeconds(60)

/**
 * Everything a command needs, built once per run.
 *
 * The redaction registry is seeded from configuration before any helper can run, so no transcript line can carry a
 * configured value even when the very first command fails. The App Store Connect client is built only when a command
 * asks for it, which is what lets `doctor` report a missing key without ever signing a token.
 */
class Session(
    private val options: GlobalOptions,
    val layout: RepoLayout = RepoLayout.discover(),
    val userPaths: UserPaths = UserPaths.discover(),
) {
    val configuration: LocalConfiguration = LocalConfiguration.load(layout)
    val redaction: Redaction = Redaction().apply {
        ConfigurationKey.entries.forEach { key -> register(configuration.value(key)) }
        // The home directory and the checkout path are not configured values, but they name a person and reach the
        // same places: a transcript line quoting a helper command, a stderr tail from a tool that echoes the file it
        // failed on, and an IOException message. Registering both here closes every one of those at once instead of
        // chasing each message, and envelopes get pasted into records where personal paths are forbidden.
        register(userPaths.home.toString())
        register(layout.root.toString())
    }

    private val transcript = Transcript { line -> if (options.verbose) System.err.println(redaction.redact(line)) }

    val subprocess: Subprocess = Subprocess(transcript, HELPER_TIMEOUT)

    val devices: LocalDeviceReader = LocalDeviceReader(subprocess) { value -> redaction.register(value) }

    val keychain: KeychainReader = KeychainReader(subprocess) { value -> redaction.register(value) }

    val developmentTeam: String
        get() = configuration.require(ConfigurationKey.DEVELOPMENT_TEAM, "Provisioning for this team")

    private val clock: Clock = Clock.systemUTC()

    private val sleeper = Sleeper { duration -> Thread.sleep(duration.toMillis()) }

    fun ascClient(): AscClient = AscClient(ascHttp())

    /** The store clients share one token source, so a release run signs at most one token per quarter hour. */
    fun storeServices(): StoreServices {
        val http = ascHttp()
        val screenshots = ScreenshotClient(http)
        return StoreServices(
            store = StoreClient(http),
            screenshots = screenshots,
            review = ReviewClient(http),
            replacement = ScreenshotReplacement(screenshots, ScreenshotUploader(JdkUploadTransport(), transcript), clock, sleeper),
        )
    }

    private fun ascHttp(): AscHttp {
        val keyId = configuration.require(ConfigurationKey.ASC_KEY_ID, "Signing an App Store Connect token")
        val issuerId = configuration.require(ConfigurationKey.ASC_ISSUER_ID, "Signing an App Store Connect token")
        val keyPath = Path.of(configuration.require(ConfigurationKey.ASC_PRIVATE_KEY_PATH, "Signing an App Store Connect token"))
        val source = TokenSource(keyId, issuerId, PrivateKeyFile.read(keyPath), clock)
        return AscHttp(source, transcript, clock, sleeper)
    }
}
