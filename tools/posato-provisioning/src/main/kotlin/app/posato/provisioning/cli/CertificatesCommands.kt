package app.posato.provisioning.cli

import app.posato.provisioning.asc.AscClient
import app.posato.provisioning.core.ConfigurationKey
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.decide.CertificateDecision
import app.posato.provisioning.decide.CertificateDecisions
import app.posato.provisioning.decide.CertificateOutcome
import app.posato.provisioning.local.CertificateCreation
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class CertificatesCommand : CliktCommand(name = "certificates") {
    override fun help(context: Context): String = "Inspect and create the Apple Development certificate this Mac signs with."

    override fun run() = Unit
}

/**
 * Reuses the development certificate this Mac already signs with, and creates one only when asked.
 *
 * Creation is behind a flag because it is the only irreversible write this tool makes: it puts a private key into
 * the login keychain and consumes one of the team's certificate slots. On a Mac that already has a valid identity
 * the reuse path is the whole story, so making creation implicit would add risk that nothing exercises.
 */
class CertificatesEnsureCommand :
    ProvisioningCommand(
        "ensure",
        "Confirms this Mac has an Apple Development certificate the account also holds, creating one only with --create.",
    ) {
    private val create by option("--create", help = "Generate a key pair and request a certificate when none is usable.").flag()

    override fun execute(session: Session): JsonElement {
        val client = session.ascClient()
        val team = session.configuration.value(ConfigurationKey.DEVELOPMENT_TEAM)
        val decision = decide(session, client, team)
        if (decision.usable) return report("reused", decision.certificateId)
        if (!create) throw missing(decision)
        return issue(session, client, team)
    }

    private fun decide(
        session: Session,
        client: AscClient,
        team: String?
    ): CertificateDecision = CertificateDecisions.decide(
        session.keychain.developmentIdentities(),
        client.certificates(),
        team,
        Instant.now(),
    )

    private fun issue(
        session: Session,
        client: AscClient,
        team: String?
    ): JsonElement {
        val creation = CertificateCreation(session.subprocess, session.userPaths.posatoDeveloperDirectory)
        val request = creation.signingRequest()
        val issued = try {
            client.createCertificate(request.pem)
        } catch (failure: ProvisioningException) {
            creation.discard(request)
            throw failure
        }
        creation.install(request, issued.attributes.certificateContent.orEmpty())
        // The keychain is the only thing that knows whether the import produced a usable identity, so it is asked
        // again rather than assuming the import succeeded because no command failed.
        val confirmed = decide(session, client, team)
        if (!confirmed.usable) {
            throw ProvisioningException(
                ErrorCode.CERTIFICATE_IMPORT_FAILED,
                "App Store Connect issued a certificate, but the login keychain does not report a usable identity for it.",
                "Approve any keychain prompt that appeared and rerun `certificates ensure`; the issued certificate is reused.",
            )
        }
        return report("created", confirmed.certificateId)
    }

    private fun report(
        state: String,
        certificateId: String?
    ): JsonElement = buildJsonObject {
        put("certificate", state)
        put("known", certificateId != null)
    }

    private fun missing(decision: CertificateDecision): ProvisioningException = ProvisioningException(
        ErrorCode.CERTIFICATE_MISSING,
        when (decision.outcome) {
            CertificateOutcome.LOCAL_NOT_IN_ACCOUNT -> {
                "This Mac holds an Apple Development certificate the account no longer lists, which is what a revoked certificate looks like."
            }

            CertificateOutcome.ACCOUNT_NOT_ON_THIS_MAC -> {
                "The account holds development certificates, but this Mac has the private key for none of them."
            }

            CertificateOutcome.WRONG_TEAM -> {
                "The usable Apple Development certificates on this Mac belong to another team."
            }

            CertificateOutcome.EXPIRED -> {
                "Every Apple Development certificate on this Mac has expired."
            }

            else -> {
                "This Mac has no Apple Development certificate."
            }
        },
        "Run `posato-provisioning certificates ensure --create` to request one for this Mac.",
    )
}
