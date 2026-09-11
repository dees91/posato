package app.posato.feature.sync.bootstrap

import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppleSyncPendingIntentTest {
    @Test
    fun `given a removal then re-add during fetch when applying then the last saved choice stays present`() = runTest {
        checkPendingOrder(endsPresent = true)
    }

    @Test
    fun `given an addition then removal during fetch when applying then the last saved choice stays absent`() = runTest {
        checkPendingOrder(endsPresent = false)
    }

    private suspend fun TestScope.checkPendingOrder(endsPresent: Boolean) {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), "pending-order-$endsPresent.db")
        val firstFetch = CompletableDeferred<Unit>()
        val secondFetch = CompletableDeferred<Unit>()
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            val present = testPolicy("pending-order.example")
            val absent = testPolicy()
            if (endsPresent) {
                harness.recordDomainChanges(absent, present)
                advanceUntilIdle()
            }
            var fetches = 0
            harness.mailbox.beforeFetch = {
                fetches++
                if (fetches == 1) firstFetch.await() else secondFetch.await()
            }
            harness.sync.syncNow()
            runCurrent()
            assertEquals(1, fetches)
            val initial = if (endsPresent) present else absent
            val intermediate = if (endsPresent) absent else present
            harness.recordDomainChanges(initial, intermediate)
            harness.recordDomainChanges(intermediate, initial)
            assertEquals(2, intentRowCount(harness))

            firstFetch.complete(Unit)
            runCurrent()
            assertEquals(2, fetches)
            val afterFirstApply = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.sqlPolicy.read()).value
            assertEquals(initial, afterFirstApply.policy)

            secondFetch.complete(Unit)
            advanceUntilIdle()
            val settled = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.sqlPolicy.read()).value
            assertEquals(initial, settled.policy)
            assertEquals(0, intentRowCount(harness))
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
        } finally {
            firstFetch.complete(Unit)
            secondFetch.complete(Unit)
            harness.close()
        }
    }
}
