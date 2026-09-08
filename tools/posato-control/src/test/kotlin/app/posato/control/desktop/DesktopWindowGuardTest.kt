package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Roles
import app.posato.control.model.SnapshotNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopWindowGuardTest {
    private fun tree(vararg roles: String): SnapshotNode = SnapshotNode(
        role = Roles.OTHER,
        children = roles.map { SnapshotNode(role = it) },
    )

    @Test
    fun `given an empty tree when the guard runs then it fails with the named code`() {
        val failure = assertFailsWith<ControlException> { requireAddressableWindow(tree()) }
        assertEquals(ErrorCode.DESKTOP_WINDOW_UNAVAILABLE, failure.code)
        assertEquals(4, failure.code.exitCode)
        assertTrue(failure.message.orEmpty().contains("no accessibility window"), failure.message)
        assertTrue(failure.message.orEmpty().contains("Relaunch it through `posato-control launch -t desktop`"), failure.message)
        assertTrue(failure.hint.orEmpty().contains("Accessibility access is granted"), failure.hint)
    }

    @Test
    fun `given top-level children with no window when the guard runs then it fails`() {
        val failure = assertFailsWith<ControlException> { requireAddressableWindow(tree(Roles.GROUP, Roles.BUTTON)) }
        assertEquals(ErrorCode.DESKTOP_WINDOW_UNAVAILABLE, failure.code)
    }

    @Test
    fun `given a tree with a window when the guard runs then it returns the same tree`() {
        val full = tree(Roles.WINDOW)
        assertSame(full, requireAddressableWindow(full))
    }
}
