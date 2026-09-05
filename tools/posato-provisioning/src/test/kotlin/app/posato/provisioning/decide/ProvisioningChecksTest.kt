package app.posato.provisioning.decide

import app.posato.provisioning.model.AppIdentifier
import app.posato.provisioning.model.CheckState
import app.posato.provisioning.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SECRET_KEY_ID = "KEYID12345"
private const val SECRET_ISSUER = "11112222-3333-4444-5555-666677778888"
private const val SECRET_TEAM = "ABCDE12345"
private const val SECRET_UDID = "00008103-000A4D2E0A88001E"
private const val SECRET_PATH = "/Users/someone/Library/Developer/Posato/AuthKey.p8"
private const val SECRET_NAME = "Apple Development: Someone (AB12CD34EF)"

class ProvisioningChecksTest {
    @Test
    fun `reports every provisioning condition in a stable order`() {
        val ids = ProvisioningChecks.checks(unconfigured()).map { check -> check.id }

        assertEquals(
            listOf(
                "asc.keyId",
                "asc.issuerId",
                "asc.privateKey",
                "asc.privateKeyPermissions",
                "asc.team",
                "asc.token",
                "asc.bundleIds",
                "asc.certificate",
                "asc.deviceMac",
                "asc.deviceIphone",
            ) + AppIdentifier.entries.map { entry -> "provisioning.profile.${entry.bundleId}" },
            ids,
        )
    }

    @Test
    fun `makes no request and says so when nothing is configured`() {
        val checks = ProvisioningChecks.checks(unconfigured()).associateBy { check -> check.id }

        assertEquals(CheckState.MISSING.name.lowercase(), checks.getValue("asc.token").state)
        assertTrue(checks.getValue("asc.token").detail.contains("No request was attempted"))
        assertFalse(ProvisioningChecks.report(unconfigured()).ok)
    }

    @Test
    fun `gives every unmet condition an action that would clear it`() {
        listOf(unconfigured(), ready(), partial()).forEach { facts ->
            ProvisioningChecks.checks(facts).filterNot { check -> check.ok }.forEach { check ->
                assertTrue(!check.hint.isNullOrBlank(), "${check.id} has no hint")
            }
        }
    }

    @Test
    fun `is ok only when no error severity condition is missing`() {
        assertTrue(ProvisioningChecks.report(ready()).ok)

        // A missing iPhone and the four profiles other than the macOS sync one are warnings, because a run that does
        // not need them is not broken.
        val withoutIphone = ready().copy(iphone = null, profiles = ready().profiles - AppIdentifier.IOS_APPLICATION)
        assertTrue(ProvisioningChecks.report(withoutIphone).ok)

        val withoutSyncProfile = ready().copy(profiles = ready().profiles - AppIdentifier.MACOS_SYNC)
        assertFalse(ProvisioningChecks.report(withoutSyncProfile).ok)
    }

    @Test
    fun `never carries a configured or discovered value into the report`() {
        val rendered = listOf(unconfigured(), ready(), partial())
            .flatMap { facts -> ProvisioningChecks.checks(facts) }
            .joinToString(" ") { check -> "${check.detail} ${check.hint.orEmpty()}" }

        listOf(SECRET_KEY_ID, SECRET_ISSUER, SECRET_TEAM, SECRET_UDID, SECRET_PATH, SECRET_NAME).forEach { secret ->
            assertFalse(rendered.contains(secret), "the report carried $secret")
        }
    }

    @Test
    fun `names a missing App ID because the identifiers are already public`() {
        val facts = ready().copy(registeredBundleIds = ready().registeredBundleIds - AppIdentifier.MACOS_SYNC.bundleId)

        val check = ProvisioningChecks.checks(facts).single { it.id == "asc.bundleIds" }

        assertFalse(check.ok)
        assertTrue(check.detail.contains("app.posato.macos.sync"))
    }

    @Test
    fun `separates a condition it could not read from one it read as missing`() {
        val facts = ready().copy(token = TokenState.UNREACHABLE, certificate = null, keyOwnerOnly = null)
        val checks = ProvisioningChecks.checks(facts).associateBy { check -> check.id }

        listOf("asc.token", "asc.certificate", "asc.privateKeyPermissions").forEach { id ->
            assertEquals(CheckState.UNKNOWN.name.lowercase(), checks.getValue(id).state, id)
            assertEquals(Severity.WARN.name.lowercase(), checks.getValue(id).severity, id)
        }
        assertTrue(ProvisioningChecks.report(facts).ok, "an unreadable condition must not be reported as broken")
    }

    private fun unconfigured(): ProvisioningFacts = ProvisioningFacts(
        keyIdConfigured = false,
        issuerConfigured = false,
        keyPathConfigured = false,
        keyReadable = null,
        keyOwnerOnly = null,
        teamConfigured = false,
        token = TokenState.NOT_ATTEMPTED,
        registeredBundleIds = emptySet(),
        certificate = null,
        mac = null,
        iphone = null,
        profiles = emptyMap(),
    )

    private fun partial(): ProvisioningFacts = ready().copy(
        keyReadable = false,
        keyOwnerOnly = false,
        token = TokenState.REJECTED,
        certificate = CertificateOutcome.LOCAL_NOT_IN_ACCOUNT,
        mac = DeviceOutcome.DISABLED,
        iphone = DeviceOutcome.REGISTERED,
        profiles = AppIdentifier.entries.associateWith { ProfileFileState.EXPIRED },
    )

    private fun ready(): ProvisioningFacts = ProvisioningFacts(
        keyIdConfigured = true,
        issuerConfigured = true,
        keyPathConfigured = true,
        keyReadable = true,
        keyOwnerOnly = true,
        teamConfigured = true,
        token = TokenState.ACCEPTED,
        registeredBundleIds = AppIdentifier.entries.map { entry -> entry.bundleId }.toSet(),
        certificate = CertificateOutcome.MATCHED,
        mac = DeviceOutcome.ALREADY_REGISTERED,
        iphone = DeviceOutcome.ALREADY_REGISTERED,
        profiles = AppIdentifier.entries.associateWith { ProfileFileState.OK },
    )
}
