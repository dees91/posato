package app.posato.control.scenario

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Query
import app.posato.control.model.SnapshotNode

/**
 * Scrolls a whole page, the way a person swipes a phone or turns a mouse wheel, until the element shows in the
 * readable part of the screen: for targets whose tree marks no scroll areas (Compose on Android, text recognition on
 * Linux). It scrolls forward until the page stops changing, then back, and gives up at the deadline.
 */
class PageScroller(
    private val snapshot: () -> SnapshotNode,
    private val scroll: (forward: Boolean) -> Unit,
    /** The share of the screen height, from the top, where a target can be used; below it a bar may cover the page. */
    private val readableShare: Double,
    private val settle: () -> Unit = { Thread.sleep(SETTLE_MILLIS) },
) {
    fun scrollTo(
        query: Query,
        timeoutMs: Long,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var forward = true
        var previous: SnapshotNode? = null
        var unchanged = 0
        while (System.currentTimeMillis() < deadline) {
            val root = snapshot()
            val target = QueryMatcher.find(root, query)
            if (target != null && readable(root, target)) return
            unchanged = if (root == previous) unchanged + 1 else 0
            if (unchanged >= STABLE_SNAPSHOTS) {
                if (!forward) break
                forward = false
                unchanged = 0
            }
            previous = root
            scroll(forward)
            settle()
        }
        throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "No element matching ${query.describe()} came into view by scrolling.")
    }

    private fun readable(
        root: SnapshotNode,
        target: SnapshotNode,
    ): Boolean {
        val centerY = target.frame.y + target.frame.h / 2
        return target.frame.h > 0 && centerY >= root.frame.y && centerY <= root.frame.y + root.frame.h * readableShare
    }

    private companion object {
        const val STABLE_SNAPSHOTS = 2
        const val SETTLE_MILLIS = 400L
    }
}
