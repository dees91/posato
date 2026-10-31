package app.posato.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

internal actual fun Modifier.accessibilityHint(hint: String): Modifier = this.semantics { stateDescription = hint }
