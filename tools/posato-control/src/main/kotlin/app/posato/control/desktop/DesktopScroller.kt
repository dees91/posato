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
            if (target != null && container.frame.contains(target.frame)) {
                return
            }
            val visible = container.copy(children = container.children.filter { it.platformRole != "AXScrollBar" })
            unchanged = if (visible == previous) unchanged + 1 else 0
            if (nowMillis() >= deadline || (unchanged >= STABLE_SNAPSHOTS && !forward)) throw notFound()
            if (unchanged >= STABLE_SNAPSHOTS) {
                forward = false
                unchanged = 0
            }
            previous = visible
            scroll(container, forward)
            settle()
        }
        throw notFound()
    }

    private fun scrollContainer(
        root: SnapshotNode,
        query: Query
    ): SnapshotNode {
        val scope = query.within?.let { QueryMatcher.scope(root, it) ?: throw notFound() } ?: root
        return scope.flatten()
            .filter { it.platformRole == "AXScrollArea" && it.frame.w > 0 && it.frame.h > 0 }
            .maxByOrNull { it.frame.w * it.frame.h }
            ?: throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "No visible scroll area matches the requested scope.")
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

private const val MAX_ATTEMPTS: Int = 80
private const val STABLE_SNAPSHOTS: Int = 3
private const val SETTLE_MILLIS: Long = 100
private const val NANOS_PER_MILLI: Long = 1_000_000
