package app.posato.core.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Whether focus arrived from the keyboard rather than from a click or tap, so a focus mark shows only to the
 * person moving through the screen with Tab.
 */
@Composable
internal fun InteractionSource.collectIsKeyboardFocusedAsState(): State<Boolean> {
    val keyboardFocused = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        var pressedRecently = false
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressedRecently = true
                }

                is FocusInteraction.Focus -> {
                    keyboardFocused.value = !pressedRecently
                }

                is FocusInteraction.Unfocus -> {
                    keyboardFocused.value = false
                    pressedRecently = false
                }
            }
        }
    }
    return keyboardFocused
}

/**
 * Draws the keyboard focus ring around a control: the accent outline that the Mac and iPadOS show only while
 * focus came from the keyboard, following the control's shape.
 */
internal fun Modifier.keyboardFocusRing(
    focused: () -> Boolean,
    color: Color,
    shape: Shape,
): Modifier {
    return drawWithContent {
        drawContent()
        if (focused()) {
            drawOutline(shape.createOutline(size, layoutDirection, this), color, style = Stroke(FOCUS_RING_WIDTH.toPx()))
        }
    }
}

private val FOCUS_RING_WIDTH = PosatoSpace.Hairline * 2
