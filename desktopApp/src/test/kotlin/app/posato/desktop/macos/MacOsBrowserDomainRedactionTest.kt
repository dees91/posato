package app.posato.desktop.macos

import kotlin.test.Test
import kotlin.test.assertFalse

class MacOsBrowserDomainRedactionTest {
    @Test
    fun `given a synthetic privacy canary when rendered then helper types stay redacted`() {
        val host = "canary-selected.example"
        val path = "/unique-path"
        val query = "marker=canary-query"
        val header = "X-Posato-Canary: unique-header"
        val payload = BrowserDomainConfigurePayload(listOf(host), null)
        val response = BrowserDomainConfigureResponse.decode(byteArrayOf(1, 3, 1, 0, 0, 0x11, 0x5B))
        val active = BrowserDomainEnforcementResult.Active(4_443u)
        val rendered = listOf(
            payload.toString(),
            response.toString(),
            active.toString(),
        )

        rendered.forEach { text ->
            assertFalse(text.contains(host))
            assertFalse(text.contains(path))
            assertFalse(text.contains(query))
            assertFalse(text.contains(header))
        }
    }
}
