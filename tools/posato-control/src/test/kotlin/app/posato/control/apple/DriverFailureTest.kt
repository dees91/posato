package app.posato.control.apple

import app.posato.control.core.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DriverFailureTest {
    @Test
    fun `an automation mode timeout asks the owner to unlock the device`() {
        val log =
            """
            Testing started
            PosatoDriverUITests-Runner (1861) encountered an error (The test runner failed to initialize for UI testing. (Underlying Error: Timed out while enabling automation mode.))
            ** TEST EXECUTE FAILED **
            """.trimIndent()

        val failure = driverLogFailure(log)

        assertEquals(ErrorCode.DEVICE_AUTOMATION_LOCKED, failure?.code)
    }

    @Test
    fun `an ordinary failing log keeps the generic driver failure`() {
        assertNull(driverLogFailure("Testing failed:\n\tSome assertion\n** TEST EXECUTE FAILED **"))
    }
}
