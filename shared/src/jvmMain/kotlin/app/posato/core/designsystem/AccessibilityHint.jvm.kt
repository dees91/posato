package app.posato.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Desktop accessibility publishes a content description as the control's help (AXHelp on the Mac, the description on
 * AT-SPI and Java Access Bridge). On a verification host that field carries the control id instead.
 */
internal actual fun Modifier.accessibilityHint(hint: String): Modifier {
    return if (publishesControlIds) this else this.semantics { contentDescription = hint }
}
