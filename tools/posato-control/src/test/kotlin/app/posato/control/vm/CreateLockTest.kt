package app.posato.control.vm

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two sessions that create the same line at once both saw no clone and both cloned: the check and the clone were
 * separate steps (review of #166). The lock must make one of them see the other's clone. E2E covers two processes;
 * this test pins the in-process half, which a timing-dependent VM run cannot reach reliably.
 */
class CreateLockTest {
    @Test
    fun `given two concurrent creates of one line when each checks then clones then exactly one clones`() {
        val lock = Files.createTempDirectory("create-lock").resolve("create.lock")
        var cloneExists = false
        val cloned = AtomicInteger()
        val refused = AtomicInteger()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        repeat(2) {
            pool.execute {
                start.await()
                withCreateLock(lock) {
                    if (cloneExists) {
                        refused.incrementAndGet()
                    } else {
                        Thread.sleep(CLONE_MS)
                        cloneExists = true
                        cloned.incrementAndGet()
                    }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
        assertEquals(1, cloned.get())
        assertEquals(1, refused.get())
    }

    private companion object {
        const val CLONE_MS = 200L
    }
}
