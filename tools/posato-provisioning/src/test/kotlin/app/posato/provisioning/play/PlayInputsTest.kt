package app.posato.provisioning.play

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RGBA = 6

/**
 * Play refuses an oversize listing text or a wrongly sized image only after an edit exists and part of it has been
 * uploaded; a refusal then costs an abandoned edit and, for screenshots, a half-replaced set. These checks run before
 * any request, and each test asserts the refusal names the offending input and not the maintainer's path.
 */
class PlayInputsTest {
    @Test
    fun `reads a complete locale and orders phone screenshots by file name`() {
        val root = listingRoot()

        val listings = PlayInputs.listings(root)

        val listing = listings.single()
        assertEquals("pl-PL", listing.language)
        assertEquals("Posato", listing.title)
        assertEquals(listOf("01-pause.png", "02-schedule.png"), listing.phoneScreenshots.map { it.fileName })
        assertTrue(listing.icon != null && listing.featureGraphic != null)
    }

    @Test
    fun `counts characters rather than bytes and refuses each text one character over Play's limit`() {
        val root = listingRoot()
        root.resolve("pl-PL/play/title.txt").writeText("\u0105".repeat(30))
        assertEquals("\u0105".repeat(30), PlayInputs.listings(root).single().title)

        mapOf("title.txt" to 31, "short-description.txt" to 81, "full-description.txt" to 4001).forEach { (file, length) ->
            val fresh = listingRoot()
            fresh.resolve("pl-PL/play/$file").writeText("a".repeat(length))
            val failure = assertFailsWith<ProvisioningException>(file) { PlayInputs.listings(fresh) }
            assertEquals(ErrorCode.RELEASE_INPUT_INVALID, failure.code)
            assertTrue(failure.message.orEmpty().contains("pl-PL/$file"), failure.message)
            assertFalse(failure.message.orEmpty().contains(fresh.toString()))
        }
    }

    @Test
    fun `refuses an icon or feature graphic of the wrong size and a feature graphic with alpha`() {
        refuses("icon.png") { root -> png(root.resolve("pl-PL/play/icon.png"), 1024, 1024) }
        refuses("feature-graphic.png") { root -> png(root.resolve("pl-PL/play/feature-graphic.png"), 1024, 512) }
        refuses("feature-graphic.png") { root -> png(root.resolve("pl-PL/play/feature-graphic.png"), 1024, 500, RGBA) }
        refuses("icon.png") { root -> png(root.resolve("pl-PL/play/icon.png"), 512, 512, RGBA, padding = 1024 * 1024) }
        refuses("icon.png") { root -> root.resolve("pl-PL/play/icon.png").writeBytes("GIF89a not a png at all".toByteArray()) }
    }

    @Test
    fun `refuses phone screenshots outside Play's count, size, and aspect bounds`() {
        refuses("phone") { root -> root.resolve("pl-PL/play/phone/02-schedule.png").deleteExisting() }
        refuses("phone") { root -> (3..9).forEach { index -> png(root.resolve("pl-PL/play/phone/0$index.png"), 1080, 1920) } }
        refuses("03.png") { root -> png(root.resolve("pl-PL/play/phone/03.png"), 1080, 2400) }
        refuses("03.png") { root -> png(root.resolve("pl-PL/play/phone/03.png"), 300, 600) }
        refuses("03.png") { root -> png(root.resolve("pl-PL/play/phone/03.png"), 2160, 3841) }
        refuses("03.png") { root -> png(root.resolve("pl-PL/play/phone/03.png"), 1080, 1920, RGBA) }
    }

    @Test
    fun `refuses a missing text file and a directory that is not a Play locale`() {
        refuses("short-description.txt") { root -> root.resolve("pl-PL/play/short-description.txt").deleteExisting() }
        refuses("pl_PL") { root -> root.resolve("pl_PL/play").createDirectories() }
    }

    @Test
    fun `reads release notes per language and refuses notes over Play's 500 characters`() {
        val notes = Files.createTempDirectory("notes")
        notes.resolve("en-US.txt").writeText("\nFixes.\n")
        notes.resolve("pl-PL.txt").writeText("Poprawki.")

        assertEquals(listOf("en-US" to "Fixes.", "pl-PL" to "Poprawki."), PlayInputs.releaseNotes(notes).map { it.language to it.text })

        notes.resolve("de-DE.txt").writeText("a".repeat(501))
        val failure = assertFailsWith<ProvisioningException> { PlayInputs.releaseNotes(notes) }
        assertTrue(failure.message.orEmpty().contains("de-DE.txt"))
    }

    @Test
    fun `refuses a bundle that is not a zip archive and an empty mapping file`() {
        val bundle = Files.createTempFile("app", ".aab")
        bundle.writeText("not a zip")
        assertEquals(ErrorCode.RELEASE_INPUT_INVALID, assertFailsWith<ProvisioningException> { PlayInputs.bundle(bundle) }.code)

        val mapping = Files.createTempFile("mapping", ".txt")
        assertEquals(ErrorCode.RELEASE_INPUT_INVALID, assertFailsWith<ProvisioningException> { PlayInputs.mapping(mapping) }.code)
    }

    private fun refuses(
        expected: String,
        change: (Path) -> Unit
    ) {
        val root = listingRoot()
        change(root)
        val failure = assertFailsWith<ProvisioningException>(expected) { PlayInputs.listings(root) }
        assertEquals(ErrorCode.RELEASE_INPUT_INVALID, failure.code)
        assertTrue(failure.message.orEmpty().contains(expected), failure.message)
        assertFalse(failure.message.orEmpty().contains(root.toString()))
    }

    private fun listingRoot(): Path {
        val root = Files.createTempDirectory("listing")
        val locale = root.resolve("pl-PL/play").createDirectories()
        locale.resolve("title.txt").writeText("Posato\n")
        locale.resolve("short-description.txt").writeText("A little space.")
        locale.resolve("full-description.txt").writeText("Pause websites and apps.")
        png(locale.resolve("icon.png"), 512, 512, RGBA)
        png(locale.resolve("feature-graphic.png"), 1024, 500)
        png(locale.resolve("phone/02-schedule.png"), 1080, 1920)
        png(locale.resolve("phone/01-pause.png"), 1080, 1920)
        return root
    }
}
