package app.posato.prototype.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoEndTime
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoNotice
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoStatusLabel
import app.posato.prototype.designsystem.PosatoSyncFooter
import app.posato.prototype.designsystem.PosatoTone

@Composable
internal fun FeedbackSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Spacious)) {
        PosatoSection(titleContent = { Text("Status and bounded repair") }) {
            PosatoStatusLabel("NO SESSION ACTIVE", tone = PosatoTone.Neutral)
            PosatoStatusLabel("SESSION ACTIVE")
            PosatoTone.entries.forEach { tone ->
                PosatoNotice(
                    tone = tone,
                    leadingContent = { PosatoIcon(if (tone == PosatoTone.Positive) PosatoIcons.Check else PosatoIcons.Cloud, null) },
                    actionContent = if (tone == PosatoTone.Caution) {
                        {
                            PosatoButton(onClick = {
                                onAction("Repair callback invoked.")
                            }, style = PosatoButtonStyle.Secondary) { Text("Review setup") }
                        }
                    } else {
                        null
                    },
                ) {
                    Text(
                        when (tone) {
                            PosatoTone.Neutral -> "Changes are saved here. Waiting to sync with iCloud."
                            PosatoTone.Positive -> "This device is ready for your next pause."
                            PosatoTone.Caution -> "Choose apps on this device. Your saved choices are still here."
                            PosatoTone.Critical -> "This device could not apply the pause. Review its setup before continuing."
                        },
                    )
                }
            }
        }
        PosatoSection(titleContent = { Text("A clear ending") }) {
            PosatoEndTime("Until 18:30", supportingText = "45 minutes · you stay in control")
            PosatoEndTime("Until 17:45 tomorrow", supportingText = "Longer labels wrap without hiding the ending.")
        }
        SyncSamples(onAction)
    }
}

@Composable
private fun SyncSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoSection(modifier = modifier, titleContent = { Text("Local synchronization states") }) {
        listOf(
            "Sync with iCloud has not been enabled.",
            "Changes are saved here. Waiting to sync with iCloud.",
            "Syncing this device with iCloud…",
            "Last sync completed on this device at 17:45.",
            "Waiting for iCloud Keychain. Your existing workspace stays unchanged.",
            "Setup on this device needs your attention.",
        ).forEach { PosatoSyncFooter(it) }
        PosatoSyncFooter(
            message = "Couldn’t sync just now. Your changes are safe on this device.",
            actionContent = {
                PosatoButton(onClick = { onAction("Retry sync callback invoked.") }, style = PosatoButtonStyle.Quiet) { Text("Try again") }
            },
        )
    }
}
