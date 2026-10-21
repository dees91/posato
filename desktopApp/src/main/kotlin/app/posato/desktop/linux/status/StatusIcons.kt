package app.posato.desktop.linux.status

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * The status icon: Posato's pause mark as two slanted bars, outlined while no restrictions are active and filled while
 * they are, as on the Mac. Panels that follow icon themes load the symbolic SVGs from [themePath], which the
 * package installs and the shell recolors; the others draw the ARGB pixmaps.
 */
internal class StatusIcons(
    val themePath: String,
) {
    val outlineName: String = "posato-symbolic"
    val filledName: String = "posato-active-symbolic"
    val outlinePixmaps: List<IconPixmap> = SIZES.map { pixmap(it, filled = false) }
    val filledPixmaps: List<IconPixmap> = SIZES.map { pixmap(it, filled = true) }

    private fun pixmap(
        size: Int,
        filled: Boolean,
    ): IconPixmap {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color(MARK_COLOR, true)
        graphics.stroke = BasicStroke(size * STROKE)
        val width = size * BAR_WIDTH
        listOf(LEFT_BAR to SHORT_BAR, RIGHT_BAR to LONG_BAR).forEach { (left, height) ->
            val bar = RoundRectangle2D.Double(size * left, size * (1 - height) / 2, width, size * height, width, width)
            val shape = AffineTransform.getRotateInstance(SLANT, size / 2.0, size / 2.0).createTransformedShape(bar)
            if (filled) graphics.fill(shape) else graphics.draw(shape)
        }
        graphics.dispose()
        val bytes = ByteArray(size * size * BYTES_PER_PIXEL)
        image.getRGB(0, 0, size, size, null, 0, size).forEachIndexed { index, argb ->
            // ARGB32 in network byte order, as the StatusNotifierItem specification asks.
            for (shift in 0 until BYTES_PER_PIXEL) bytes[index * BYTES_PER_PIXEL + shift] = (argb ushr (BITS * (BYTES_PER_PIXEL - 1 - shift))).toByte()
        }
        return IconPixmap(size, size, bytes)
    }

    private companion object {
        val SIZES = listOf(16, 22, 32, 48)
        const val MARK_COLOR = 0xFFF2F2E9.toInt()
        const val STROKE = 0.08f
        const val BAR_WIDTH = 0.2
        const val LEFT_BAR = 0.22
        const val RIGHT_BAR = 0.56
        const val SHORT_BAR = 0.58
        const val LONG_BAR = 0.78
        const val SLANT = 0.17
        const val BYTES_PER_PIXEL = 4
        const val BITS = 8
    }
}
