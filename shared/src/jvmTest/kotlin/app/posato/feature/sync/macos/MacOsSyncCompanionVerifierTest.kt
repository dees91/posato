package app.posato.feature.sync.macos

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MacOsSyncCompanionVerifierTest {
    @Test
    fun `given a missing nested companion when verified then verification fails`() {
        val root = Files.createTempDirectory("posato-missing-companion")
        Files.createDirectories(root.resolve("Contents/Helpers"))

        assertFails {
            MacOsSyncCompanionVerifier().verify(root)
        }
    }

    @Test
    fun `given the staged ad-hoc application when verified then the companion executable is returned`() {
        val root = java.nio.file.Path.of(
            "../desktopApp/build/compose/binaries/main/development-package/Posato.app",
        )
        if (!Files.isDirectory(root)) {
            return
        }

        val executable = MacOsSyncCompanionVerifier().verify(root)
        checkNotNull(MacOsSyncCompanionClient.verified(root))
        assertTrue(executable.fileName.toString() == MacOsSyncCompanionProtocol.COMPANION_EXECUTABLE)
    }
}
