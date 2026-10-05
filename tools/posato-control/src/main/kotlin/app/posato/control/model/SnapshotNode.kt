package app.posato.control.model

import kotlinx.serialization.Serializable

@Serializable
data class Frame(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
)

@Serializable
data class SnapshotNode(
    val role: String,
    val id: String? = null,
    val label: String? = null,
    val value: String? = null,
    val placeholder: String? = null,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val frame: Frame = Frame(),
    val platformRole: String? = null,
    val path: String? = null,
    val children: List<SnapshotNode> = emptyList(),
) {
    fun walk(visit: (SnapshotNode) -> Unit) {
        visit(this)
        children.forEach { child -> child.walk(visit) }
    }

    fun flatten(): List<SnapshotNode> {
        val nodes = mutableListOf<SnapshotNode>()
        walk { node -> nodes.add(node) }
        return nodes
    }

    fun outline(indent: String = ""): String = buildString {
        append(indent).append(role)
        id?.let { append(" #").append(it) }
        label?.let { append(" \"").append(it).append('"') }
        value?.let { append(" =").append('"').append(it.take(MAX_VALUE_PREVIEW)).append('"') }
        placeholder?.let { append(" ph=\"").append(it).append('"') }
        if (!enabled) append(" [disabled]")
        if (focused) append(" [focused]")
        path?.let { append(" @").append(it) }
        append('\n')
        children.forEach { child -> append(child.outline("$indent  ")) }
    }

    /**
     * One line per element that a query can name (an id, a label, or a placeholder), with a text field's contents,
     * in tree order and without paths or layout: the short list of what is on screen.
     */
    fun labels(): String = flatten()
        .filter { node -> node.id != null || node.label != null || node.placeholder != null }
        .map { node ->
            buildString {
                append(node.role)
                node.id?.let { append(" #").append(it) }
                node.label?.let { append(" \"").append(it).append('"') }
                if (node.role == Roles.TEXT_FIELD) node.value?.let { append(" =\"").append(it.take(MAX_VALUE_PREVIEW)).append('"') }
                node.placeholder?.let { append(" ph=\"").append(it).append('"') }
                if (!node.enabled) append(" [disabled]")
                if (node.focused) append(" [focused]")
            }
        }
        .fold(mutableListOf<String>()) { lines, line -> lines.apply { if (lastOrNull() != line) add(line) } }
        .joinToString("\n", postfix = "\n")
}

private const val MAX_VALUE_PREVIEW = 60

object Roles {
    const val BUTTON = "button"
    const val TEXT_FIELD = "textField"
    const val TEXT = "text"
    const val GROUP = "group"
    const val WINDOW = "window"
    const val PROGRESS = "progress"
    const val OTHER = "other"
    const val ANY = "any"
}
