package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

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
        if (binary.exists() && binary.getLastModifiedTime() >= source.getLastModifiedTime()) return
        Files.createDirectories(binary.parent)
        context.log("Compiling the accessibility bridge")
        context.subprocess.run(listOf("/usr/bin/xcrun", "swiftc", "-O", "-o", binary.toString(), source.toString()))
            .requireSuccess(ErrorCode.BUILD_FAILED, "Compiling the accessibility bridge", "Install Xcode command line tools.")
    }
}
