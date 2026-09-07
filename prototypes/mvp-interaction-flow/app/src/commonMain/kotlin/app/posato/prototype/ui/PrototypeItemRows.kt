package app.posato.prototype.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoDisclosureRow
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoItemMenu
import app.posato.prototype.designsystem.PosatoItemMenuAction
import app.posato.prototype.designsystem.PosatoItemRow
import app.posato.prototype.designsystem.PosatoItemSymbol
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction

@Composable
internal fun PrototypeWebsiteRow(
    domain: String,
    onAction: ((PrototypeAction) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (onAction == null) {
        PosatoItemRow(
            modifier = modifier,
            headlineContent = { Text(domain) },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
        )
    } else {
        PosatoDisclosureRow(
            modifier = modifier,
            onClick = { onAction(ItemAction.OpenDomain(domain)) },
            headlineContent = { Text(domain) },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
            trailingContent = {
                PosatoItemMenu("More options for $domain") { dismiss ->
                    PosatoItemMenuAction(leadingContent = { PosatoIcon(PosatoIcons.Edit, null) }, onClick = {
                        dismiss()
                        onAction(ItemAction.OpenDomain(domain))
                    }) { Text("Edit") }
                    PosatoItemMenuAction(destructive = true, leadingContent = { PosatoIcon(PosatoIcons.Remove, null) }, onClick = {
                        dismiss()
                        onAction(ItemAction.RemoveDomain(domain))
                    }) { Text("Remove") }
                }
            },
        )
    }
}

@Composable
internal fun PrototypeApplicationRow(
    name: String,
    onAction: ((PrototypeAction) -> Unit)?,
    modifier: Modifier = Modifier
) {
    PosatoItemRow(
        modifier = modifier,
        headlineContent = { Text(name) },
        leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
        trailingContent = onAction?.let { dispatch ->
            {
                PosatoItemMenu("More options for $name") { dismiss ->
                    PosatoItemMenuAction(destructive = true, leadingContent = { PosatoIcon(PosatoIcons.Remove, null) }, onClick = {
                        dismiss()
                        dispatch(ItemAction.RemoveApplication(name))
                    }) { Text("Remove") }
                }
            }
        },
    )
}
