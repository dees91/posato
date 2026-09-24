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
    fun `given a transiently missing scroll region when scrolling then the next snapshot can complete the same search`() {
        listOf(null, Query(text = "Websites", role = "group")).forEach { scope ->
            var snapshots = 0
            var scrolls = 0
            val scroller = DesktopScroller(
                snapshot = {
                    when (snapshots++) {
                        0 -> tree(0, rowY = 500.0)
                        1 -> tree(1).copy(children = emptyList())
                        else -> tree(2)
                    }
                },
                scroll = { _, _ -> scrolls++ },
                settle = {},
            )

            scroller.scrollTo(Query(text = "target.example", within = scope), 1_000)

            assertEquals(3, snapshots)
            assertEquals(1, scrolls)
        }
    }

    @Test
    fun `given a missing scroll region that never returns when scrolling then the deadline still bounds waiting`() {
        var time = 0L
        var scrolls = 0
        val scroller = DesktopScroller(
            snapshot = { tree(0).copy(children = emptyList()) },
            scroll = { _, _ -> scrolls++ },
            nowMillis = { time },
            settle = { time += 100 },
        )

        assertFailsWith<ControlException> { scroller.scrollTo(Query(text = "target.example"), 250) }

        assertEquals(300L, time)
        assertEquals(0, scrolls)
    }

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
    fun `given a button below the scroll area in a tall window when scrolling then it counts as reached without scrolling`() {
        var scrolls = 0
        val scroller = DesktopScroller(snapshot = { onboardingWindow(buttonY = 588.0) }, scroll = { _, _ -> scrolls++ }, settle = {})

        scroller.scrollTo(Query(text = "Continue", role = "button"), 1_000)

        assertEquals(0, scrolls)
    }

    @Test
    fun `given a short scoped list without its own scroll area inside a visible page when scrolling then the row is reached`() {
        var scrolls = 0
        val page = SnapshotNode(
            role = "window",
            frame = Frame(0.0, 0.0, 900.0, 700.0),
            children = listOf(
                scrollArea("Page", Frame(200.0, 0.0, 700.0, 700.0)).copy(
                    children = listOf(
                        SnapshotNode(
                            role = "group",
                            label = "Saved websites",
                            frame = Frame(230.0, 240.0, 600.0, 60.0),
                            children = listOf(SnapshotNode(role = "text", label = "target.example", frame = Frame(270.0, 260.0, 120.0, 20.0))),
                        ),
                    ),
                ),
            ),
        )
        val scroller = DesktopScroller(snapshot = { page }, scroll = { _, _ -> scrolls++ }, settle = {})

        scroller.scrollTo(Query(text = "target.example", role = "text", within = Query(text = "Saved websites", role = "group")), 1_000)

        assertEquals(0, scrolls)
    }

    @Test
    fun `given a button outside the scroll area and below the window when scrolling then it is not reached`() {
        val scroller = DesktopScroller(snapshot = { onboardingWindow(buttonY = 900.0) }, scroll = { _, _ -> }, settle = {})

        assertFailsWith<ControlException> { scroller.scrollTo(Query(text = "Continue", role = "button"), 300) }
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

/** The guest onboarding window from QUALITY-010 run `m3-guest-scenario`: Continue sits below the privacy list's scroll area. */
private fun onboardingWindow(buttonY: Double): SnapshotNode {
    return SnapshotNode(
        role = "other",
        label = "Posato",
        children = listOf(
            SnapshotNode(
                role = "window",
                label = "Posato",
                frame = Frame(48.0, 44.0, 1060.0, 780.0),
                children = listOf(
                    scrollArea("Privacy", Frame(216.0, 254.0, 600.0, 302.0)).copy(
                        children = listOf(SnapshotNode(role = "text", label = "No browsing history", frame = Frame(216.0, 300.0, 400.0, 30.0))),
                    ),
                    SnapshotNode(role = "button", label = "Continue", frame = Frame(216.0, buttonY, 93.0, 44.0)),
                ),
            ),
        ),
    )
}
