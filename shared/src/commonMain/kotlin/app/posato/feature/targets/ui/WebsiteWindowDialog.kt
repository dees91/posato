package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoChoiceGroup
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoToggleButton
import app.posato.core.designsystem.accessibilityHint
import app.posato.core.designsystem.controlId
import app.posato.feature.targets.domain.WebsiteWindow
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_save_short
import app.posato.generated.resources.targets_window_caption_120
import app.posato.generated.resources.targets_window_caption_30
import app.posato.generated.resources.targets_window_caption_60
import app.posato.generated.resources.targets_window_clear
import app.posato.generated.resources.targets_window_every
import app.posato.generated.resources.targets_window_for
import app.posato.generated.resources.targets_window_in_use
import app.posato.generated.resources.targets_window_length_10
import app.posato.generated.resources.targets_window_length_10_a11y
import app.posato.generated.resources.targets_window_length_15
import app.posato.generated.resources.targets_window_length_15_a11y
import app.posato.generated.resources.targets_window_length_5
import app.posato.generated.resources.targets_window_length_5_a11y
import app.posato.generated.resources.targets_window_period_120
import app.posato.generated.resources.targets_window_period_120_a11y
import app.posato.generated.resources.targets_window_period_30
import app.posato.generated.resources.targets_window_period_30_a11y
import app.posato.generated.resources.targets_window_period_60
import app.posato.generated.resources.targets_window_period_60_a11y
import app.posato.generated.resources.targets_window_row_120
import app.posato.generated.resources.targets_window_row_30
import app.posato.generated.resources.targets_window_row_60
import app.posato.generated.resources.targets_window_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One fixed choice of the window sheet: its visible label, its spoken label, and its control id. */
private class WindowChoice(
    val minutes: Int,
    val label: StringResource,
    val spoken: StringResource,
    val controlId: String,
)

private val periodChoices = listOf(
    WindowChoice(30, Res.string.targets_window_period_30, Res.string.targets_window_period_30_a11y, "targets_window_period_30"),
    WindowChoice(60, Res.string.targets_window_period_60, Res.string.targets_window_period_60_a11y, "targets_window_period_60"),
    WindowChoice(120, Res.string.targets_window_period_120, Res.string.targets_window_period_120_a11y, "targets_window_period_120"),
)

private val lengthChoices = listOf(
    WindowChoice(5, Res.string.targets_window_length_5, Res.string.targets_window_length_5_a11y, "targets_window_length_5"),
    WindowChoice(10, Res.string.targets_window_length_10, Res.string.targets_window_length_10_a11y, "targets_window_length_10"),
    WindowChoice(15, Res.string.targets_window_length_15, Res.string.targets_window_length_15_a11y, "targets_window_length_15"),
)

/**
 * Open briefly: two fixed choice groups, Every and For, with a caption that follows them (DESIGN.md, Release 1.6
 * website windows). Off is the default; a website with a rule also offers Don't open, which clears it.
 */
@Composable
internal fun WebsiteWindowDialog(
    domain: String,
    current: WebsiteWindow?,
    inUse: Boolean,
    onSave: (WebsiteWindow) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var period by remember(domain, current) { mutableIntStateOf(current?.periodMinutes ?: WebsiteWindow.PERIODS.first()) }
    var length by remember(domain, current) { mutableIntStateOf(current?.lengthMinutes ?: WebsiteWindow.LENGTHS.first()) }
    val chosen = checkNotNull(WebsiteWindow.of(period, length))
    val caption = windowCaption(chosen)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.targets_window_title, domain)) },
        text = {
            Column(Modifier.accessibilityHint(caption), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                PosatoCaption(stringResource(Res.string.targets_window_every))
                WindowChoices(periodChoices, period) { period = it }
                PosatoCaption(stringResource(Res.string.targets_window_for))
                WindowChoices(lengthChoices, length) { length = it }
                Text(caption)
                if (inUse) PosatoCaption(stringResource(Res.string.targets_window_in_use))
            }
        },
        confirmButton = {
            PosatoActionRow {
                if (current != null) {
                    PosatoButton(onClear, style = PosatoButtonStyle.Secondary, controlId = "targets_window_clear") {
                        Text(stringResource(Res.string.targets_window_clear))
                    }
                }
                PosatoButton({ onSave(chosen) }, controlId = "action_save_short") { Text(stringResource(Res.string.action_save_short)) }
            }
        },
        dismissButton = {
            PosatoButton(onDismiss, style = PosatoButtonStyle.Quiet, controlId = "action_cancel") { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun WindowChoices(
    choices: List<WindowChoice>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    PosatoChoiceGroup {
        choices.forEach { choice ->
            val spoken = stringResource(choice.spoken)
            PosatoToggleButton(
                selected = choice.minutes == selected,
                onClick = { onSelect(choice.minutes) },
                // The id comes last, so on a verification host it replaces the spoken label as every control id does.
                modifier = Modifier.semantics { contentDescription = spoken }.controlId(choice.controlId),
            ) { Text(stringResource(choice.label)) }
        }
    }
}

/** "During a pause it opens at :00 and every 30 minutes, for 5 minutes." for [window]. */
@Composable
internal fun windowCaption(window: WebsiteWindow): String {
    val text = when (window.periodMinutes) {
        30 -> Res.string.targets_window_caption_30
        60 -> Res.string.targets_window_caption_60
        else -> Res.string.targets_window_caption_120
    }
    return stringResource(text, window.lengthMinutes)
}

/** The website row's second caption, "Opens every hour for 5 min". */
@Composable
internal fun windowRowCaption(window: WebsiteWindow): String {
    val text = when (window.periodMinutes) {
        30 -> Res.string.targets_window_row_30
        60 -> Res.string.targets_window_row_60
        else -> Res.string.targets_window_row_120
    }
    return stringResource(text, window.lengthMinutes)
}
