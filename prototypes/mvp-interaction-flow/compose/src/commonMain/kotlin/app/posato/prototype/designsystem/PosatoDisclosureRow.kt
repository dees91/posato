package app.posato.prototype.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@Composable
fun PosatoDisclosureRow(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: @Composable () -> Unit = { PosatoIcon(PosatoIcons.Chevron, null) }
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = PosatoSize.Control).semantics { role = Role.Button },
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
