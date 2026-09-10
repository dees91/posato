package app.posato.feature.targets.data

import app.posato.feature.sync.bootstrap.AppleSyncTestHarness
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertSame

class SyncTargetPolicyStoreTest {
    @Test
    fun `given the decorator when reading the signal then the raw store flow is forwarded`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            assertSame(harness.sqlPolicy.policyChanges, harness.syncPolicy.policyChanges)
        } finally {
            harness.close()
        }
    }
}
