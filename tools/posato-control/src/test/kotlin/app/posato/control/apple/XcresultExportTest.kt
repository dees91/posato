package app.posato.control.apple

import kotlin.test.Test
import kotlin.test.assertEquals

class XcresultExportTest {
    @Test
    fun `attachment names lose the xcresulttool suffix`() {
        assertEquals("result.json", attachmentFileName("result_0_00000000-0000-4000-8000-000000000001.json"))
        assertEquals("failure-3-snapshot.json", attachmentFileName("failure-3-snapshot_0_00000000-0000-4000-8000-000000000002.json"))
        assertEquals("screenshot-6-after-add.png", attachmentFileName("screenshot-6-after-add_1_00000000-0000-4000-8000-000000000002.png"))
        assertEquals("plain.png", attachmentFileName("plain.png"))
    }
}
