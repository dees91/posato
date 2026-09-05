package app.posato.provisioning.cli

import app.posato.provisioning.asc.AscClient
import app.posato.provisioning.asc.PrivateKeyFile
import app.posato.provisioning.core.ConfigurationKey
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.decide.CertificateDecisions
import app.posato.provisioning.decide.CertificateOutcome
import app.posato.provisioning.decide.DeviceDecisions
import app.posato.provisioning.decide.DeviceOutcome
import app.posato.provisioning.decide.ProfileFileState
import app.posato.provisioning.decide.ProvisioningChecks
import app.posato.provisioning.decide.ProvisioningFacts
import app.posato.provisioning.decide.TokenState
import app.posato.provisioning.local.DecodedProfile
import app.posato.provisioning.model.AppIdentifier
import app.posato.provisioning.model.ApplePlatform
import app.posato.provisioning.model.DoctorReport
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists

/**
 * Reports every provisioning condition as OK, MISSING, or UNKNOWN with one action that would clear it.
 *
 * No value reaches the report. The key identifier, the issuer, the team, every device identifier, and the path of
 * the private key are all things a reader could paste somewhere; what a maintainer needs is which condition is
 * unmet and what to run, so that is all this prints.
 *
 * When the credentials are not configured, no request is made at all rather than one being attempted and failing:
 * a tool that has been told nothing has no business contacting the account.
 */
class DoctorCommand :
    ProvisioningCommand(
        "doctor",
        "Reports whether this Mac can provision Apple development resources, and what to run for anything missing.",
    ) {
    override fun execute(session: Session): JsonElement {
        val report = ProvisioningChecks.report(gather(session))
        if (!report.ok) {
            reportFailure(
                ErrorCode.CONFIGURATION_MISSING,
                "${report.checks.count { check -> !check.ok }} provisioning conditions are not met.",
                "Follow the hint on each check that is not ok, then rerun doctor.",
            )
        }
        return ProvisioningJson.pretty.encodeToJsonElement(DoctorReport.serializer(), report)
    }

    private fun gather(session: Session): ProvisioningFacts {
        val configuration = session.configuration
        val keyPath = configuration.value(ConfigurationKey.ASC_PRIVATE_KEY_PATH)?.let { value -> Path.of(value) }
        val credentialsPresent = configuration.value(ConfigurationKey.ASC_KEY_ID) != null &&
            configuration.value(ConfigurationKey.ASC_ISSUER_ID) != null &&
            keyPath != null &&
            configuration.value(ConfigurationKey.DEVELOPMENT_TEAM) != null
        val account = if (credentialsPresent) probe(session) else Account()
        return ProvisioningFacts(
            keyIdConfigured = configuration.value(ConfigurationKey.ASC_KEY_ID) != null,
            issuerConfigured = configuration.value(ConfigurationKey.ASC_ISSUER_ID) != null,
            keyPathConfigured = keyPath != null,
            keyReadable = keyPath?.let { path -> runCatching { PrivateKeyFile.read(path) }.isSuccess },
            keyOwnerOnly = keyPath?.takeIf { path -> path.exists() }?.let { path -> PrivateKeyFile.isOwnerOnly(path) },
            teamConfigured = configuration.value(ConfigurationKey.DEVELOPMENT_TEAM) != null,
            token = account.token,
            registeredBundleIds = account.bundleIds,
            certificate = account.certificate,
            mac = account.mac,
            iphone = account.iphone,
            profiles = installedProfiles(session),
        )
    }

    /**
     * One pass over the account.
     *
     * Listing the App IDs doubles as the token check: it is a read-only request that proves the signed token was
     * accepted, so no separate probe is needed and nothing is created to find out.
     */
    private fun probe(session: Session): Account {
        val client = try {
            session.ascClient()
        } catch (_: ProvisioningException) {
            return Account(token = TokenState.NOT_ATTEMPTED)
        }
        val bundleIds = try {
            client.bundleIds()
        } catch (failure: ProvisioningException) {
            return Account(token = if (failure.code == ErrorCode.ASC_UNAUTHORIZED) TokenState.REJECTED else TokenState.UNREACHABLE)
        }
        return accountState(session, client, bundleIds.mapNotNull { resource -> resource.attributes.identifier }.toSet())
    }

    private fun accountState(
        session: Session,
        client: AscClient,
        bundleIds: Set<String>
    ): Account {
        val team = session.configuration.value(ConfigurationKey.DEVELOPMENT_TEAM)
        val registered = client.devices()
        val certificate = CertificateDecisions
            .decide(session.keychain.developmentIdentities(), client.certificates(), team, Instant.now())
            .outcome
        val mac = session.devices.mac()
        val iphones = session.devices.connectedIphones()
        val decisions = DeviceDecisions.decide(listOfNotNull(mac) + iphones, registered)
        return Account(
            token = TokenState.ACCEPTED,
            bundleIds = bundleIds,
            certificate = certificate,
            mac = decisions.firstOrNull { decision -> decision.device.platform == ApplePlatform.MACOS }?.outcome,
            iphone = decisions.firstOrNull { decision -> decision.device.platform == ApplePlatform.IOS }?.outcome,
        )
    }

    private fun installedProfiles(session: Session): Map<AppIdentifier, ProfileFileState> {
        val team = session.configuration.value(ConfigurationKey.DEVELOPMENT_TEAM)
        return AppIdentifier.entries.associateWith { identifier ->
            val file = session.userPaths.posatoDeveloperDirectory.resolve(identifier.fileName)
            if (!file.exists()) return@associateWith ProfileFileState.MISSING
            val output = session.subprocess.run(listOf("/usr/bin/security", "cms", "-D", "-i", file.toString()))
            val decoded = if (output.succeeded) DecodedProfile.decode(output.stdout) else null
            state(decoded, identifier, team)
        }
    }

    private fun state(
        decoded: DecodedProfile?,
        identifier: AppIdentifier,
        team: String?
    ): ProfileFileState = when {
        decoded == null -> ProfileFileState.UNREADABLE
        decoded.applicationIdentifier?.endsWith(".${identifier.bundleId}") != true -> ProfileFileState.WRONG_APP_ID
        team != null && decoded.teamIdentifier != team -> ProfileFileState.WRONG_TEAM
        decoded.expiresAt == null || !decoded.expiresAt.isAfter(Instant.now()) -> ProfileFileState.EXPIRED
        else -> ProfileFileState.OK
    }

    private data class Account(
        val token: TokenState = TokenState.NOT_ATTEMPTED,
        val bundleIds: Set<String> = emptySet(),
        val certificate: CertificateOutcome? = null,
        val mac: DeviceOutcome? = null,
        val iphone: DeviceOutcome? = null,
    )
}
