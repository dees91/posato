package app.posato.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal actual fun Modifier.controlId(id: String): Modifier {
    return this.testTag(id).semantics { contentDescription = id }
}
