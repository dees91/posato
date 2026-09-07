package app.posato.prototype.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDisclosureRow
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoItemSymbol
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.model.PrototypeState

@Composable
internal fun PrototypeSelectedItems(
    state: PrototypeState,
    modifier: Modifier = Modifier
) {
    var details by remember { mutableStateOf<PrototypeItemSection?>(null) }
    val websites = state.policy.domains
    val applications = state.localApplications()
    PosatoSection(
        modifier = modifier,
        titleContent = { Text(if (state.session.active) "Selected for this pause" else "What will be paused") },
    ) {
        PosatoDisclosureRow(
            onClick = { details = PrototypeItemSection.Websites },
            headlineContent = { Text(if (websites.size == 1) "1 website" else "${websites.size} websites") },
            supportingContent = {
                PosatoCaption(if (websites.size == 1) websites.first() else "Shared exact domains · view all")
            },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
        )
        PosatoDisclosureRow(
            onClick = { details = PrototypeItemSection.Applications },
            headlineContent = { Text(if (applications.size == 1) "1 application" else "${applications.size} applications") },
            supportingContent = {
                PosatoCaption(if (applications.size == 1) applications.first() else "On this ${state.platform.label} · view all")
            },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
        )
    }
    details?.let { section -> PrototypeSelectionDetails(state, section, onDismiss = { details = null }) }
}
