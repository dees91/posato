package app.posato.control.linux

import app.posato.control.core.ControlJson
import app.posato.control.model.Frame
import app.posato.control.model.Roles
import app.posato.control.model.SnapshotNode
import kotlinx.serialization.Serializable

/** One element as `posato-atspi.py dump` prints it. */
@Serializable
private data class AtspiNode(
    val role: String,
    val name: String? = null,
    val description: String? = null,
    val text: String? = null,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val frame: List<Int> = emptyList(),
    val path: String = "",
    val children: List<AtspiNode> = emptyList(),
)

private val BUTTON_ROLES =
    setOf("push button", "toggle button", "page tab", "check box", "radio button", "menu item", "link", "combo box", "list item")
private val TEXT_ROLES = setOf("label", "static", "heading", "paragraph")
private val FIELD_ROLES = setOf("text", "entry", "password text")

/**
 * Reads Posato's AT-SPI tree into the element tree every target shares: buttons, tabs, and menu items as buttons,
 * labels as text, entries as text fields with their hint as placeholder; an element without a name takes its
 * description, where Compose puts a content description. Containers without either are dropped, so
 * the tree holds what a person sees; each element keeps its AT-SPI path, which presses and focus go by.
 */
fun parseAtspiDump(json: String): SnapshotNode {
    val application = ControlJson.lenient.decodeFromString(AtspiNode.serializer(), json)
    val window = application.children.firstOrNull()
    val pane = window?.children?.firstOrNull()
    return SnapshotNode(
        role = Roles.WINDOW,
        label = window?.name,
        frame = frameOf(pane?.frame.orEmpty()),
        path = window?.path ?: "",
        children = application.children.flatMap { child -> convert(child, isWindow = true) },
    )
}

private fun convert(
    node: AtspiNode,
    isWindow: Boolean = false,
): List<SnapshotNode> {
    val children = node.children.flatMap { convert(it) }
    val role = when (node.role) {
        in BUTTON_ROLES -> Roles.BUTTON
        in TEXT_ROLES -> Roles.TEXT
        in FIELD_ROLES -> Roles.TEXT_FIELD
        else -> null
    }
    // Compose publishes a control's content description as its AT-SPI description and a text field's hint likewise.
    val label = node.name ?: node.description.takeIf { role != Roles.TEXT_FIELD }
    if (isWindow || (role == null && label == null)) return children
    return listOf(
        SnapshotNode(
            role = role ?: Roles.GROUP,
            label = label,
            value = if (role == Roles.TEXT_FIELD) node.text?.ifEmpty { null } else null,
            placeholder = if (role == Roles.TEXT_FIELD) node.description else null,
            enabled = node.enabled,
            focused = node.focused,
            frame = frameOf(node.frame),
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
