package app.posato.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

/** One action in an item's menu. A destructive action is shown in the error color, or as the system's destructive item. */
@Immutable
internal class PosatoMenuItem(
    val title: String,
    val onClick: () -> Unit,
    val symbol: PosatoMenuSymbol? = null,
    val destructive: Boolean = false,
    val detail: String? = null,
    val enabled: Boolean = true,
)

/** One value a picker offers, with an optional line under its title. */
@Immutable
internal class PosatoPickerOption(
    val title: String,
    val detail: String? = null,
)

/** An action a swipe reveals at the trailing end of a row. A destructive action is shown in the error color. */
@Immutable
internal class PosatoSwipeAction(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/** A menu action's symbol: the app's own icon, and the SF Symbol the iOS menu shows instead. */
internal enum class PosatoMenuSymbol(
    val icon: ImageVector,
    val systemName: String,
) {
    Edit(PosatoIcons.Edit, "pencil"),
    Rename(PosatoIcons.Edit, "character.cursor.ibeam"),
    MakeDefault(PosatoIcons.Check, "checkmark.circle"),
    Skip(PosatoIcons.Arrow, "forward.end"),
    Remove(PosatoIcons.Remove, "trash"),
}

/** The "…" button that opens an item's actions: the system menu on iOS, a menu drawn in the app's palette elsewhere. */
@Composable
internal fun PosatoItemMenu(
    label: String,
    items: List<PosatoMenuItem>,
    modifier: Modifier = Modifier,
) {
    if (platformUsesCupertinoChrome) {
        PlatformItemMenu(label, items, modifier)
    } else {
        DrawnItemMenu(label, items, modifier)
    }
}

/** The host's own menu, where the platform has one the app can present. */
@Composable
internal expect fun PlatformItemMenu(
    label: String,
    items: List<PosatoMenuItem>,
    modifier: Modifier = Modifier,
)

@Composable
internal fun DrawnItemMenu(
    label: String,
    items: List<PosatoMenuItem>,
    modifier: Modifier = Modifier,
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
            items.forEach { item ->
                val color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = PosatoSize.Control).semantics { role = Role.Button },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                            Text(item.title)
                            item.detail?.let { PosatoCaption(it) }
                        }
                    },
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                    leadingIcon = item.symbol?.let { symbol -> { PosatoIcon(symbol.icon, null) } },
                    colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
                    contentPadding = PaddingValues(horizontal = PosatoSpace.Large, vertical = PosatoSpace.Tiny),
                )
            }
        }
    }
}

@Preview(name = "Menu trigger", widthDp = 280)
@Preview(name = "Menu trigger · dark", widthDp = 280, uiMode = 0x20)
@Composable
private fun PosatoItemMenuPreview() {
    PosatoComponentPreview {
        DrawnItemMenu(
            "Actions for example.com",
            listOf(
                PosatoMenuItem("Edit", {}, PosatoMenuSymbol.Edit),
                PosatoMenuItem("Remove", {}, PosatoMenuSymbol.Remove, destructive = true),
            ),
        )
    }
}
