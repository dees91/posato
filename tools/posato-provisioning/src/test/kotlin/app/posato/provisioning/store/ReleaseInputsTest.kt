package app.posato.provisioning.store

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RGB = 2
private const val RGBA = 6

class ReleaseInputsTest {
    @Test
    fun `computes the MD5 App Store Connect checks a committed screenshot against`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", ReleaseInputs.md5(ByteArray(0)))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ReleaseInputs.md5("abc".toByteArray()))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ScreenshotFile("a.png", "abc".toByteArray()).checksum)
    }

    @Test
    fun `reads both sets in file-name order`() {
        val root = screenshotRoot()
        png(root.resolve("iphone-6.9/02-session.png"))
        png(root.resolve("iphone-6.9/01-paused.png"))
        png(root.resolve("ipad-13/01-paused.png"))
        root.resolve("iphone-6.9/notes.txt").writeText("ignored")

        val sets = ReleaseInputs.screenshots(root)

        assertEquals(listOf("01-paused.png", "02-session.png"), sets.getValue(ScreenshotSlot.IPHONE).map { it.fileName })
        assertEquals(listOf("01-paused.png"), sets.getValue(ScreenshotSlot.IPAD).map { it.fileName })
    }

    @Test
    fun `refuses a screenshot with an alpha channel`() {
        val root = screenshotRoot()
        png(root.resolve("iphone-6.9/01.png"), colorType = RGBA)
        png(root.resolve("ipad-13/01.png"))

        val failure = assertFailsWith<ProvisioningException> { ReleaseInputs.screenshots(root) }

        assertEquals(ErrorCode.RELEASE_INPUT_INVALID, failure.code)
        assertTrue(failure.message.orEmpty().contains("alpha"))
    }

    @Test
    fun `refuses a missing set without naming the path`() {
        val root = screenshotRoot()
        png(root.resolve("iphone-6.9/01.png"))

        val failure = assertFailsWith<ProvisioningException> { ReleaseInputs.screenshots(root) }

        assertTrue(failure.message.orEmpty().contains("ipad-13"))
        assertFalse(failure.message.orEmpty().contains(root.toString()))
    }

    @Test
    fun `trims What's New and refuses an empty file`() {
        val file = Files.createTempFile("whats-new", ".txt")
        file.writeText("\n- Something new.\n\n")
        assertEquals("- Something new.", ReleaseInputs.whatsNew(file))

        file.writeText("  \n")
        assertEquals(ErrorCode.RELEASE_INPUT_INVALID, assertFailsWith<ProvisioningException> { ReleaseInputs.whatsNew(file) }.code)
    }

    @Test
    fun `accepts only a three-part marketing version`() {
        assertEquals("1.2.0", ReleaseInputs.version("1.2.0"))
        assertFailsWith<ProvisioningException> { ReleaseInputs.version("1.2") }
        assertFailsWith<ProvisioningException> { ReleaseInputs.version("1.2.0/../x") }
    }

    private fun screenshotRoot(): Path = Files.createTempDirectory("screenshots")

    /** The PNG signature and an IHDR header, which is all the input check reads; the pixels are irrelevant here. */
    private fun png(
        file: Path,
        colorType: Int = RGB
    ) {
        file.parent.createDirectories()
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val header = byteArrayOf(0, 0, 0, 13) + "IHDR".toByteArray() + ByteArray(8) + byteArrayOf(8, colorType.toByte(), 0, 0, 0)
        file.writeBytes(signature + header + file.fileName.toString().toByteArray())
    }
}
