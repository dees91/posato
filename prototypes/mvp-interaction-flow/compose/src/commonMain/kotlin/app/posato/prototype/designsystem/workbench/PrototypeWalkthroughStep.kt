package app.posato.prototype.designsystem.workbench

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoControlDefaults
import app.posato.prototype.designsystem.PosatoSpace

@Composable
fun PrototypeWalkthroughStep(
    number: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    completed: Boolean = false,
    supportingContent: (@Composable () -> Unit)? = null,
    statusContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.alpha(if (enabled) 1f else PosatoControlDefaults.DISABLED_ALPHA).semantics { role = Role.Button },
        onClick = onClick,
        enabled = enabled,
        color = if (completed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(PosatoSpace.Medium), horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
            PosatoCaption(number)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                content()
                supportingContent?.invoke()
                statusContent?.invoke()
            }
        }
    }
}
