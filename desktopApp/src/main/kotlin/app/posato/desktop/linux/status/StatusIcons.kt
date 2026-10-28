package app.posato.desktop.linux.status

import app.posato.desktop.resident.statusMark

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
        val image = statusMark(size, filled, MARK_COLOR)
        val bytes = ByteArray(size * size * BYTES_PER_PIXEL)
        image.getRGB(0, 0, size, size, null, 0, size).forEachIndexed { index, argb ->
            // ARGB32 in network byte order, as the StatusNotifierItem specification asks.
            for (shift in 0 until BYTES_PER_PIXEL) {
                val bits = BITS * (BYTES_PER_PIXEL - 1 - shift)
                bytes[index * BYTES_PER_PIXEL + shift] = (argb ushr bits).toByte()
            }
        }
        return IconPixmap(size, size, bytes)
    }

    private companion object {
        val SIZES = listOf(16, 22, 32, 48)
        const val MARK_COLOR = 0xFFF2F2E9.toInt()
        const val BYTES_PER_PIXEL = 4
        const val BITS = 8
    }
}

/** Where the package installs the symbolic icons (jpackage copies `linux/` into the application directory). */
internal const val LINUX_ICON_THEME_PATH = "/opt/posato/lib/app/icons"
