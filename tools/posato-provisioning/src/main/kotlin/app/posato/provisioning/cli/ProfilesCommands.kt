package app.posato.provisioning.cli

import app.posato.provisioning.asc.AscClient
import app.posato.provisioning.core.ConfigurationKey
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.decide.CertificateDecisions
import app.posato.provisioning.decide.ProfileAction
import app.posato.provisioning.decide.ProfileDecisions
import app.posato.provisioning.local.ProfileInstaller
import app.posato.provisioning.model.AppIdentifier
import app.posato.provisioning.model.ApplePlatform
import app.posato.provisioning.model.ProfileResource
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class ProfilesCommand : CliktCommand(name = "profiles") {
    override fun help(context: Context): String = "Create and install the development profiles the Posato targets need."

    override fun run() = Unit
}

/**
 * Makes the development profile for one Posato App ID current, and installs it where the build and Xcode read it.
 *
 * `--platform` is accepted because the App ID already determines the platform: it is checked against that rather
 * than used to choose, so a mistaken flag fails instead of quietly producing a macOS profile for an iOS target.
 */
class ProfilesEnsureCommand :
    ProvisioningCommand(
        "ensure",
        "Ensures the development profile for one Posato App ID exists, is current, and is installed.",
    ) {
    private val appId by argument("app-id", help = "One of the five Posato App IDs.")
    private val platform by option("--platform", help = "Asserts the platform the App ID already implies.").choice("ios", "macos")
    private val replace by option("--replace", help = "Allow replacing a valid profile that no longer covers this Mac.").flag()

    override fun execute(session: Session): JsonElement {
        val identifier = AppIdentifier.of(appId)
        assertPlatform(identifier)
        val team = session.configuration.require(ConfigurationKey.DEVELOPMENT_TEAM, "Installing a development profile")
        val client = session.ascClient()
        val certificateId = certificateId(session, client, team)
        val bundleId = client.bundleId(identifier)?.id ?: throw ProvisioningException(
            ErrorCode.BUNDLE_ID_MISSING,
            "The account has no App ID for ${identifier.bundleId}.",
            "Register it in the developer portal, as APPLE-001 did for the other Posato App IDs.",
        )
        val deviceIds = client.devices()
            .filter { device -> device.enabled && device.attributes.platform == identifier.platform.ascName }
            .map { device -> device.id }
            .toSet()
        val profiles = client.profiles()
        val existing = profiles.firstOrNull { profile -> profile.attributes.name == identifier.profileName }
        val decision = ProfileDecisions.decide(existing, certificateId, deviceIds, Instant.now(), replace)
        if (decision.action == ProfileAction.STALE) {
            throw ProvisioningException(
                ErrorCode.PROFILE_STALE,
                "The existing ${identifier.bundleId} profile is still valid, but ${decision.reason}.",
                "Rerun with --replace to have App Store Connect issue a profile that does.",
            )
        }
        val profile = resolve(client, identifier, decision.action, existing, bundleId, certificateId, deviceIds)
        val installer = ProfileInstaller(session.subprocess, session.userPaths, team)
        val result = installer.install(identifier, profile.attributes.profileContent.orEmpty(), Instant.now())
        return buildJsonObject {
            put("profile", decision.action.name.lowercase())
            put("reason", decision.reason)
            put("fileChanged", result.changed)
            put("installedForXcode", result.installedForXcode)
            put("configures", if (identifier == AppIdentifier.MACOS_SYNC) ConfigurationKey.MACOS_SYNC_PROVISIONING_PROFILE.propertyName else null)
        }
    }

    private fun resolve(
        client: AscClient,
        identifier: AppIdentifier,
        action: ProfileAction,
        existing: ProfileResource?,
        bundleId: String,
        certificateId: String,
        deviceIds: Set<String>,
    ): ProfileResource {
        if (action == ProfileAction.REUSE && existing != null) return existing
        if (action == ProfileAction.REPLACE && existing != null) client.deleteProfile(existing.id)
        return client.createProfile(
            name = identifier.profileName,
            profileType = identifier.profileType,
            bundleIdId = bundleId,
            certificateIds = listOf(certificateId),
            deviceIds = deviceIds.toList(),
        )
    }

    private fun certificateId(
        session: Session,
        client: AscClient,
        team: String
    ): String {
        val decision = CertificateDecisions.decide(
            session.keychain.developmentIdentities(),
            client.certificates(),
            team,
            Instant.now(),
        )
        return decision.certificateId ?: throw ProvisioningException(
            ErrorCode.CERTIFICATE_MISSING,
            "No Apple Development certificate is usable on this Mac and held by the account, so a profile built now " +
                "would name a certificate this Mac cannot sign with.",
            "Run `posato-provisioning certificates ensure --create` first.",
        )
    }

    private fun assertPlatform(identifier: AppIdentifier) {
        val requested = when (platform) {
            "ios" -> ApplePlatform.IOS
            "macos" -> ApplePlatform.MACOS
            else -> null
        }
        if (requested != null && requested != identifier.platform) {
            throw ProvisioningException(
                ErrorCode.USAGE,
                "${identifier.bundleId} is a ${identifier.platform.name.lowercase()} App ID, not $platform.",
                "Drop --platform; the App ID already determines it.",
            )
        }
    }
}
