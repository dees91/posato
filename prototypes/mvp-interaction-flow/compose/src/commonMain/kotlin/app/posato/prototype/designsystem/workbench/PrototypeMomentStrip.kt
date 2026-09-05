package app.posato.prototype.designsystem.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDivider
import app.posato.prototype.designsystem.PosatoSpace

@Composable
fun PrototypeMomentStrip(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoCaption(label)
        PosatoActionRow(content = content)
    }
}

@Composable
fun PrototypeFooter(
    note: String,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoDivider()
        PosatoCaption(note)
        detail?.let { PosatoCaption(it) }
    }
}
