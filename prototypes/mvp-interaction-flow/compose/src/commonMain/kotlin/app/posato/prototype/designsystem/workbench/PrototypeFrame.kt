package app.posato.prototype.designsystem.workbench

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDivider
import app.posato.prototype.designsystem.PosatoShapes
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace

@Composable
fun PrototypeFrame(
    device: PrototypeDevice,
    modifier: Modifier = Modifier,
    chromeContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = (if (device == PrototypeDevice.IPhone) Modifier.widthIn(max = PosatoSize.Phone) else Modifier).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outline),
            shape = if (device == PrototypeDevice.IPhone) PosatoShapes.Phone else MaterialTheme.shapes.large,
        ) {
            Column {
                chromeContent?.invoke()
                content()
            }
        }
    }
}

@Composable
fun PrototypeWindowChrome(
    device: PrototypeDevice,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(PosatoSpace.Large),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (device == PrototypeDevice.Mac) {
                Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                    repeat(WINDOW_DOTS) {
                        Box(Modifier.size(WindowDotSize).background(MaterialTheme.colorScheme.outline, CircleShape))
                    }
                }
            } else {
                PosatoCaption("17:45")
            }
            PosatoCaption("Posato · Preview", Modifier.weight(1f))
            PosatoCaption(if (device == PrototypeDevice.Mac) "Mac" else "iPhone")
        }
        PosatoDivider()
    }
}

private const val WINDOW_DOTS = 3
private val WindowDotSize = 8.dp
