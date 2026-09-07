package app.posato.prototype.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class PrototypeControlsState {
    var isOpen: Boolean by mutableStateOf(false)
}

@Composable
fun rememberPrototypeControlsState(): PrototypeControlsState {
    return remember { PrototypeControlsState() }
}
