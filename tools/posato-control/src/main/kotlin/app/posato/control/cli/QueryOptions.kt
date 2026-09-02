package app.posato.control.cli

import app.posato.control.model.Query
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class QueryOptions : OptionGroup(name = "Element query") {
    private val id by option("--id", help = "Accessibility identifier (iOS test tag).")
    private val text by option("--text", help = "Exact label, value, or placeholder text.")
    private val textContains by option("--text-contains", help = "Substring of the label or value.")
    private val role by option("--role", help = "Unified role: button, textField, text, group, window, progress, other, any.")
    private val index by option("--index", help = "Pick the nth match (0-based).").int()
    private val path by option("--path", help = "Desktop accessibility path from a previous snapshot, e.g. 0/2/1.")
    private val withinText by option("--within-text", help = "Restrict the search to the subtree of the element with this text.")
    private val withinRole by option("--within-role", help = "Role of the ancestor selected with --within-text.")

    fun toQuery(): Query? {
        val within = withinText?.let { Query(text = it, role = withinRole) }
        val query = Query(id = id, text = text, textContains = textContains, role = role, index = index, within = within, path = path)
        return query.takeUnless { it.isEmpty }
    }
}
