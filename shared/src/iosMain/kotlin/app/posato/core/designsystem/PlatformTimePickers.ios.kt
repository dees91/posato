package app.posato.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlatformTimePicker(
    label: String,
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier,
) {
    val latest by rememberUpdatedState(onChange)
    val target = remember {
        PickerTarget { picker ->
            val parts = NSCalendar.currentCalendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = picker.date)
            latest(parts.hour.toInt(), parts.minute.toInt())
        }
    }
    val tint = MaterialTheme.colorScheme.primary.toUIColor()
    // Compose leaves a hole where a native view sits; the picker's own background fills it in the screen's color.
    val surface = MaterialTheme.colorScheme.surface.toUIColor()
    UIKitView(
        factory = {
            UIDatePicker().apply {
                datePickerMode = UIDatePickerMode.UIDatePickerModeTime
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleCompact
                addTarget(target, NSSelectorFromString("changed:"), UIControlEventValueChanged)
            }
        },
        modifier = modifier.size(CompactPickerWidth, CompactPickerHeight),
        update = { picker ->
            NSCalendar.currentCalendar.dateBySettingHour(hour.toLong(), minute.toLong(), 0, NSDate(), 0u)?.let { picker.date = it }
            picker.tintColor = tint
            picker.backgroundColor = surface
            picker.accessibilityLabel = label
        },
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
    )
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlatformDurationPicker(
    minutes: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier,
) {
    val latest by rememberUpdatedState(onChange)
    val latestRange by rememberUpdatedState(range)
    val surface = MaterialTheme.colorScheme.surface.toUIColor()
    val target = remember {
        PickerTarget { picker ->
            val chosen = (picker.countDownDuration / SECONDS_PER_MINUTE).toInt()
            val kept = chosen.coerceIn(latestRange.first, latestRange.last)
            if (kept != chosen) picker.countDownDuration = kept * SECONDS_PER_MINUTE
            latest(kept)
        }
    }
    UIKitView(
        factory = {
            UIDatePicker().apply {
                datePickerMode = UIDatePickerMode.UIDatePickerModeCountDownTimer
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
                minuteInterval = 1
                addTarget(target, NSSelectorFromString("changed:"), UIControlEventValueChanged)
            }
        },
        modifier = modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth().height(WheelPickerHeight),
        update = { picker ->
            picker.backgroundColor = surface
            if ((picker.countDownDuration / SECONDS_PER_MINUTE).toInt() != minutes) picker.countDownDuration = minutes * SECONDS_PER_MINUTE
        },
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
    )
}

/** Receives a picker's value changes through UIKit's target and action. */
@OptIn(BetaInteropApi::class)
private class PickerTarget(
    private val onChanged: (UIDatePicker) -> Unit,
) : NSObject() {
    @ObjCAction
    fun changed(sender: UIDatePicker) {
        onChanged(sender)
    }
}

private val CompactPickerWidth = 104.dp
private val CompactPickerHeight = 44.dp
private val WheelPickerHeight = 216.dp
private const val SECONDS_PER_MINUTE = 60.0
