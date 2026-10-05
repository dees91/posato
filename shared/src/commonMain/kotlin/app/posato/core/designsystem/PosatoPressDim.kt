package app.posato.core.designsystem

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * The press feedback on the Apple hosts for rows and other plain tappable areas: while pressed, the content dims,
 * as a pressed button does, with no fill, outline, or ripple around it.
 */
internal object PosatoPressDim : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return PressDimNode(interactionSource)
    }

    override fun equals(other: Any?): Boolean {
        return other === this
    }

    override fun hashCode(): Int {
        return PressDimNode::class.hashCode()
    }
}

private class PressDimNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(),
    DrawModifierNode {
    private var pressed = false
    private val dim = Paint().apply { alpha = PRESSED_ROW_ALPHA }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val nowPressed = when (interaction) {
                    is PressInteraction.Press -> true
                    is PressInteraction.Release, is PressInteraction.Cancel -> false
                    else -> pressed
                }
                if (nowPressed != pressed) {
                    pressed = nowPressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (!pressed) {
            drawContent()
            return
        }
        drawIntoCanvas { canvas -> canvas.saveLayer(Rect(Offset.Zero, size), dim) }
        drawContent()
        drawIntoCanvas { canvas -> canvas.restore() }
    }
}

private const val PRESSED_ROW_ALPHA = 0.5f
