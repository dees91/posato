package app.posato.provisioning.store

import app.posato.provisioning.asc.StoreBuild
import app.posato.provisioning.model.AppStoreVersionResource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A read-only picture of the iOS app's release state.
 *
 * It prints versions, build numbers, states, and the public What's New text, never a resource identifier: nothing in
 * the report is needed to act on it, and an envelope may be pasted into a record.
 */
class StoreStatus(
    private val services: StoreServices
) {
    fun report(version: String?): JsonObject {
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
                put("selected", if (selected == null) missing(version) else details(selected))
            }
        }
    }

    private fun missing(version: String): JsonObject = buildJsonObject {
        put("version", version)
        put("exists", false)
    }

    private fun details(resource: AppStoreVersionResource): JsonObject {
        val store = services.store
        val build = store.attachedBuild(resource.id)
        val localization = store.localizations(resource.id).firstOrNull { it.attributes.locale == STORE_LOCALE }
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
            put("whatsNew", localization?.attributes?.whatsNew)
            put("screenshotSets", localization?.let { screenshotSets(it.id) } ?: JsonArray(emptyList()))
        }
    }

    private fun screenshotSets(localizationId: String): JsonArray = buildJsonArray {
        services.screenshots.sets(localizationId).forEach { set ->
            val screenshots = services.screenshots.screenshots(set.id)
            add(
                buildJsonObject {
                    put("displayType", set.attributes.screenshotDisplayType)
                    put("count", screenshots.size)
                    put(
                        "deliveryStates",
                        buildJsonObject {
                            screenshots.groupingBy { it.deliveryState ?: "UNKNOWN" }.eachCount().forEach { (state, count) -> put(state, count) }
                        },
                    )
                },
            )
        }
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
