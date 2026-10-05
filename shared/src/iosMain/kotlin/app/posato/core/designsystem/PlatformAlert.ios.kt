package app.posato.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldViewMode
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Presents the system alert while this composable is in the composition and withdraws it when it leaves. An
 * answer closes the alert and runs its action with the text field's content, so a rejected name can come back as
 * a new alert that keeps what the person typed. A change of the question or of its answers replaces the alert, and
 * each button runs the current action with its own title and role, never another answer that moved into its place.
 * When UIKit cannot present the alert, as while another presentation is under way, the same question is drawn in
 * the app instead, so the screen never waits for an answer that cannot come.
 */
@Composable
internal actual fun PlatformAlert(
    title: String,
    actions: List<PosatoAlertAction>,
    onDismiss: () -> Unit,
    message: String?,
    field: PosatoAlertField?,
    attempt: Int,
) {
    val latestActions by rememberUpdatedState(actions)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val answers = actions.map { action -> action.title to action.role }
    val presentation = remember(title, message, field, attempt, answers) { AlertPresentation() }
    if (presentation.drawn) {
        DrawnAlert(title, actions, onDismiss, message, field)
        return
    }
    LaunchedEffect(presentation) {
        delay(PRESENTATION_CHECK_MILLIS)
        if (!presentation.answered && presentation.alert?.presentingViewController == null) presentation.drawn = true
    }
    DisposableEffect(presentation) {
        val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
        presentation.alert = alert
        field?.let { request ->
            alert.addTextFieldWithConfigurationHandler { textField ->
                textField?.text = request.initial
                textField?.placeholder = request.placeholder
                textField?.clearButtonMode = UITextFieldViewMode.UITextFieldViewModeWhileEditing
            }
        }
        var preferred: UIAlertAction? = null
        latestActions.forEachIndexed { index, action ->
            val native = UIAlertAction.actionWithTitle(action.title, action.role.nativeStyle()) { _ ->
                presentation.answered = true
                val text = (alert.textFields?.firstOrNull() as? UITextField)?.text.orEmpty()
                // The alert is rebuilt whenever its answers change, so the answer in this position is this button's,
                // even when two answers read alike; a mismatch in between closes the alert without acting.
                val current = latestActions.getOrNull(index)?.takeIf { it.title == action.title && it.role == action.role }
                current?.onClick?.invoke(text) ?: latestDismiss()
            }
            alert.addAction(native)
            if (preferred == null && action.role == PosatoAlertRole.Default) preferred = native
        }
        alert.preferredAction = preferred
        val presenter = topViewController()
        if (presenter == null) {
            presentation.drawn = true
        } else {
            presenter.presentViewController(alert, animated = true, completion = null)
        }
        onDispose {
            // Withdrawn without an answer, the alert leaves at once, so a replacement can be presented in its place.
            if (alert.presentingViewController != null && !alert.isBeingDismissed()) {
                alert.dismissViewControllerAnimated(false, completion = null)
            }
        }
    }
}

/** One presentation of the system alert: whether it was answered, and whether it fell back to the drawn one. */
private class AlertPresentation {
    var alert: UIAlertController? = null
    var answered = false
    var drawn by mutableStateOf(false)
}

private const val PRESENTATION_CHECK_MILLIS = 1_000L

private fun PosatoAlertRole.nativeStyle(): Long {
    return when (this) {
        PosatoAlertRole.Default -> UIAlertActionStyleDefault
        PosatoAlertRole.Cancel -> UIAlertActionStyleCancel
        PosatoAlertRole.Destructive -> UIAlertActionStyleDestructive
    }
}

private fun topViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
    val windows = scene?.windows.orEmpty().filterIsInstance<UIWindow>()
    var controller = (windows.firstOrNull { it.keyWindow } ?: windows.firstOrNull())?.rootViewController
    // A controller on its way out cannot present, so the search stops below one that is being dismissed.
    while (controller?.presentedViewController?.isBeingDismissed() == false) {
        controller = controller.presentedViewController
    }
    return controller
}
