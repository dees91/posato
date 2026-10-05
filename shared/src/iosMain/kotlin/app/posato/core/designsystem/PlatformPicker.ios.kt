package app.posato.core.designsystem

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.NSDirectionalRectEdgeTrailing
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIContextMenuConfigurationElementOrderFixed
import platform.UIKit.UIControlContentHorizontalAlignmentTrailing
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightSemibold
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementState
import platform.UIKit.accessibilityLabel
import platform.UIKit.accessibilityValue

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlatformPicker(
    label: String,
    value: String,
    options: List<PosatoPickerOption>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    val tint = MaterialTheme.colorScheme.primary.toUIColor()
    NativeButtonHost(
        modifier = modifier.width(PickerWidth).height(PosatoSize.Control),
        configure = { button ->
            button.showsMenuAsPrimaryAction = true
            button.preferredMenuElementOrder = UIContextMenuConfigurationElementOrderFixed
            button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentTrailing
        },
        update = { button ->
            val configuration = UIButtonConfiguration.plainButtonConfiguration()
            configuration.title = value
            configuration.image = UIImage.systemImageNamed("chevron.up.chevron.down")
            configuration.imagePlacement = NSDirectionalRectEdgeTrailing
            configuration.imagePadding = PICKER_IMAGE_PADDING
            configuration.preferredSymbolConfigurationForImage =
                UIImageSymbolConfiguration.configurationWithPointSize(PICKER_SYMBOL_POINTS, UIImageSymbolWeightSemibold)
            configuration.contentInsets = NSDirectionalEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
            button.configuration = configuration
            button.tintColor = tint
            button.accessibilityLabel = label
            button.accessibilityValue = value
            button.menu = UIMenu.menuWithChildren(
                options.mapIndexed { index, option ->
                    UIAction.actionWithTitle(option.title, null, null) { _ -> onSelect(index) }.apply {
                        if (index == selected) state = UIMenuElementState.UIMenuElementStateOn
                        option.detail?.let { subtitle = it }
                    }
                },
            )
        },
    )
}

private val PickerWidth = 200.dp
private const val PICKER_IMAGE_PADDING = 4.0
private const val PICKER_SYMBOL_POINTS = 12.0
