package app.posato.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.io.File

/**
 * Desktop accessibility has no identifier field, so the id rides the description, which screen readers announce.
 * It is published only on a verification host, marked by a file `posato-control` creates in the user's home.
 */
private val publishesControlIds: Boolean by lazy {
    runCatching { File(System.getProperty("user.home"), ".posato-control-ids").isFile }.getOrDefault(false)
}

internal actual fun Modifier.controlId(id: String): Modifier {
    val tagged = this.testTag(id)
    return if (publishesControlIds) tagged.semantics { contentDescription = id } else tagged
}
