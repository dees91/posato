package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Compiles the single-file accessibility bridge on demand, whenever the binary is missing or older than its source. */
class AxBridgeBinary(
    private val context: RunContext
) {
    fun ensureBuilt() {
        val layout = context.layout
        val source = layout.accessibilityBridgeSource
        val binary = layout.accessibilityBridgeBinary
        if (!source.exists()) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "The accessibility bridge source is missing at ${layout.relativize(source)}.")
        }
        val flags = listOf("-O", "-target", GUEST_TARGET)
        val stamp = layout.accessibilityBridgeCommand
        val current = binary.exists() && binary.getLastModifiedTime() >= source.getLastModifiedTime() &&
            stamp.exists() && stamp.readText() == flags.joinToString(" ")
        if (current) return
        Files.createDirectories(binary.parent)
        context.log("Compiling the accessibility bridge")
        context.subprocess.run(listOf("/usr/bin/xcrun", "swiftc") + flags + listOf("-o", binary.toString(), source.toString()))
            .requireSuccess(ErrorCode.BUILD_FAILED, "Compiling the accessibility bridge", "Install Xcode command line tools.")
        stamp.writeText(flags.joinToString(" "))
    }

    private companion object {
        /** The oldest guest the bridge runs in: the ventura VM line runs macOS 13. */
        const val GUEST_TARGET = "arm64-apple-macos13.0"
    }
}
