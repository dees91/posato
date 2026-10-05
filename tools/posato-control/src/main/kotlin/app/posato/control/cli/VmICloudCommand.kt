package app.posato.control.cli

import app.posato.control.vm.GuestICloud
import app.posato.control.vm.VmLine
import app.posato.control.vm.keychainRefusal
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
    private val lineOption by option("--line", help = "VM line: primary, peer, legacy, or ventura.").default(VmLine.PRIMARY.id)
    private val resume by option("--resume", help = "Run Resume Data Sync and answer the dialogs it raises.").flag()
    private val timeoutSeconds by option("--timeout-seconds", help = "How long each step may take.").long().default(ICLOUD_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val iCloud = GuestICloud(session.context)
        val timeoutMs = timeoutSeconds * MILLIS_PER_SECOND
        val state = if (resume) iCloud.resume(line, timeoutMs) else iCloud.check(line, timeoutMs)
        keychainRefusal(line, state)?.let { throw it }
        return buildJsonObject {
            put("vm", line.cloneName)
            put("iCloudKeychain", state.id)
        }
    }
}

private const val ICLOUD_TIMEOUT_SECONDS = 90L
private const val MILLIS_PER_SECOND = 1_000L
