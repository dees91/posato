package app.posato.control.scenario

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Query
import app.posato.control.model.Roles
import app.posato.control.model.SnapshotNode

object QueryMatcher {
    fun matches(
        node: SnapshotNode,
        query: Query
    ): Boolean {
        if (query.path != null && node.path != query.path) return false
        if (query.id != null && node.id != query.id) return false
        if (!roleMatches(node, query.role)) return false
        return textMatches(node, query)
    }

    fun findAll(
        root: SnapshotNode,
        query: Query
    ): List<SnapshotNode> {
        val scope = query.within?.let { inner -> scope(root, inner) ?: return emptyList() } ?: root
        var hits = scope.flatten().filter { node -> matches(node, query) }
        query.near?.let { nearQuery ->
            val anchor = find(root, nearQuery) ?: return emptyList()
            hits = hits.sortedBy { candidate -> distance(candidate, anchor) }
        }
        val index = query.index ?: return hits
        return listOfNotNull(hits.getOrNull(index))
    }

    fun find(
        root: SnapshotNode,
        query: Query
    ): SnapshotNode? = findAll(root, query).firstOrNull()

    fun require(
        root: SnapshotNode,
        query: Query
    ): SnapshotNode = find(root, query)
        ?: throw ControlException(
            ErrorCode.ELEMENT_NOT_FOUND,
            "No element matches ${query.describe()}.",
            "Run `snapshot --format text` to see the current labels, roles, and paths.",
        )

    /**
     * Resolves the scope of a `within` query: the nearest ancestor (or the element itself) with the
     * requested role of the first element whose text or identifier matches the inner query.
     */
    fun scope(
        root: SnapshotNode,
        within: Query
    ): SnapshotNode? {
        val anchorQuery = within.copy(role = null, within = null, near = null)
        val ancestors = ArrayDeque<SnapshotNode>()
        var found: SnapshotNode? = null

        fun visit(node: SnapshotNode) {
            if (found != null) return
            ancestors.addLast(node)
            if (matches(node, anchorQuery)) {
                found = ancestors.lastOrNull { roleMatches(it, within.role) }
            }
            node.children.forEach(::visit)
            ancestors.removeLast()
        }
        visit(root)
        return found
    }

    private fun distance(
        a: SnapshotNode,
        b: SnapshotNode
    ): Double {
        val dx = (a.frame.x + a.frame.w / 2) - (b.frame.x + b.frame.w / 2)
        val dy = (a.frame.y + a.frame.h / 2) - (b.frame.y + b.frame.h / 2)
        return dx * dx + dy * dy
    }

    private fun roleMatches(
        node: SnapshotNode,
        role: String?
    ): Boolean = role == null || role == Roles.ANY || node.role == role

    private fun textMatches(
        node: SnapshotNode,
        query: Query
    ): Boolean {
        if (query.text != null && node.label != query.text && node.value != query.text) return false
        val needle = query.textContains ?: return true
        return node.label?.contains(needle) == true || node.value?.contains(needle) == true
    }
}
