package app.posato.core.designsystem

import androidx.compose.ui.Modifier

/**
 * Marks a clickable control with the name of its string resource.
 * Desktop exposes that name as the accessibility help description, which the Mac bridge reads as an identifier.
 * VoiceOver speaks the control title. iOS and Android keep the id off the spoken description.
 */
internal expect fun Modifier.controlId(id: String): Modifier
