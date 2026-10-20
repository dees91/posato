package app.posato.control.android

import app.posato.control.model.Query
import app.posato.control.model.Roles
import app.posato.control.scenario.QueryMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Compose on Android exposes a control as a clickable view whose texts and role marker are separate children. Flows
 * address controls by role and whole label, as on the Mac and iOS, so a control must read as one element; a missed
 * merge makes every flow step fail, and a merge across controls makes a step tap the wrong one.
 */
class AndroidSnapshotTest {
    private val root = parseUiAutomatorDump(checkNotNull(javaClass.getResource("/android/websites-screen.xml")).readText())

    @Test
    fun `a clickable view reads as one button labelled by its text`() {
        val back = QueryMatcher.require(root, Query(text = "Back to pause sets", role = Roles.BUTTON))

        assertEquals(206.0, back.frame.x + back.frame.w / 2)
        assertNull(QueryMatcher.find(root, Query(text = "Back to pause sets", role = Roles.TEXT)))
    }

    @Test
    fun `a row joins its texts and keeps the control inside it apart`() {
        QueryMatcher.require(root, Query(text = "example.org, Also pauses www.example.org", role = Roles.BUTTON))

        val actions = QueryMatcher.require(root, Query(text = "Actions for example.org", role = Roles.BUTTON))
        assertEquals(959.0, actions.frame.x + actions.frame.w / 2)
    }

    @Test
    fun `a text field shows its placeholder or its text and keeps its trailing button`() {
        val empty = QueryMatcher.require(root, Query(role = Roles.TEXT_FIELD, index = 0))
        assertEquals("Add websites", empty.placeholder)
        assertNull(empty.value)

        val filled = QueryMatcher.require(root, Query(role = Roles.TEXT_FIELD, index = 1))
        assertEquals("example.org", filled.value)
        assertEquals(true, filled.focused)

        assertEquals(false, QueryMatcher.require(root, Query(text = "Add", role = Roles.BUTTON)).enabled)
    }

    @Test
    fun `the selected tab still reads as a button`() {
        QueryMatcher.require(root, Query(text = "Session", role = Roles.BUTTON))
        QueryMatcher.require(root, Query(text = "Pause sets", role = Roles.BUTTON))
    }
}
