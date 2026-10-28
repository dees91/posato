package app.posato.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal actual fun Modifier.controlId(id: String): Modifier = this.testTag(id)
