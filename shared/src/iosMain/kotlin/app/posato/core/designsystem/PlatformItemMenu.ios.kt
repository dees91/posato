package app.posato.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIContextMenuConfigurationElementOrderFixed
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightMedium
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.accessibilityLabel

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlatformItemMenu(
    label: String,
    items: List<PosatoMenuItem>,
    modifier: Modifier,
) {
    val tint = MaterialTheme.colorScheme.primary.toUIColor()
    NativeButtonHost(
        modifier = modifier.size(PosatoSize.Control),
        configure = { button ->
            val configuration = UIImageSymbolConfiguration.configurationWithPointSize(MENU_SYMBOL_POINTS, UIImageSymbolWeightMedium)
            button.setImage(UIImage.systemImageNamed("ellipsis.circle", configuration), UIControlStateNormal)
            button.showsMenuAsPrimaryAction = true
            button.preferredMenuElementOrder = UIContextMenuConfigurationElementOrderFixed
        },
        update = { button ->
            button.tintColor = tint
            button.accessibilityLabel = label
            button.menu = UIMenu.menuWithChildren(items.map { item -> item.toAction() })
        },
    )
}

private fun PosatoMenuItem.toAction(): UIAction {
    val action = UIAction.actionWithTitle(title, symbol?.let { UIImage.systemImageNamed(it.systemName) }, null) { _ -> onClick() }
    if (destructive) action.attributes = UIMenuElementAttributesDestructive
    return action
}

private const val MENU_SYMBOL_POINTS = 20.0
