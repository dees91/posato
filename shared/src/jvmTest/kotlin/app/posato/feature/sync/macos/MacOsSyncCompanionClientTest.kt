package app.posato.feature.sync.macos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MacOsSyncCompanionClientTest {
    @Test
    fun `given a scripted companion when created then created is returned`() = runBlocking {
        val client = client("created")
        val exchange = client.transact(request())

        val message = assertIs<CompanionExchange.Message>(exchange).message
        assertEquals(SyncCompanionOutcome.Created, message.outcome)
        assertEquals("CompanionExchange.Message(redacted)", exchange.toString())
    }

    @Test
    fun `given a wrong request identity when transacting then the exchange is unknown`() = runBlocking {
        val exchange = client("wrong-identity").transact(request())

        assertEquals(CompanionExchange.Unknown, exchange)
    }

    @Test
    fun `given a malformed frame when transacting then the exchange is unknown`() = runBlocking {
        val exchange = client("malformed").transact(request())

        assertEquals(CompanionExchange.Unknown, exchange)
    }

    @Test
    fun `given a hanging companion when the deadline elapses then the exchange is unknown`() = runBlocking {
        var process: Process? = null
        val elapsedMilliseconds = measureTimeMillis {
            val exchange = client("hang") { child -> process = child }
                .transact(request(deadlineMilliseconds = 400))
            assertEquals(CompanionExchange.Unknown, exchange)
        }
        val child = checkNotNull(process)

        assertTrue(elapsedMilliseconds < HANG_LIMIT_MILLISECONDS)
        assertTrue(child.waitFor(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS))
        assertFalse(child.isAlive)
    }

    @Test
    fun `given a hanging companion when cancelled then no exchange is returned`() = runBlocking {
        val started = CompletableDeferred<Process>()
        val elapsedMilliseconds = measureTimeMillis {
            val deferred = async {
                client("hang") { process -> started.complete(process) }
                    .transact(request(deadlineMilliseconds = 30_000))
            }
            val process = withTimeout(START_WAIT_MILLISECONDS) { started.await() }
            deferred.cancelAndJoin()
            assertTrue(deferred.isCancelled)
            assertTrue(process.waitFor(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS))
            assertFalse(process.isAlive)
        }

        assertTrue(elapsedMilliseconds < HANG_LIMIT_MILLISECONDS)
    }

    private fun request(deadlineMilliseconds: Int = 5_000): SyncCompanionMessage {
        return SyncCompanionMessage(
            operation = SyncCompanionOperation.CreateItem,
            requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
            deadlineMilliseconds = deadlineMilliseconds,
            capabilities = MacOsSyncCompanionProtocol.KEYCHAIN_CAPABILITY,
            outcome = null,
            payload = ByteArray(0),
        )
    }

    private fun client(
        mode: String,
        onProcessStarted: (Process) -> Unit = {},
    ): MacOsSyncCompanionClient {
        val javaHome = checkNotNull(System.getProperty("java.home"))
        val java = Path.of(javaHome, "bin", "java")
        val classpath = checkNotNull(System.getProperty("java.class.path"))
        return MacOsSyncCompanionClient(
            executable = java,
            arguments = listOf(
                "-cp",
                classpath,
                FakeSyncCompanionMain::class.java.name,
                mode,
            ),
            onProcessStarted = onProcessStarted,
        )
    }

    private companion object {
        const val HANG_LIMIT_MILLISECONDS: Long = 10_000L
        const val START_WAIT_MILLISECONDS: Long = 5_000L
        const val PROCESS_WAIT_SECONDS: Long = 2L
    }
}
