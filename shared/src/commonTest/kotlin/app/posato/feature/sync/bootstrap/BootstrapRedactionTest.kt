package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BootstrapRedactionTest {
    @Test
    fun `given bootstrap carriers when converted to strings then bytes remain redacted`() {
        val binding = assertNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 1 }))
        val key = assertNotNull(WorkspaceKeyValue.fromBytes(ByteArray(WORKSPACE_KEY_BYTES) { 2 }))
        val anchor = WorkspaceAnchor(
            WorkspaceId(testIdentifier(1)),
            TransportEpochId(testIdentifier(2)),
            KeyEpochId(testIdentifier(3)),
        )

        assertEquals("AccountBinding(redacted)", binding.toString())
        assertEquals("WorkspaceKeyValue(redacted)", key.toString())
        assertEquals("WorkspaceAnchor(redacted)", anchor.toString())
        assertEquals("WorkspaceKeyItem(redacted)", workspaceItem().toString())
        assertEquals(
            "KeyAccount(redacted)",
            assertNotNull(KeyAccount.fromText("00000000-0000-4000-8000-000000000001")).toString(),
        )
    }

    @Test
    fun `given persisted bootstrap state when converted to strings then identifiers remain redacted`() {
        val binding = assertNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 1 }))
        val candidate = PersistedCandidate(
            WorkspaceId(testIdentifier(1)),
            TransportEpochId(testIdentifier(2)),
            KeyEpochId(testIdentifier(3)),
            binding,
        )
        val established = EstablishedWorkspace(testContext, binding)

        assertEquals("PersistedCandidate(redacted)", candidate.toString())
        assertEquals("EstablishedWorkspace(redacted)", established.toString())
        val domain = assertNotNull(ExactDomain.restore("example.com"))
        val write = PolicySyncWrite(ByteArray(16) { 4 }, listOf(StoredPolicyIntent.PresentDomain(domain)))
        assertEquals("PolicySyncWrite(redacted)", write.toString())
        val sequenced = SequencedPolicyIntent(1, ByteArray(16) { 4 }, StoredPolicyIntent.RemoveDomain(domain))
        assertEquals("SequencedPolicyIntent(redacted)", sequenced.toString())
        assertEquals("EstablishedCheck(redacted)", EstablishedCheck(EstablishedStatus.READY, established).toString())
        assertEquals("DecodedKeyItem(redacted)", decodedItem().toString())
    }

    @Test
    fun `given bootstrap outcomes when converted to strings then secret material remains redacted`() {
        val key = assertNotNull(WorkspaceKeyValue.fromBytes(ByteArray(WORKSPACE_KEY_BYTES) { 2 }))

        assertEquals("WorkspaceKeyRead.Ready(redacted)", WorkspaceKeyRead.Ready(testContext, key).toString())
        assertTrue(BootstrapResult.Ready(testContext).toString().contains("redacted"))
    }

    private fun workspaceItem(): WorkspaceKeyItem {
        val key = ByteArray(WORKSPACE_KEY_BYTES) { 6 }
        val encoded = assertNotNull(
            BootstrapEncoding.encodeKeyItem(
                WorkspaceId(testIdentifier(5)),
                TransportEpochId(testIdentifier(6)),
                KeyEpochId(testIdentifier(7)),
                key,
            ),
        )
        return encoded
    }

    private fun decodedItem(): DecodedKeyItem {
        val key = ByteArray(WORKSPACE_KEY_BYTES) { 4 }
        val encoded = assertNotNull(
            BootstrapEncoding.encodeKeyItem(
                WorkspaceId(testIdentifier(5)),
                TransportEpochId(testIdentifier(6)),
                KeyEpochId(testIdentifier(7)),
                key,
            ),
        )
        return assertNotNull(BootstrapEncoding.decodeKeyItem(encoded))
    }
}
