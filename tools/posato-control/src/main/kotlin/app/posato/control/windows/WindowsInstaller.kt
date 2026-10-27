package app.posato.control.windows

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.windows.WindowsGuest.Companion.psQuote
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Installs and removes a Posato MSI in the Windows clone with `msiexec` and a verbose log, as a quiet per-machine
 * install. A failed run keeps the end of the log, which names the failing action, as evidence. The clone runs with UAC
 * off, so the elevation that a person approves once is not driven here.
 */
class WindowsInstaller(
    private val context: RunContext,
    private val guest: WindowsGuest,
) {
    fun install(msi: Path) {
        if (!Files.isRegularFile(msi) || !msi.fileName.toString().endsWith(".msi", ignoreCase = true)) {
            throw ControlException(ErrorCode.APP_NOT_STAGED, "No MSI at $msi.", "Pass a built Posato .msi.")
        }
        val guestMsi = "$PACKAGE_FOLDER\\${msi.fileName}"
        guest.tools.push(msi, guestMsi)
        msiexec(listOf("/i", guestMsi), "Installing ${msi.fileName}", "install")
    }

    /** Removes [msi], or the product registered as [productName]; returns what was removed. */
    fun uninstall(
        msi: Path?,
        productName: String,
    ): String {
        val target = if (msi != null) {
            "$PACKAGE_FOLDER\\${msi.fileName}".also { guest.tools.push(msi, it) }
        } else {
            productCode(productName) ?: throw ControlException(ErrorCode.APP_NOT_INSTALLED, "No installed product named $productName.")
        }
        msiexec(listOf("/x", target), "Removing $productName", "uninstall")
        return target
    }

    /** The product code of the installed product named [productName], from the machine's uninstall entries. */
    fun productCode(productName: String): String? = guest.powershell(
        "\$entry = Get-ItemProperty $UNINSTALL_KEYS -ErrorAction SilentlyContinue | " +
            "Where-Object { \$_.DisplayName -eq ${psQuote(productName)} } | Select-Object -First 1\n" +
            "if (\$entry) { \$entry.PSChildName }",
    ).stdout.trim().ifEmpty { null }

    private fun msiexec(
        arguments: List<String>,
        what: String,
        name: String,
    ) {
        val log = "$PACKAGE_FOLDER\\msiexec-$name.log"
        // Paths go in double quotes; msiexec reads the switches only bare.
        val argumentList = (arguments + listOf("/qn", "/norestart", "/l*v", log))
            .joinToString(" ") { if (it.startsWith("/")) it else "\"$it\"" }
        // The guest agent passes only the low byte of an exit code (1620 arrives as 84), so the code travels on stdout.
        val script = "Remove-Item ${psQuote(log)} -ErrorAction SilentlyContinue\n" +
            "\$p = Start-Process msiexec.exe -ArgumentList ${psQuote(argumentList)} -Wait -PassThru\n" +
            "\"msiexec=\$(\$p.ExitCode)\""
        val output = guest.powershell(script, timeout = Duration.ofMinutes(MSI_MINUTES))
        val code = MSI_CODE.find(output.stdout)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw ControlException(ErrorCode.INSTALL_FAILED, "$what: msiexec did not report an exit code.\n${output.stderr.trim()}")
        if (code in MSI_SUCCESS) return
        val kept = runCatching {
            val file = context.artifactPath("logs", "msiexec-$name.log")
            // A verbose Windows Installer log is UTF-16; its end names the failing action.
            Files.writeString(file, String(guest.tools.pull(log), Charsets.UTF_16LE).lines().takeLast(LOG_TAIL).joinToString("\n"))
            context.layout.relativize(context.recordArtifact(file))
        }.getOrNull()
        throw ControlException(
            ErrorCode.INSTALL_FAILED,
            "$what failed: msiexec exited with $code.",
            kept?.let { "The end of the msiexec log is in $it." } ?: "Read $log in the clone with `windows exec`.",
        )
    }

    companion object {
        /** The name the MSI registers in Apps and in its uninstall entry. */
        const val PRODUCT_NAME = "Posato"
        const val UNINSTALL_KEYS = "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*"
        private const val PACKAGE_FOLDER = "C:\\Windows\\Temp\\posato-package"

        /** msiexec's success and success-with-restart-required. */
        private val MSI_SUCCESS = setOf(0, 3010)
        private val MSI_CODE = Regex("""msiexec=(\d+)""")
        private const val MSI_MINUTES = 10L
        private const val LOG_TAIL = 200
    }
}
