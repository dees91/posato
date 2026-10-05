package app.posato.core.designsystem

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformAlert(
    title: String,
    actions: List<PosatoAlertAction>,
    onDismiss: () -> Unit,
    message: String?,
    field: PosatoAlertField?,
    attempt: Int,
) {
    DrawnAlert(title, actions, onDismiss, message, field)
}
