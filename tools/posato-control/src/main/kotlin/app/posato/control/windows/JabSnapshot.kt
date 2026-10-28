package app.posato.control.windows

import app.posato.control.core.ControlJson
import app.posato.control.model.Frame
import app.posato.control.model.Roles
import app.posato.control.model.SnapshotNode
import kotlinx.serialization.Serializable

/** One element as `posato-jab.py dump` prints it. */
@Serializable
private data class JabNode(
    val role: String,
    val name: String? = null,
    val description: String? = null,
    val text: String? = null,
    val states: List<String> = emptyList(),
    val bounds: List<Int> = emptyList(),
    val actions: List<String> = emptyList(),
    val path: String = "",
    val children: List<JabNode> = emptyList(),
)

private val BUTTON_ROLES =
    setOf("push button", "toggle button", "page tab", "check box", "radio button", "menu item", "hyperlink", "combo box", "list item")
private val TEXT_ROLES = setOf("label", "heading", "paragraph")
private val FIELD_ROLES = setOf("text", "password text")

/**
 * Reads Posato's Java Access Bridge tree into the element tree every target shares, as the AT-SPI tree on Linux is
 * read: buttons, tabs, and menu items as buttons, labels as text, text fields with their contents as value and their
 * description as placeholder. Swing's frame, panes, and Compose's unnamed containers are dropped, so the tree holds
 * what a person sees; each element keeps its probe path, which presses and focus go by.
 */
fun parseJabDump(json: String): SnapshotNode {
    val window = ControlJson.lenient.decodeFromString(JabNode.serializer(), json)
    return SnapshotNode(
        role = Roles.WINDOW,
        label = window.name,
        frame = frameOf(window.bounds),
        path = window.path,
        children = window.children.flatMap(::convert),
    )
}

private fun convert(node: JabNode): List<SnapshotNode> {
    val children = node.children.flatMap(::convert)
    val role = when {
        node.role in BUTTON_ROLES || (node.role != "text" && "click" in node.actions) -> Roles.BUTTON
        node.role in TEXT_ROLES -> Roles.TEXT
        node.role in FIELD_ROLES -> Roles.TEXT_FIELD
        else -> null
    }
    val label = node.name ?: node.description.takeIf { role != Roles.TEXT_FIELD }
    if (role == null && label == null) return children
    return listOf(
        SnapshotNode(
            role = role ?: Roles.GROUP,
            label = label,
            value = if (role == Roles.TEXT_FIELD) node.text?.ifEmpty { null } else null,
            placeholder = if (role == Roles.TEXT_FIELD) node.description else null,
            enabled = "enabled" in node.states,
            focused = "focused" in node.states,
            frame = frameOf(node.bounds),
            platformRole = node.role,
            path = node.path,
            children = children,
        ),
    )
}

private fun frameOf(box: List<Int>): Frame {
    if (box.size != BOX_SIZE || box[2] < 0) return Frame()
    return Frame(box[0].toDouble(), box[1].toDouble(), box[2].toDouble(), box[BOX_SIZE - 1].toDouble())
}

private const val BOX_SIZE = 4
