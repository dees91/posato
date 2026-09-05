package app.posato.control.desktop

import app.posato.control.model.DoctorCheck
import app.posato.control.model.Severity
import java.time.Instant

/** The decoded fields of a development provisioning profile that decide whether packaging can succeed. */
data class SyncProfile(
    val applicationIdentifier: String?,
    val teamIdentifier: String?,
    val expiresAt: Instant?,
) {
    companion object {
        private val applicationIdentifierPattern = Regex(
            "<key>com\\.apple\\.application-identifier</key>\\s*<string>([^<]+)</string>",
        )
        private val teamIdentifierPattern = Regex("<key>TeamIdentifier</key>\\s*<array>\\s*<string>([A-Z0-9]{10})</string>")
        private val expirationPattern = Regex("<key>ExpirationDate</key>\\s*<date>([^<]+)</date>")

        fun decode(plist: String): SyncProfile? {
            if (!plist.contains("<key>ExpirationDate</key>") && !plist.contains("com.apple.application-identifier")) return null
            return SyncProfile(
                applicationIdentifier = applicationIdentifierPattern.find(plist)?.groupValues?.get(1),
                teamIdentifier = teamIdentifierPattern.find(plist)?.groupValues?.get(1),
                expiresAt = expirationPattern.find(plist)?.groupValues?.get(1)?.let { runCatching { Instant.parse(it) }.getOrNull() },
            )
        }
    }
}

/**
 * The team a certificate signs under, read from its subject.
 *
 * `openssl x509 -noout -subject` renders a subject in two shapes depending on the implementation: LibreSSL, which
 * ships with macOS, separates fields with slashes, while OpenSSL 3 uses commas and pads the equals sign. Both are
 * accepted, because a rendering this parser fails to read would silently degrade every certificate to `unknown` and
 * the team-mismatch error would never fire.
 */
object CertificateSubject {
    private val organizationalUnit = Regex("""(?:^|[/,])\s*OU\s*=\s*([A-Za-z0-9]{10})\s*(?:[/,]|$)""")

    fun team(subject: String): String? = organizationalUnit.find(subject)?.groupValues?.get(1)
}

/**
 * Host-observable provisioning state, gathered once. Keeping it as plain data makes the check inventory testable
 * without a Mac, a keychain, or a staged bundle.
 */
data class ProvisioningFacts(
    val signingIdentity: String?,
    val signingIdentityInKeychain: Boolean,
    /** The team the configured certificate signs under, from its subject, or null when it could not be read. */
    val signingIdentityTeam: String?,
    val syncProfileConfigured: Boolean,
    val syncProfileReadable: Boolean,
    val syncProfile: SyncProfile?,
    val developmentTeam: String?,
    val staged: Boolean,
    val helperExecutablePresent: Boolean,
    /** Both the daemon binary and its launchd plist, because the helper installs the proxy service from the pair. */
    val proxyDaemonPresent: Boolean,
    val syncCompanionPresent: Boolean,
    val now: Instant,
)

/**
 * The one-time macOS provisioning conditions, each as a named check with one remedy sentence.
 *
 * Ad-hoc signing is a supported mode in which everything except the application picker works, so an unconfigured
 * identity is reported at info severity and never fails the report. Only a configuration that packaging genuinely
 * rejects — a configured Apple Development identity without a usable companion profile — is an error.
 */
object DesktopProvisioningChecks {
    const val SYNC_APPLICATION_IDENTIFIER: String = "app.posato.macos.sync"

    private const val IDENTITY_REMEDY =
        "Add posato.macos.signingIdentity=<Apple Development identity> to the ignored local.properties, then rerun `build -t desktop`."
    private const val PROFILE_REMEDY =
        "Add posato.macos.syncProvisioningProfile=<path to the untracked $SYNC_APPLICATION_IDENTIFIER development profile> " +
            "to the ignored local.properties, then rerun `build -t desktop`."

    fun checks(facts: ProvisioningFacts): List<DoctorCheck> = listOf(
        signingIdentity(facts),
        syncProfile(facts),
        helperBundle(facts),
        proxyDaemon(facts),
        syncCompanion(facts),
        helperBackground(),
    )

    private fun signingIdentity(facts: ProvisioningFacts): DoctorCheck {
        val identity = facts.signingIdentity
        return when {
            identity == null -> DoctorCheck.fail(
                "desktop.signingIdentity",
                "No macOS signing identity is configured, so the staged package is ad-hoc signed and the application picker cannot open.",
                IDENTITY_REMEDY,
                Severity.INFO,
            )

            !facts.signingIdentityInKeychain -> DoctorCheck.fail(
                "desktop.signingIdentity",
                "The configured macOS signing identity is not in the login keychain, so staging the package will fail.",
                "Install the Apple Development certificate for this Mac, or correct posato.macos.signingIdentity.",
            )

            // The certificate's own team decides what it signs under, not the identifier in its common name, so a
            // certificate from another team packages under a team the companion profile does not match.
            signsUnderAnotherTeam(facts) -> DoctorCheck.fail(
                "desktop.signingIdentity",
                "The configured macOS signing identity signs under a different Apple development team than " +
                    "posato.apple.developmentTeam, so packaging would produce a bundle the companion profile does not match.",
                "Configure the identity issued for the same team, or correct posato.apple.developmentTeam.",
            )

            facts.signingIdentityTeam == null -> DoctorCheck.unknown(
                "desktop.signingIdentity",
                "The configured macOS signing identity is in the keychain, but the team it signs under could not be read.",
                "Run `security find-certificate -c \"<identity>\" -p | openssl x509 -noout -subject` and check its OU field.",
            )

            // Nothing was compared when no team is configured, so the detail must not claim a match.
            facts.developmentTeam == null -> DoctorCheck.pass(
                "desktop.signingIdentity",
                "A configured macOS signing identity is present in the keychain; no development team is configured to compare it against.",
            )

            else -> DoctorCheck.pass(
                "desktop.signingIdentity",
                "A configured macOS signing identity is present in the keychain and signs under the configured team.",
            )
        }
    }

    private fun signsUnderAnotherTeam(facts: ProvisioningFacts): Boolean {
        val certificateTeam = facts.signingIdentityTeam ?: return false
        val configuredTeam = facts.developmentTeam ?: return false
        return certificateTeam != configuredTeam
    }

    private fun syncProfile(facts: ProvisioningFacts): DoctorCheck {
        val id = "desktop.syncProfile"
        if (facts.signingIdentity == null) {
            return DoctorCheck.fail(
                id,
                "No companion provisioning profile is configured; it is only required once a macOS signing identity is set.",
                PROFILE_REMEDY,
                Severity.INFO,
            )
        }
        if (!facts.syncProfileConfigured) {
            return DoctorCheck.fail(id, "A macOS signing identity is configured but no companion provisioning profile is.", PROFILE_REMEDY)
        }
        if (!facts.syncProfileReadable || facts.syncProfile == null) {
            return DoctorCheck.fail(
                id,
                "The configured companion provisioning profile could not be read or decoded.",
                "Check that posato.macos.syncProvisioningProfile points at a readable .provisionprofile file.",
            )
        }
        val profile = facts.syncProfile
        val applicationIdentifier = profile.applicationIdentifier
        if (applicationIdentifier == null || !applicationIdentifier.endsWith(".$SYNC_APPLICATION_IDENTIFIER")) {
            return DoctorCheck.fail(
                id,
                "The configured companion profile is not for $SYNC_APPLICATION_IDENTIFIER.",
                "Download the development profile for the $SYNC_APPLICATION_IDENTIFIER App ID and point the property at it.",
            )
        }
        val expiresAt = profile.expiresAt
        if (expiresAt != null && !expiresAt.isAfter(facts.now)) {
            return DoctorCheck.fail(
                id,
                "The configured companion provisioning profile has expired.",
                "Download a current development profile for $SYNC_APPLICATION_IDENTIFIER and point the property at it.",
            )
        }
        val team = facts.developmentTeam
        if (team != null && profile.teamIdentifier != null && profile.teamIdentifier != team) {
            return DoctorCheck.fail(
                id,
                "The configured companion provisioning profile belongs to a different Apple development team than posato.apple.developmentTeam.",
                "Use a profile issued for the configured team, or correct posato.apple.developmentTeam.",
            )
        }
        val compared = if (team == null) "; no development team is configured to compare it against" else " for the configured team"
        return DoctorCheck.pass(id, "The companion provisioning profile matches $SYNC_APPLICATION_IDENTIFIER and is current$compared.")
    }

    private fun helperBundle(facts: ProvisioningFacts): DoctorCheck {
        val id = "desktop.helperBundle"
        return when {
            !facts.staged -> DoctorCheck.fail(
                id,
                "No staged desktop application, so the nested macOS helper is absent.",
                "Run `posato-control build -t desktop`.",
                Severity.WARN,
            )

            !facts.helperExecutablePresent -> DoctorCheck.fail(
                id,
                "The staged application does not contain the nested macOS helper executable.",
                "Rerun `posato-control build -t desktop --verify` and inspect the packaging verification failure.",
                Severity.WARN,
            )

            else -> DoctorCheck.pass(id, "The staged application contains the nested macOS helper executable.")
        }
    }

    private fun proxyDaemon(facts: ProvisioningFacts): DoctorCheck = nestedComponent(
        id = "desktop.proxyDaemon",
        present = facts.proxyDaemonPresent,
        staged = facts.staged,
        component = "the nested proxy-settings daemon and its launchd property list",
    )

    private fun syncCompanion(facts: ProvisioningFacts): DoctorCheck = nestedComponent(
        id = "desktop.syncCompanion",
        present = facts.syncCompanionPresent,
        staged = facts.staged,
        component = "the nested synchronization companion executable",
    )

    /**
     * One nested component of the staged package. Packaging verifies the same components at build time, so a report
     * that names the missing one turns a later failure deep inside a recipe into a setup answer.
     */
    private fun nestedComponent(
        id: String,
        present: Boolean,
        staged: Boolean,
        component: String,
    ): DoctorCheck = when {
        !staged -> DoctorCheck.fail(
            id,
            "No staged desktop application, so $component is absent.",
            "Run `posato-control build -t desktop`.",
            Severity.WARN,
        )

        !present -> DoctorCheck.fail(
            id,
            "The staged application does not contain $component.",
            "Rerun `posato-control build -t desktop --verify` and inspect the packaging verification failure.",
            Severity.WARN,
        )

        else -> DoctorCheck.pass(id, "The staged application contains $component.")
    }

    private fun helperBackground(): DoctorCheck = DoctorCheck.unknown(
        "desktop.helperBackground",
        "Whether the macOS helper is approved to run in the background is readable only by the application itself.",
        "Run `posato-control launch -t desktop --capture-logs` and read the enforcement state the application reports.",
    )
}
