package app.posato.desktop.macos

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MacOsBrowserDomainPhysicalHarnessTest {
    @Test
    fun `given the physical gate when unset then the maintainer checklist is skipped`() {
        if (System.getenv(GATE)?.isNotBlank() != true) {
            return
        }
        val outputDirectory = Path.of("build/verification/macos-004")
        Files.createDirectories(outputDirectory)
        val checklist = outputDirectory.resolve("checklist.md")
        Files.writeString(
            checklist,
            """
            # MACOS-004 physical checklist

            | Row | Result |
            | --- | --- |
            | Safari regular HTTP deny | |
            | Safari regular HTTPS deny | |
            | Safari private HTTP deny | |
            | Safari private HTTPS deny | |
            | Chrome regular HTTP deny | |
            | Chrome regular HTTPS deny | |
            | Chrome incognito HTTP deny | |
            | Chrome incognito HTTPS deny | |
            | Control / sibling / subdomain reachable | |
            | Presentation failure leaves denial intact | |
            | Conflict preflight refuses Apply | |
            | Sleep / wake restores baseline | |
            | Forced helper termination restores baseline | |
            | Reboot restores baseline | |
            | Listener failure with cache-busted selected target never DIRECT | |
            | Privacy canary absent from logs, IPC, durable state | |
            """.trimIndent() + "\n",
        )
        assertTrue(Files.isRegularFile(checklist))
    }

    @Test
    fun `given the physical gate when set then enforcement holds active for manual rows`() {
        if (System.getenv(GATE)?.isNotBlank() != true) {
            return
        }
        val outputDirectory = Path.of("build/verification/macos-004")
        Files.createDirectories(outputDirectory)
        val runLog = outputDirectory.resolve("run.md")
        val activeMarker = outputDirectory.resolve("ACTIVE")
        val rowsDone = outputDirectory.resolve("ROWS_DONE")
        Files.deleteIfExists(rowsDone)

        fun record(line: String) {
            Files.writeString(
                runLog,
                "${Instant.now()} $line\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
        if (!Files.isRegularFile(runLog)) {
            Files.writeString(runLog, "# MACOS-004 physical run\n")
        }
        val helperPath = Path.of(
            System.getenv(HELPER_ENV) ?: DEFAULT_HELPER_PATH,
        )
        assertTrue(Files.isExecutable(helperPath), "Installed helper is missing: $helperPath")
        val baseline = proxyBaseline()
        record("baseline web=<$baseline>")
        MacOsHelperClient(helperPath = helperPath).use { client ->
            val enabled = waitForEnable(client, outputDirectory, ::record)
            record("enabled outcome=${enabled.outcome}")
            assertEquals(HelperResult.Outcome.Success, enabled.outcome)
            val enforcer = MacOsBrowserDomainEnforcer(client)
            val started = enforcer.start(listOf(SELECTED_DOMAIN))
            val active = assertIs<BrowserDomainEnforcementResult.Active>(
                started,
                "Enforcement did not activate: $started",
            )
            record("active port=${active.port}")
            Files.writeString(activeMarker, "${active.port}\n")
            try {
                waitForFile(rowsDone, HOLD_TIMEOUT_MILLISECONDS, ::record)
                record("manual rows done")
            } finally {
                Files.deleteIfExists(activeMarker)
                val cleared = enforcer.clear()
                record("cleared outcome=${cleared.outcome}")
                assertEquals(HelperResult.Outcome.Success, cleared.outcome)
            }
        }
        val restored = proxyBaseline()
        record("restored web=<$restored>")
        assertEquals(baseline, restored, "Proxy baseline was not restored byte-identical")
    }

    private fun waitForEnable(
        client: MacOsHelperClient,
        outputDirectory: Path,
        record: (String) -> Unit,
    ): HelperResult {
        val approved = outputDirectory.resolve("ENABLE_APPROVED")
        Files.deleteIfExists(approved)
        val deadline = System.currentTimeMillis() + ENABLE_TIMEOUT_MILLISECONDS
        while (true) {
            val result = client.enable()
            record("enable attempt outcome=${result.outcome} state=${result.serviceState}")
            if (result.outcome == HelperResult.Outcome.Success) {
                Files.deleteIfExists(approved)
                return result
            }
            if (System.currentTimeMillis() > deadline) {
                return result
            }
            waitForFile(approved, ENABLE_RETRY_MILLISECONDS, record)
        }
    }

    private fun waitForFile(
        path: Path,
        timeoutMilliseconds: Long,
        record: (String) -> Unit,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMilliseconds
        while (!Files.isRegularFile(path)) {
            if (System.currentTimeMillis() > deadline) {
                error("Timed out waiting for ${path.fileName}")
            }
            Thread.sleep(POLL_MILLISECONDS)
        }
        record("observed ${path.fileName}")
    }

    private fun proxyBaseline(): String {
        return readProxy("Wi-Fi", "web") + "|" + readProxy("Wi-Fi", "secureweb")
    }

    private fun readProxy(
        service: String,
        kind: String,
    ): String {
        return try {
            val process = ProcessBuilder("networksetup", "-get${kind}proxy", service)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readAllBytes().decodeToString()
            process.waitFor()
            output.trim().replace(Regex("\\s+"), " ")
        } catch (_: Exception) {
            "unavailable"
        }
    }

    private companion object {
        const val GATE = "POSATO_MACOS_004_PHYSICAL"
        const val HELPER_ENV = "POSATO_MACOS_004_HELPER"
        const val DEFAULT_HELPER_PATH = "/Applications/Posato-MACOS-004.app/Contents/Helpers/PosatoMacOSHelper.app/Contents/MacOS/PosatoMacOSHelper"
        const val SELECTED_DOMAIN = "example.com"
        const val ENABLE_TIMEOUT_MILLISECONDS = 10 * 60 * 1_000L
        const val ENABLE_RETRY_MILLISECONDS = 60 * 1_000L
        const val HOLD_TIMEOUT_MILLISECONDS = 45 * 60 * 1_000L
        const val POLL_MILLISECONDS = 5_000L
    }
}
