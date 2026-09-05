package app.posato.provisioning.decide

import app.posato.provisioning.model.AppIdentifier
import app.posato.provisioning.model.DoctorCheck
import app.posato.provisioning.model.DoctorReport
import app.posato.provisioning.model.Severity

/** What the installed copy of a profile looks like, or why it cannot be used. */
enum class ProfileFileState { OK, MISSING, UNREADABLE, WRONG_APP_ID, WRONG_TEAM, EXPIRED }

/** Whether the account accepted a signed token, when one was attempted at all. */
enum class TokenState { ACCEPTED, REJECTED, UNREACHABLE, NOT_ATTEMPTED }

/**
 * Everything doctor observed, gathered once.
 *
 * Keeping it as plain data is what makes the whole report testable without an Apple account, a keychain, or a
 * network, including the rule that no message may carry a value it was given.
 */
data class ProvisioningFacts(
    val keyIdConfigured: Boolean,
    val issuerConfigured: Boolean,
    val keyPathConfigured: Boolean,
    val keyReadable: Boolean?,
    val keyOwnerOnly: Boolean?,
    val teamConfigured: Boolean,
    val token: TokenState,
    val registeredBundleIds: Set<String>,
    val certificate: CertificateOutcome?,
    val mac: DeviceOutcome?,
    val iphone: DeviceOutcome?,
    val profiles: Map<AppIdentifier, ProfileFileState>,
)

/**
 * The provisioning conditions this tool can clear, each as one named check with one remedy sentence.
 *
 * The report answers a different question from the verification driver's `doctor`. That one asks whether this
 * checkout can build, sign, and drive the applications with what is already on the Mac, and its remedies say which
 * property to set. This one asks whether the Mac can obtain Apple resources at all and whether the account holds
 * them, and its remedies name a `posato-provisioning` command. The identifiers do not overlap.
 */
object ProvisioningChecks {
    fun report(facts: ProvisioningFacts): DoctorReport {
        val checks = checks(facts)
        return DoctorReport(checks.none { check -> !check.ok && check.severity == Severity.ERROR.name.lowercase() }, checks)
    }

    fun checks(facts: ProvisioningFacts): List<DoctorCheck> = listOf(
        configured("asc.keyId", facts.keyIdConfigured, "posato.asc.keyId", "the App Store Connect key identifier"),
        configured("asc.issuerId", facts.issuerConfigured, "posato.asc.issuerId", "the App Store Connect issuer identifier"),
        privateKey(facts),
        keyPermissions(facts),
        configured("asc.team", facts.teamConfigured, "posato.apple.developmentTeam", "the Apple development team"),
        token(facts),
    ) + bundleIds(facts) + certificate(facts) + devices(facts) + profiles(facts)

    private fun configured(
        id: String,
        present: Boolean,
        property: String,
        what: String
    ): DoctorCheck = if (present) {
        DoctorCheck.pass(id, "A value for $what is configured.")
    } else {
        DoctorCheck.fail(id, "No value for $what is configured.", "Add $property to the ignored local.properties file.")
    }

    private fun privateKey(facts: ProvisioningFacts): DoctorCheck {
        val id = "asc.privateKey"
        val remedy = "Store the .p8 under ~/Library/Developer/Posato/ and point posato.asc.privateKeyPath at it."
        return when {
            !facts.keyPathConfigured -> DoctorCheck.fail(id, "No App Store Connect private key is configured.", remedy)
            facts.keyReadable == false -> DoctorCheck.fail(id, "The configured App Store Connect private key could not be read.", remedy)
            else -> DoctorCheck.pass(id, "The configured App Store Connect private key reads as an elliptic-curve key.")
        }
    }

    private fun keyPermissions(facts: ProvisioningFacts): DoctorCheck {
        val id = "asc.privateKeyPermissions"
        val remedy = "Run chmod 600 on the configured .p8 file."
        if (!facts.keyPathConfigured) {
            return DoctorCheck.unknown(id, "No private key is configured, so its permissions were not read.", "Configure the key first.")
        }
        return when (facts.keyOwnerOnly) {
            true -> DoctorCheck.pass(id, "The App Store Connect private key is readable only by its owner.")
            false -> DoctorCheck.fail(id, "The App Store Connect private key is readable by more than its owner.", remedy, Severity.WARN)
            null -> DoctorCheck.unknown(id, "The App Store Connect private key's permissions could not be read.", remedy)
        }
    }

    private fun token(facts: ProvisioningFacts): DoctorCheck {
        val id = "asc.token"
        val remedy = "Confirm the key is active under Users and Access, and that the issuer belongs to the same team."
        return when (facts.token) {
            TokenState.ACCEPTED -> {
                DoctorCheck.pass(id, "App Store Connect accepted a signed token from this Mac.")
            }

            TokenState.REJECTED -> {
                DoctorCheck.fail(id, "App Store Connect rejected the signed token.", remedy)
            }

            TokenState.UNREACHABLE -> {
                DoctorCheck.unknown(id, "App Store Connect could not be reached, so the token was not confirmed.", "Rerun once connected.")
            }

            TokenState.NOT_ATTEMPTED -> {
                DoctorCheck.fail(
                    id,
                    "No request was attempted, because the key, issuer, or team is not configured.",
                    "Configure the values reported above, then rerun doctor.",
                )
            }
        }
    }

    private fun bundleIds(facts: ProvisioningFacts): List<DoctorCheck> {
        val id = "asc.bundleIds"
        if (facts.token != TokenState.ACCEPTED) {
            return listOf(DoctorCheck.unknown(id, "The account's App IDs were not read.", "Clear the token condition and rerun doctor."))
        }
        // App IDs are public and already tracked in this repository, so naming a missing one costs nothing and is
        // the only way the remedy can be acted on.
        val missing = AppIdentifier.entries.filter { entry -> entry.bundleId !in facts.registeredBundleIds }
        return listOf(
            if (missing.isEmpty()) {
                DoctorCheck.pass(id, "All five Posato App IDs are registered.")
            } else {
                DoctorCheck.fail(
                    id,
                    "The account is missing ${missing.joinToString(", ") { entry -> entry.bundleId }}.",
                    "Register the missing App ID in the developer portal, as APPLE-001 did for the others.",
                )
            },
        )
    }

    private fun certificate(facts: ProvisioningFacts): List<DoctorCheck> {
        val id = "asc.certificate"
        val remedy = "Run `posato-provisioning certificates ensure --create`."
        val check = when (facts.certificate) {
            null -> {
                DoctorCheck.unknown(id, "The certificate state was not read.", "Clear the token condition and rerun doctor.")
            }

            CertificateOutcome.MATCHED -> {
                DoctorCheck.pass(id, "This Mac signs with an Apple Development certificate the account holds.")
            }

            CertificateOutcome.LOCAL_NOT_IN_ACCOUNT -> {
                DoctorCheck.fail(id, "This Mac's Apple Development certificate is not one the account lists.", remedy)
            }

            CertificateOutcome.ACCOUNT_NOT_ON_THIS_MAC -> {
                DoctorCheck.fail(id, "The account holds development certificates, but this Mac has none of their private keys.", remedy)
            }

            CertificateOutcome.WRONG_TEAM -> {
                DoctorCheck.fail(id, "This Mac's Apple Development certificates belong to another team.", remedy)
            }

            CertificateOutcome.EXPIRED -> {
                DoctorCheck.fail(id, "Every Apple Development certificate on this Mac has expired.", remedy)
            }

            CertificateOutcome.ABSENT -> {
                DoctorCheck.fail(id, "This Mac has no Apple Development certificate.", remedy)
            }
        }
        return listOf(check)
    }

    private fun devices(facts: ProvisioningFacts): List<DoctorCheck> {
        // Saying why a device was not checked matters: "no iPhone is connected" and "the account was never read"
        // call for different actions, and reporting the first when the second happened sends the reader nowhere.
        val unread = facts.token != TokenState.ACCEPTED
        return listOf(
            device(
                "asc.deviceMac",
                facts.mac,
                "This Mac",
                Severity.ERROR,
                if (unread) "the account was not read" else "its Provisioning UDID could not be read",
            ),
            device(
                "asc.deviceIphone",
                facts.iphone,
                "The connected iPhone",
                Severity.WARN,
                if (unread) "the account was not read" else "no iPhone is connected",
            ),
        )
    }

    private fun device(
        id: String,
        outcome: DeviceOutcome?,
        subject: String,
        severity: Severity,
        absence: String,
    ): DoctorCheck {
        val remedy = "Run `posato-provisioning devices register`."
        return when (outcome) {
            null -> DoctorCheck.unknown(id, "$subject was not checked, because $absence.", remedy)

            DeviceOutcome.ALREADY_REGISTERED -> DoctorCheck.pass(id, "$subject is registered and enabled in the account.")

            DeviceOutcome.REGISTERED -> DoctorCheck.fail(id, "$subject is not registered in the account.", remedy, severity)

            DeviceOutcome.DISABLED -> DoctorCheck.fail(
                id,
                "$subject is registered but disabled in the account.",
                "Re-enable the device in the developer portal.",
                severity,
            )
        }
    }

    private fun profiles(facts: ProvisioningFacts): List<DoctorCheck> = AppIdentifier.entries.map { identifier ->
        val id = "provisioning.profile.${identifier.bundleId}"
        // Only the macOS sync profile blocks work today: the desktop packaging task refuses to sign without it. The
        // other four are warnings so a run that does not need them is not reported as broken.
        val severity = if (identifier == AppIdentifier.MACOS_SYNC) Severity.ERROR else Severity.WARN
        val remedy = "Run `posato-provisioning profiles ensure ${identifier.bundleId}`."
        when (facts.profiles[identifier] ?: ProfileFileState.MISSING) {
            ProfileFileState.OK -> {
                DoctorCheck.pass(id, "An installed development profile for ${identifier.bundleId} is current.")
            }

            ProfileFileState.MISSING -> {
                DoctorCheck.fail(id, "No development profile for ${identifier.bundleId} is installed.", remedy, severity)
            }

            ProfileFileState.UNREADABLE -> {
                DoctorCheck.fail(id, "The installed ${identifier.bundleId} profile could not be decoded.", remedy, severity)
            }

            ProfileFileState.WRONG_APP_ID -> {
                DoctorCheck.fail(id, "The installed ${identifier.bundleId} profile is for a different App ID.", remedy, severity)
            }

            ProfileFileState.WRONG_TEAM -> {
                DoctorCheck.fail(id, "The installed ${identifier.bundleId} profile belongs to another team.", remedy, severity)
            }

            ProfileFileState.EXPIRED -> {
                DoctorCheck.fail(id, "The installed ${identifier.bundleId} profile has expired.", remedy, severity)
            }
        }
    }
}
