package app.posato.control.desktop

import app.posato.control.core.LocalConfiguration
import app.posato.control.core.RepoLayout
import app.posato.control.core.RunContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopProcessesTest {
    private fun layout(): RepoLayout {
        val root = createTempDirectory("posato-control-processes")
        root.resolve("settings.gradle.kts").writeText("rootProject.name = \"Posato\"\n")
        return RepoLayout(root)
    }

    private fun executable(
        bundle: Path,
        relative: String
    ): Path {
        val path = bundle.resolve(relative)
        path.parent.createDirectories()
        path.writeText("#!/bin/sh\n")
        return path
    }

    private fun processes(
        layout: RepoLayout,
        entries: List<ProcessEntry>
    ): DesktopProcesses {
        val context = RunContext(
            layout = layout,
            configuration = LocalConfiguration(emptyMap(), emptyMap()),
            runId = "test",
            artifactsRoot = layout.runsDirectory,
            verbose = false,
            timeout = Duration.ofSeconds(1),
        )
        return DesktopProcesses(context) { entries }
    }

    @Test
    fun `only executables inside the staged bundle are addressable`() {
        val layout = layout()
        val bundle = layout.stagedDesktopApplication
        val application = executable(bundle, "Contents/MacOS/Posato")
        val helper = executable(bundle, "Contents/Helpers/PosatoMacOSHelper.app/Contents/MacOS/PosatoMacOSHelper")
        val outside = executable(layout.root, "elsewhere/Safari")

        val contained = processes(
            layout,
            listOf(
                ProcessEntry(100, application.toString()),
                ProcessEntry(200, helper.toString()),
                ProcessEntry(300, outside.toString()),
                ProcessEntry(400, layout.root.resolve("missing/Ghost").toString()),
            ),
        ).containedProcesses()

        assertEquals(listOf(100L, 200L), contained.map { it.pid })
    }

    @Test
    fun `a symbolic link that points out of the staged bundle is not addressable`() {
        val layout = layout()
        val bundle = layout.stagedDesktopApplication
        executable(bundle, "Contents/MacOS/Posato")
        val outside = executable(layout.root, "elsewhere/Impostor")
        val link = bundle.resolve("Contents/MacOS/Impostor")
        Files.createSymbolicLink(link, outside)

        val contained = processes(layout, listOf(ProcessEntry(500, link.toString()))).containedProcesses()

        assertTrue(contained.isEmpty(), contained.toString())
    }

    @Test
    fun `nothing is addressable without a staged bundle`() {
        val layout = layout()
        val contained = processes(layout, listOf(ProcessEntry(100, "/usr/bin/true"))).containedProcesses()

        assertTrue(contained.isEmpty(), contained.toString())
    }
}
