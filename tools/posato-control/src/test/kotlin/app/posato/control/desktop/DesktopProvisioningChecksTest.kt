package app.posato.control.desktop

import app.posato.control.model.DoctorCheck
import app.posato.control.model.Severity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TEAM = "AAAAAAAAAA"
private const val OTHER_TEAM = "BBBBBBBBBB"
private val NOW: Instant = Instant.parse("2026-09-04T12:00:00Z")

private fun profile(
    applicationIdentifier: String? = "$TEAM.app.posato.macos.sync",
    teamIdentifier: String? = TEAM,
    expiresAt: Instant? = NOW.plusSeconds(86_400),
) = SyncProfile(applicationIdentifier, teamIdentifier, expiresAt)

private fun facts(
    signingIdentity: String? = null,
    signingIdentityInKeychain: Boolean = false,
    syncProfileConfigured: Boolean = false,
    syncProfileReadable: Boolean = false,
    syncProfile: SyncProfile? = null,
    developmentTeam: String? = TEAM,
    staged: Boolean = true,
    helperExecutablePresent: Boolean = true,
) = ProvisioningFacts(
    signingIdentity = signingIdentity,
    signingIdentityInKeychain = signingIdentityInKeychain,
    syncProfileConfigured = syncProfileConfigured,
    syncProfileReadable = syncProfileReadable,
    syncProfile = syncProfile,
    developmentTeam = developmentTeam,
    staged = staged,
    helperExecutablePresent = helperExecutablePresent,
    now = NOW,
)

private val developmentSigned = facts(
    signingIdentity = "Apple Development: Someone (AAAAAAAAAA)",
    signingIdentityInKeychain = true,
    syncProfileConfigured = true,
    syncProfileReadable = true,
    syncProfile = profile(),
)

private fun List<DoctorCheck>.check(id: String): DoctorCheck = single { it.id == id }

class DesktopProvisioningChecksTest {
    @Test
    fun `every one-time condition is reported as a named check`() {
        val ids = DesktopProvisioningChecks.checks(facts()).map { it.id }
        assertEquals(
            listOf("desktop.signingIdentity", "desktop.syncProfile", "desktop.helperBundle", "desktop.helperBackground"),
            ids,
        )
    }

    @Test
    fun `every check that is not ok carries one remedy`() {
        listOf(facts(), developmentSigned, facts(staged = false)).forEach { input ->
            DesktopProvisioningChecks.checks(input).filterNot { it.ok }.forEach { check ->
                assertFalse(check.hint.isNullOrBlank(), "${check.id} has no remedy")
            }
        }
    }

    @Test
    fun `an unconfigured ad-hoc checkout never reports an error`() {
        val checks = DesktopProvisioningChecks.checks(facts())
        assertTrue(checks.none { it.severity == Severity.ERROR.name.lowercase() }, checks.toString())
        assertEquals("missing", checks.check("desktop.signingIdentity").state)
        assertEquals(Severity.INFO.name.lowercase(), checks.check("desktop.signingIdentity").severity)
        assertEquals(Severity.INFO.name.lowercase(), checks.check("desktop.syncProfile").severity)
    }

    @Test
    fun `a configured identity that is absent from the keychain is an error`() {
        val checks = DesktopProvisioningChecks.checks(facts(signingIdentity = "Apple Development: Someone (AAAAAAAAAA)"))
        assertEquals(Severity.ERROR.name.lowercase(), checks.check("desktop.signingIdentity").severity)
    }

    @Test
    fun `a configured identity without a companion profile is an error`() {
        val checks = DesktopProvisioningChecks.checks(
            facts(signingIdentity = "Apple Development: Someone (AAAAAAAAAA)", signingIdentityInKeychain = true),
        )
        val check = checks.check("desktop.syncProfile")
        assertEquals(Severity.ERROR.name.lowercase(), check.severity)
        assertEquals("missing", check.state)
    }

    @Test
    fun `an unreadable, foreign, expired, or mismatched profile is an error`() {
        val broken = listOf(
            developmentSigned.copy(syncProfileReadable = false, syncProfile = null),
            developmentSigned.copy(syncProfile = profile(applicationIdentifier = "$TEAM.app.posato.macos.helper")),
            developmentSigned.copy(syncProfile = profile(expiresAt = NOW.minusSeconds(1))),
            developmentSigned.copy(syncProfile = profile(teamIdentifier = OTHER_TEAM)),
        )
        broken.forEach { input ->
            val check = DesktopProvisioningChecks.checks(input).check("desktop.syncProfile")
            assertEquals(Severity.ERROR.name.lowercase(), check.severity, check.detail)
        }
    }

    @Test
    fun `a complete development setup passes both signing checks`() {
        val checks = DesktopProvisioningChecks.checks(developmentSigned)
        assertTrue(checks.check("desktop.signingIdentity").ok)
        assertTrue(checks.check("desktop.syncProfile").ok)
    }

    @Test
    fun `a missing nested helper is a warning, not an error`() {
        val check = DesktopProvisioningChecks.checks(developmentSigned.copy(helperExecutablePresent = false)).check("desktop.helperBundle")
        assertEquals(Severity.WARN.name.lowercase(), check.severity)
    }

    @Test
    fun `the helper background approval is unknown, never guessed, and never blocking`() {
        val check = DesktopProvisioningChecks.checks(developmentSigned).check("desktop.helperBackground")
        assertEquals("unknown", check.state)
        assertFalse(check.ok)
        assertEquals(Severity.WARN.name.lowercase(), check.severity)
        assertTrue(check.hint.orEmpty().contains("posato-control launch"), check.hint)
    }

    @Test
    fun `no check leaks the configured identity, profile path, or team`() {
        val revealing = developmentSigned.copy(signingIdentity = "Apple Development: Someone (AAAAAAAAAA)")
        DesktopProvisioningChecks.checks(revealing).forEach { check ->
            val text = check.detail + " " + check.hint.orEmpty()
            assertFalse(text.contains("Someone"), check.id)
            assertFalse(text.contains(TEAM), check.id)
        }
    }
}
