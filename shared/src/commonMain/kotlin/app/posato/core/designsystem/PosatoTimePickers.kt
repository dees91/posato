package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/** A time of day as a labelled row with the host's own time picker, which follows the device's 12- or 24-hour clock. */
@Composable
internal fun PosatoTimeRow(
    label: String,
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = PosatoSize.Control),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                supportingText?.let { PosatoCaption(it) }
            }
            PlatformTimePicker(label, hour, minute, onChange)
        }
        PosatoDivider()
    }
}

/** The host's time picker, labelled [label] for VoiceOver. */
@Composable
internal expect fun PlatformTimePicker(
    label: String,
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
)

/** The host's picker for a length of time in hours and minutes, kept within [range] minutes. */
@Composable
internal expect fun PlatformDurationPicker(
    minutes: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
)

/** The last hour and minute a clock shows, for pickers that count from zero. */
internal const val LAST_HOUR = 23
internal const val LAST_MINUTE = 59
