package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Clone names are machine-wide, so a refusal names the worktree that created the clone and whether its process still
 * runs. A pid the system reused must not make an exited creator look alive, also when no start time is readable to
 * tell the two apart; E2E can neither make the system reuse a pid nor hide a process's start time.
 */
class CloneOwnerTest {
    @Test
    fun `given a reused pid when judging the creator then a process started at another time does not count`() {
        val current = ProcessHandle.current()
        val started = current.info().startInstant().map { it.toString() }.orElse(null)

        assertTrue(creatorRuns(CloneOwner("/work/a", "r1", "2026-10-04T20:00:00Z", current.pid(), started)))
        assertFalse(creatorRuns(CloneOwner("/work/a", "r1", "2026-10-04T20:00:00Z", current.pid(), "2000-01-01T00:00:00Z")))
    }

    @Test
    fun `given no readable start time on either side when judging the creator then it does not count as running`() {
        assertFalse(sameStart(recorded = null, current = null))
        assertTrue(sameStart(recorded = "2026-10-04T20:00:00Z", current = "2026-10-04T20:00:00Z"))
    }
}
