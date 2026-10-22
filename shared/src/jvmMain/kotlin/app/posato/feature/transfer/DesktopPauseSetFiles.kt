package app.posato.feature.transfer

import app.posato.feature.transfer.data.PauseSetFilePorts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The save and open panels of the Mac and Linux: AWT's file dialog, which is the system panel on macOS and
 * the GTK chooser on Linux. It opens on the event thread, over Posato's window.
 */
internal object DesktopPauseSetFiles : PauseSetFilePorts {
    override suspend fun export(
        bytes: ByteArray,
        fileName: String,
    ): Boolean {
        val chosen = withContext(Dispatchers.Main) { choose("Export pause sets", FileDialog.SAVE, fileName) } ?: return false
        val target = if (chosen.fileName.toString().endsWith(EXTENSION)) chosen else chosen.resolveSibling(chosen.fileName.toString() + EXTENSION)
        return withContext(Dispatchers.IO) {
            try {
                Files.write(target, bytes)
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    override suspend fun pick(limit: Int): ByteArray? {
        val chosen = withContext(Dispatchers.Main) { choose("Import pause sets", FileDialog.LOAD, null) } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                Files.newInputStream(chosen).use { stream -> stream.readNBytes(limit + 1) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IOException) {
                null
            }
        }
    }

    private fun choose(
        title: String,
        mode: Int,
        fileName: String?,
    ): Path? {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
            ?: Frame.getFrames().firstOrNull { frame -> frame.isVisible }
        val dialog = FileDialog(owner, title, mode)
        fileName?.let { name -> dialog.file = name }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return try {
            Path.of(dialog.directory, file)
        } catch (_: InvalidPathException) {
            null
        } finally {
            dialog.dispose()
            // On Linux the window manager can leave keyboard focus nowhere after the chooser closes, so the code
            // field that follows would take no typing until a click.
            owner?.toFront()
            owner?.requestFocus()
        }
    }

    private const val EXTENSION: String = ".posato"
}
