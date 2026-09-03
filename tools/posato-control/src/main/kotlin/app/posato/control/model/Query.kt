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
) {
    val isEmpty: Boolean
        get() = listOf(id, text, textContains, role, index, within, near, path).all { it == null }

    fun describe(): String = buildList {
        id?.let { add("id=$it") }
        text?.let { add("text=\"$it\"") }
        textContains?.let { add("textContains=\"$it\"") }
        role?.let { add("role=$it") }
        index?.let { add("index=$it") }
        path?.let { add("path=$it") }
        within?.let { add("within(${it.describe()})") }
        near?.let { add("near(${it.describe()})") }
    }.joinToString(" ")
}
