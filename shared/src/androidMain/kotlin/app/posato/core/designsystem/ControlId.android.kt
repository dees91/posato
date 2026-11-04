package app.posato.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * The tag becomes the control's resource id on the control itself. A dialog or a menu is its own window, which the
 * main window's `testTagsAsResourceId` does not reach, so its buttons would otherwise carry no id. The activities keep
 * their own flag for plain `testTag` uses outside this modifier.
 */
internal actual fun Modifier.controlId(id: String): Modifier = this.semantics { testTagsAsResourceId = true }.testTag(id)
