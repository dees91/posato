package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testIdentifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BootstrapEncodingTest {
    @Test
    fun `given sixteen identifier bytes when formatted then canonical lowercase uuid text is produced`() {
        assertEquals("00000000-0000-4000-8000-000000000001", BootstrapEncoding.identifierToAccountText(testIdentifier(1)))
    }

    @Test
    fun `given a canonical account text when validated then it is accepted`() {
        assertTrue(BootstrapEncoding.isCanonicalAccountText("00000000-0000-4000-8000-000000000001"))
    }

    @Test
    fun `given uppercase or malformed account text when validated then it is rejected`() {
        assertTrue(!BootstrapEncoding.isCanonicalAccountText("00000000-0000-4000-8000-00000000000Z"))
        assertTrue(!BootstrapEncoding.isCanonicalAccountText("abcdef00-0000-4000-8000-000000000001".uppercase()))
        assertTrue(!BootstrapEncoding.isCanonicalAccountText("0000000040008000000000000001"))
        assertTrue(!BootstrapEncoding.isCanonicalAccountText(""))
    }

    @Test
    fun `given arbitrary bytes when version stamped then a valid uuidv4 identifier results`() {
        val stamped = BootstrapEncoding.applyUuidVersionFour(ByteArray(16) { index -> index.toByte() })

        assertNotNull(stamped)
        assertTrue(stamped.isUuidV4())
    }

    @Test
    fun `given a wrong size when stamped then null is returned`() {
        assertNull(BootstrapEncoding.applyUuidVersionFour(ByteArray(15)))
    }

    @Test
    fun `given the standard check input when checksummed then the iso-hdlc check value results`() {
        assertEquals(0xCBF43926.toInt(), BootstrapEncoding.crc32("123456789".encodeToByteArray(), 9))
    }

    @Test
    fun `given valid identifiers and key when round-tripped then the decoded item matches`() {
        val key = ByteArray(WORKSPACE_KEY_BYTES) { index -> (index * 3).toByte() }
        val encoded = assertNotNull(
            BootstrapEncoding.encodeKeyItem(
                WorkspaceId(testIdentifier(1)),
                TransportEpochId(testIdentifier(2)),
                KeyEpochId(testIdentifier(3)),
                key,
            ),
        )

        assertEquals(KEYCHAIN_ITEM_BYTES, encoded.copyBytes().size)
        val decoded = assertNotNull(BootstrapEncoding.decodeKeyItem(encoded))
        assertEquals(WorkspaceId(testIdentifier(1)), decoded.workspaceId)
        assertEquals(TransportEpochId(testIdentifier(2)), decoded.transportEpochId)
        assertEquals(KeyEpochId(testIdentifier(3)), decoded.keyEpochId)
        assertTrue(key.contentEquals(decoded.workspaceKey))
        decoded.clear()
    }

    @Test
    fun `given a corrupted checksum when decoded then null is returned`() {
        val key = ByteArray(WORKSPACE_KEY_BYTES) { 9 }
        val encoded = assertNotNull(
            BootstrapEncoding.encodeKeyItem(
                WorkspaceId(testIdentifier(1)),
                TransportEpochId(testIdentifier(2)),
                KeyEpochId(testIdentifier(3)),
                key,
            ),
        )
        val tampered = encoded.copyBytes()
        tampered[20] = (tampered[20] + 1).toByte()

        assertNull(BootstrapEncoding.decodeKeyItem(checkNotNull(WorkspaceKeyItem.fromBytes(tampered))))
    }

    @Test
    fun `given non-uuid identifiers when decoded then null is returned`() {
        val key = ByteArray(WORKSPACE_KEY_BYTES) { 9 }
        val raw = ByteArray(KEYCHAIN_ITEM_BYTES)
        ByteArray(16) { 1 }.copyInto(raw, 0)
        ByteArray(16) { 2 }.copyInto(raw, 16)
        ByteArray(16) { 3 }.copyInto(raw, 32)
        key.copyInto(raw, 48)
        val checksum = BootstrapEncoding.crc32(raw, KEYCHAIN_ITEM_BYTES - 4)
        repeat(4) { index ->
            raw[80 + index] = (checksum ushr (24 - index * 8)).toByte()
        }

        assertNull(BootstrapEncoding.decodeKeyItem(checkNotNull(WorkspaceKeyItem.fromBytes(raw))))
    }

    @Test
    fun `given equal and different arrays when compared then constant-time equality holds`() {
        assertTrue(BootstrapEncoding.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertTrue(!BootstrapEncoding.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertTrue(!BootstrapEncoding.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
    }
}
