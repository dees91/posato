package app.posato.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoItemMenu(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            modifier = Modifier.size(PosatoSize.Control),
            onClick = { expanded = true },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            PosatoIcon(PosatoIcons.More, label, Modifier.size(PosatoSize.LargeIcon))
        }
        DropdownMenu(
            modifier = Modifier.widthIn(min = PosatoSize.Menu),
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            border = BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outlineVariant),
        ) {
            content { expanded = false }
        }
    }
}

@Composable
internal fun PosatoItemMenuAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        modifier = modifier.heightIn(min = PosatoSize.Control).semantics { role = Role.Button },
        text = content,
        onClick = onClick,
        leadingIcon = leadingContent,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
        contentPadding = PaddingValues(horizontal = PosatoSpace.Large, vertical = PosatoSpace.Tiny),
    )
}

@Preview(name = "Menu trigger and actions", widthDp = 280)
@Preview(name = "Menu trigger and actions · dark", widthDp = 280, uiMode = 0x20)
@Composable
private fun PosatoItemMenuPreview() {
    PosatoComponentPreview {
        PosatoItemMenu("Actions for example.com") { dismiss ->
            PosatoItemMenuAction(onClick = dismiss, leadingContent = { PosatoIcon(PosatoIcons.Edit, null) }) { Text("Edit") }
            PosatoItemMenuAction(onClick = dismiss, destructive = true, leadingContent = { PosatoIcon(PosatoIcons.Remove, null) }) { Text("Remove") }
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                PosatoItemMenuAction(onClick = {}, leadingContent = { PosatoIcon(PosatoIcons.Edit, null) }) { Text("Edit") }
                PosatoItemMenuAction(onClick = {}, destructive = true, leadingContent = { PosatoIcon(PosatoIcons.Remove, null) }) { Text("Remove") }
            }
        }
    }
}
