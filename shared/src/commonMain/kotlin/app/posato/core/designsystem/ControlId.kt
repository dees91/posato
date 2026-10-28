package app.posato.core.designsystem

import androidx.compose.ui.Modifier

/**
 * Marks a clickable control with the name of its string resource.
 * iOS and Android carry it as a test tag, off the spoken description. Desktop has no such field and publishes it as
 * the accessibility description only on a verification host, because screen readers announce that description.
 */
internal expect fun Modifier.controlId(id: String): Modifier
