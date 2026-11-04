package app.posato.provisioning.store

import app.posato.provisioning.asc.StoreBuild
import app.posato.provisioning.model.AppInfoLocalizationResource
import app.posato.provisioning.model.AppInfoResource
import app.posato.provisioning.model.AppStoreVersionResource
import app.posato.provisioning.model.LocalizationResource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

/** One screenshot set of a localization, with how many screenshots it holds in each delivery state. */
private class SetSummary(
    val displayType: String?,
    val deliveryStates: Map<String, Int>,
) {
    val count: Int get() = deliveryStates.values.sum()
}

/**
 * A read-only picture of the iOS app's release state.
 *
 * It prints versions, build numbers, states, and the public What's New text, never a resource identifier: nothing in
 * the report is needed to act on it, and an envelope may be pasted into a record.
 */
class StoreStatus(
    private val services: StoreServices
) {
    /**
     * With [version], also that version in detail, including each localization it holds and each locale in [locales]
     * that it should hold, with the listing fields App Review requires that the localization still lacks.
     */
    fun report(
        version: String?,
        locales: List<String> = emptyList(),
    ): JsonObject {
        val store = services.store
        val app = StoreLookups.app(store)
        val versions = store.versions(app.id)
        val builds = store.builds(app.id)
        val highest = builds.builds.mapNotNull { build -> build.number?.toIntOrNull() }.maxOrNull()
        return buildJsonObject {
            put("app", IOS_BUNDLE_ID)
            put("versions", buildJsonArray { versions.forEach { resource -> add(versionSummary(resource)) } })
            put("builds", buildJsonArray { builds.builds.forEach { build -> add(buildSummary(build)) } })
            put("moreBuilds", builds.hasMore)
            put("nextBuildNumber", (highest ?: 0) + 1)
            if (version != null) {
                val selected = versions.firstOrNull { resource -> resource.attributes.versionString == version }
                put("selected", if (selected == null) missing(version) else details(app.id, selected, versions, locales))
            }
        }
    }

    private fun missing(version: String): JsonObject = buildJsonObject {
        put("version", version)
        put("exists", false)
    }

    private fun details(
        appId: String,
        resource: AppStoreVersionResource,
        versions: List<AppStoreVersionResource>,
        locales: List<String>,
    ): JsonObject {
        val store = services.store
        val build = store.attachedBuild(resource.id)
        val localizations = store.localizations(resource.id)
        val sets = localizations.associate { localization -> localization.attributes.locale to screenshotSets(localization.id) }
        val english = localizations.firstOrNull { it.attributes.locale == STORE_LOCALE }
        val update = versions.any { other -> other.id != resource.id && other.state in RELEASED_VERSION_STATES }
        val info = appInfo(appId, resource)
        val infoLocalizations = info?.let { services.appInfo.localizations(it.id) }.orEmpty()
        val wanted = (localizations.mapNotNull { it.attributes.locale } + locales).distinct()
        val listings = wanted.map { locale ->
            listing(
                locale,
                localizations.firstOrNull { it.attributes.locale == locale },
                infoLocalizations.firstOrNull { it.attributes.locale == locale },
                sets[locale].orEmpty(),
                update,
            )
        }
        return buildJsonObject {
            put("version", resource.attributes.versionString)
            put("exists", true)
            put("state", resource.state)
            put("releaseType", resource.attributes.releaseType)
            put(
                "build",
                build?.let {
                    buildJsonObject {
                        put("build", it.attributes.version)
                        put("processingState", it.attributes.processingState)
                    }
                } ?: JsonNull,
            )
            put("locale", STORE_LOCALE)
            put("whatsNew", english?.attributes?.whatsNew)
            put("screenshotSets", JsonArray(sets[STORE_LOCALE].orEmpty().map(::setSummary)))
            put("localizations", JsonArray(listings))
            put("localizationsComplete", listings.all { listing -> listing.getValue("missing").jsonArray.isEmpty() })
        }
    }

    /**
     * The App Information that goes with [version]: the one in the same state, such as the editable copy a version in
     * preparation brings, or otherwise the editable one.
     */
    private fun appInfo(
        appId: String,
        version: AppStoreVersionResource,
    ): AppInfoResource? {
        val infos = services.appInfo.appInfos(appId)
        return infos.firstOrNull { info -> info.currentState == version.state }
            ?: infos.firstOrNull { info -> info.currentState in EDITABLE_VERSION_STATES }
    }

    /**
     * Which required listing fields one locale lacks. What's New is required only on an update, and both screenshot
     * sets Posato ships must hold at least one screenshot.
     */
    private fun listing(
        locale: String,
        version: LocalizationResource?,
        info: AppInfoLocalizationResource?,
        sets: List<SetSummary>,
        update: Boolean,
    ): JsonObject {
        val fields = listOfNotNull(
            "name" to info?.attributes?.name,
            "description" to version?.attributes?.description,
            "keywords" to version?.attributes?.keywords,
            "supportUrl" to version?.attributes?.supportUrl,
            "privacyPolicyUrl" to info?.attributes?.privacyPolicyUrl,
            if (update) "whatsNew" to version?.attributes?.whatsNew else null,
        )
        val missingFields = fields.filter { (_, value) -> value.isNullOrBlank() }.map { (name, _) -> name }
        val missingSets = ScreenshotSlot.entries
            .filter { slot -> sets.none { set -> set.displayType == slot.displayType && set.count > 0 } }
            .map { slot -> "screenshots ${slot.displayType}" }
        val missing = missingFields + missingSets
        return buildJsonObject {
            put("locale", locale)
            put("exists", version != null)
            put("complete", missing.isEmpty())
            put("missing", buildJsonArray { missing.forEach { name -> add(name) } })
        }
    }

    private fun screenshotSets(localizationId: String): List<SetSummary> = services.screenshots.sets(localizationId).map { set ->
        val screenshots = services.screenshots.screenshots(set.id)
        SetSummary(set.attributes.screenshotDisplayType, screenshots.groupingBy { it.deliveryState ?: "UNKNOWN" }.eachCount())
    }

    private fun setSummary(set: SetSummary): JsonObject = buildJsonObject {
        put("displayType", set.displayType)
        put("count", set.count)
        put("deliveryStates", buildJsonObject { set.deliveryStates.forEach { (state, count) -> put(state, count) } })
    }

    private fun versionSummary(resource: AppStoreVersionResource): JsonObject = buildJsonObject {
        put("version", resource.attributes.versionString)
        put("state", resource.state)
        put("releaseType", resource.attributes.releaseType)
    }

    private fun buildSummary(build: StoreBuild): JsonObject = buildJsonObject {
        put("build", build.number)
        put("version", build.version)
        put("processingState", build.processingState)
        put("uploadedDate", build.uploadedDate)
    }
}
