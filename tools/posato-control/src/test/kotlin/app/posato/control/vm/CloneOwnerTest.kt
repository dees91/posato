package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Clone names are machine-wide, so a refusal names the worktree that created the clone and whether its process still
 * runs. A pid the system reused must not make an exited creator look alive; E2E cannot make the system reuse a pid.
 */
class CloneOwnerTest {
    @Test
    fun `given a reused pid when judging the creator then a process started at another time does not count`() {
        val current = ProcessHandle.current()
        val started = current.info().startInstant().map { it.toString() }.orElse(null)

        assertTrue(creatorRuns(CloneOwner("/work/a", "r1", "2026-10-04T20:00:00Z", current.pid(), started)))
        assertFalse(creatorRuns(CloneOwner("/work/a", "r1", "2026-10-04T20:00:00Z", current.pid(), "2000-01-01T00:00:00Z")))
    }
}
