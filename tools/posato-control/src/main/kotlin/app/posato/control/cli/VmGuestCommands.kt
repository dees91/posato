package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.vm.Tart
import app.posato.control.vm.VmLine
import app.posato.control.vm.shellQuote
import app.posato.control.vm.vmAdminPassword
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

class VmPushCommand :
    ControlCommand(
        "push",
        "Copy one host file into the guest user's home, such as a verification probe, and check its SHA-256 there.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val from by option("--from", help = "Host file to copy.").required()
    private val to by option("--to", help = "Destination relative to the guest user's home, without '..'.").required()
    private val executable by option("--executable", help = "Make the copy executable.").flag()

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val source = pushSource(from)
        val segments = guestSegments(to)
        val digest = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        val destination = "\"\$HOME\"/" + segments.joinToString("/") { segment -> shellQuote(segment) }
        val mode = if (executable) "755" else "644"
        val tart = Tart(session.context)
        tart.pipe(
            line.cloneName,
            hostScript = "cat ${shellQuote(source.toAbsolutePath().toString())}",
            guestScript = "mkdir -p \"\$(dirname $destination)\" && cat > $destination && chmod $mode $destination",
            what = "Copying $from into ${line.cloneName}",
        )
        val check = tart.exec(line.cloneName, "shasum -a 256 $destination")
        if (!check.succeeded || check.stdout.substringBefore(' ').trim() != digest) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "The copy of $from in ${line.cloneName} does not match its SHA-256.")
        }
        return buildJsonObject {
            put("vm", line.cloneName)
            put("path", to)
            put("sha256", digest)
        }
    }
}

class VmKillCommand :
    ControlCommand(
        "kill",
        "Kill the guest's session helper or proxy-settings daemon with SIGKILL, for crash and restore runs; launchd " +
            "restarts the daemon.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val process by option("--process", help = "helper or daemon.").choice("helper", "daemon").required()

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val tart = Tart(session.context)
        val output = when (process) {
            "helper" -> tart.exec(line.cloneName, "pkill -KILL -x $HELPER_PROCESS")

            else -> tart.exec(
                line.cloneName,
                "sudo -S -p '' pkill -KILL -x $DAEMON_PROCESS",
                stdin = vmAdminPassword(session.context) + "\n",
            )
        }
        if (!output.succeeded) {
            throw ControlException(
                ErrorCode.COMMAND_FAILED,
                "No running $process process was killed in ${line.cloneName} (exit code ${output.exitCode}).",
                "Start a session first; the helper and the daemon run only while Posato enforces or reconciles.",
            )
        }
        return buildJsonObject {
            put("vm", line.cloneName)
            put("killed", process)
        }
    }
}

private fun pushSource(from: String): Path {
    val source = Path.of(from)
    if (!source.isRegularFile() || Files.size(source) > MAX_PUSH_BYTES) {
        throw ControlException(ErrorCode.USAGE, "--from must be a regular file of at most $MAX_PUSH_BYTES bytes.")
    }
    return source
}

/** The destination's path segments below the guest home; absolute paths, '.' and '..' are refused. */
private fun guestSegments(to: String): List<String> {
    val segments = to.split('/')
    val escapes = segments.any { segment -> segment.isEmpty() || segment == "." || segment == ".." }
    if (to.startsWith('/') || escapes) {
        throw ControlException(ErrorCode.USAGE, "--to must be a relative path inside the guest home without '.' or '..'.")
    }
    return segments
}

private const val MAX_PUSH_BYTES = 64L * 1024 * 1024

private const val HELPER_PROCESS = "PosatoMacOSHelper"

private const val DAEMON_PROCESS = "PosatoProxySettingsDaemon"
