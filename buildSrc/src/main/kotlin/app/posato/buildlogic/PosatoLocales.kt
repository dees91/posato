package app.posato.buildlogic

/**
 * A language Posato ships besides English. [code] names its resource directories (`values-pl`, `pl.lproj`,
 * `website/src/pages/pl`), [storeDirectory] its App Store listing under `docs/store`. [pluralQuantities] are the
 * CLDR plural categories its counts use. A number of 1000 or more groups its digits with [groupingSeparator] once
 * it has at least [minimumGroupingDigits] digits before the first separator, as CLDR writes it.
 */
data class PosatoLocale(
    val code: String,
    val storeDirectory: String,
    val pluralQuantities: Set<String>,
    val groupingSeparator: Char,
    val minimumGroupingDigits: Int,
) {
    /** [value] as this language writes it in prose. */
    fun format(value: Long): String = group(value, groupingSeparator, minimumGroupingDigits)

    companion object {
        /** English, the source catalog every translation is compared with. */
        val ENGLISH: PosatoLocale = PosatoLocale("en", "en-US", setOf("one", "other"), ',', 1)

        internal fun group(
            value: Long,
            separator: Char,
            minimumGroupingDigits: Int,
        ): String {
            val digits = value.toString()
            if (digits.length < GROUP + minimumGroupingDigits) return digits
            return digits.reversed().chunked(GROUP).joinToString(separator.toString()).reversed()
        }

        private const val GROUP = 3
    }
}

/**
 * The languages Posato ships. Adding one starts with an entry here; `docs/development/localization.md` lists every
 * other file a new language needs, and `verifyCatalogs` and `verifyLocaleLists` name whatever is still missing.
 */
object PosatoLocales {
    val POLISH: PosatoLocale = PosatoLocale("pl", "pl-PL", setOf("one", "few", "many", "other"), NO_BREAK_SPACE, 2)

    val GERMAN: PosatoLocale = PosatoLocale("de", "de-DE", setOf("one", "other"), '.', 1)
    val FRENCH: PosatoLocale = PosatoLocale("fr", "fr-FR", setOf("one", "many", "other"), NARROW_NO_BREAK_SPACE, 1)
    val SPANISH: PosatoLocale = PosatoLocale("es", "es-ES", setOf("one", "many", "other"), '.', 2)
    val PORTUGUESE: PosatoLocale = PosatoLocale("pt", "pt-BR", setOf("one", "many", "other"), '.', 1)

    val SHIPPED: List<PosatoLocale> = listOf(POLISH, GERMAN, FRENCH, SPANISH, PORTUGUESE)

    /** Every language code the apps declare, English first, as Info.plist and the Android locale config list them. */
    val DECLARED_CODES: List<String> = listOf(PosatoLocale.ENGLISH.code) + SHIPPED.map { it.code }
}

private const val NO_BREAK_SPACE = ' '
private const val NARROW_NO_BREAK_SPACE = ' '
