package app.posato.provisioning.play

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** A Play language code such as en-US, pl-PL, pt-BR, es-419, or sr-Latn. */
private val LOCALE_PATTERN = Regex("[a-z]{2,3}(-[A-Z]{2}|-[0-9]{3}|-[A-Z][a-z]{3})?")
private const val MAX_TITLE = 30
private const val MAX_SHORT_DESCRIPTION = 80
private const val MAX_FULL_DESCRIPTION = 4000
private const val MAX_RELEASE_NOTES = 500
private const val REMEDY = "Correct the input; nothing was sent to Google Play."
private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

class ImageFile(
    val fileName: String,
    val bytes: ByteArray,
)

/**
 * One locale of the store listing. An absent image is left as Play holds it; a present one replaces every image of
 * its type, so a phone set is always exactly the files given.
 */
class LocaleListing(
    val language: String,
    val title: String,
    val shortDescription: String,
    val fullDescription: String,
    val icon: ImageFile?,
    val featureGraphic: ImageFile?,
    val phoneScreenshots: List<ImageFile>,
)

/**
 * Local inputs of the Play commands, checked completely before an edit is opened, so a refusal never leaves a
 * half-replaced listing or an abandoned edit. No message names a path; each names the file relative to the input
 * directory and the limit it breaks.
 */
object PlayInputs {
    fun bundle(file: Path): ByteArray {
        val bytes = bytes(file, "--aab")
        ensureInput(bytes.size > ZIP_SIGNATURE.size && bytes.copyOfRange(0, ZIP_SIGNATURE.size).contentEquals(ZIP_SIGNATURE)) {
            "--aab is not an Android App Bundle (a zip archive)"
        }
        return bytes
    }

    fun mapping(file: Path): ByteArray = bytes(file, "--mapping").also { bytes -> ensureInput(bytes.isNotEmpty()) { "--mapping is empty" } }

    /** `<locale>.txt` files in file-name order, each at most Play's 500 characters. */
    fun releaseNotes(directory: Path): List<LocalizedText> {
        ensureInput(directory.isDirectory()) { "--notes-dir is not a directory" }
        val files = readInput { directory.listDirectoryEntries("*.txt") }.filter { it.isRegularFile() }.sortedBy { it.name }
        ensureInput(files.isNotEmpty()) { "--notes-dir holds no <locale>.txt file" }
        return files.map { file ->
            val language = file.name.removeSuffix(".txt")
            ensureInput(language.matches(LOCALE_PATTERN)) { "${file.name} is not named after a Play language code such as en-US" }
            LocalizedText(language, text(file, file.name, MAX_RELEASE_NOTES))
        }
    }

    fun listings(root: Path): List<LocaleListing> {
        ensureInput(root.isDirectory()) { "--dir is not a directory" }
        val directories = readInput { root.listDirectoryEntries() }.filter { it.isDirectory() && !it.name.startsWith(".") }.sortedBy { it.name }
        ensureInput(directories.isNotEmpty()) { "--dir holds no locale directory such as en-US" }
        return directories.map(::listing)
    }

    private fun listing(directory: Path): LocaleListing {
        val language = directory.name
        ensureInput(language.matches(LOCALE_PATTERN)) { "$language is not a Play language code such as en-US or pl-PL" }
        return LocaleListing(
            language = language,
            title = text(directory.resolve("title.txt"), "$language/title.txt", MAX_TITLE),
            shortDescription = text(directory.resolve("short-description.txt"), "$language/short-description.txt", MAX_SHORT_DESCRIPTION),
            fullDescription = text(directory.resolve("full-description.txt"), "$language/full-description.txt", MAX_FULL_DESCRIPTION),
            icon = PlayImages.icon(directory.resolve("icon.png"), "$language/icon.png"),
            featureGraphic = PlayImages.featureGraphic(directory.resolve("feature-graphic.png"), "$language/feature-graphic.png"),
            phoneScreenshots = PlayImages.phoneScreenshots(directory.resolve("phone"), "$language/phone"),
        )
    }

    private fun text(
        file: Path,
        name: String,
        limit: Int,
    ): String {
        ensureInput(file.isRegularFile()) { "$name does not exist or is not a regular file" }
        val text = readInput { String(Files.readAllBytes(file), StandardCharsets.UTF_8) }.trim()
        ensureInput(text.isNotEmpty()) { "$name is empty" }
        ensureInput(text.codePointCount(0, text.length) <= limit) { "$name is longer than Play's $limit characters" }
        return text
    }

    private fun bytes(
        file: Path,
        option: String,
    ): ByteArray {
        ensureInput(file.isRegularFile()) { "$option does not exist or is not a regular file" }
        return readInput { Files.readAllBytes(file) }
    }
}

internal fun <T> readInput(block: () -> T): T = try {
    block()
} catch (exception: IOException) {
    throw ProvisioningException(ErrorCode.RELEASE_INPUT_INVALID, "A Google Play input could not be read.", REMEDY, exception)
}

internal fun ensureInput(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) refuseInput(message())
}

internal fun refuseInput(message: String): Nothing = throw ProvisioningException(ErrorCode.RELEASE_INPUT_INVALID, "$message.", REMEDY)
