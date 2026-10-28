package app.posato.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * The language lists that cannot read [PosatoLocales] name exactly its languages. A language missing from a list
 * ships translated text that the system never offers: I18N-001 left Polish out of the Mac app's Info.plist, so the
 * Language & Region settings could not choose Polish for Posato until review.
 */
abstract class VerifyLocaleLists : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val listFiles: ConfigurableFileCollection

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = repositoryDirectory.get().asFile
        val declared = PosatoLocales.DECLARED_CODES.toSet()
        val findings = mutableListOf<String>()

        fun expect(
            path: String,
            found: Set<String>,
        ) {
            if (found != declared) findings += "$path lists ${found.sorted()}, expected ${declared.sorted()}"
        }
        INFO_PLISTS.forEach { path -> expect(path, bundleLocalizations(File(root, path).readText())) }
        val project = File(root, XCODE_PROJECT).readText()
        expect("$XCODE_PROJECT knownRegions", knownRegions(project) - "Base")
        val androidConfig = File(root, ANDROID_LOCALES).readText()
        expect(ANDROID_LOCALES, ANDROID_LOCALE.findAll(androidConfig).map { it.groupValues[1] }.toSet())
        val englishPages = pages(File(root, WEBSITE_PAGES))
        PosatoLocales.SHIPPED.forEach { locale ->
            val code = locale.code
            if ("path = $code.lproj/InfoPlist.strings;" !in project) {
                findings += "$XCODE_PROJECT does not reference $code.lproj/InfoPlist.strings"
            }
            val localized = File(root, "$WEBSITE_PAGES/$code")
            (englishPages - pages(localized)).sorted().forEach { findings += "$WEBSITE_PAGES/$code has no $it" }
            if (!File(localized, "chrome.json").isFile) findings += "$WEBSITE_PAGES/$code has no chrome.json"
            if (!File(root, "docs/store/${locale.storeDirectory}/listing.md").isFile) {
                findings += "docs/store/${locale.storeDirectory} has no listing.md"
            }
        }
        if (findings.isNotEmpty()) {
            throw GradleException(
                findings.joinToString(prefix = "Language lists do not match PosatoLocales:\n", separator = "\n") { "- $it" } +
                    "\nSee docs/development/localization.md.",
            )
        }
    }

    private fun bundleLocalizations(plist: String): Set<String> {
        val array = BUNDLE_LOCALIZATIONS.find(plist)?.groupValues?.get(1) ?: return emptySet()
        return PLIST_STRING.findAll(array).map { it.groupValues[1] }.toSet()
    }

    private fun knownRegions(project: String): Set<String> {
        val list = KNOWN_REGIONS.find(project)?.groupValues?.get(1) ?: return emptySet()
        return list.split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }.toSet()
    }

    private fun pages(directory: File): Set<String> =
        directory.listFiles { file -> file.isFile && file.extension == "astro" }.orEmpty().map { it.name }.toSet()

    companion object {
        /** The files this task reads, for its inputs. */
        val LIST_FILES: List<String> by lazy { INFO_PLISTS + XCODE_PROJECT + ANDROID_LOCALES }

        private val INFO_PLISTS = listOf("iosApp/iosApp/Info.plist", "macosHelper/Resources/HelperInfo.plist")
        private const val XCODE_PROJECT = "iosApp/iosApp.xcodeproj/project.pbxproj"
        private const val ANDROID_LOCALES = "androidApp/src/main/res/xml/locales_config.xml"
        private const val WEBSITE_PAGES = "website/src/pages"
        private val BUNDLE_LOCALIZATIONS = Regex("""<key>CFBundleLocalizations</key>\s*<array>([\s\S]*?)</array>""")
        private val PLIST_STRING = Regex("""<string>([^<]+)</string>""")
        private val KNOWN_REGIONS = Regex("""knownRegions = \(([^)]*)\);""")
        private val ANDROID_LOCALE = Regex("""<locale android:name="([^"]+)"""")
    }
}
