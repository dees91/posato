package app.posato.desktop.windows

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Posato's icon for its windows' title bars and the taskbar, which Windows takes from each window rather than from
 * `Posato.exe`; the Windows jar carries it. Without it (run outside the package) Java's default stays.
 */
internal fun windowIconImage(): BufferedImage? = icon

private val icon: BufferedImage? by lazy {
    WindowIconResource::class.java.getResourceAsStream(WINDOW_ICON)?.use(ImageIO::read)
}

private object WindowIconResource

private const val WINDOW_ICON = "/app/posato/desktop/windows/posato-icon.png"
