package app.posato.provisioning.store

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** The iOS app record every store command acts on. */
const val IOS_BUNDLE_ID = "app.posato.ios"
const val STORE_LOCALE = "en-US"

private val VERSION_PATTERN = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
private const val MAX_WHATS_NEW = 4000
private const val MAX_SCREENSHOTS_PER_SET = 10
private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val HEX_WIDTH = 2
private const val REMEDY = "Correct the input; nothing was sent to App Store Connect."

/** PNG signature, then the IHDR chunk; the colour type byte sits at a fixed offset inside it. */
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private const val COLOR_TYPE_OFFSET = 25
private val ALPHA_COLOR_TYPES = setOf(4, 6)

enum class ReleaseType(
    val option: String,
    val ascName: String,
) {
    AFTER_APPROVAL("after-approval", "AFTER_APPROVAL"),
    MANUAL("manual", "MANUAL"),
    ;

    companion object {
        fun of(option: String): ReleaseType = entries.first { entry -> entry.option == option }
    }
}

/** The two screenshot sets Posato ships, the directory each is read from, and the App Store Connect slot it fills. */
enum class ScreenshotSlot(
    val directory: String,
    val displayType: String,
) {
    IPHONE("iphone-6.9", "APP_IPHONE_67"),
    IPAD("ipad-13", "APP_IPAD_PRO_3GEN_129"),
}

class ScreenshotFile(
    val fileName: String,
    val bytes: ByteArray,
) {
    val checksum: String = ReleaseInputs.md5(bytes)
}

/**
 * Local inputs to a release, checked before any request is made.
 *
 * No message names a path: the inputs usually live under the maintainer's home directory, and the failure travels
 * into an envelope. Each message names the input and what is wrong with it.
 */
object ReleaseInputs {
    fun version(value: String): String {
        ensure(value.matches(VERSION_PATTERN)) { "--version must be a marketing version such as 1.2.0" }
        return value
    }

    fun whatsNew(file: Path): String {
        ensure(file.isRegularFile()) { "The What's New file does not exist or is not a regular file" }
        val text = read { String(Files.readAllBytes(file), StandardCharsets.UTF_8) }.trim()
        ensure(text.isNotEmpty()) { "The What's New file is empty" }
        ensure(text.length <= MAX_WHATS_NEW) { "The What's New text is longer than App Store Connect's $MAX_WHATS_NEW characters" }
        return text
    }

    /** Both sets, each in file-name order, which is the order App Store Connect shows them in. */
    fun screenshots(root: Path): Map<ScreenshotSlot, List<ScreenshotFile>> {
        ensure(root.isDirectory()) { "The screenshots directory does not exist" }
        return ScreenshotSlot.entries.associateWith { slot -> screenshots(root.resolve(slot.directory), slot) }
    }

    fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5")
        .digest(bytes)
        .joinToString("") { byte -> (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_WIDTH, '0') }

    private fun screenshots(
        directory: Path,
        slot: ScreenshotSlot,
    ): List<ScreenshotFile> {
        ensure(directory.isDirectory()) { "The screenshots directory has no ${slot.directory} folder" }
        val files = read { directory.listDirectoryEntries("*.png") }.filter { it.isRegularFile() }.sortedBy { it.name }
        ensure(files.size in 1..MAX_SCREENSHOTS_PER_SET) {
            "The ${slot.directory} folder must hold between 1 and $MAX_SCREENSHOTS_PER_SET PNG files"
        }
        return files.map { file ->
            val bytes = read { Files.readAllBytes(file) }
            ensure(isPng(bytes)) { "${slot.directory}/${file.name} is not a PNG image" }
            ensure(!hasAlpha(bytes)) {
                "${slot.directory}/${file.name} has an alpha channel, which App Store Connect rejects; convert it with " +
                    "`ffmpeg -i <in> -pix_fmt rgb24 <out>`"
            }
            ScreenshotFile(file.name, bytes)
        }
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size > COLOR_TYPE_OFFSET && bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)

    private fun hasAlpha(bytes: ByteArray): Boolean = (bytes[COLOR_TYPE_OFFSET].toInt() and BYTE_MASK) in ALPHA_COLOR_TYPES

    private fun <T> read(block: () -> T): T = try {
        block()
    } catch (exception: IOException) {
        throw ProvisioningException(ErrorCode.RELEASE_INPUT_INVALID, "A release input could not be read.", REMEDY, exception)
    }

    private fun ensure(
        condition: Boolean,
        message: () -> String
    ) {
        if (!condition) throw ProvisioningException(ErrorCode.RELEASE_INPUT_INVALID, "${message()}.", REMEDY)
    }
}
