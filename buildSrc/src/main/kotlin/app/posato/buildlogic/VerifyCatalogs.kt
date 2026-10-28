package app.posato.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Properties

/**
 * Each shipped language's catalog must name every English string and plural. A string keeps the same placeholders in
 * the same order. A plural carries every CLDR category of its language, and each of its items may use only
 * placeholders that some English item of that plural already uses, so a singular can name a count the English
 * singular writes as a digit. A number of 1000 or more that English writes appears in the translation, grouped as the
 * language writes it (I18N-001 shipped `1,024` in Polish until review).
 */
abstract class VerifyCatalogs : DefaultTask() {
    /**
     * Translated path template, with `{code}` for the language code, to the English catalog's path. An empty English
     * path marks Info.plist strings, which have no English file: each language only needs the file, without blanks.
     */
    @get:Input
    abstract val families: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFiles: ConfigurableFileCollection

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        check(PosatoLocales.POLISH.format(NUMBER_FIXTURE) == "65 536" && PosatoLocales.POLISH.format(SMALL_FIXTURE) == "1024") {
            "The digit grouping no longer writes its own fixture."
        }
        val root = repositoryDirectory.get().asFile
        val findings = families.get().toSortedMap().flatMap { (template, englishPath) ->
            PosatoLocales.SHIPPED.flatMap { locale ->
                val path = template.replace("{code}", locale.code)
                val translated = File(root, path)
                when {
                    !translated.isFile -> listOf("Missing ${locale.code} catalog $path")
                    englishPath.isEmpty() -> Catalog.read(translated).blanks().map { "$path: $it is blank" }
                    else -> compare(Catalog.read(File(root, englishPath)), Catalog.read(translated), locale).map { "$path: $it" }
                }
            }
        }
        if (findings.isNotEmpty()) {
            throw GradleException(
                findings.joinToString(prefix = "Translated catalogs do not match the English ones:\n", separator = "\n") { "- $it" } +
                    "\nSee docs/development/localization.md.",
            )
        }
    }

    private fun compare(
        english: Catalog,
        translated: Catalog,
        locale: PosatoLocale,
    ): List<String> {
        val findings = mutableListOf<String>()
        (english.strings.keys - translated.strings.keys).sorted().forEach { findings += "missing string $it" }
        (translated.strings.keys - english.strings.keys).sorted().forEach { findings += "string $it has no English key" }
        english.strings.keys.intersect(translated.strings.keys).sorted().forEach { name ->
            val source = english.strings.getValue(name)
            val target = translated.strings.getValue(name)
            if (target.isBlank()) findings += "string $name is blank"
            if (placeholders(source) != placeholders(target)) {
                findings += "string $name placeholders ${placeholders(source)} differ from ${placeholders(target)}"
            }
            findings += numbers("string $name", largeNumbers(source), target, locale)
        }
        (english.plurals.keys - translated.plurals.keys).sorted().forEach { findings += "missing plural $it" }
        (translated.plurals.keys - english.plurals.keys).sorted().forEach { findings += "plural $it has no English key" }
        english.plurals.keys.intersect(translated.plurals.keys).sorted().forEach { name ->
            findings += comparePlural(name, english.plurals.getValue(name), translated.plurals.getValue(name), locale)
        }
        return findings
    }

    private fun comparePlural(
        name: String,
        english: Map<String, String>,
        translated: Map<String, String>,
        locale: PosatoLocale,
    ): List<String> {
        val findings = mutableListOf<String>()
        (english.keys + locale.pluralQuantities - translated.keys).sorted().forEach { findings += "plural $name is missing quantity $it" }
        val allowed = english.values.flatMap(::placeholders).toSet()
        val sharedNumbers = english.values.map(::largeNumbers).reduce { common, next -> common intersect next }
        translated.forEach { (quantity, value) ->
            if (value.isBlank()) findings += "plural $name quantity $quantity is blank"
            val extra = placeholders(value).toSet() - allowed
            if (extra.isNotEmpty()) findings += "plural $name quantity $quantity uses $extra, which the English plural does not"
            findings += numbers("plural $name quantity $quantity", sharedNumbers, value, locale)
        }
        return findings
    }

    /** Each English number must appear grouped as [locale] writes it, and the translation adds no other one. */
    private fun numbers(
        label: String,
        english: Set<Long>,
        translated: String,
        locale: PosatoLocale,
    ): List<String> {
        val written = NUMBER.findAll(translated).map { it.value }.filter { value(it) >= LARGE }.toList()
        val missing = english.filter { locale.format(it) !in written }.map { "$label must write $it as \"${visible(locale.format(it))}\"" }
        val unknown = written.filter { value(it) !in english }.map { "$label writes ${visible(it)}, which English does not" }
        return missing + unknown
    }

    private fun largeNumbers(text: String): Set<Long> = NUMBER.findAll(text).map { value(it.value) }.filter { it >= LARGE }.toSet()

    private fun value(written: String): Long = written.filter(Char::isDigit).toLong()

    private fun visible(text: String): String = text.replace(" ", "<U+00A0>").replace(" ", "<U+202F>")

    private fun placeholders(value: String): List<String> = PLACEHOLDER.findAll(value).map { it.value }.toList()

    private class Catalog(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    ) {
        fun blanks(): List<String> = strings.filterValues { it.isBlank() }.keys.sorted()

        companion object {
            fun read(file: File): Catalog {
                val text = file.readText()
                return when (file.extension) {
                    "xml" -> readXml(text)
                    "strings" -> Catalog(unique(APPLE_STRING.findAll(text).map { it.groupValues[1] to it.groupValues[2] }), emptyMap())
                    "properties" -> Catalog(readProperties(text), emptyMap())
                    else -> throw GradleException("Unknown catalog format ${file.name}")
                }
            }

            private fun readProperties(text: String): Map<String, String> {
                val properties = Properties().apply { load(text.reader()) }
                return properties.stringPropertyNames().associateWith(properties::getProperty)
            }

            private fun readXml(text: String): Catalog {
                val strings = unique(XML_STRING.findAll(text).map { it.groupValues[1] to it.groupValues[2] })
                val plurals = linkedMapOf<String, Map<String, String>>()
                PLURAL.findAll(text).forEach { match ->
                    val name = match.groupValues[1]
                    val items = linkedMapOf<String, String>()
                    ITEM.findAll(match.groupValues[2]).forEach { item ->
                        val quantity = item.groupValues[1]
                        if (items.put(quantity, item.groupValues[2]) != null) {
                            throw GradleException("Duplicate quantity $quantity in plural $name")
                        }
                    }
                    if (plurals.put(name, items) != null) throw GradleException("Duplicate plural $name")
                }
                return Catalog(strings, plurals)
            }

            private fun unique(entries: Sequence<Pair<String, String>>): Map<String, String> {
                val strings = linkedMapOf<String, String>()
                entries.forEach { (name, value) ->
                    if (strings.put(name, value) != null) throw GradleException("Duplicate string $name")
                }
                return strings
            }
        }
    }

    private companion object {
        val XML_STRING: Regex = Regex("""<string name="([^"]+)">([\s\S]*?)</string>""")
        val APPLE_STRING: Regex = Regex("""^\s*"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;""", RegexOption.MULTILINE)
        val PLURAL: Regex = Regex("""<plurals name="([^"]+)">([\s\S]*?)</plurals>""")
        val ITEM: Regex = Regex("""<item quantity="([^"]+)">([\s\S]*?)</item>""")
        val PLACEHOLDER: Regex = Regex("""%(?:\d+\$)?(?:@|[A-Za-z])""")

        // A grouped number, with any separator a shipped language uses, or a plain run of digits.
        val NUMBER: Regex = Regex("""\d{1,3}(?:[.,   ]\d{3})+(?!\d)|\d+""")
        const val LARGE: Long = 1000
        const val NUMBER_FIXTURE: Long = 65_536
        const val SMALL_FIXTURE: Long = 1024
    }
}
