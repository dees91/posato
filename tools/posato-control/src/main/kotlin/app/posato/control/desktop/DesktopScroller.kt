package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Frame
import app.posato.control.model.Query
import app.posato.control.model.SnapshotNode
import app.posato.control.scenario.QueryMatcher

internal class DesktopScroller(
    private val snapshot: () -> SnapshotNode,
    private val scroll: (SnapshotNode, Boolean) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val settle: () -> Unit = { Thread.sleep(SETTLE_MILLIS) },
) {
    fun scrollTo(
        query: Query,
        timeoutMs: Long
    ) {
        val deadline = nowMillis() + timeoutMs
        var previous: SnapshotNode? = null
        var forward = true
        var unchanged = 0
        repeat(MAX_ATTEMPTS) {
            val root = snapshot()
            val container = scrollContainer(root, query)
            val target = QueryMatcher.find(root, query)
            if (target != null && (container?.frame?.contains(target.frame) == true || visibleOutsideScrollAreas(root, target))) {
                return
            }
            val visible = container?.copy(children = container.children.filter { it.platformRole != "AXScrollBar" })
            if (visible != null) unchanged = if (visible == previous) unchanged + 1 else 0
            val reachedStart = container != null && unchanged >= STABLE_SNAPSHOTS && !forward
            if (nowMillis() >= deadline || reachedStart) throw notFound()
            if (container != null) {
                if (unchanged >= STABLE_SNAPSHOTS) {
                    forward = false
                    unchanged = 0
                }
                previous = visible
                scroll(container, forward)
            }
            settle()
        }
        throw notFound()
    }

    private fun scrollContainer(
        root: SnapshotNode,
        query: Query
    ): SnapshotNode? {
        val scope = query.within?.let { QueryMatcher.scope(root, it) ?: return null } ?: root
        return scope.flatten()
            .filter { it.platformRole == SCROLL_AREA && it.frame.w > 0 && it.frame.h > 0 }
            .maxByOrNull { it.frame.w * it.frame.h }
    }

    /**
     * A target counts as visible when every scroll area enclosing it shows it whole and a window contains it. This covers
     * controls laid out below a list, like onboarding's Continue in a tall window, and short scoped lists that have no
     * scroll area of their own inside a page-level one.
     */
    private fun visibleOutsideScrollAreas(
        root: SnapshotNode,
        target: SnapshotNode
    ): Boolean {
        val nodes = root.flatten()
        val enclosing = nodes.filter { node -> node.platformRole == SCROLL_AREA && node.flatten().drop(1).any { it === target } }
        val insideWindow = nodes.any { node -> node.role == "window" && node.frame.contains(target.frame) }
        return insideWindow && enclosing.all { it.frame.contains(target.frame) }
    }

    private fun notFound(): ControlException {
        return ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The requested element did not become visible within the scrolling limit.")
    }
}

private fun Frame.contains(other: Frame): Boolean {
    val hasArea = other.w > 0 && other.h > 0
    val horizontallyInside = other.x >= x && other.x + other.w <= x + w
    val verticallyInside = other.y >= y && other.y + other.h <= y + h
    return hasArea && horizontallyInside && verticallyInside
}

private const val SCROLL_AREA: String = "AXScrollArea"
private const val MAX_ATTEMPTS: Int = 80
private const val STABLE_SNAPSHOTS: Int = 3
private const val SETTLE_MILLIS: Long = 100
private const val NANOS_PER_MILLI: Long = 1_000_000
