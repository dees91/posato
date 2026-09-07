package app.posato.desktop.macos

import kotlin.test.Test
import kotlin.test.assertFalse

class MacOsApplicationRedactionTest {
    @Test
    fun `given a synthetic canary when rendered then helper types stay redacted`() {
        val canary = "canary-application-blob"
        val payload = ApplicationEnforcementPayload(listOf(canary.encodeToByteArray()), null)
        val response = ApplicationEnforcementResponse.decode(byteArrayOf(1, 3, 1, 0, 0, 0, 1))
        val active = ApplicationEnforcementResult.Active(1)
        val rendered = listOf(
            payload.toString(),
            response.toString(),
            active.toString(),
        )

        rendered.forEach { text ->
            assertFalse(text.contains(canary))
        }
    }
}
