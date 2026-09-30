package app.posato.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Escape in a focused field only leaves the field and keeps what was typed. Escape is also back, so
 * without this a reflexive Escape in an editor would close it and discard the draft; the next Escape,
 * with no field focused, goes back.
 */
internal fun Modifier.escapeLeavesField(focusManager: FocusManager): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.key != Key.Escape) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) focusManager.clearFocus()
        true
    }
}
