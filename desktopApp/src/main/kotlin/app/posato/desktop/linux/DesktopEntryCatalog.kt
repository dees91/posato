package app.posato.desktop.linux

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

/**
 * The applications a Linux desktop lists, read from its desktop entries. A pause matches a snap by its directory and
 * any other program by the real path of its executable; Posato itself is never offered.
 */
internal class DesktopEntryCatalog(
    private val directories: List<Path> = defaultDirectories(),
) : ApplicationCatalog {
    override suspend fun installed(): List<CatalogEntry> {
        return withContext(Dispatchers.IO) {
            directories.filter(Files::isDirectory).flatMap { directory ->
                try {
                    Files.list(directory).use { files -> files.filter { it.toString().endsWith(".desktop") }.toList() }
                } catch (_: IOException) {
                    emptyList()
                }
            }.mapNotNull(::entry).distinctBy { it.key }.sortedBy { it.name.lowercase() }
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

    private fun showChooser(
        entries: List<CatalogEntry>,
        chosen: Set<String>,
    ): List<CatalogEntry>? {
        val dialog = JDialog(null as java.awt.Frame?, "Choose apps", true)
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

    private fun entry(file: Path): CatalogEntry? {
        val values = readDesktopEntry(file) ?: return null
        if (values["Type"] != "Application" || values["NoDisplay"] == "true" || values["Hidden"] == "true") return null
        val name = values["Name"]?.takeIf { it.isNotBlank() } ?: return null
        val key = file.fileName.toString().removeSuffix(".desktop")
        if (key.contains("posato", ignoreCase = true)) return null
        val target = values["X-SnapInstanceName"]?.let { "/snap/$it/" } ?: executableTarget(values["Exec"]) ?: return null
        return CatalogEntry(key, name, target)
    }

    private fun executableTarget(exec: String?): String? {
        val tokens = exec?.split(' ')?.filter { it.isNotEmpty() && !it.startsWith("%") } ?: return null
        val command = tokens.dropWhile { it == "env" || it.contains('=') }.firstOrNull()?.trim('"') ?: return null
        if (command.startsWith("/snap/bin/")) return "/snap/${command.removePrefix("/snap/bin/").substringBefore('.')}/"
        val path = if (command.startsWith("/")) Paths.get(command) else searchPath(command) ?: return null
        return try {
            path.toRealPath().toString()
        } catch (_: IOException) {
            null
        }
    }

    private fun searchPath(command: String): Path? {
        return System.getenv("PATH").orEmpty().split(':').map { Paths.get(it, command) }.firstOrNull(Files::isExecutable)
    }

    private fun readDesktopEntry(file: Path): Map<String, String>? {
        val lines = try {
            Files.readAllLines(file)
        } catch (_: IOException) {
            return null
        }
        val section = lines.dropWhile { it.trim() != "[Desktop Entry]" }.drop(1).takeWhile { !it.startsWith("[") }
        return section.mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key.trim() to value.trim() }
    }

    private companion object {
        const val PADDING = 12
        const val WIDTH = 420
        const val HEIGHT = 520

        fun defaultDirectories(): List<Path> {
            val home = System.getProperty("user.home")
            return listOf(
                "/usr/share/applications",
                "/usr/local/share/applications",
                "/var/lib/snapd/desktop/applications",
                "/var/lib/flatpak/exports/share/applications",
                "$home/.local/share/applications",
            ).map { Paths.get(it) }
        }
    }
}
