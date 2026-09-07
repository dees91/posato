package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.model.Frame
import app.posato.control.model.Query
import app.posato.control.model.SnapshotNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopScrollerTest {
    @Test
    fun `given an offscreen row when scrolling then scrolling continues until the whole row is visible`() {
        var position = 0
        val directions = mutableListOf<Boolean>()
        val scroller = DesktopScroller(
            snapshot = { tree(position, rowY = if (position < 2) 380.0 else 100.0) },
            scroll = { _, forward ->
                directions.add(forward)
                position++
            },
            settle = {},
        )

        scroller.scrollTo(Query(text = "target.example"), 1_000)

        assertEquals(listOf(true, true), directions)
    }

    @Test
    fun `given a missing row at the end when scrolling then the search reverses and fails within a bound`() {
        val directions = mutableListOf<Boolean>()
        val scroller = DesktopScroller(snapshot = { tree(0) }, scroll = { _, forward -> directions.add(forward) }, settle = {})

        assertFailsWith<ControlException> { scroller.scrollTo(Query(text = "missing.example"), 1_000) }

        assertEquals(listOf(true, true, true, false, false, false), directions)
    }

    @Test
    fun `given a short timeout when rows keep changing then the deadline stops scrolling`() {
        var time = 0L
        var calls = 0
        val scroller = DesktopScroller(
            snapshot = { tree(calls) },
            scroll = { _, _ -> calls++ },
            nowMillis = { time },
            settle = { time += 100 },
        )

        assertFailsWith<ControlException> { scroller.scrollTo(Query(text = "missing.example"), 250) }

        assertEquals(3, calls)
    }

    @Test
    fun `given an explicitly scoped list when scrolling then the sidebar is not scrolled`() {
        val calls = mutableListOf<String?>()
        val scroller = DesktopScroller(
            snapshot = {
                tree(0).copy(
                    children = listOf(
                        scrollArea("Sidebar", Frame(0.0, 0.0, 900.0, 900.0)),
                        scrollArea("Websites", Frame(900.0, 0.0, 300.0, 400.0)),
                    ),
                )
            },
            scroll = { node, _ -> calls.add(node.label) },
            settle = {},
        )

        assertFailsWith<ControlException> {
            scroller.scrollTo(Query(text = "missing.example", within = Query(text = "Websites", role = "group")), 1_000)
        }

        assertTrue(calls.isNotEmpty())
        assertTrue(calls.all { it == "Websites" })
    }
}

private fun tree(
    position: Int,
    rowY: Double = 100.0
): SnapshotNode {
    return SnapshotNode(
        role = "window",
        value = position.toString(),
        frame = Frame(0.0, 0.0, 600.0, 400.0),
        children = listOf(
            scrollArea("Websites", Frame(0.0, 0.0, 600.0, 400.0)).copy(
                value = position.toString(),
                children = listOf(SnapshotNode(role = "text", label = "target.example", frame = Frame(20.0, rowY, 200.0, 44.0))),
            ),
        ),
    )
}

private fun scrollArea(
    label: String,
    frame: Frame
): SnapshotNode {
    return SnapshotNode(role = "group", label = label, platformRole = "AXScrollArea", frame = frame)
}
