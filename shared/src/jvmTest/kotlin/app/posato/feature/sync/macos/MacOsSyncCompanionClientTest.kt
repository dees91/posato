package app.posato.feature.sync.macos

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val exchange = client("hang").transact(request(deadlineMilliseconds = 400))

        assertEquals(CompanionExchange.Unknown, exchange)
    }

    @Test
    fun `given a hanging companion when cancelled then no exchange is returned`() = runBlocking {
        val deferred = async {
            client("hang").transact(request(deadlineMilliseconds = 30_000))
        }
        deferred.cancelAndJoin()
        assertTrue(deferred.isCancelled)
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

    private fun client(mode: String): MacOsSyncCompanionClient {
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
        )
    }
}
