package app.posato.prototype.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun PosatoSectionHeader(
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
