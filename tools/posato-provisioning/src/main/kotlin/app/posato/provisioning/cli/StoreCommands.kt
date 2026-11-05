package app.posato.provisioning.cli

import app.posato.provisioning.store.PrepareRequest
import app.posato.provisioning.store.ReleaseInputs
import app.posato.provisioning.store.ReleaseType
import app.posato.provisioning.store.STORE_LOCALE
import app.posato.provisioning.store.StorePreparation
import app.posato.provisioning.store.StoreStatus
import app.posato.provisioning.store.StoreSubmission
import app.posato.provisioning.store.StoreSubtitle
import app.posato.provisioning.store.StoreUrl
import app.posato.provisioning.store.StoreWithdrawal
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

class StoreCommand : CliktCommand(name = "store") {
    override fun help(context: Context): String =
        "Inspect, prepare, submit, and withdraw the iOS App Store version of app.posato.ios through the App Store Connect API."

    override fun run() = Unit
}

class StoreStatusCommand :
    ProvisioningCommand(
        "status",
        "Reports the iOS app's App Store versions, its builds with the next free build number, and, for --version, the " +
            "release type, attached build, en-US What's New, screenshot delivery, and, for each localization and each " +
            "--locale, the required listing fields it still lacks. Read-only.",
    ) {
    private val version by option("--version", help = "Also report this App Store version in detail, such as 1.2.0.")
    private val locales by option(
        "--locale",
        help = "A locale the --version must hold, such as pl; repeat it for each. Reported even when the version lacks it.",
    ).multiple()

    override fun execute(session: Session): JsonElement {
        val requested = version?.let(ReleaseInputs::version)
        return StoreStatus(session.storeServices()).report(requested, locales.map(ReleaseInputs::locale))
    }
}

/**
 * Brings one App Store version to the state a release needs. Paths are plain strings checked inside the command, so
 * a failure goes through redaction instead of printing a personal path in a usage error.
 */
class StorePrepareCommand :
    ProvisioningCommand(
        "prepare",
        "Creates the iOS App Store version when absent, sets its release type and What's New, attaches a VALID " +
            "build, and optionally sets the description, keywords, subtitle, and support, marketing, and privacy policy " +
            "URLs and replaces the iPhone 6.9-inch and iPad 13-inch screenshot sets. --locale chooses the localization " +
            "and defaults to en-US; a missing localization is created. " +
            "With --rename-from, renames the app's one unreleased version instead of creating one. Changes only what differs.",
    ) {
    private val version by option("--version", help = "The marketing version, such as 1.2.0.").required()
    private val build by option("--build", help = "The build number App Store Connect already processed.").int().restrictTo(min = 1).required()
    private val whatsNew by option("--whats-new", help = "A UTF-8 text file holding the What's New text for --locale.").required()
    private val locale by option(
        "--locale",
        help = "The App Store localization to write, as App Store Connect names it, such as pl. Defaults to en-US.",
    ).default(STORE_LOCALE)
    private val release by option("--release", help = "Release automatically after approval, or manually.")
        .choice(*ReleaseType.entries.map { it.option }.toTypedArray())
        .required()
    private val description by option(
        "--description",
        help = "A UTF-8 text file holding the description for --locale; without it the version keeps the copied one.",
    )
    private val screenshots by option(
        "--screenshots",
        help = "A directory with iphone-6.9/*.png and ipad-13/*.png; each set is replaced in file-name order.",
    )
    private val subtitle by option(
        "--subtitle",
        help = "The subtitle for --locale, at most 30 characters; it is set in the App Information this version can still change.",
    )
    private val keywords by option(
        "--keywords",
        help = "A UTF-8 text file holding the comma-separated keywords for --locale, at most 100 characters.",
    )
    private val supportUrl by option("--support-url", help = "The support URL for --locale, such as https://posato.app/pl/support/.")
    private val marketingUrl by option("--marketing-url", help = "The marketing URL for --locale, such as https://posato.app/pl/.")
    private val privacyUrl by option(
        "--privacy-url",
        help = "The privacy policy URL for --locale, such as https://posato.app/pl/privacy/, set in the editable App Information.",
    )
    private val renameFrom by option(
        "--rename-from",
        help = "When --version does not exist, rename this editable version to it, provided it is the only unreleased one.",
    )

    override fun execute(session: Session): JsonElement {
        val request = PrepareRequest(
            version = ReleaseInputs.version(version),
            build = build,
            whatsNew = ReleaseInputs.whatsNew(Path.of(whatsNew)),
            releaseType = ReleaseType.of(release),
            screenshots = screenshots?.let { directory -> ReleaseInputs.screenshots(Path.of(directory)) },
            description = description?.let { file -> ReleaseInputs.description(Path.of(file)) },
            renameFrom = renameFrom?.let(ReleaseInputs::version),
            subtitle = subtitle?.let(StoreSubtitle::input),
            locale = ReleaseInputs.locale(locale),
            keywords = keywords?.let { file -> ReleaseInputs.keywords(Path.of(file)) },
            supportUrl = supportUrl?.let { url -> StoreUrl.input(url, "--support-url") },
            marketingUrl = marketingUrl?.let { url -> StoreUrl.input(url, "--marketing-url") },
            privacyPolicyUrl = privacyUrl?.let { url -> StoreUrl.input(url, "--privacy-url") },
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

class StoreWithdrawCommand :
    ProvisioningCommand(
        "withdraw",
        "Withdraws one iOS App Store version from App Review by canceling its review submission, then waits until the " +
            "version is editable again. Does nothing when the version is already editable.",
    ) {
    private val version by option("--version", help = "The marketing version to withdraw, such as 1.1.0.").required()

    override fun execute(session: Session): JsonElement {
        val requested = ReleaseInputs.version(version)
        return StoreWithdrawal(session.storeServices()).withdraw(requested)
    }
}
