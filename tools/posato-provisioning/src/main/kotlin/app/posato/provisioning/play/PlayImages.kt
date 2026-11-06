package app.posato.provisioning.play

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private const val ICON_SIDE = 512
private const val MAX_ICON_BYTES = 1024 * 1024
private const val FEATURE_WIDTH = 1024
private const val FEATURE_HEIGHT = 500
private const val MIN_SCREENSHOTS = 2
private const val MAX_SCREENSHOTS = 8
private const val MIN_SCREENSHOT_SIDE = 320
private const val MAX_SCREENSHOT_SIDE = 3840
private const val MAX_ASPECT = 2
private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
private const val IHDR_TYPE_OFFSET = 12
private const val WIDTH_OFFSET = 16
private const val HEIGHT_OFFSET = 20
private const val COLOR_TYPE_OFFSET = 25
private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val INT_BYTES = 4

/** PNG signature, IHDR length and type, then width, height, bit depth, and colour type at fixed offsets. */
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val ALPHA_COLOR_TYPES = setOf(4, 6)

/**
 * Play's graphic asset rules for the images this tool replaces, read from the PNG header alone: the icon 512 x 512 and
 * at most 1024 KB, the feature graphic 1024 x 500 without alpha, and 2 to 8 phone screenshots without alpha, 320 to
 * 3840 pixels a side, the longer side at most twice the shorter.
 */
internal object PlayImages {
    fun icon(
        file: Path,
        name: String,
    ): ImageFile? {
        val image = png(file, name) ?: return null
        ensureInput(image.bytes.size <= MAX_ICON_BYTES) { "$name is larger than Play's 1024 KB" }
        ensureInput(width(image.bytes) == ICON_SIDE && height(image.bytes) == ICON_SIDE) { "$name is not 512 x 512 pixels" }
        return image
    }

    fun featureGraphic(
        file: Path,
        name: String,
    ): ImageFile? {
        val image = png(file, name) ?: return null
        ensureInput(width(image.bytes) == FEATURE_WIDTH && height(image.bytes) == FEATURE_HEIGHT) { "$name is not 1024 x 500 pixels" }
        ensureInput(!hasAlpha(image.bytes)) { "$name has an alpha channel, which Play refuses for a feature graphic" }
        return image
    }

    fun phoneScreenshots(
        directory: Path,
        name: String,
    ): List<ImageFile> {
        if (!directory.isDirectory()) return emptyList()
        val files = readInput { directory.listDirectoryEntries("*.png") }.filter { it.isRegularFile() }.sortedBy { it.name }
        ensureInput(files.size in MIN_SCREENSHOTS..MAX_SCREENSHOTS) { "$name must hold between $MIN_SCREENSHOTS and $MAX_SCREENSHOTS PNG files" }
        return files.map { file ->
            val label = "$name/${file.name}"
            val image = png(file, label) ?: refuseInput("$label could not be read")
            val shorter = minOf(width(image.bytes), height(image.bytes))
            val longer = maxOf(width(image.bytes), height(image.bytes))
            ensureInput(shorter >= MIN_SCREENSHOT_SIDE && longer <= MAX_SCREENSHOT_SIDE) {
                "$label must measure between $MIN_SCREENSHOT_SIDE and $MAX_SCREENSHOT_SIDE pixels on each side"
            }
            ensureInput(longer <= shorter * MAX_ASPECT) { "$label has a longer side more than twice its shorter side" }
            ensureInput(!hasAlpha(image.bytes)) { "$label has an alpha channel, which Play refuses for a screenshot" }
            image
        }
    }

    private fun png(
        file: Path,
        name: String,
    ): ImageFile? {
        if (!file.isRegularFile()) return null
        val bytes = readInput { Files.readAllBytes(file) }
        val signed = bytes.size > COLOR_TYPE_OFFSET && bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
        ensureInput(signed && String(bytes, IHDR_TYPE_OFFSET, INT_BYTES, StandardCharsets.US_ASCII) == "IHDR") { "$name is not a PNG image" }
        ensureInput(bytes.size <= MAX_IMAGE_BYTES) { "$name is larger than Play's 15 MB" }
        return ImageFile(file.name, bytes)
    }

    private fun width(bytes: ByteArray): Int = int(bytes, WIDTH_OFFSET)

    private fun height(bytes: ByteArray): Int = int(bytes, HEIGHT_OFFSET)

    private fun int(
        bytes: ByteArray,
        offset: Int,
    ): Int = (0 until INT_BYTES).fold(0) { value, index -> (value shl BITS_PER_BYTE) or (bytes[offset + index].toInt() and BYTE_MASK) }

    private fun hasAlpha(bytes: ByteArray): Boolean = (bytes[COLOR_TYPE_OFFSET].toInt() and BYTE_MASK) in ALPHA_COLOR_TYPES
}
