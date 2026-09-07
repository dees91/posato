package app.posato.prototype.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PosatoPanel(
    modifier: Modifier = Modifier,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            headerContent?.let {
                Box(Modifier.padding(PosatoSpace.Large)) { it() }
                PosatoDivider()
            }
            Column(Modifier.padding(PosatoSpace.Large), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium), content = content)
        }
    }
}

@Composable
fun PosatoDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant, thickness = PosatoSpace.Hairline)
}

@Composable
fun PosatoActionRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
        content = content,
    )
}

@Composable
fun PosatoSection(
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actionContent: (@Composable () -> Unit)? = null,
    descriptionContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoSectionHeader(
            modifier = Modifier.fillMaxWidth(),
            titleContent = titleContent,
            supportingContent = descriptionContent,
            actionContent = actionContent,
        )
        content()
    }
}
