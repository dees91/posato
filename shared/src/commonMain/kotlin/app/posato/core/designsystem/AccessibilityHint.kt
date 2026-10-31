package app.posato.core.designsystem

import androidx.compose.ui.Modifier

/**
 * Gives a control [hint], read after its label, such as the reason a disabled action is unavailable.
 * A computer publishes it as the accessibility help; iOS and Android, which Compose gives no hint field, read it as
 * the control's value right after the label.
 */
internal expect fun Modifier.accessibilityHint(hint: String): Modifier
