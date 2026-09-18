package app.posato.feature.targets.data

import app.posato.feature.sync.bootstrap.AppleSyncTestHarness
import app.posato.feature.sync.bootstrap.testPolicy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun `given an apex-only policy when first read then the www counterpart is stored once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(
                harness.sqlPolicy.replace(0, testPolicy("example.com")),
            )

            val first = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.syncPolicy.read()).value
            assertEquals(
                listOf("example.com", "www.example.com"),
                first.policy.domains.map { domain -> domain.canonicalValue },
            )
            val second = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.syncPolicy.read()).value
            assertEquals(first.revision, second.revision)
            assertTrue(harness.sqlPolicy.wwwCounterpartExpansionCompleted())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a paired policy when first read then revision is unchanged`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(
                harness.sqlPolicy.replace(0, testPolicy("example.com", "www.example.com")),
            )

            val first = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.syncPolicy.read()).value
            assertEquals(1, first.revision)
            assertEquals(
                listOf("example.com", "www.example.com"),
                first.policy.domains.map { domain -> domain.canonicalValue },
            )
            assertTrue(harness.sqlPolicy.wwwCounterpartExpansionCompleted())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an expanded policy when one counterpart is removed then the next read does not restore it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(
                harness.sqlPolicy.replace(0, testPolicy("example.com")),
            )
            val expanded = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.syncPolicy.read()).value
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(
                harness.syncPolicy.replace(expanded.revision, testPolicy("example.com")),
            )

            val afterRemoval = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.syncPolicy.read()).value
            assertEquals(listOf("example.com"), afterRemoval.policy.domains.map { domain -> domain.canonicalValue })
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an apex-only policy when first read then the raw store also contains the counterpart`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(
                harness.sqlPolicy.replace(0, testPolicy("example.com")),
            )

            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.syncPolicy.read())
            val raw = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.sqlPolicy.read()).value
            assertEquals(
                listOf("example.com", "www.example.com"),
                raw.policy.domains.map { domain -> domain.canonicalValue },
            )
        } finally {
            harness.close()
        }
    }
}
