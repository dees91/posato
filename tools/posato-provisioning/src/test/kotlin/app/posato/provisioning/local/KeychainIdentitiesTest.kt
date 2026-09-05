package app.posato.provisioning.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeychainIdentitiesTest {
    @Test
    fun `reads the signable development identities and ignores the rest`() {
        val output =
            """
            1) 0123456789ABCDEF0123456789ABCDEF01234567 "Apple Development: Someone (AB12CD34EF)"
            2) FEDCBA9876543210FEDCBA9876543210FEDCBA98 "Apple Distribution: Someone (AB12CD34EF)"
            3) 1111111111111111111111111111111111111111 "Apple Development: Someone Else (ZZ99YY88XX)"
               3 valid identities found
            """.trimIndent()

        assertEquals(
            listOf("Apple Development: Someone (AB12CD34EF)", "Apple Development: Someone Else (ZZ99YY88XX)"),
            KeychainIdentities.parseIdentityNames(output),
        )
    }

    @Test
    fun `reports no identity when the keychain holds none`() {
        assertTrue(KeychainIdentities.parseIdentityNames("     0 valid identities found").isEmpty())
    }

    @Test
    fun `ignores a block that is not a certificate rather than failing the whole read`() {
        val pem = "-----BEGIN CERTIFICATE-----\nnot base64 !!\n-----END CERTIFICATE-----\n"

        assertTrue(KeychainIdentities.parseCertificates(pem).isEmpty())
    }
}
