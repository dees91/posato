package app.posato.desktop.macos

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unattended harness for MACOS-030, run inside a Tart guest whose Posato is onboarded with the
 * standing grant on. It runs only when `POSATO_HELPER_BOUND_VM` is set, takes the helper to drive
 * from `POSATO_HELPER_PATH` and a parent process signed as the application from
 * `POSATO_HELPER_PARENT`, and quits nothing itself: Posato must not run in the guest meanwhile.
 *
 * ADR 0004 bounds one connection at 256 operations. A pause polls Status every 15 seconds, so the
 * harness sends more than twice that many operations while restrictions apply, through the client
 * and the helper's daemon connections, then clears, applies, and clears again on the same helper.
 */
class MacOsHelperOperationBoundHarnessTest {
    @Test
    fun `given restrictions applied when more operations than one connection allows are sent then the helper keeps answering`() {
        if (System.getenv(GATE)?.isNotBlank() != true) {
            return
        }
        val log = Files.createDirectories(Path.of(System.getenv(EVIDENCE_ENV) ?: DEFAULT_EVIDENCE)).resolve("run.md")
        val helper = Path.of(checkNotNull(System.getenv(HELPER_ENV)) { "$HELPER_ENV is not set" })
        val parent = Path.of(checkNotNull(System.getenv(PARENT_ENV)) { "$PARENT_ENV is not set" })
        record(log, "helper=$helper")
        MacOsHelperClient(helperPath = helper, launchPrefix = listOf(parent.toString())).use { client ->
            val enforcer = MacOsBrowserDomainEnforcer(client)
            try {
                applyThenPoll(client, enforcer, log, polls = FIRST_POLLS)
                assertClears(enforcer, log)
                applyThenPoll(client, enforcer, log, polls = SECOND_POLLS)
                assertClears(enforcer, log)
            } finally {
                runCatching { enforcer.clear() }
            }
        }
        assertTrue(!proxyEnabled(), "Proxy settings stayed applied after the harness")
        record(log, "passed")
    }

    private fun applyThenPoll(
        client: MacOsHelperClient,
        enforcer: MacOsBrowserDomainEnforcer,
        log: Path,
        polls: Int,
    ) {
        val started = enforcer.start(listOf(DOMAIN), System.currentTimeMillis() + SESSION_MILLISECONDS, grantOnly = true)
        if (started is BrowserDomainEnforcementResult.Failed) {
            record(log, "start failed ${started.result.categories()}")
        }
        assertIs<BrowserDomainEnforcementResult.Active>(started, "Restrictions did not apply")
        assertTrue(proxyEnabled(), "Proxy settings were not applied")
        record(log, "applied")
        repeat(polls) { index ->
            val status = runCatching { client.status() }
                .onFailure { failure -> record(log, "status $index threw ${failure::class.simpleName}") }
                .getOrThrow()
            if (status.outcome != HelperResult.Outcome.Success || status.ownershipPhase != HelperResult.Phase.Applied) {
                record(log, "status $index ${status.categories()}")
            }
            assertEquals(HelperResult.Outcome.Success, status.outcome, "Status $index after applying")
            assertEquals(HelperResult.Phase.Applied, status.ownershipPhase, "Status $index after applying")
            if (index % GRANT_READ_INTERVAL == 0) {
                assertEquals(HelperGrantState.On, client.grantState(), "Grant read $index after applying")
            }
        }
        record(log, "$polls statuses answered")
    }

    private fun assertClears(
        enforcer: MacOsBrowserDomainEnforcer,
        log: Path,
    ) {
        val cleared = enforcer.clear()
        record(log, "cleared ${cleared.categories()}")
        assertEquals(HelperResult.Outcome.Success, cleared.outcome, "Restrictions did not clear")
        assertTrue(!proxyEnabled(), "Proxy settings stayed applied after clearing")
    }

    private fun proxyEnabled(): Boolean {
        val process = ProcessBuilder("/usr/sbin/scutil", "--proxy").redirectErrorStream(true).start()
        val output = process.inputStream.readAllBytes().decodeToString()
        check(process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "scutil timed out" }
        return output.lines().any { line -> line.trim() == "HTTPEnable : 1" }
    }

    private fun record(
        log: Path,
        line: String,
    ) {
        Files.writeString(log, "${Instant.now()} $line\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun HelperResult.categories(): String {
        return "outcome=$outcome state=$serviceState phase=$ownershipPhase action=$requiredAction failure=$failure"
    }

    private companion object {
        const val GATE = "POSATO_HELPER_BOUND_VM"
        const val HELPER_ENV = "POSATO_HELPER_PATH"
        const val PARENT_ENV = "POSATO_HELPER_PARENT"
        const val EVIDENCE_ENV = "POSATO_HELPER_EVIDENCE"
        const val DEFAULT_EVIDENCE = "build/verification/helper-bound"
        const val DOMAIN = "example.com"
        const val FIRST_POLLS = 600
        const val SECOND_POLLS = 300
        const val GRANT_READ_INTERVAL = 10
        const val SESSION_MILLISECONDS = 30 * 60 * 1_000L
        const val TOOL_TIMEOUT_SECONDS = 10L
    }
}
