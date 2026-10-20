package app.posato.control.linux

import app.posato.control.model.Query
import app.posato.control.model.Roles
import app.posato.control.scenario.QueryMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Linux tree comes from AT-SPI, whose role names differ from the shared ones flows query. A wrong role mapping
 * leaves every flow step unfound, and a wrong path presses another control, since presses go by path.
 */
class AtspiSnapshotTest {
    private val root = parseAtspiDump(checkNotNull(javaClass.getResource("/linux/session-screen.json")).readText())

    @Test
    fun `tabs and push buttons are buttons that keep their accessibility path`() {
        assertEquals("0/0/0/1", QueryMatcher.require(root, Query(text = "Pause sets", role = Roles.BUTTON)).path)
        assertEquals("0/0/0/2/1", QueryMatcher.require(root, Query(text = "Start a session", role = Roles.BUTTON)).path)
        assertEquals(false, QueryMatcher.require(root, Query(text = "Add", role = Roles.BUTTON)).enabled)
    }

    @Test
    fun `a control labelled by its content description reads by that label`() {
        assertEquals("0/0/0/2/6", QueryMatcher.require(root, Query(text = "Increase Minutes", role = Roles.BUTTON)).path)
    }

    @Test
    fun `labels are text and unnamed containers are dropped`() {
        assertEquals(Roles.TEXT, QueryMatcher.require(root, Query(text = "NO SESSION ACTIVE")).role)
        assertEquals(Roles.WINDOW, root.role)
        assertEquals(
            listOf("0/0/0/0", "0/0/0/1", "0/0/0/2/0", "0/0/0/2/1", "0/0/0/2/2", "0/0/0/2/6", "0/0/0/2/3", "0/0/0/2/4", "0/0/0/2/5"),
            root.children.map { it.path },
        )
    }

    @Test
    fun `a text field shows its text as value and its hint as placeholder`() {
        val empty = QueryMatcher.require(root, Query(role = Roles.TEXT_FIELD, index = 0))
        assertNull(empty.value)
        assertEquals("Add websites", empty.placeholder)

        val filled = QueryMatcher.require(root, Query(role = Roles.TEXT_FIELD, index = 1))
        assertEquals("Deep work", filled.value)
        assertEquals("Name", filled.label)
        assertEquals(true, filled.focused)
    }
}
