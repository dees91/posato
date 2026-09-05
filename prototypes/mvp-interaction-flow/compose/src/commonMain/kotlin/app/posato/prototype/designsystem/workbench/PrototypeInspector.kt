package app.posato.prototype.designsystem.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDivider
import app.posato.prototype.designsystem.PosatoPanel
import app.posato.prototype.designsystem.PosatoSpace

@Composable
fun PrototypeInspector(
    title: String,
    modifier: Modifier = Modifier,
    outcomeContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PosatoPanel(modifier = modifier, headerContent = { Text(title, style = MaterialTheme.typography.titleSmall) }) {
        content()
        outcomeContent?.invoke()
    }
}

@Composable
fun PrototypeStateFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(vertical = PosatoSpace.Small), horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
            PosatoCaption(label, Modifier.weight(1f))
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
        PosatoDivider()
    }
}
