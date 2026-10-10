package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * A relayed command's evidence lands under the run id the relay chooses, so a label passed as `--run-id=<label>` must
 * be the one it keeps. When the relay missed that form it appended a second `--run-id`, and the guest stored the
 * artifacts under the generated one, so six cited runs of MACOS-026 had no directory (release 1.4 retro).
 */
class GuestRunIdTest {
    @Test
    fun `given --run-id=label when relaying then the label is the run id`() {
        assertEquals("ac01-9103", relayRunId(listOf("observe", "-t", "desktop", "--run-id=ac01-9103")))
    }

    @Test
    fun `given --run-id label when relaying then the label is the run id`() {
        assertEquals("ac01-9103", relayRunId(listOf("observe", "-t", "desktop", "--run-id", "ac01-9103")))
    }

    @Test
    fun `given no run id when relaying then the relay chooses one`() {
        assertNull(relayRunId(listOf("observe", "-t", "desktop")))
    }

    @Test
    fun `given two run ids when relaying then the command is refused as usage`() {
        val refusal = assertFailsWith<ControlException> {
            relayRunId(listOf("observe", "-t", "desktop", "--run-id=a", "--run-id", "b"))
        }
        assertEquals(ErrorCode.USAGE, refusal.code)
    }
}
