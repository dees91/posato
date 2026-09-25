package app.posato.control.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostGuardTest {
    @Test
    fun `refuses to drive the desktop application on the host`() {
        val refusal = assertFailsWith<ControlException> { refuseHostDesktop(Target.DESKTOP, hostAllowed = false) { false } }

        assertEquals(ErrorCode.DESKTOP_HOST_REFUSED, refusal.code)
    }

    @Test
    fun `allows the desktop target inside a virtual machine`() {
        refuseHostDesktop(Target.DESKTOP, hostAllowed = false) { true }
    }

    @Test
    fun `allows building and read-only checks on the host`() {
        refuseHostDesktop(Target.DESKTOP, hostAllowed = true) { false }
    }

    @Test
    fun `leaves the iOS targets and target-less commands alone without probing the host`() {
        val probe: () -> Boolean = { error("must not probe") }

        refuseHostDesktop(Target.SIMULATOR, hostAllowed = false, inVirtualMachine = probe)
        refuseHostDesktop(Target.DEVICE, hostAllowed = false, inVirtualMachine = probe)
        refuseHostDesktop(null, hostAllowed = false, inVirtualMachine = probe)
    }
}
