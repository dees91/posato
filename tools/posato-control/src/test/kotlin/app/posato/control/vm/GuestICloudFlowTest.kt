package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The host splits `flow icloud`'s wait into slices so it can check iCloud Keychain between them. A link on a fresh
 * clone can wait about 20 minutes for its key, which E2E cannot provoke without changing the shared test account's
 * workspace, so the slicing rules are proven here: the whole requested wait is kept in either option spelling, and
 * only a timeout leads to another slice.
 */
class GuestICloudFlowTest {
    @Test
    fun `given the flow's timeout in either spelling when slicing the wait then the whole requested time is kept`() {
        assertEquals(ICLOUD_FLOW_TIMEOUT_SECONDS, flowTimeoutSeconds(listOf("flow", "icloud", "link", "-t", "desktop")))
        assertEquals(1_200L, flowTimeoutSeconds(listOf("flow", "icloud", "link", "--timeout-seconds", "1200", "-t", "desktop")))
        assertEquals(1_200L, flowTimeoutSeconds(listOf("flow", "icloud", "link", "--timeout-seconds=1200")))
        assertNull(flowTimeoutSeconds(listOf("flow", "icloud", "link", "--timeout-seconds", "soon")))
    }

    @Test
    fun `given a slice when rewriting the arguments then the slice replaces the requested timeout once`() {
        val arguments = listOf("flow", "icloud", "link", "--timeout-seconds=1200", "-t", "desktop", "--run-id", "r1")

        assertEquals(
            listOf("flow", "icloud", "link", "-t", "desktop", "--run-id", "r1", "--timeout-seconds", "120"),
            withFlowTimeout(arguments, 120),
        )
        assertEquals(
            listOf("flow", "icloud", "remove", "-t", "desktop", "--timeout-seconds", "60"),
            withFlowTimeout(listOf("flow", "icloud", "remove", "--timeout-seconds", "300", "-t", "desktop"), 60),
        )
    }

    @Test
    fun `given a guest envelope when deciding on another slice then only a timeout counts`() {
        fun envelope(code: String?) = if (code == null) {
            """{"ok":true,"command":"icloud","runId":"r1","durationMs":1,"result":{"presses":0}}"""
        } else {
            """{"ok":false,"command":"icloud","runId":"r1","durationMs":1,"error":{"code":"$code","message":"m"}}"""
        }

        assertTrue(guestTimedOut(envelope("WAIT_TIMEOUT")))
        assertFalse(guestTimedOut(envelope("ELEMENT_NOT_FOUND")))
        assertFalse(guestTimedOut(envelope(null)))
        assertFalse(guestTimedOut("Error: the guest agent stopped"))
    }

    /**
     * A clone whose keychain cannot be resumed must still be able to remove its workspace, or `vm destroy` refuses it
     * with WORKSPACE_LINKED and the workspace stays in the shared test account. E2E cannot pause a clone's keychain on
     * demand without signing a device in to that account.
     */
    @Test
    fun `given a workspace removal when relaying flow icloud then the keychain gate applies to a link only`() {
        assertTrue(checksICloudKeychain(listOf("flow", "icloud", "link", "-t", "desktop")))
        assertFalse(checksICloudKeychain(listOf("flow", "icloud", "remove", "-t", "desktop")))
        assertFalse(checksICloudKeychain(listOf("flow", "set", "create", "-t", "desktop")))
    }
}
