package app.posato.provisioning.cli

import app.posato.provisioning.store.PrepareRequest
import app.posato.provisioning.store.ReleaseInputs
import app.posato.provisioning.store.ReleaseType
import app.posato.provisioning.store.StorePreparation
import app.posato.provisioning.store.StoreStatus
import app.posato.provisioning.store.StoreSubmission
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

class StoreCommand : CliktCommand(name = "store") {
    override fun help(context: Context): String =
        "Inspect, prepare, and submit the iOS App Store version of app.posato.ios through the App Store Connect API."

    override fun run() = Unit
}

class StoreStatusCommand :
    ProvisioningCommand(
        "status",
        "Reports the iOS app's App Store versions, its builds with the next free build number, and, for --version, the " +
            "release type, attached build, en-US What's New, and screenshot delivery. Read-only.",
    ) {
    private val version by option("--version", help = "Also report this App Store version in detail, such as 1.2.0.")

    override fun execute(session: Session): JsonElement {
        val requested = version?.let(ReleaseInputs::version)
        return StoreStatus(session.storeServices()).report(requested)
    }
}

/**
 * Brings one App Store version to the state a release needs. Paths are plain strings checked inside the command, so
 * a failure goes through redaction instead of printing a personal path in a usage error.
 */
class StorePrepareCommand :
    ProvisioningCommand(
        "prepare",
        "Creates the iOS App Store version when absent, sets its release type and en-US What's New, attaches a VALID " +
            "build, and optionally replaces the iPhone 6.9-inch and iPad 13-inch screenshot sets. Changes only what differs.",
    ) {
    private val version by option("--version", help = "The marketing version, such as 1.2.0.").required()
    private val build by option("--build", help = "The build number App Store Connect already processed.").int().restrictTo(min = 1).required()
    private val whatsNew by option("--whats-new", help = "A UTF-8 text file holding the en-US What's New text.").required()
    private val release by option("--release", help = "Release automatically after approval, or manually.")
        .choice(*ReleaseType.entries.map { it.option }.toTypedArray())
        .required()
    private val screenshots by option(
        "--screenshots",
        help = "A directory with iphone-6.9/*.png and ipad-13/*.png; each set is replaced in file-name order.",
    )

    override fun execute(session: Session): JsonElement {
        val request = PrepareRequest(
            version = ReleaseInputs.version(version),
            build = build,
            whatsNew = ReleaseInputs.whatsNew(Path.of(whatsNew)),
            releaseType = ReleaseType.of(release),
            screenshots = screenshots?.let { directory -> ReleaseInputs.screenshots(Path.of(directory)) },
        )
        return StorePreparation(session.storeServices()).prepare(request)
    }
}

class StoreSubmitCommand :
    ProvisioningCommand(
        "submit",
        "Submits one iOS App Store version to App Review once it has a build and every screenshot is COMPLETE. Does " +
            "nothing when the version is already waiting for or in review.",
    ) {
    private val version by option("--version", help = "The marketing version to submit, such as 1.2.0.").required()

    override fun execute(session: Session): JsonElement {
        val requested = ReleaseInputs.version(version)
        return StoreSubmission(session.storeServices()).submit(requested)
    }
}
