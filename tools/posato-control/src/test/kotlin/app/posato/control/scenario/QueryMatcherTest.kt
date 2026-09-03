package app.posato.control.scenario

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.model.Query
import app.posato.control.model.SnapshotNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class QueryMatcherTest {
    private val root: SnapshotNode = ControlJson.lenient.decodeFromString(
        SnapshotNode.serializer(),
        checkNotNull(javaClass.getResource("/snapshots/targets-desktop.json")).readText(),
    )

    @Test
    fun `text matches label or value`() {
        assertEquals("0/0/0/3/2", QueryMatcher.require(root, Query(text = "Add website")).path)
        assertEquals("0/0/0/4/0", QueryMatcher.require(root, Query(text = "example.com")).path)
    }

    @Test
    fun `role and index select text fields in tree order`() {
        assertEquals("0/0/0/1/1", QueryMatcher.require(root, Query(role = "textField", index = 0)).path)
        assertEquals("0/0/0/3/0", QueryMatcher.require(root, Query(role = "textField", index = 1)).path)
        assertNull(QueryMatcher.find(root, Query(role = "textField", index = 2)))
    }

    @Test
    fun `within restricts the search to an ancestor subtree`() {
        val inRow = QueryMatcher.require(root, Query(text = "Remove", role = "button", within = Query(text = "example.com", role = "group")))
        assertEquals("0/0/0/4/2", inRow.path)
        val disabled = QueryMatcher.require(root, Query(text = "Remove", within = Query(text = "Social feeds", role = "group")))
        assertEquals(false, disabled.enabled)
    }

    @Test
    fun `near prefers the match closest to the anchor`() {
        assertEquals("0/0/0/4/2", QueryMatcher.require(root, Query(text = "Remove", role = "button", near = Query(text = "example.com"))).path)
        assertEquals("0/0/0/1/3", QueryMatcher.require(root, Query(text = "Remove", near = Query(text = "Social feeds"))).path)
        assertEquals("0/0/0/3/0", QueryMatcher.require(root, Query(role = "textField", near = Query(text = "Add website"))).path)
        assertEquals("0/0/0/1/1", QueryMatcher.require(root, Query(role = "textField", near = Query(text = "Add website"), index = 1)).path)
        assertNull(QueryMatcher.find(root, Query(text = "Remove", near = Query(text = "Missing anchor"))))
    }

    @Test
    fun `without within every remove button matches in tree order`() {
        val matches = QueryMatcher.findAll(root, Query(text = "Remove"))
        assertEquals(listOf("0/0/0/1/3", "0/0/0/4/2"), matches.map { it.path })
    }

    @Test
    fun `within without a role scopes to the anchor element itself`() {
        assertNull(QueryMatcher.find(root, Query(text = "Remove", within = Query(text = "example.com"))))
        assertEquals("0/0/0/4", QueryMatcher.scope(root, Query(text = "example.com", role = "group"))?.path)
        assertEquals("0", QueryMatcher.scope(root, Query(text = "example.com", role = "window"))?.path)
    }

    @Test
    fun `text contains and path queries work`() {
        assertEquals("0/0/0/3/1", QueryMatcher.require(root, Query(textContains = "exact domain")).path)
        assertEquals("button", QueryMatcher.require(root, Query(path = "0/0/0/4/1")).role)
    }

    @Test
    fun `missing element reports a hint`() {
        val failure = assertFailsWith<ControlException> { QueryMatcher.require(root, Query(text = "Missing")) }
        assertEquals(ErrorCode.ELEMENT_NOT_FOUND, failure.code)
    }

    @Test
    fun `outline lists roles labels and paths`() {
        val outline = root.outline()
        assertEquals(true, outline.contains("button \"Add website\" @0/0/0/3/2"))
        assertEquals(true, outline.contains("[disabled]"))
    }
}
