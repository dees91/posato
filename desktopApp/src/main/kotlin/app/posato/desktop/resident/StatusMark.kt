package app.posato.desktop.resident

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * Posato's pause mark for a status icon: two slanted bars in [argb], outlined while no restrictions are active and
 * filled while they are, as on the Mac.
 */
internal fun statusMark(
    size: Int,
    filled: Boolean,
    argb: Int,
): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.color = Color(argb, true)
    graphics.stroke = BasicStroke(size * STROKE)
    val width = size * BAR_WIDTH
    listOf(LEFT_BAR to SHORT_BAR, RIGHT_BAR to LONG_BAR).forEach { (left, height) ->
        val bar = RoundRectangle2D.Double(size * left, size * (1 - height) / 2, width, size * height, width, width)
        val shape = AffineTransform.getRotateInstance(SLANT, size / 2.0, size / 2.0).createTransformedShape(bar)
        if (filled) graphics.fill(shape) else graphics.draw(shape)
    }
    graphics.dispose()
    return image
}

private const val STROKE = 0.08f
private const val BAR_WIDTH = 0.2
private const val LEFT_BAR = 0.22
private const val RIGHT_BAR = 0.56
private const val SHORT_BAR = 0.58
private const val LONG_BAR = 0.78
private const val SLANT = 0.17
