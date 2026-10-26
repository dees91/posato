package app.posato.desktop.macos

import app.posato.desktop.macos.HelperHarnessGuest.categories
import app.posato.desktop.macos.HelperHarnessGuest.proxyEnabled
import app.posato.desktop.macos.HelperHarnessGuest.record
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unattended harness for MACOS-031, run inside a Tart guest whose Posato is onboarded with the
 * standing grant on, while Posato itself is not running there. It runs only when
 * `POSATO_HELPER_STALL_VM` is set and takes the helper and its signed parent as
 * [MacOsHelperOperationBoundHarnessTest] does.
 *
 * On a busy Mac the daemon answered one request in 5.4 s, longer than the helper's 5 s renewal
 * deadline, and the helper ended itself, so the daemon restored the proxy in the middle of a pause.
 * After applying, the harness writes `applied` and waits for a `stall-done` file in its evidence
 * directory. The host writes it after two `posato-control vm stall --process daemon --seconds 8`
 * runs 4.5 s apart: one 8 s stall misses a 5 s renewal period when it starts after the renewal's
 * first 3 s, and two cover every phase. The same helper must then still hold the restrictions past
 * the daemon's lease, change them, and clear them.
 */
class MacOsHelperDaemonStallHarnessTest {
    @Test
    fun `given restrictions applied when the daemon stops answering for seconds then the helper keeps them`() {
        if (System.getenv(GATE)?.isNotBlank() != true) {
            return
        }
        val evidence = Files.createDirectories(Path.of(System.getenv(EVIDENCE_ENV) ?: DEFAULT_EVIDENCE))
        val log = evidence.resolve("run.md")
        val stallDone = evidence.resolve(STALL_DONE)
        Files.deleteIfExists(stallDone)
        val helper = Path.of(checkNotNull(System.getenv(HELPER_ENV)) { "$HELPER_ENV is not set" })
        val parent = Path.of(checkNotNull(System.getenv(PARENT_ENV)) { "$PARENT_ENV is not set" })
        record(log, "helper=$helper")
        MacOsHelperClient(helperPath = helper, launchPrefix = listOf(parent.toString())).use { client ->
            val enforcer = MacOsBrowserDomainEnforcer(client)
            try {
                val started = enforcer.start(listOf(DOMAIN), System.currentTimeMillis() + SESSION_MILLISECONDS, grantOnly = true)
                if (started is BrowserDomainEnforcementResult.Failed) {
                    record(log, "start failed ${started.result.categories()}")
                }
                assertIs<BrowserDomainEnforcementResult.Active>(started, "Restrictions did not apply")
                assertTrue(proxyEnabled(), "Proxy settings were not applied")
                record(log, "applied")
                awaitStall(stallDone, log)
                assertTrue(daemonRunning(), "The daemon is still stopped after the stall")
                repeat(POLLS_AFTER_STALL) { index ->
                    val status = client.status()
                    record(log, "status $index ${status.categories()} proxy=${proxyEnabled()}")
                    assertEquals(HelperResult.Outcome.Success, status.outcome, "Status $index after the stall")
                    assertEquals(HelperResult.Phase.Applied, status.ownershipPhase, "Status $index after the stall")
                    assertTrue(proxyEnabled(), "Proxy settings were restored $index polls after the stall")
                    Thread.sleep(POLL_MILLISECONDS)
                }
                assertEquals(HelperResult.Outcome.Success, enforcer.clear().outcome, "Restrictions did not clear for the change")
                val changed = enforcer.start(CHANGED_DOMAINS, System.currentTimeMillis() + SESSION_MILLISECONDS, grantOnly = true)
                record(log, "changed $changed")
                assertIs<BrowserDomainEnforcementResult.Active>(changed, "Changed restrictions did not apply")
                assertTrue(proxyEnabled(), "Proxy settings were not applied after the change")
                val cleared = enforcer.clear()
                record(log, "cleared ${cleared.categories()}")
                assertEquals(HelperResult.Outcome.Success, cleared.outcome, "Restrictions did not clear")
            } finally {
                runCatching { enforcer.clear() }
            }
        }
        assertTrue(!proxyEnabled(), "Proxy settings stayed applied after the harness")
        record(log, "passed")
    }

    private fun awaitStall(
        stallDone: Path,
        log: Path,
    ) {
        val deadline = System.currentTimeMillis() + STALL_WAIT_MILLISECONDS
        while (!Files.exists(stallDone)) {
            check(System.currentTimeMillis() < deadline) { "No stall-done file within the wait" }
            Thread.sleep(WAIT_STEP_MILLISECONDS)
        }
        record(log, "stall done")
    }

    /** A process stopped by SIGSTOP shows `T` in its state. */
    private fun daemonRunning(): Boolean {
        val process = ProcessBuilder("/bin/sh", "-c", "ps -o stat= -p \"$(pgrep -x PosatoProxySettingsDaemon)\"")
            .redirectErrorStream(true)
            .start()
        val state = process.inputStream.readAllBytes().decodeToString().trim()
        check(process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "ps timed out" }
        return state.isNotEmpty() && 'T' !in state
    }

    private companion object {
        const val GATE = "POSATO_HELPER_STALL_VM"
        const val HELPER_ENV = "POSATO_HELPER_PATH"
        const val PARENT_ENV = "POSATO_HELPER_PARENT"
        const val EVIDENCE_ENV = "POSATO_HELPER_EVIDENCE"
        const val DEFAULT_EVIDENCE = "build/verification/helper-stall"
        const val STALL_DONE = "stall-done"
        const val DOMAIN = "example.com"
        val CHANGED_DOMAINS = listOf("example.com", "example.org")
        const val TOOL_TIMEOUT_SECONDS = 10L
        const val SESSION_MILLISECONDS = 30 * 60 * 1_000L
        const val STALL_WAIT_MILLISECONDS = 120_000L
        const val WAIT_STEP_MILLISECONDS = 200L

        // Ten polls two seconds apart outlast the daemon's 15 s lease after the stall.
        const val POLLS_AFTER_STALL = 10
        const val POLL_MILLISECONDS = 2_000L
    }
}
