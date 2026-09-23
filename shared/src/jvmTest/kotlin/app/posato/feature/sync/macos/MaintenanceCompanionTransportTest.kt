package app.posato.feature.sync.macos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaintenanceCompanionTransportTest {
    @Test
    fun `given maintenance when a transaction arrives then it is refused and cleared without starting the companion`() = runTest {
        val delegate = CountingTransport()
        val transport = MaintenanceCompanionTransport { delegate }
        assertTrue(transport.drainForMaintenance())
        val message = message()

        assertEquals(CompanionExchange.Unknown, transport.transact(message))
        assertEquals(0, delegate.transactions)
        assertTrue(message.payload.all { byte -> byte == 0.toByte() })
    }

    @Test
    fun `given a transaction in flight when maintenance drains then draining waits for it`() = runTest {
        val release = CompletableDeferred<Unit>()
        val transport = MaintenanceCompanionTransport { CountingTransport(beforeExchange = { release.await() }) }

        val exchanging = async { transport.transact(message()) }
        runCurrent()
        val draining = async { transport.drainForMaintenance() }
        runCurrent()

        assertFalse(draining.isCompleted)
        release.complete(Unit)
        exchanging.await()
        assertTrue(draining.await())
    }

    @Test
    fun `given a transaction that outlives the grace period when maintenance drains then its companion is stopped and draining succeeds`() = runTest {
        val companion = ProcessBuilder("/bin/sleep", "30").start()
        try {
            val transport = MaintenanceCompanionTransport(drainGraceMillis = 50L) {
                CountingTransport(beforeExchange = { withContext(Dispatchers.IO) { companion.waitFor() } })
            }
            transport.registerProcess(companion)
            val exchanging = async { transport.transact(message()) }
            runCurrent()

            assertTrue(transport.drainForMaintenance())
            assertFalse(companion.isAlive)
            exchanging.await()
        } finally {
            companion.destroyForcibly().waitFor()
        }
    }

    @Test
    fun `given a transaction that never finishes when maintenance drains then draining fails instead of waiting forever`() = runTest {
        val never = CompletableDeferred<Unit>()
        val transport = MaintenanceCompanionTransport(drainGraceMillis = 50L, exitTimeoutMillis = 50L) {
            CountingTransport(beforeExchange = { never.await() })
        }
        backgroundScope.launch { transport.transact(message()) }
        runCurrent()

        assertFalse(transport.drainForMaintenance())
    }

    @Test
    fun `given a companion process that has not exited when maintenance drains then it is stopped before draining succeeds`() = runTest {
        val transport = MaintenanceCompanionTransport(exitTimeoutMillis = 50L) { CountingTransport() }
        val lingering = ProcessBuilder("/bin/sleep", "30").start()
        try {
            transport.registerProcess(lingering)

            assertTrue(transport.drainForMaintenance())
            assertFalse(lingering.isAlive)
        } finally {
            lingering.destroyForcibly().waitFor()
        }
    }

    @Test
    fun `given a companion process that exited when maintenance drains then draining succeeds`() = runTest {
        val transport = MaintenanceCompanionTransport { CountingTransport() }
        val finished = ProcessBuilder("/usr/bin/true").start()
        finished.waitFor()
        transport.registerProcess(finished)

        assertTrue(transport.drainForMaintenance())
    }

    @Test
    fun `given maintenance ended when a transaction arrives then it reaches the companion`() = runTest {
        val delegate = CountingTransport()
        val transport = MaintenanceCompanionTransport { delegate }
        transport.drainForMaintenance()

        transport.resumeAfterMaintenance()
        transport.transact(message())

        assertEquals(1, delegate.transactions)
    }

    private fun message(): SyncCompanionMessage {
        return SyncCompanionMessage(
            operation = SyncCompanionOperation.ReadItem,
            requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
            deadlineMilliseconds = 5_000,
            capabilities = MacOsSyncCompanionProtocol.KEYCHAIN_CAPABILITY,
            outcome = null,
            payload = byteArrayOf(1, 2, 3),
        )
    }

    private class CountingTransport(
        private val beforeExchange: suspend () -> Unit = {},
    ) : SyncCompanionTransport {
        var transactions: Int = 0

        override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
            beforeExchange()
            transactions += 1
            message.clear()
            return CompanionExchange.Unknown
        }

        override fun newRequestIdentifier(): ByteArray {
            return ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES)
        }
    }
}
