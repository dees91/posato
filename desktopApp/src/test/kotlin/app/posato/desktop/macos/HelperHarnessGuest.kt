package app.posato.desktop.macos

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.TimeUnit

/** What the Tart-guest helper harnesses read from the guest and how they write their evidence. */
internal object HelperHarnessGuest {
    fun proxyEnabled(): Boolean {
        val process = ProcessBuilder("/usr/sbin/scutil", "--proxy").redirectErrorStream(true).start()
        val output = process.inputStream.readAllBytes().decodeToString()
        check(process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "scutil timed out" }
        return output.lines().any { line -> line.trim() == "HTTPEnable : 1" }
    }

    fun record(
        log: Path,
        line: String,
    ) {
        Files.writeString(log, "${Instant.now()} $line\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun HelperResult.categories(): String {
        return "outcome=$outcome state=$serviceState phase=$ownershipPhase action=$requiredAction failure=$failure"
    }

    private const val TOOL_TIMEOUT_SECONDS = 10L
}
