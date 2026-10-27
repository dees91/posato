package app.posato.desktop.windows

import app.posato.feature.targets.data.ApplicationCatalog
import app.posato.feature.targets.data.CatalogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/**
 * The applications the Start menu lists, read from the shortcuts in the machine's and the user's Start menu folders.
 * Only a shortcut that starts a local `.exe` itself, without launch arguments, is offered, and a pause matches the
 * program by the long canonical path of that executable. Store applications have no such shortcut; interpreters,
 * script hosts, and launchers, whose ending would end every program they run, are never offered, and neither is
 * Posato.
 */
internal class StartMenuCatalog(
    private val roots: List<Path> = defaultRoots(),
) : ApplicationCatalog {
    override suspend fun installed(): List<CatalogEntry> {
        return withContext(Dispatchers.IO) {
            roots.filter(Files::isDirectory).flatMap { root ->
                shortcuts(root).mapNotNull { shortcut -> entry(root, shortcut) }
            }.distinctBy { it.key.lowercase() }.distinctBy { it.target.lowercase() }.sortedBy { it.name.lowercase() }
        }
    }

    override suspend fun choose(
        entries: List<CatalogEntry>,
        chosen: Set<String>,
    ): List<CatalogEntry>? {
        return suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater { continuation.resume(showChooser(entries, chosen)) }
        }
    }

    private fun shortcuts(root: Path): List<Path> {
        return try {
            Files.walk(root, MAX_DEPTH).use { files -> files.filter { it.extension.equals("lnk", ignoreCase = true) }.toList() }
        } catch (_: IOException) {
            emptyList()
        } catch (_: java.io.UncheckedIOException) {
            emptyList()
        }
    }

    private fun entry(
        root: Path,
        shortcut: Path,
    ): CatalogEntry? {
        val link = read(shortcut)?.let(ShellLink::parse) ?: return null
        // A shortcut with arguments starts something other than the program alone, such as a web app through a
        // browser's proxy (`--app-id`, `chrome_proxy.exe`) or Squirrel's `Update.exe --processStart`. Chrome's and
        // Edge's own shortcuts only say where they were started from, so they still start the browser alone.
        if (link.arguments.replace(shortcutLocation, "").isNotBlank()) return null
        val target = link.target?.let(::canonicalExecutable) ?: return null
        val name = shortcut.nameWithoutExtension.takeIf { it.isNotBlank() } ?: return null
        if (name.contains("posato", ignoreCase = true) || target.contains("posato", ignoreCase = true)) return null
        val key = root.relativize(shortcut).toString().removeSuffix(".lnk").removeSuffix(".LNK")
        return CatalogEntry(key, name, target)
    }

    private fun read(shortcut: Path): ByteArray? {
        return try {
            if (Files.size(shortcut) > MAX_SHORTCUT_BYTES) null else Files.readAllBytes(shortcut)
        } catch (_: IOException) {
            null
        }
    }

    /** The long, canonical path of a local program that is not an interpreter or a launcher. */
    private fun canonicalExecutable(target: String): String? {
        val real = try {
            Paths.get(target).toRealPath().toString()
        } catch (_: IOException) {
            return null
        } catch (_: java.nio.file.InvalidPathException) {
            return null
        }
        val local = localExecutable.matches(real)
        val name = real.substringAfterLast('\\').substringBeforeLast('.')
        return real.takeIf { local && !launcherNames.matches(name.lowercase()) }
    }

    private fun showChooser(
        entries: List<CatalogEntry>,
        chosen: Set<String>,
    ): List<CatalogEntry>? {
        val dialog = JDialog(null as java.awt.Frame?, "Choose apps", true)
        windowIconImage()?.let { dialog.setIconImage(it) }
        val boxes = entries.map { entry -> JCheckBox(entry.name, entry.key in chosen) }
        val list = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING)
            boxes.forEach(::add)
        }
        var result: List<CatalogEntry>? = null
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Cancel").apply { addActionListener { dialog.dispose() } })
            add(
                JButton("Choose").apply {
                    addActionListener {
                        result = entries.filterIndexed { index, _ -> boxes[index].isSelected }
                        dialog.dispose()
                    }
                },
            )
        }
        dialog.contentPane.add(JScrollPane(list), BorderLayout.CENTER)
        dialog.contentPane.add(buttons, BorderLayout.SOUTH)
        dialog.setSize(WIDTH, HEIGHT)
        dialog.setLocationRelativeTo(null)
        dialog.isVisible = true
        return result
    }

    private companion object {
        const val MAX_DEPTH = 6
        const val MAX_SHORTCUT_BYTES = 1_048_576L
        const val PADDING = 12
        const val WIDTH = 420
        const val HEIGHT = 520

        /** Chromium's note of the shortcut that started it, such as `--source-shortcut-location=start-menu`. */
        val shortcutLocation = Regex("--source-shortcut-location=[A-Za-z-]+")

        /** The service's grammar: a drive letter, plain names, and an `.exe`. */
        val localExecutable = Regex("[A-Za-z]:\\\\[^/:*?\"<>|]+\\.[eE][xX][eE]")
        val launcherNames = Regex(
            "cmd|powershell(_ise)?|pwsh|wscript|cscript|rundll32|explorer|msiexec|javaw?s?|mshta|conhost|wt|windowsterminal|" +
                "python[0-9.]*w?|pyw?|node|update|chrome_proxy|msedge_proxy|regsvr32|control|mmc|wsl|wslhost|bash|sh|" +
                "ruby[0-9.]*w?|perl[0-9.]*|php[0-9.]*|dotnet|electron[0-9]*|hh|schtasks|cmstp",
        )

        fun defaultRoots(): List<Path> {
            val start = "Microsoft\\Windows\\Start Menu\\Programs"
            return listOfNotNull(System.getenv("ProgramData"), System.getenv("APPDATA")).map { Paths.get(it, start) }
        }
    }
}
