package app.posato.desktop

import java.util.Locale

/**
 * The Mac app follows a per-app language from its own preferences domain. The JVM otherwise
 * keeps the system locale, so Compose resources and the Rosetta dialog would ignore that choice.
 * No new field is added to the root daemon protocol; the helper reads the same domain itself.
 */
internal object MacAppLanguage {
    private const val BUNDLE_ID = "app.posato.macos"

    fun apply() {
        val tag = preferredTag() ?: return
        Locale.setDefault(Locale.forLanguageTag(tag.replace('_', '-')))
    }

    private fun preferredTag(): String? {
        val process = ProcessBuilder("/usr/bin/defaults", "read", BUNDLE_ID, "AppleLanguages")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) return null
        return firstEntry(text)
    }

    /** The first entry of the array `defaults` prints, such as `(pl, "en-US")`, which quotes only some entries. */
    private fun firstEntry(text: String): String? = text.trim()
        .removePrefix("(")
        .removeSuffix(")")
        .split(',')
        .firstOrNull()
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() }
}
