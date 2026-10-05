package app.posato.core.designsystem

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The press feedback on the Apple hosts for rows and other plain tappable areas: while pressed, the content dims,
 * as a pressed button does, with no fill, outline, or ripple around it. Focus that came from the keyboard draws
 * the accent ring, so a person moving with Tab sees where they are.
 */
internal class PosatoPressDim(
    private val focusColor: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return PressDimNode(interactionSource, focusColor)
    }

    override fun equals(other: Any?): Boolean {
        return other is PosatoPressDim && other.focusColor == focusColor
    }

    override fun hashCode(): Int {
        return focusColor.hashCode()
    }
}

private class PressDimNode(
    private val interactionSource: InteractionSource,
    private val focusColor: Color,
) : Modifier.Node(),
    DrawModifierNode {
    private var pressed = false
    private var pressedRecently = false
    private var keyboardFocused = false
    private val dim = Paint().apply { alpha = PRESSED_ROW_ALPHA }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressed = true
                        pressedRecently = true
                    }

                    // A press ends its own claim: focus that arrives later came from the keyboard. On the Mac a
                    // click never focuses, so a clicked control must still show the ring when Tab reaches it.
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        pressed = false
                        pressedRecently = false
                    }

                    is FocusInteraction.Focus -> {
                        keyboardFocused = !pressedRecently
                    }

                    is FocusInteraction.Unfocus -> {
                        keyboardFocused = false
                        pressedRecently = false
                    }
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (pressed) {
            drawIntoCanvas { canvas -> canvas.saveLayer(Rect(Offset.Zero, size), dim) }
            drawContent()
            drawIntoCanvas { canvas -> canvas.restore() }
        } else {
            drawContent()
        }
        if (keyboardFocused) {
            val width = FOCUS_RING.toPx()
            drawRoundRect(
                color = focusColor,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius(FOCUS_RING_CORNER.toPx()),
                style = Stroke(width),
            )
        }
    }
}

private val FOCUS_RING = 2.dp
private val FOCUS_RING_CORNER = 8.dp

private const val PRESSED_ROW_ALPHA = 0.5f
