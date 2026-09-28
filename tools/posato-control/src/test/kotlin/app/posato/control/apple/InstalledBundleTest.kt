package app.posato.control.apple

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstalledBundleTest {
    private fun bundle(
        root: Path,
        name: String,
        executable: ByteArray?,
    ): Path {
        val app = root.resolve(name).resolve("Posato.app").createDirectories()
        executable?.let { bytes -> app.resolve("Posato").writeBytes(bytes) }
        return app
    }

    @Test
    fun `given the same executable bytes then the install is current`() {
        val root = Files.createTempDirectory("bundle")

        assertTrue(sameExecutable(bundle(root, "installed", byteArrayOf(1, 2, 3)), bundle(root, "built", byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `given another build's executable or none then the install is stale`() {
        val root = Files.createTempDirectory("bundle")
        val built = bundle(root, "built", byteArrayOf(1, 2, 3))

        assertFalse(sameExecutable(bundle(root, "older", byteArrayOf(1, 2, 4)), built))
        assertFalse(sameExecutable(bundle(root, "empty", null), built))
    }
}
