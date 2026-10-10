package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Parallel sessions share the machine-wide clone names and Virtualization's limit of two running guests. A session
 * that waits for a line must wait only for what another session will release, and the lease listing must tell a
 * clone someone still uses from one its worktree abandoned; neither state can be staged reliably in a Tart E2E run.
 */
class VmLeaseTest {
    private val golden = TartVm("posato-golden", running = false)

    @Test
    fun `given a free line when deciding whether to wait then nothing blocks`() {
        assertNull(waitableBlock(listOf(golden), "posato-golden", "posato-run-primary"))
    }

    @Test
    fun `given the line's clone or two running guests when deciding whether to wait then the creation waits`() {
        assertNotNull(waitableBlock(listOf(golden, TartVm("posato-run-primary", true)), "posato-golden", "posato-run-primary"))
        val two = listOf(golden, TartVm("posato-run-peer", true), TartVm("other", true))
        assertNotNull(waitableBlock(two, "posato-golden", "posato-run-primary"))
        assertNotNull(waitableBlock(listOf(TartVm("posato-golden", true)), "posato-golden", "posato-run-primary"))
    }

    @Test
    fun `given a missing golden VM when deciding whether to wait then waiting cannot help`() {
        assertNull(waitableBlock(emptyList(), "posato-golden", "posato-run-primary"))
    }

    @Test
    fun `given clones in different states when listing leases then only an abandoned stopped clone is stale`() {
        val owner = CloneOwner("/work/a", "r1", "2026-10-10T08:00:00Z", 1)
        assertEquals(LeaseState.FREE, leaseState(exists = false, running = false, owner = null, worktreeExists = false))
        assertEquals(LeaseState.HELD, leaseState(exists = true, running = true, owner = null, worktreeExists = false))
        assertEquals(LeaseState.HELD, leaseState(exists = true, running = false, owner = owner, worktreeExists = true))
        assertEquals(LeaseState.STALE, leaseState(exists = true, running = false, owner = owner, worktreeExists = false))
        assertEquals(LeaseState.STALE, leaseState(exists = true, running = false, owner = null, worktreeExists = false))
    }
}
