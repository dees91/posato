package app.posato.core.designsystem

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoPanel(
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
internal fun PosatoDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant, thickness = PosatoSpace.Hairline)
}

@Composable
internal fun PosatoActionRow(
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
internal fun PosatoSection(
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

@Preview(name = "Panel and section", widthDp = 390)
@Composable
private fun PosatoLayoutPreview() {
    PosatoComponentPreview {
        PosatoPanel(headerContent = { Text("Your next pause") }) {
            PosatoBody("A clear ending leaves room for what matters.")
            PosatoActionRow {
                PosatoButton(onClick = {}) { Text("Review session") }
                PosatoButton(onClick = {}, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
            }
        }
        PosatoSection(
            titleContent = { Text("What will be paused") },
            descriptionContent = { PosatoCaption("Your saved selection") },
            actionContent = { PosatoButton(onClick = {}, style = PosatoButtonStyle.Quiet) { Text("View all") } },
        ) {
            PosatoBody("50 websites and 4 applications")
        }
    }
}
