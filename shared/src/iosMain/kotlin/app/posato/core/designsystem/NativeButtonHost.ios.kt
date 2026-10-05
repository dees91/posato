package app.posato.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

/**
 * A system button inside an opaque view in the screen's surface color. Compose leaves a hole where a native view
 * sits; the host fills it, so nothing behind the app shows through while the system morphs the button into its menu.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun NativeButtonHost(
    configure: (UIButton) -> Unit,
    update: (UIButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface.toUIColor()
    val button = remember { UIButton.buttonWithType(UIButtonTypeSystem).also(configure) }
    UIKitView(
        factory = {
            UIView().apply {
                button.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
                addSubview(button)
            }
        },
        modifier = modifier,
        update = { host ->
            host.backgroundColor = surface
            button.backgroundColor = UIColor.clearColor
            button.setFrame(host.bounds)
            update(button)
        },
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
    )
}

internal fun Color.toUIColor(): UIColor {
    return UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
}
