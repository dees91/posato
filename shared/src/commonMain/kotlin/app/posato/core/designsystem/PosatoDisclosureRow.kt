package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoDisclosureRow(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: @Composable () -> Unit = { PosatoIcon(PosatoIcons.Chevron, null) },
    onClickLabel: String? = null,
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = PosatoSize.Control).semantics {
                role = Role.Button
                onClick(label = onClickLabel, action = null)
            },
            onClick = onClick,
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(vertical = PosatoSpace.Medium),
                horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingContent?.invoke()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                    headlineContent()
                    supportingContent?.invoke()
                }
                trailingContent()
            }
        }
        PosatoDivider()
    }
}

@Preview(name = "Selection disclosures", widthDp = 390)
@Composable
private fun PosatoDisclosureRowPreview() {
    PosatoComponentPreview {
        PosatoDisclosureRow(
            onClick = {},
            headlineContent = { Text("50 websites") },
            supportingContent = { PosatoCaption("View all exact domains") },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
        )
        PosatoDisclosureRow(onClick = {}, headlineContent = { Text("Selected applications") })
    }
}
