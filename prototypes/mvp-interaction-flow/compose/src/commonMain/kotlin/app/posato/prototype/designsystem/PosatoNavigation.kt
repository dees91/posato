package app.posato.prototype.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

@Composable
fun PosatoNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.heightIn(min = PosatoSize.Control).semantics { role = Role.Tab },
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else PosatoControlDefaults.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(PosatoSpace.Medium),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke()
            content()
        }
    }
}

@Composable
fun PosatoSidebar(
    modifier: Modifier = Modifier,
    footerContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(PosatoSpace.Large), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoWordmark()
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small), content = content)
        footerContent?.invoke()
    }
}

@Composable
fun PosatoSetupStep(
    number: String,
    label: String,
    current: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.semantics { selected = current },
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Text(number, color = color, style = MaterialTheme.typography.bodySmall)
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PosatoDeviceLabel(
    label: String,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small), verticalAlignment = Alignment.CenterVertically) {
        leadingContent?.invoke()
        PosatoCaption(label)
    }
}
