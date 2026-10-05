package app.posato.core.designsystem

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.NSDirectionalRectEdgeTrailing
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIContentSizeCategoryExtraExtraExtraLarge
import platform.UIKit.UIContextMenuConfigurationElementOrderFixed
import platform.UIKit.UIControlContentHorizontalAlignmentTrailing
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightSemibold
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementState
import platform.UIKit.accessibilityLabel
import platform.UIKit.accessibilityValue
import platform.UIKit.maximumContentSizeCategory

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
    val measurer = rememberTextMeasurer()
    val valueWidth = with(LocalDensity.current) { measurer.measure(value, PosatoTypography.BarAction).size.width.toDp() }
    NativeButtonHost(
        modifier = modifier.width((valueWidth + PickerChrome).coerceIn(PickerMinWidth, PickerMaxWidth)).heightIn(min = PosatoSize.Control),
        configure = { button ->
            button.showsMenuAsPrimaryAction = true
            button.preferredMenuElementOrder = UIContextMenuConfigurationElementOrderFixed
            button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentTrailing
            button.maximumContentSizeCategory = UIContentSizeCategoryExtraExtraExtraLarge
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
            configuration.titleLineBreakMode = NSLineBreakByTruncatingTail
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

private val PickerChrome = 28.dp
private val PickerMinWidth = 64.dp
private val PickerMaxWidth = 240.dp
private const val PICKER_IMAGE_PADDING = 4.0
private const val PICKER_SYMBOL_POINTS = 12.0
