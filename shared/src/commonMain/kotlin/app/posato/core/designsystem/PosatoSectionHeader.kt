package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoSectionHeader(
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    actionContent: (@Composable () -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny),
        ) {
            Column(Modifier.semantics { heading() }) { titleContent() }
            supportingContent?.invoke()
        }
        actionContent?.invoke()
    }
}

@Preview(name = "Section header with action", widthDp = 390)
@Composable
private fun PosatoSectionHeaderPreview() {
    PosatoComponentPreview {
        PosatoSectionHeader(
            titleContent = { Text("Applications") },
            supportingContent = { PosatoCaption("On this Mac only") },
            actionContent = { PosatoButton(onClick = {}) { Text("Choose apps") } },
        )
    }
}
