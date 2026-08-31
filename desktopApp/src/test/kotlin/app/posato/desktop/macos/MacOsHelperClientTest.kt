package app.posato.desktop.macos

import kotlin.test.Test

class MacOsHelperClientTest {
    @Test
    fun `default client construction does not require a packaged application`() {
        MacOsHelperClient().close()
    }
}
