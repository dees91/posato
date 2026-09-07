package app.posato.prototype.designsystem.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDivider
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoWordmark

@Composable
fun PrototypeHeader(
    title: String,
    studyLabel: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    layout: PosatoLayout = PosatoLayout.Expanded,
    actionsContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosatoWordmark()
                if (layout == PosatoLayout.Expanded) PosatoCaption(studyLabel)
            }
            actionsContent?.invoke()
        }
        PosatoDivider()
        PosatoHeading(title = title, eyebrow = studyLabel, description = description, layout = layout)
    }
}
