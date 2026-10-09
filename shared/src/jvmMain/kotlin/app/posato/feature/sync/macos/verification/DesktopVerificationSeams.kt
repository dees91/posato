package app.posato.feature.sync.macos.verification

import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.AppleBootstrap
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.macos.MacOsBootstrapCloudAdapter
import app.posato.feature.sync.macos.MacOsBootstrapKeychainAdapter
import app.posato.feature.sync.macos.MacOsSyncCompanionProtocol
import app.posato.feature.sync.macos.MacOsSyncCompanionVerifier
import app.posato.feature.sync.macos.SyncCompanionTransport
import java.nio.file.Files
import java.nio.file.Path

/**
 * The launch-argument entry of the verification seams (ADR 0007 amendment of 2026-10-09). It ships in every
 * build and stays inert unless the running package carries the development-only `Info.plist` key.
 */
interface DesktopVerificationSeams {
    /** Whether this package carries the seams; false in every release build. */
    fun available(): Boolean

    /** Runs `status` or `delete-zone` and returns one line of JSON for the verification driver. */
    suspend fun run(command: String): String
}

internal class MacOsDesktopVerificationSeams(
    private val bootstrap: AppleBootstrap,
    private val transport: SyncCompanionTransport,
    private val applicationRoot: () -> Path,
) : DesktopVerificationSeams {
    override fun available(): Boolean {
        val info = applicationRoot().resolve("Contents/Info.plist")
        return Files.isRegularFile(info) && String(Files.readAllBytes(info), Charsets.UTF_8).contains("<key>$INFO_PLIST_KEY</key>")
    }

    override suspend fun run(command: String): String {
        if (!available()) return result("inert")
        if (command != STATUS && command != DELETE_ZONE) return result("unknown-command")
        // The synchronization lock is held for the whole operation, and it runs only while this Mac has no
        // established workspace; the companion then checks the environment, the signature, and the anchor.
        return bootstrap.whileLocalOnly {
            val binding = when (val resolution = MacOsBootstrapKeychainAdapter(transport).resolveBinding()) {
                is BindingResolution.Available -> resolution.binding
                else -> return@whileLocalOnly result("account-unavailable")
            }
            if (command == STATUS) {
                val anchor = when (MacOsBootstrapCloudAdapter(transport).readAnchor(binding)) {
                    is AnchorReadResult.Found -> "present"
                    AnchorReadResult.Missing -> "absent"
                    else -> "unknown"
                }
                result("local-only", anchor)
            } else {
                val bytes = binding.copyBytes()
                try {
                    val executable = MacOsSyncCompanionVerifier().verify(applicationRoot())
                    result(VerificationCompanionClient(executable).run(VerificationSeamOperation.DeleteZone, bytes).label())
                } finally {
                    bytes.fill(0)
                }
            }
        } ?: result("workspace-established")
    }

    private fun VerificationSeamOutcome.label(): String {
        return when (this) {
            VerificationSeamOutcome.DeletedAndAbsent -> "zone-deleted"
            VerificationSeamOutcome.AlreadyAbsent -> "zone-already-absent"
            VerificationSeamOutcome.AnchorPresent -> "anchor-present"
            VerificationSeamOutcome.Refused -> "refused"
            VerificationSeamOutcome.Retryable -> "retryable"
            VerificationSeamOutcome.AccountChanged -> "account-changed"
            VerificationSeamOutcome.Unknown -> "unknown"
        }
    }

    private fun result(
        outcome: String,
        anchor: String? = null,
    ): String {
        val anchorField = anchor?.let { ",\"anchor\":\"$it\"" }.orEmpty()
        return "{\"seam\":\"${MacOsSyncCompanionProtocol.COMPANION_EXECUTABLE}\",\"outcome\":\"$outcome\"$anchorField}"
    }

    companion object {
        const val INFO_PLIST_KEY: String = "PosatoVerificationSeams"
        const val STATUS: String = "status"
        const val DELETE_ZONE: String = "delete-zone"
    }
}
