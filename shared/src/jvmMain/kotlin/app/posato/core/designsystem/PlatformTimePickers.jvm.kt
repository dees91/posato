package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.generated.resources.Res
import app.posato.generated.resources.session_duration_hours
import app.posato.generated.resources.session_duration_label
import app.posato.generated.resources.wheel_hours_of
import app.posato.generated.resources.wheel_minutes_of
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun PlatformTimePicker(
    label: String,
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoNumberWheel(hour, 0..LAST_HOUR, stringResource(Res.string.wheel_hours_of, label), { onChange(it, minute) }, Modifier.weight(1f))
        PosatoNumberWheel(minute, 0..LAST_MINUTE, stringResource(Res.string.wheel_minutes_of, label), { onChange(hour, it) }, Modifier.weight(1f))
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
        PosatoNumberWheel(hours, 0..range.last / MINUTES_PER_HOUR, stringResource(Res.string.session_duration_hours), {
            onChange((it * MINUTES_PER_HOUR + minutes % MINUTES_PER_HOUR).coerceIn(range))
        }, Modifier.weight(1f))
        PosatoNumberWheel(minutes % MINUTES_PER_HOUR, 0 until MINUTES_PER_HOUR, stringResource(Res.string.session_duration_label), {
            onChange((hours * MINUTES_PER_HOUR + it).coerceIn(range))
        }, Modifier.weight(1f))
    }
}

private const val MINUTES_PER_HOUR = 60
