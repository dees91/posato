package app.posato.desktop.linux

import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.targets.data.CatalogApplicationMappings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pauses on Linux through the root service: the hosts go to `/etc/hosts`, the chosen applications' executables are
 * ended while the pause runs, and the service clears by itself at the end and reports that expiry until it is seen.
 */
internal class LinuxSessionEnforcement(
    private val client: LinuxHelperClient,
    private val installer: LinuxHelperInstaller,
    private val mappings: CatalogApplicationMappings,
) : EnforcementPort {
    override val reapplyRequiresPrompt: Boolean = false

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        if (request.domains.isEmpty() && request.mappingIds.isEmpty()) return report(EnforcementOutcome.NOTHING_TO_ENFORCE)
        if (!client.installed()) {
            val installed = !request.grantOnly && withContext(Dispatchers.IO) { installer.install() }
            if (!installed) return report(EnforcementOutcome.AUTHORIZATION_REQUIRED)
        }
        val executables = mappings.targets(request.mappingIds)
        val line = "apply\t${request.sessionId.safe()}\t${request.sessionEndEpochMillis}\t${request.domains.joinToString(",")}\t" +
            executables.joinToString("\u001f")
        return report(if (send(line) == "ok") EnforcementOutcome.APPLIED else EnforcementOutcome.FAILED)
    }

    /** Only a service that is not installed holds nothing; one that does not answer may still hold a pause. */
    override suspend fun clear(): EnforcementOutcome {
        return when (send("clear")) {
            "ok" -> EnforcementOutcome.CLEARED
            null -> if (client.present()) EnforcementOutcome.FAILED else EnforcementOutcome.CLEARED
            else -> EnforcementOutcome.FAILED
        }
    }

    override suspend fun status(): EnforcementOutcome {
        val status = readStatus() ?: return EnforcementOutcome.UNAVAILABLE
        return if (status.applied != null) EnforcementOutcome.APPLIED else EnforcementOutcome.CLEARED
    }

    override suspend fun holdsSession(sessionId: String): Boolean {
        return readStatus()?.applied == sessionId.safe()
    }

    override suspend fun peekSuspendedExpiry(sessionId: String): Boolean {
        return readStatus()?.expired == sessionId.safe()
    }

    override suspend fun acknowledgeSuspendedExpiry(sessionId: String): Boolean {
        return send("ack\t${sessionId.safe()}") == "ok"
    }

    private suspend fun readStatus(): HelperStatus? {
        val fields = send("status")?.split('\t')?.takeIf { it.size == STATUS_FIELDS && it[0] == "ok" } ?: return null
        return HelperStatus(fields[1].takeUnless { it == "-" }, fields[2].takeUnless { it == "-" })
    }

    private suspend fun send(line: String): String? {
        return withContext(Dispatchers.IO) { client.send(line) }
    }

    private fun report(outcome: EnforcementOutcome): EnforcementApplyReport {
        return EnforcementApplyReport(outcome, belowPlatformMinimum = false, repeatsSystemPrompt = false)
    }

    private class HelperStatus(
        val applied: String?,
        val expired: String?,
    )

    private companion object {
        const val STATUS_FIELDS = 3
    }
}

/** The service accepts session identifiers of letters, digits, hyphens, and underscores only. */
private fun String.safe(): String {
    return filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(SESSION_LENGTH)
}

private const val SESSION_LENGTH = 128
