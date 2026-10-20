package app.posato.control.android

import app.posato.control.model.Frame
import app.posato.control.model.Roles
import app.posato.control.model.SnapshotNode
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads a UIAutomator window dump into the element tree that every target shares. Compose exposes a control as a
 * clickable view whose texts and role marker are separate children, so a clickable (or selected) view becomes one
 * button labelled by its texts, joined with ", " as the Mac's accessibility joins a row; a text field takes its
 * placeholder from the hint text Compose draws inside it. Controls nested in a control stay separate elements, and
 * unlabelled layout containers are dropped so the tree holds what a person sees.
 */
fun parseUiAutomatorDump(xml: String): SnapshotNode {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
    val hierarchy = document.documentElement
    val children = hierarchy.elementChildren().flatMapIndexed { index, child -> convert(child, "$index") }
    // The top-level views span the window; a scroll stops at its edges, so the frame is theirs, not the elements'.
    val frame = hierarchy.elementChildren().map { UiNode(it).frame }.fold(Frame()) { outer, inner -> union(outer, inner) }
    return SnapshotNode(role = Roles.WINDOW, frame = frame, path = "", children = children)
}

private fun convert(
    element: Element,
    path: String,
): List<SnapshotNode> {
    val node = UiNode(element)
    return when {
        node.isField -> listOf(field(element, node, path))

        node.isControl -> listOf(control(element, node, path))

        node.label != null -> listOf(
            SnapshotNode(
                role = if (element.elementChildren().isEmpty()) Roles.TEXT else Roles.GROUP,
                id = node.id,
                label = node.label,
                enabled = node.enabled,
                frame = node.frame,
                platformRole = node.className,
                path = path,
                children = convertChildren(element, path),
            ),
        )

        else -> convertChildren(element, path)
    }
}

private fun convertChildren(
    element: Element,
    path: String,
): List<SnapshotNode> = element.elementChildren().flatMapIndexed { index, child -> convert(child, "$path/$index") }

private fun control(
    element: Element,
    node: UiNode,
    path: String,
): SnapshotNode {
    val texts = node.label?.let(::listOf) ?: innerTexts(element, node.frame)
    return SnapshotNode(
        role = Roles.BUTTON,
        id = node.id,
        label = texts.distinct().joinToString(", ").ifEmpty { null },
        enabled = node.enabled,
        focused = node.focused,
        frame = node.frame,
        platformRole = node.className,
        path = path,
        children = nestedControls(element, path),
    )
}

private fun field(
    element: Element,
    node: UiNode,
    path: String,
): SnapshotNode = SnapshotNode(
    role = Roles.TEXT_FIELD,
    id = node.id,
    value = node.text,
    placeholder = if (node.text == null) innerTexts(element, node.frame).firstOrNull() else null,
    enabled = node.enabled,
    focused = node.focused,
    frame = node.frame,
    platformRole = node.className,
    path = path,
    children = nestedControls(element, path),
)

/** The texts drawn inside a control, without those of the controls nested in it or of views outside its bounds. */
private fun innerTexts(
    element: Element,
    bounds: Frame,
): List<String> = element.elementChildren().flatMap { child ->
    val inner = UiNode(child)
    when {
        inner.isField || inner.isControl -> emptyList()
        else -> listOfNotNull(inner.label?.takeIf { contains(bounds, inner.frame) }) + innerTexts(child, bounds)
    }
}

private fun nestedControls(
    element: Element,
    path: String,
): List<SnapshotNode> = element.elementChildren().flatMapIndexed { index, child ->
    val inner = UiNode(child)
    val childPath = "$path/$index"
    when {
        inner.isField -> listOf(field(child, inner, childPath))
        inner.isControl -> listOf(control(child, inner, childPath))
        else -> nestedControls(child, childPath)
    }
}

/** One `node` element's attributes as the tree uses them. */
private class UiNode(
    element: Element,
) {
    val className: String = element.getAttribute("class")
    val text: String? = element.getAttribute("text").ifBlank { null }
    val label: String? = text ?: element.getAttribute("content-desc").ifBlank { null }
    val id: String? = element.getAttribute("resource-id").ifBlank { null }
    val enabled: Boolean = element.getAttribute("enabled") != "false"
    val focused: Boolean = element.getAttribute("focused") == "true"
    val frame: Frame = bounds(element.getAttribute("bounds"))
    val isField: Boolean = className.endsWith("EditText")
    val isControl: Boolean = !isField && listOf("clickable", "selected", "checkable").any { element.getAttribute(it) == "true" }
}

private const val LEFT = 0
private const val TOP = 1
private const val RIGHT = 2
private const val BOTTOM = 3
private val BOUNDS = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

private fun bounds(text: String): Frame {
    val edges = BOUNDS.find(text)?.groupValues?.drop(1)?.map(String::toDouble) ?: return Frame()
    return Frame(edges[LEFT], edges[TOP], edges[RIGHT] - edges[LEFT], edges[BOTTOM] - edges[TOP])
}

private fun contains(
    outer: Frame,
    inner: Frame,
): Boolean {
    val x = inner.x + inner.w / 2
    val y = inner.y + inner.h / 2
    return x >= outer.x && x <= outer.x + outer.w && y >= outer.y && y <= outer.y + outer.h
}

private fun union(
    a: Frame,
    b: Frame,
): Frame {
    if (a.w == 0.0 && a.h == 0.0) return b
    val left = minOf(a.x, b.x)
    val top = minOf(a.y, b.y)
    return Frame(left, top, maxOf(a.x + a.w, b.x + b.w) - left, maxOf(a.y + a.h, b.y + b.h) - top)
}

private fun Element.elementChildren(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}
