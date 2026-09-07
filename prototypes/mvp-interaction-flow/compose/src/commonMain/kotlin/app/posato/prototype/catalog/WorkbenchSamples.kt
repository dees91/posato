package app.posato.prototype.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoBadge
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoChoiceGroup
import app.posato.prototype.designsystem.PosatoDeviceLabel
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoNavigationItem
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.workbench.PrototypeDevice
import app.posato.prototype.designsystem.workbench.PrototypeFrame
import app.posato.prototype.designsystem.workbench.PrototypeInspector
import app.posato.prototype.designsystem.workbench.PrototypeMomentStrip
import app.posato.prototype.designsystem.workbench.PrototypeStateFact
import app.posato.prototype.designsystem.workbench.PrototypeWalkthroughStep
import app.posato.prototype.designsystem.workbench.PrototypeWindowChrome

@Composable
internal fun WorkbenchSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Spacious)) {
        PreviewFrameSamples()
        WalkthroughSamples(onAction)
        PrototypeInspector(
            modifier = Modifier.fillMaxWidth(),
            title = "Current state",
            outcomeContent = { PosatoCaption("Setup is complete on this device. No session is active.") },
        ) {
            PrototypeStateFact("Session", "Inactive")
            PrototypeStateFact("Device", "This Mac")
            PrototypeStateFact("Workspace key", "Ready")
            PrototypeStateFact("Synchronization", "Last completed on this device at 17:45")
        }
        PrototypeMomentStrip("Explore a moment") {
            listOf("Ready", "First visit", "In a session", "Needs attention", "Waiting for iCloud").forEach { label ->
                PosatoButton(onClick = { onAction("Moment requested: $label.") }, style = PosatoButtonStyle.Compact) { Text(label) }
            }
        }
        PrototypeMomentStrip("Simulate an external event") {
            PosatoButton(onClick = {
                onAction("Synthetic sync-success event requested.")
            }, style = PosatoButtonStyle.Compact) { Text("Sync succeeds") }
            PosatoButton(onClick = { onAction("Synthetic sync-failure event requested.") }, style = PosatoButtonStyle.Compact) { Text("Sync fails") }
        }
    }
}

@Composable
private fun PreviewFrameSamples(modifier: Modifier = Modifier) {
    var device by remember { mutableStateOf(PrototypeDevice.Mac) }
    PosatoSection(modifier = modifier, titleContent = { Text("Device preview and chrome") }) {
        PosatoChoiceGroup {
            PrototypeDevice.entries.forEach { candidate ->
                PosatoNavigationItem(selected = device == candidate, onClick = { device = candidate }) {
                    Text(if (candidate == PrototypeDevice.Mac) "MacBook" else "iPhone")
                }
            }
        }
        PrototypeFrame(
            modifier = Modifier.fillMaxWidth(),
            device = device,
            chromeContent = { PrototypeWindowChrome(device) },
        ) {
            Column(Modifier.padding(PosatoSpace.Section), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                PosatoDeviceLabel(
                    label = if (device == PrototypeDevice.Mac) "This Mac" else "This iPhone",
                    leadingContent = { PosatoIcon(if (device == PrototypeDevice.Mac) PosatoIcons.Mac else PosatoIcons.Phone, null) },
                )
                PosatoCaption("Preview frame only. This does not imitate a system permission prompt or provide native window controls.")
            }
        }
    }
}

@Composable
private fun WalkthroughSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var completed by remember { mutableStateOf(false) }
    var recoverySelected by remember { mutableStateOf(false) }
    PosatoSection(modifier = modifier, titleContent = { Text("Guided walkthrough") }) {
        PosatoActionRow {
            PosatoNavigationItem(selected = !recoverySelected, onClick = {
                recoverySelected = false
                onAction("Happy-path tab selected.")
            }) { Text("Happy path") }
            PosatoNavigationItem(selected = recoverySelected, onClick = {
                recoverySelected = true
                onAction("Recovery tab selected.")
            }) { Text("Recovery") }
        }
        PrototypeWalkthroughStep(
            modifier = Modifier.fillMaxWidth(),
            number = "01",
            completed = completed,
            onClick = {
                completed = !completed
                onAction("Walkthrough step toggled.")
            },
            supportingContent = { PosatoCaption("The catalog caller controls progress.") },
            statusContent = { PosatoBadge(if (completed) "Complete" else "Ready") },
        ) { Text("Connect with iCloud") }
        PrototypeWalkthroughStep(
            modifier = Modifier.fillMaxWidth(),
            number = "02",
            enabled = false,
            onClick = {},
            supportingContent = { PosatoCaption("A prerequisite is still missing.") },
            statusContent = { PosatoCaption("Unavailable") },
        ) { Text("Choose local applications") }
    }
}
