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

    @Test
    fun `given an unknown apply with grant when retained then it reconciles under the apply identity`() {
        val port = byteArrayOf(0xC3.toByte(), 0x51)
        val failed = HelperMessage(
            kind = HelperMessageKind.Request,
            operation = HelperOperation.ApplyWithGrant,
            sequence = 2,
            deadlineMilliseconds = 1_000,
            connectionIdentifier = ByteArray(16) { 1 },
            sessionIdentifier = ByteArray(16) { 2 },
            requestIdentifier = ByteArray(16) { 3 },
            payload = port,
        )

        val retained = retainPendingUnknownRequest(pending = null, failed = failed)

        assertEquals(HelperOperation.Apply, retained.operation)
        assertTrue(retained.payload.contentEquals(port))
        assertTrue(retained.requestIdentifier.contentEquals(failed.requestIdentifier))
    }

    @Test
    fun `given a flagged status reply when decoded then only a six byte success names the grant`() {
        val success = byteArrayOf(1, 3, 1, 0, 0)
        val rejectedFlag = byteArrayOf(5, 3, 1, 0, 1)
        val localAnswer = byteArrayOf(3, 5, 5, 5, 8)

        assertEquals(HelperGrantState.On, decodeGrantState(success + byteArrayOf(3)))
        assertEquals(HelperGrantState.Off, decodeGrantState(success + byteArrayOf(1)))
        assertEquals(HelperGrantState.Off, decodeGrantState(success + byteArrayOf(0)))
        assertEquals(HelperGrantState.Unsupported, decodeGrantState(rejectedFlag))
        assertEquals(HelperGrantState.Unknown, decodeGrantState(localAnswer))
        assertEquals(HelperGrantState.Unknown, decodeGrantState(success))
        assertEquals(HelperGrantState.Unknown, decodeGrantState(null))
        assertEquals(HelperGrantState.Unknown, decodeGrantState(success + byteArrayOf(3, 0)))
    }

    @Test
    fun `given the grant failure code when decoded then it follows cancelled`() {
        val result = HelperResult.decode(byteArrayOf(5, 3, 1, 0, 10))

        assertEquals(HelperResult.Failure.StandingGrantUnavailable, result.failure)
    }
}
