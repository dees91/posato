package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformTimePicker(
    label: String,
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoNumberWheel(hour, 0..LAST_HOUR, "$label hours", { onChange(it, minute) }, Modifier.weight(1f))
        PosatoNumberWheel(minute, 0..LAST_MINUTE, "$label minutes", { onChange(hour, it) }, Modifier.weight(1f))
    }
}

@Composable
internal actual fun PlatformDurationPicker(
    minutes: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier,
) {
    val hours = minutes / MINUTES_PER_HOUR
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoNumberWheel(hours, 0..range.last / MINUTES_PER_HOUR, "Hours", {
            onChange((it * MINUTES_PER_HOUR + minutes % MINUTES_PER_HOUR).coerceIn(range))
        }, Modifier.weight(1f))
        PosatoNumberWheel(minutes % MINUTES_PER_HOUR, 0 until MINUTES_PER_HOUR, "Minutes", {
            onChange((hours * MINUTES_PER_HOUR + it).coerceIn(range))
        }, Modifier.weight(1f))
    }
}

private const val MINUTES_PER_HOUR = 60
