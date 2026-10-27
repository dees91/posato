package app.posato.control.windows

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.windows.WindowsGuest.Companion.TOOLS
import app.posato.control.windows.WindowsGuest.Companion.psQuote
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

/**
 * The driver's side of the Windows clone: files travel on the guest command's standard input, not over the directory
 * share, whose first reads after boot can fail with "Error performing inpage operation". The guest scripts under
 * `resources/windows` are copied in on each boot, so changing them needs no new golden VM: [PROBE] reads and presses
 * Posato's Compose tree through the Java Access Bridge, [SCREENSHOT] captures the primary screen, and [CHECKS] reads
 * the facts that acceptance checks need.
 */
class WindowsTools(
    private val guest: WindowsGuest,
) {
    /** Writes [bytes] to [guestPath], creating its folder. */
    fun push(
        bytes: ByteArray,
        guestPath: String,
    ) {
        val script = "\$path = ${psQuote(guestPath)}\n" +
            "New-Item -ItemType Directory -Force (Split-Path -Parent \$path) | Out-Null\n" +
            "\$in = [Console]::OpenStandardInput(); \$out = [IO.File]::Create(\$path); \$in.CopyTo(\$out); \$out.Close()\n" +
            "(Get-Item \$path).Length"
        val output = guest.powershell(script, stdin = bytes, timeout = COPY_TIMEOUT)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Copying $guestPath into ${WindowsGuest.CLONE}")
        if (output.stdout.trim().toLongOrNull() != bytes.size.toLong()) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "Copying $guestPath wrote ${output.stdout.trim()} of ${bytes.size} bytes.")
        }
    }

    fun push(
        file: Path,
        guestPath: String,
    ) = push(Files.readAllBytes(file), guestPath)

    /** Reads a guest file through base64 on standard output; for evidence such as logs and databases. */
    fun pull(guestPath: String): ByteArray {
        val output = guest.powershell("[Convert]::ToBase64String([IO.File]::ReadAllBytes(${psQuote(guestPath)}))", timeout = COPY_TIMEOUT)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Reading $guestPath from ${WindowsGuest.CLONE}")
        return Base64.getDecoder().decode(output.stdout.trim())
    }

    fun installScripts() {
        mapOf(PROBE_RESOURCE to PROBE, SCREENSHOT_RESOURCE to SCREENSHOT, CHECKS_RESOURCE to CHECKS, LIMITED_RESOURCE to LIMITED).forEach { (resource, guestPath) ->
            push(checkNotNull(WindowsTools::class.java.getResourceAsStream(resource)).use { it.readBytes() }, guestPath)
        }
    }

    /** Runs the Java Access Bridge probe with [arguments], which travel as JSON so quotes and non-ASCII text arrive intact. */
    fun probe(vararg arguments: String): ProcessOutput {
        val json = JsonArray(arguments.map(::JsonPrimitive)).toString()
        val command = listOf(PYTHON, PROBE, "-")
        val input = json.toByteArray(Charsets.UTF_8)
        // With UAC on, an elevated probe cannot reach Posato's window, so it runs at Posato's own level.
        return if (guest.uacOn) guest.runLimited(command, input) else guest.run(command, stdin = input)
    }

    fun screenshot(file: Path): Path {
        val script = "& ${psQuote(SCREENSHOT)} -Path ${psQuote(SCREEN_FILE)} | Out-Null\n" +
            "[Convert]::ToBase64String([IO.File]::ReadAllBytes(${psQuote(SCREEN_FILE)}))"
        // The capture script reports its size with Write-Host, which reaches standard output; the image is the last line.
        val encoded = guest.powershell(script).requireSuccess(ErrorCode.COMMAND_FAILED, "Capturing the screen of ${WindowsGuest.CLONE}")
            .stdout.lines().last { it.isNotBlank() }.trim()
        Files.createDirectories(file.parent)
        Files.write(file, Base64.getDecoder().decode(encoded))
        return file
    }

    /** Runs one guest check with its [request] and returns the JSON object it prints. */
    fun check(
        request: JsonObject,
        timeout: Duration = CHECK_TIMEOUT,
    ): JsonObject {
        val what = "The ${request["check"]} check"
        val output = guest.run(WindowsGuest.POWERSHELL + listOf("-File", CHECKS), request.toString().toByteArray(Charsets.UTF_8), timeout)
        val line = output.stdout.lines().lastOrNull { it.trim().startsWith("{") }
            ?: throw ControlException(ErrorCode.COMMAND_FAILED, "$what printed no result: ${(output.stderr + output.stdout).trim().take(MAX_DETAIL)}")
        return ControlJson.lenient.parseToJsonElement(line) as? JsonObject
            ?: throw ControlException(ErrorCode.COMMAND_FAILED, "$what printed no JSON object.")
    }

    companion object {
        const val PYTHON = "$TOOLS\\Python\\python.exe"
        const val PROBE = "$TOOLS\\posato-control\\posato-jab.py"
        const val SCREENSHOT = "$TOOLS\\posato-control\\screenshot.ps1"
        const val CHECKS = "$TOOLS\\posato-control\\posato-checks.ps1"
        const val LIMITED = "$TOOLS\\posato-control\\posato-limited.ps1"
        private const val SCREEN_FILE = "C:\\Windows\\Temp\\posato-screen.png"
        private const val PROBE_RESOURCE = "/windows/posato-jab.py"
        private const val SCREENSHOT_RESOURCE = "/windows/screenshot.ps1"
        private const val CHECKS_RESOURCE = "/windows/posato-checks.ps1"
        private const val LIMITED_RESOURCE = "/windows/posato-limited.ps1"
        private const val MAX_DETAIL = 600
        private val COPY_TIMEOUT: Duration = Duration.ofMinutes(10)
        private val CHECK_TIMEOUT: Duration = Duration.ofMinutes(3)
    }
}
