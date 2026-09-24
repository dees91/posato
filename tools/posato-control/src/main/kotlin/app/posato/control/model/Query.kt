package app.posato.control.model

import kotlinx.serialization.Serializable

@Serializable
data class Query(
    val id: String? = null,
    val text: String? = null,
    val textContains: String? = null,
    val role: String? = null,
    val index: Int? = null,
    val within: Query? = null,
    val near: Query? = null,
    val path: String? = null,
    val scope: String? = null,
) {
    val isEmpty: Boolean
        get() = listOf(id, text, textContains, role, index, within, near, path).all { it == null }

    /** True when the query addresses iOS system surfaces (SpringBoard) rather than the application under test. */
    val isSystemScope: Boolean
        get() = scope == Scopes.SPRINGBOARD || within?.isSystemScope == true || near?.isSystemScope == true

    fun describe(): String = buildList {
        id?.let { add("id=$it") }
        text?.let { add("text=\"$it\"") }
        textContains?.let { add("textContains=\"$it\"") }
        role?.let { add("role=$it") }
        index?.let { add("index=$it") }
        path?.let { add("path=$it") }
        scope?.let { add("scope=$it") }
        within?.let { add("within(${it.describe()})") }
        near?.let { add("near(${it.describe()})") }
    }.joinToString(" ")
}

object Scopes {
    /** iOS system dialogs and sheets shown over the application, such as Screen Time consent and the passcode keypad. */
    const val SPRINGBOARD = "springboard"
}
