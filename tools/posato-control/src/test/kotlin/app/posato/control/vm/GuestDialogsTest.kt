package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertEquals

class GuestDialogsTest {
    @Test
    fun `system dialogs are named by their owning process`() {
        val windows = parseGuestWindows(
            """
            [{"owner":"SecurityAgent","layer":1000,"title":"Untitled"},
             {"owner":"CoreServicesUIAgent","layer":8,"title":""},
             {"owner":"UserNotificationCenter","layer":8,"title":""},
             {"owner":"universalAccessAuthWarn","layer":8,"title":""},
             {"owner":"Notification Center","layer":23,"title":"Notification Center"}]
            """.trimIndent(),
        )

        assertEquals(
            listOf("admin", "gatekeeper", "system-alert", "accessibility", "notification"),
            dialogsAmong(windows).map { it.kind },
        )
    }

    @Test
    fun `application windows and notification widgets are not dialogs`() {
        val windows = listOf(
            GuestWindow("Posato", 0, "Posato"),
            GuestWindow("Finder", 0, ""),
            GuestWindow("Notification Center", -2147483624, ""),
            GuestWindow("Window Server", 25, "Menubar"),
        )

        assertEquals(emptyList(), dialogsAmong(windows))
    }

    @Test
    fun `a window without a title parses as an empty title`() {
        assertEquals(listOf(GuestWindow("Dock", 20, "")), parseGuestWindows("""[{"owner":"Dock","layer":20}]"""))
    }
}
