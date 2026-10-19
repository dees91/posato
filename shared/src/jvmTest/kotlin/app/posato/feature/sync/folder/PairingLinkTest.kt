package app.posato.feature.sync.folder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A pairing link arrives from outside the application (a camera scan, a message, a paste), so only a well-formed
 * join link or code may reach the join; anything else is ignored, never guessed at.
 */
class PairingLinkTest {
    private val code = encodeCode(ByteArray(16) { index -> (index * 7).toByte() })

    @Test
    fun `given a code when made into a link then reading the link gives the same code back`() {
        val link = pairingLink(code)

        assertEquals("posato://join?code=$code", link)
        assertEquals(code, pairingCodeFrom(link))
    }

    @Test
    fun `given a pasted link or code in any case with spaces when read then the code is found`() {
        assertEquals(code, pairingCodeFrom("  POSATO://JOIN?CODE=${code.lowercase()} "))
        assertEquals(code, pairingCodeFrom(code.chunked(3).joinToString(" ")))
        assertNotNull(decodeCode(checkNotNull(pairingCodeFrom(code.lowercase()))))
    }

    @Test
    fun `given a link for something else or with extra parts when read then no code is taken`() {
        assertNull(pairingCodeFrom("posato://remove?code=$code"))
        assertNull(pairingCodeFrom("https://example.org/join?code=$code"))
        assertNull(pairingCodeFrom("posato://join?code=$code&next=https://example.org"))
        assertNull(pairingCodeFrom("posato://join"))
    }

    @Test
    fun `given a link whose code does not check when read then it is not offered as a code`() {
        val mistyped = code.dropLast(1) + if (code.last() == 'A') 'B' else 'A'

        assertNull(pairingCodeFrom(pairingLink(mistyped)))
    }
}
