package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.vm.GuestICloud
import app.posato.control.vm.ICloudKeychainState
import app.posato.control.vm.VmLine
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VmICloudCommand :
    ControlCommand(
        "icloud",
        "Report whether iCloud Keychain syncs in the guest (exit 3 when paused); --resume repairs a paused keychain.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val resume by option("--resume", help = "Run Resume Data Sync and answer its account, Mac password, and passcode dialogs.").flag()
    private val timeoutSeconds by option("--timeout-seconds", help = "How long each step may take.").long().default(ICLOUD_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val iCloud = GuestICloud(session.context)
        val timeoutMs = timeoutSeconds * MILLIS_PER_SECOND
        val state = if (resume) iCloud.resume(line, timeoutMs) else iCloud.check(line, timeoutMs)
        if (state == ICloudKeychainState.SIGNED_OUT) {
            throw ControlException(
                ErrorCode.ICLOUD_KEYCHAIN_PAUSED,
                "${line.cloneName} is not signed in to an Apple Account, so iCloud Keychain cannot sync.",
                "Sign the golden VM in to the test account as described in docs/development/unattended-verification.md.",
            )
        }
        if (state == ICloudKeychainState.PAUSED) {
            throw ControlException(
                ErrorCode.ICLOUD_KEYCHAIN_PAUSED,
                "iCloud Keychain is paused in ${line.cloneName} (\"Some iCloud Data Isn't Syncing\"); workspace keys will not arrive.",
                "Run `posato-control vm icloud --line ${line.id} --resume`, then repair the golden VM the same way.",
            )
        }
        return buildJsonObject {
            put("vm", line.cloneName)
            put("iCloudKeychain", state.id)
        }
    }
}

private const val ICLOUD_TIMEOUT_SECONDS = 90L
private const val MILLIS_PER_SECOND = 1_000L
