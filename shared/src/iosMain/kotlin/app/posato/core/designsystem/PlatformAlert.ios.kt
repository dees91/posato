package app.posato.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
 * a new alert that keeps what the person typed.
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
    DisposableEffect(title, message, field, attempt) {
        val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
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
                val text = (alert.textFields?.firstOrNull() as? UITextField)?.text.orEmpty()
                latestActions.getOrNull(index)?.onClick?.invoke(text) ?: latestDismiss()
            }
            alert.addAction(native)
            if (preferred == null && action.role == PosatoAlertRole.Default) preferred = native
        }
        alert.preferredAction = preferred
        topViewController()?.presentViewController(alert, animated = true, completion = null)
        onDispose {
            if (alert.presentingViewController != null && !alert.isBeingDismissed()) {
                alert.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }
}

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
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
