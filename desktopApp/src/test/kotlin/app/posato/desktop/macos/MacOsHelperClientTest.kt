package app.posato.desktop.macos

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacOsHelperClientTest {
    @Test
    fun `default client construction does not require a packaged application`() {
        MacOsHelperClient().close()
    }

    @Test
    fun `given a pending unknown request when remove is sent again then the original request is reconciled`() {
        assertTrue(shouldReconcileUnknownRequest(pendingUnknown = true, operation = HelperOperation.Remove))
        assertFalse(shouldReconcileUnknownRequest(pendingUnknown = false, operation = HelperOperation.Remove))
    }

    @Test
    fun `given an unknown request of another operation when removing then it is reconciled before the removal is sent`() {
        val otherOperations = listOf(
            HelperOperation.Apply,
            HelperOperation.Enable,
            HelperOperation.Restore,
            HelperOperation.ConfigureApplications,
        )
        otherOperations.forEach { operation ->
            assertTrue(reconcilesEarlierRequestBeforeRemoval(operation), "operation $operation")
        }
        assertFalse(reconcilesEarlierRequestBeforeRemoval(HelperOperation.Remove))
    }
}
