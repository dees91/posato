package app.posato.desktop.linux

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxAutostartTest {
    private val home: Path = Files.createTempDirectory("posato-autostart-home")
    private val autostart = LinuxAutostart(linuxAutostartDirectory(environment = emptyMap(), home = home.toString()))
    private val entry: Path = home.resolve(".config/autostart/posato.desktop")

    @Test
    fun `given the switch is turned on then a login starts Posato in the background and an uninstalled package is skipped`() {
        autostart.setEnabled(true)

        val text = entry.readText()
        assertContains(text, "[Desktop Entry]\n")
        assertContains(text, "\nType=Application\n")
        assertContains(text, "\nExec=/opt/posato/bin/posato --background\n")
        assertContains(text, "\nTryExec=/opt/posato/bin/posato\n")
        assertFalse(text.contains("Hidden="))
        assertTrue(autostart.isEnabled())
        assertEquals(listOf(entry), entry.parent.listDirectoryEntries())
    }

    @Test
    fun `given the switch is turned off then no entry remains`() {
        autostart.setEnabled(true)

        autostart.setEnabled(false)

        assertFalse(entry.exists())
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `given Tweaks turned the entry off with Hidden then the switch reads off`() {
        writeEntry("Hidden=true")

        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `given Tweaks turned the entry off with the GNOME key then the switch reads off and turning it on starts it again`() {
        writeEntry("X-GNOME-Autostart-enabled=false")
        assertFalse(autostart.isEnabled())

        autostart.setEnabled(true)

        assertTrue(autostart.isEnabled())
        assertFalse(entry.readText().contains("X-GNOME-Autostart-enabled=false"))
    }

    @Test
    fun `given XDG_CONFIG_HOME is absolute then the entry lives there and a relative value is ignored`() {
        val config = Files.createTempDirectory("posato-config")

        assertEquals(config.resolve("autostart"), linuxAutostartDirectory(mapOf("XDG_CONFIG_HOME" to config.toString()), home.toString()))
        assertEquals(home.resolve(".config/autostart"), linuxAutostartDirectory(mapOf("XDG_CONFIG_HOME" to "relative"), home.toString()))
    }

    private fun writeEntry(extra: String) {
        Files.createDirectories(entry.parent)
        entry.writeText("[Desktop Entry]\nType=Application\nName=Posato\nExec=/opt/posato/bin/posato --background\n$extra\n")
    }
}
