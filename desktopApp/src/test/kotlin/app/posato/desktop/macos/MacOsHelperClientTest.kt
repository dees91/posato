package app.posato.desktop.macos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacOsHelperClientTest {
    @Test
    fun `default client construction does not require a packaged application`() {
        MacOsHelperClient().close()
    }

    @Test
    fun `given a pending unknown request when remove is sent again then the original request is reconciled`() {
        assertTrue(shouldReconcileUnknownRequest(pendingUnknown = true, operation = HelperOperation.Remove))
        assertFalse(shouldReconcileUnknownRequest(pendingUnknown = false, operation = HelperOperation.Remove))
    }

    @Test
    fun `given an unknown request of another operation when removing then it is reconciled before the removal is sent`() {
        val otherOperations = listOf(
            HelperOperation.Apply,
            HelperOperation.Enable,
            HelperOperation.Restore,
            HelperOperation.ConfigureApplications,
        )
        otherOperations.forEach { operation ->
            assertTrue(reconcilesEarlierRequestBeforeRemoval(operation), "operation $operation")
        }
        assertFalse(reconcilesEarlierRequestBeforeRemoval(HelperOperation.Remove))
    }

    @Test
    fun `given an earlier request that stays unknown when removing then remove is not sent and the result is not a removal`() {
        val calls = mutableListOf<String>()
        val attempt = removeAfterReconciling(
            pendingOperation = HelperOperation.Apply,
            reconcileEarlier = {
                calls.add("reconcile")
                HelperResult.unknownOutcome()
            },
            sendRemove = {
                calls.add("remove")
                throw AssertionError("remove must wait for the earlier request")
            },
        )

        assertEquals(listOf("reconcile"), calls)
        assertFalse(attempt.concernsRemove)
    }

    @Test
    fun `given an earlier request that concludes when removing then remove is sent afterwards`() {
        val calls = mutableListOf<String>()
        val removed = HelperResult(
            HelperResult.Outcome.Success,
            HelperResult.State.NotRegistered,
            HelperResult.Phase.Idle,
            HelperResult.RequiredAction.None,
            HelperResult.Failure.None,
        )
        val attempt = removeAfterReconciling(
            pendingOperation = HelperOperation.Enable,
            reconcileEarlier = {
                calls.add("reconcile")
                removed.copy(serviceState = HelperResult.State.Ready)
            },
            sendRemove = {
                calls.add("remove")
                removed
            },
        )

        assertEquals(listOf("reconcile", "remove"), calls)
        assertEquals(HelperRemovalAttempt(removed, concernsRemove = true), attempt)
    }

    @Test
    fun `given a pending remove when removing again then the removal itself is retried without a separate reconcile`() {
        val calls = mutableListOf<String>()
        val attempt = removeAfterReconciling(
            pendingOperation = HelperOperation.Remove,
            reconcileEarlier = {
                calls.add("reconcile")
                throw AssertionError("a pending remove is finished by the removal request")
            },
            sendRemove = {
                calls.add("remove")
                HelperResult.unknownOutcome()
            },
        )

        assertEquals(listOf("remove"), calls)
        assertTrue(attempt.concernsRemove)
    }
}
