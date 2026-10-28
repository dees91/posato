package app.posato.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The Polish catalog must name every English string and plural. A string keeps the same placeholders in the same
 * order. A Polish plural may add quantities, and each of its items may use only placeholders that some English item
 * of that plural already uses, so a Polish singular can name a count the English singular writes as a digit.
 */
abstract class VerifyPolishCatalog : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val englishCatalog: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val polishCatalog: RegularFileProperty

    @TaskAction
    fun verify() {
        val english = Catalog.read(englishCatalog.get().asFile.readText())
        val polish = Catalog.read(polishCatalog.get().asFile.readText())
        val findings = compare(english, polish)
        if (findings.isNotEmpty()) {
            throw GradleException(
                findings.joinToString(
                    prefix = "The Polish catalog does not match the English one:\n",
                    separator = "\n",
                ) { "- $it" },
            )
        }
    }

    private fun compare(
        english: Catalog,
        polish: Catalog,
    ): List<String> {
        val findings = mutableListOf<String>()
        val englishStrings = english.strings.keys
        val polishStrings = polish.strings.keys
        (englishStrings - polishStrings).sorted().forEach { findings += "Missing Polish string $it" }
        (polishStrings - englishStrings).sorted().forEach { findings += "Polish string $it has no English key" }
        englishStrings.intersect(polishStrings).sorted().forEach { name ->
            val translated = polish.strings.getValue(name)
            if (translated.isBlank()) findings += "Polish string $name is blank"
            val source = placeholders(english.strings.getValue(name))
            val target = placeholders(translated)
            if (source != target) findings += "String $name placeholders $source differ from Polish $target"
        }
        val englishPlurals = english.plurals.keys
        val polishPlurals = polish.plurals.keys
        (englishPlurals - polishPlurals).sorted().forEach { findings += "Missing Polish plural $it" }
        (polishPlurals - englishPlurals).sorted().forEach { findings += "Polish plural $it has no English key" }
        englishPlurals.intersect(polishPlurals).sorted().forEach { name ->
            findings += comparePlural(name, english.plurals.getValue(name), polish.plurals.getValue(name))
        }
        return findings
    }

    private fun comparePlural(
        name: String,
        english: Map<String, String>,
        polish: Map<String, String>,
    ): List<String> {
        val findings = mutableListOf<String>()
        (english.keys - polish.keys).sorted().forEach { findings += "Plural $name is missing Polish quantity $it" }
        val allowed = english.values.flatMap(::placeholders).toSet()
        polish.forEach { (quantity, value) ->
            if (value.isBlank()) findings += "Plural $name quantity $quantity is blank"
            val used = placeholders(value).toSet()
            val extra = used - allowed
            if (extra.isNotEmpty()) findings += "Plural $name quantity $quantity uses $extra, which the English plural does not"
        }
        return findings
    }

    private fun placeholders(value: String): List<String> = PLACEHOLDER.findAll(value).map { it.value }.toList()

    private class Catalog(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    ) {
        companion object {
            fun read(text: String): Catalog {
                val strings = linkedMapOf<String, String>()
                STRING.findAll(text).forEach { match ->
                    val name = match.groupValues[1]
                    if (strings.put(name, match.groupValues[2]) != null) {
                        throw GradleException("Duplicate string $name")
                    }
                }
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
        }
    }

    private companion object {
        val STRING: Regex = Regex("""<string name="([^"]+)">([\s\S]*?)</string>""")
        val PLURAL: Regex = Regex("""<plurals name="([^"]+)">([\s\S]*?)</plurals>""")
        val ITEM: Regex = Regex("""<item quantity="([^"]+)">([\s\S]*?)</item>""")
        val PLACEHOLDER: Regex = Regex("""%(?:\d+\$)?[A-Za-z]""")
    }
}
