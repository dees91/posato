package app.posato.core.designsystem

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Opens a drop-down menu once the on-screen keyboard is gone. A menu placed while the keyboard is up goes above its
 * button, for lack of room below, and stays there after the keyboard hides, covering the field just typed in; so
 * opening a menu ends typing first, as tapping elsewhere does.
 */
@Composable
internal fun rememberMenuOpening(onOpen: () -> Unit): () -> Unit {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val open by rememberUpdatedState(onOpen)
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(pending, keyboardUp) {
        if (pending && !keyboardUp) {
            pending = false
            open()
        }
    }
    return {
        focus.clearFocus()
        keyboard?.hide()
        pending = true
    }
}
