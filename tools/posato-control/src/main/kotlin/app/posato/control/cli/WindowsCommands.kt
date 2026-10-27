package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.windows.WindowsBackend
import app.posato.control.windows.WindowsChecks
import app.posato.control.windows.WindowsGuest
import app.posato.control.windows.WindowsInstaller
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Every `windows` subcommand. */
fun windowsCommand(): WindowsCommand = WindowsCommand().subcommands(
    WindowsCreateCommand(),
    WindowsBootCommand(),
    WindowsStopCommand(),
    WindowsDestroyCommand(),
    WindowsExecCommand(),
    WindowsUninstallCommand(),
    WindowsCheckCommand().subcommands(
        WindowsConnectCheck(),
        WindowsProcessCheck(),
        WindowsServiceCheck(),
        WindowsFiltersCheck(),
        WindowsRunValueCheck(),
        WindowsPipeCheck(),
    ),
)

/** The Windows clone of `PLATFORM-002`: a Tart Windows 11 guest; Posato in it is driven with the shared commands and `-t windows`. */
class WindowsCommand : CliktCommand("windows") {
    override fun help(context: Context): String =
        "Create, start, stop, or delete the Tart Windows clone, run PowerShell in it, or read its state; drive Posato with -t windows."

    override fun run() = Unit
}

class WindowsCreateCommand : ControlCommand("create", "Clone the Windows golden VM through the Tart fork and wait for its guest agent.") {
    override fun execute(session: Session): JsonElement = buildJsonObject { put("vm", WindowsGuest(session.context).create()) }
}

class WindowsBootCommand : ControlCommand("boot", "Start the stopped Windows clone again with its data.") {
    override fun execute(session: Session): JsonElement = buildJsonObject { put("vm", WindowsGuest(session.context).boot()) }
}

class WindowsStopCommand : ControlCommand("stop", "Shut the Windows clone down and keep it.") {
    override fun execute(session: Session): JsonElement {
        WindowsGuest(session.context).stop()
        return buildJsonObject { put("vm", WindowsGuest.CLONE) }
    }
}

class WindowsDestroyCommand : ControlCommand("destroy", "Stop and delete the Windows clone and end its lease.") {
    override fun execute(session: Session): JsonElement {
        WindowsGuest(session.context).destroy()
        return buildJsonObject { put("vm", WindowsGuest.CLONE) }
    }
}

class WindowsExecCommand : ControlCommand("exec", "Run a PowerShell script in the clone's signed-in session, or as a second standard user.") {
    private val script by option("--script", help = "The PowerShell script text.").required()
    private val asOtherUser by option(
        "--as-other-user",
        help = "Run it as ${WindowsChecks.OTHER_USER}, a standard local user created in the clone, through a scheduled task.",
    ).flag()

    override fun execute(session: Session): JsonElement {
        val guest = WindowsGuest(session.context).also { it.requireRunning() }
        if (asOtherUser) return WindowsChecks(guest).asOtherUser(script)
        val output = guest.powershell(script)
        return buildJsonObject {
            put("exitCode", output.exitCode)
            put("stdout", output.stdout)
            put("stderr", output.stderr)
        }
    }
}

class WindowsUninstallCommand : ControlCommand("uninstall", "Remove Posato with msiexec /x: the given MSI or the installed product.") {
    private val packageFile by option("--package", help = "The MSI to remove; by default the product registered as --product.")
        .path(mustExist = true, canBeDir = false)
    private val product by option("--product", help = "The registered product name.").default(WindowsInstaller.PRODUCT_NAME)

    override fun execute(session: Session): JsonElement {
        val backend = session.backend() as? WindowsBackend
            ?: throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "windows uninstall needs -t windows.")
        val removed = backend.uninstall(packageFile, product)
        return buildJsonObject {
            put("removed", removed)
            put("stillRegistered", backend.productCode(product) != null)
        }
    }
}

/** Guest facts for acceptance checks; with --expect each one fails with ASSERTION_FAILED when the fact differs. */
class WindowsCheckCommand : CliktCommand("check") {
    override fun help(context: Context): String =
        "Read a fact in the Windows clone: connect, process, service, filters, run-value, or pipe; --expect turns it into an assertion."

    override fun run() = Unit
}

/** A check whose result carries one boolean that `--expect` asserts. */
abstract class WindowsCheck(
    name: String,
    help: String,
    private val field: String,
    private val expectations: Map<String, Boolean>,
) : ControlCommand(name, help) {
    private val expect by option(
        "--expect",
        help = "Assert the result: ${expectations.keys.joinToString(" or ")}.",
    ).choice(*expectations.keys.toTypedArray())

    protected abstract fun read(checks: WindowsChecks): JsonObject

    override fun execute(session: Session): JsonElement {
        val guest = WindowsGuest(session.context).also { it.requireRunning() }
        val result = read(WindowsChecks(guest))
        expect?.let { wanted ->
            val actual = (result[field] as? JsonPrimitive)?.booleanOrNull
                ?: throw ControlException(ErrorCode.ASSERTION_FAILED, "The check reported no $field, so $wanted cannot be asserted.", result = result)
            val holds = actual == expectations.getValue(wanted)
            if (!holds) throw ControlException(ErrorCode.ASSERTION_FAILED, "Expected $wanted, but $field is ${result[field]}.", result = result)
        }
        return result
    }
}

class WindowsConnectCheck :
    WindowsCheck(
        "connect",
        "Test a TCP connection and an HTTP request from the clone to --host; blocked means the TCP connection fails.",
        "tcp",
        mapOf("allowed" to true, "blocked" to false),
    ) {
    private val host by option("--host", help = "Host name or address, such as example.org.").required()
    private val port by option("--port", help = "TCP port; 443 asks over https, any other over http.").int().default(HTTP_PORT)

    override fun read(checks: WindowsChecks): JsonObject {
        if (!HOST.matches(host)) throw ControlException(ErrorCode.USAGE, "--host takes a host name or an address.")
        return checks.connect(host, port)
    }

    private companion object {
        const val HTTP_PORT = 80
        val HOST = Regex("""^[A-Za-z0-9.:\-]+$""")
    }
}

class WindowsProcessCheck :
    WindowsCheck(
        "process",
        "List the clone's processes with an image name, such as notepad, with path, session, and owner.",
        "running",
        mapOf("running" to true, "absent" to false),
    ) {
    private val name by option("--name", help = "Image name without .exe.").required()

    override fun read(checks: WindowsChecks): JsonObject = checks.processes(name.removeSuffix(".exe"))
}

class WindowsServiceCheck :
    WindowsCheck(
        "service",
        "Read the pausing service's state from the service control manager.",
        "present",
        mapOf("present" to true, "absent" to false),
    ) {
    private val name by option("--name", help = "The service name.").default(HELPER_SERVICE)
    private val state by option("--state", help = "Also assert the state, such as Running or Stopped.")

    override fun read(checks: WindowsChecks): JsonObject {
        val result = checks.service(name)
        state?.let { wanted ->
            val actual = (result["state"] as? JsonPrimitive)?.content
            if (!actual.equals(wanted, ignoreCase = true)) {
                throw ControlException(ErrorCode.ASSERTION_FAILED, "Expected the $name service $wanted, but it is $actual.", result = result)
            }
        }
        return result
    }
}

class WindowsFiltersCheck :
    WindowsCheck(
        "filters",
        "Count the Windows Filtering Platform filters whose name, description, provider, or sublayer names --match.",
        "present",
        mapOf("present" to true, "absent" to false),
    ) {
    private val match by option("--match", help = "Text in the filter, provider, or sublayer name.").default(FILTER_MATCH)

    override fun read(checks: WindowsChecks): JsonObject {
        val result = checks.filters(match)
        val count = (result["count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        return JsonObject(result + ("present" to JsonPrimitive(count > 0)))
    }
}

class WindowsRunValueCheck :
    WindowsCheck(
        "run-value",
        "Read the signed-in user's start-at-login value under HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run.",
        "present",
        mapOf("present" to true, "absent" to false),
    ) {
    private val name by option("--name", help = "The value name.").default(WindowsInstaller.PRODUCT_NAME)

    override fun read(checks: WindowsChecks): JsonObject = checks.runValue(name)
}

class WindowsPipeCheck :
    WindowsCheck(
        "pipe",
        "Connect to the service's named pipe, send one line, and read the answer; --as-other-user calls as a standard second user.",
        "connected",
        mapOf("connected" to true, "refused" to false),
    ) {
    private val pipe by option("--name", help = "The pipe name, without \\\\.\\pipe\\.").default(HELPER_PIPE)
    private val line by option("--line", help = "One request line to send, such as status; nothing is sent without it.")
    private val asOtherUser by option("--as-other-user", help = "Call as ${WindowsChecks.OTHER_USER} through a scheduled task.").flag()

    override fun read(checks: WindowsChecks): JsonObject = checks.pipe(pipe, line, asOtherUser)
}

/** The service and pipe names the Windows helper of `PLATFORM-002` uses; each check takes another with --name. */
const val HELPER_SERVICE = "PosatoHelper"
const val HELPER_PIPE = "app.posato.helper"

/** The text Posato's filters, provider, and sublayer carry in their names. */
const val FILTER_MATCH = "Posato"
