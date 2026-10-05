package app.posato.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIContentSizeCategoryExtraExtraExtraLarge
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.accessibilityLabel
import platform.UIKit.maximumContentSizeCategory
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
    // The picker sizes itself to its time at the current text size, as UIKit lays it out; the font scale is read here
    // so a change of text size updates the measured size.
    val fontScale = LocalDensity.current.fontScale
    var fitted by remember { mutableStateOf(DpSize(CompactPickerWidth, CompactPickerHeight)) }
    // Compose leaves a hole where a native view sits; the picker's own background fills it in the screen's color.
    val surface = MaterialTheme.colorScheme.surface.toUIColor()
    UIKitView(
        factory = {
            UIDatePicker().apply {
                datePickerMode = UIDatePickerMode.UIDatePickerModeTime
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleCompact
                maximumContentSizeCategory = UIContentSizeCategoryExtraExtraExtraLarge
                addTarget(target, NSSelectorFromString("changed:"), UIControlEventValueChanged)
            }
        },
        modifier = modifier.size(fitted),
        update = { picker ->
            timeOnReferenceDay(hour, minute)?.let { picker.date = it }
            picker.tintColor = tint
            picker.backgroundColor = surface
            picker.accessibilityLabel = label
            val natural = picker.intrinsicContentSize.useContents { DpSize(width.dp, maxOf(height, CompactPickerHeight.value.toDouble()).dp) }
            if (fontScale > 0f && natural.width.value > 0f && natural != fitted) fitted = natural
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
            picker.accessibilityLabel = "Duration"
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

/**
 * The time on a fixed day without a clock change, so a time that a daylight-saving change skips today, such as
 * 02:30, still shows as itself. The picker shows and returns only the hour and minute.
 */
private fun timeOnReferenceDay(
    hour: Int,
    minute: Int,
): NSDate? {
    val components = NSDateComponents().apply {
        year = REFERENCE_YEAR
        month = 1
        day = 1
        this.hour = hour.toLong()
        this.minute = minute.toLong()
    }
    return NSCalendar.currentCalendar.dateFromComponents(components)
}

private const val REFERENCE_YEAR = 2001L
