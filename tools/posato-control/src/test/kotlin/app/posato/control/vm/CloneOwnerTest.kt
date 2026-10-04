package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Clone names are machine-wide, so a refusal names the worktree that created the clone. A marker another driver
 * version wrote, or one cut short, must not break that refusal, and a pid the system reused must not make an exited
 * creator look alive.
 */
class CloneOwnerTest {
    @Test
    fun `given a marker cut short or unreadable when reading the owner then the clone reads as unrecorded`() {
        assertNull(parseCloneOwner("""{"worktree":"/work/a","runId":"""))
        assertNull(parseCloneOwner("not json"))
        assertNull(parseCloneOwner("""{"worktree":"/work/a"}"""))
    }

    @Test
    fun `given a marker with fields a later driver added when reading the owner then the known fields are kept`() {
        val json = """{"worktree":"/work/a","runId":"r1","createdAt":"2026-10-04T20:00:00Z","pid":42,"pidStartedAt":"x","later":true}"""

        assertEquals(CloneOwner("/work/a", "r1", "2026-10-04T20:00:00Z", 42, "x"), parseCloneOwner(json))
    }

    @Test
    fun `given a reused pid when judging the creator then a process started at another time does not count`() {
        val current = ProcessHandle.current()
        val started = current.info().startInstant().map { it.toString() }.orElse(null)

        assertTrue(creatorRuns(CloneOwner("/work/a", "r1", "2026-10-04T20:00:00Z", current.pid(), started)))
        assertFalse(creatorRuns(CloneOwner("/work/a", "r1", "2026-10-04T20:00:00Z", current.pid(), "2000-01-01T00:00:00Z")))
    }
}
