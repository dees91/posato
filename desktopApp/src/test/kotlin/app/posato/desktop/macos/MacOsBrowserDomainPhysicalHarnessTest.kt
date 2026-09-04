package app.posato.desktop.macos

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class MacOsBrowserDomainPhysicalHarnessTest {
    @Test
    fun `given the physical gate when unset then the maintainer checklist is skipped`() {
        if (System.getenv(GATE)?.isNotBlank() != true) {
            return
        }
        val outputDirectory = Path.of("build/verification/macos-004")
        Files.createDirectories(outputDirectory)
        val checklist = outputDirectory.resolve("checklist.md")
        Files.writeString(
            checklist,
            """
            # MACOS-004 physical checklist

            | Row | Result |
            | --- | --- |
            | Safari regular HTTP deny | |
            | Safari regular HTTPS deny | |
            | Safari private HTTP deny | |
            | Safari private HTTPS deny | |
            | Chrome regular HTTP deny | |
            | Chrome regular HTTPS deny | |
            | Chrome incognito HTTP deny | |
            | Chrome incognito HTTPS deny | |
            | Control / sibling / subdomain reachable | |
            | Presentation failure leaves denial intact | |
            | Conflict preflight refuses Apply | |
            | Sleep / wake restores baseline | |
            | Forced helper termination restores baseline | |
            | Reboot restores baseline | |
            | Listener failure with cache-busted selected target never DIRECT | |
            | Privacy canary absent from logs, IPC, durable state | |
            """.trimIndent() + "\n",
        )
        assertTrue(Files.isRegularFile(checklist))
    }

    private companion object {
        const val GATE = "POSATO_MACOS_004_PHYSICAL"
    }
}
