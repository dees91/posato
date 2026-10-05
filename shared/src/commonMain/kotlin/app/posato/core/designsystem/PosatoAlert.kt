package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

/** How an alert action reads: the default answer, the way out, or the one that destroys something. */
internal enum class PosatoAlertRole {
    Default,
    Cancel,
    Destructive,
}

/** One answer an alert offers. [onClick] receives the text field's content, or an empty string without one. */
@Immutable
internal class PosatoAlertAction(
    val title: String,
    val onClick: (String) -> Unit,
    val role: PosatoAlertRole = PosatoAlertRole.Default,
)

/** The single text field an alert can ask for, such as a name. */
@Immutable
internal data class PosatoAlertField(
    val initial: String,
    val placeholder: String,
)

/**
 * A question that interrupts the screen until it is answered: the system alert on iOS, a dialog drawn in the
 * app's palette elsewhere. The first [PosatoAlertRole.Default] action is the preferred answer. A new [attempt]
 * presents the alert again, as after a rejected answer.
 */
@Composable
internal fun PosatoAlert(
    title: String,
    actions: List<PosatoAlertAction>,
    onDismiss: () -> Unit,
    message: String? = null,
    field: PosatoAlertField? = null,
    attempt: Int = 0,
) {
    if (platformUsesCupertinoChrome) {
        PlatformAlert(title, actions, onDismiss, message, field, attempt)
    } else {
        DrawnAlert(title, actions, onDismiss, message, field)
    }
}

/** The host's own alert, where the platform has one the app can present. */
@Composable
internal expect fun PlatformAlert(
    title: String,
    actions: List<PosatoAlertAction>,
    onDismiss: () -> Unit,
    message: String?,
    field: PosatoAlertField?,
    attempt: Int,
)

@Composable
internal fun DrawnAlert(
    title: String,
    actions: List<PosatoAlertAction>,
    onDismiss: () -> Unit,
    message: String? = null,
    field: PosatoAlertField? = null,
) {
    val text = remember(field) { TextFieldState(field?.initial.orEmpty()) }
    val answers = actions.filter { action -> action.role != PosatoAlertRole.Cancel }
    val cancel = actions.firstOrNull { action -> action.role == PosatoAlertRole.Cancel }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                message?.let { Text(it) }
                field?.let {
                    PosatoTextField(state = text, label = it.placeholder, onSubmit = { answers.firstOrNull()?.onClick?.invoke(text.text.toString()) })
                }
            }
        },
        confirmButton = {
            PosatoActionRow {
                answers.forEach { action ->
                    PosatoButton(
                        onClick = { action.onClick(text.text.toString()) },
                        style = if (action.role == PosatoAlertRole.Destructive) PosatoButtonStyle.Destructive else PosatoButtonStyle.Primary,
                    ) { Text(action.title) }
                }
            }
        },
        dismissButton = cancel?.let { action ->
            { PosatoButton(onClick = { action.onClick(text.text.toString()) }, style = PosatoButtonStyle.Quiet) { Text(action.title) } }
        },
    )
}
