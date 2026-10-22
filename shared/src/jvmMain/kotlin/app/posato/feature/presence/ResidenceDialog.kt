package app.posato.feature.presence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme

/**
 * The content of a desktop host's own notice window (LINUX-003), drawn in Posato's theme even while the main window
 * is closed: an optional title, the message, and one or two buttons, the first being the default.
 */
@Composable
public fun ResidenceDialogContent(
    title: String?,
    message: String,
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    // The first button is the default: focused at once, so Enter or Space keeps Posato open.
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    PosatoTheme {
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(PosatoSpace.Section),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
        ) {
            title?.let { Text(it, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
            PosatoBody(message)
            PosatoActionRow {
                secondary?.let { label -> PosatoButton(onClick = onSecondary, style = PosatoButtonStyle.Secondary) { Text(label) } }
                PosatoButton(onClick = onPrimary, modifier = Modifier.focusRequester(focus), style = PosatoButtonStyle.Primary) { Text(primary) }
            }
        }
    }
}
