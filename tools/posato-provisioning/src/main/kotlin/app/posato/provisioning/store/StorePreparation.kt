package app.posato.provisioning.store

import app.posato.provisioning.asc.StoreBuild
import app.posato.provisioning.asc.StoreClient
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.model.AppStoreVersionResource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val UNCHANGED = "unchanged"

/** The version states in which App Store Connect accepts metadata, build, and screenshot changes. */
private val EDITABLE_VERSION_STATES = setOf(
    "PREPARE_FOR_SUBMISSION",
    "DEVELOPER_REJECTED",
    "REJECTED",
    "METADATA_REJECTED",
    "INVALID_BINARY",
)

class PrepareRequest(
    val version: String,
    val build: Int,
    val whatsNew: String,
    val releaseType: ReleaseType,
    val screenshots: Map<ScreenshotSlot, List<ScreenshotFile>>?,
)

/**
 * Brings one iOS App Store version to the state a release needs, changing only what differs.
 *
 * The build is checked before anything is written, so a build that is missing or still processing leaves the account
 * untouched, and an existing version outside the editable states (waiting for or in review, pending release, or
 * released) is refused before the first write. A second run with the same inputs is intended to issue only reads.
 * A new version copies its description, keywords, review details, and screenshots from the previous version; this
 * command then overwrites the What's New text and, when asked, the two screenshot sets.
 */
class StorePreparation(
    private val services: StoreServices
) {
    fun prepare(request: PrepareRequest): JsonObject {
        val store = services.store
        val app = StoreLookups.app(store)
        val build = validBuild(store, app.id, request)
        val existing = editableVersion(store, app.id, request.version)
        val version = existing ?: store.createVersion(app.id, request.version, request.releaseType.ascName)
        val releaseType = when {
            existing == null -> "set"
            existing.attributes.releaseType == request.releaseType.ascName -> UNCHANGED
            else -> "updated".also { store.updateReleaseType(version.id, request.releaseType.ascName) }
        }
        val buildOutcome = if (store.attachedBuild(version.id)?.id == build.id) {
            UNCHANGED
        } else {
            "attached".also { store.attachBuild(version.id, build.id) }
        }
        val localization = StoreLookups.localization(store, version.id)
        val whatsNew = if (localization.attributes.whatsNew?.trim() == request.whatsNew) {
            UNCHANGED
        } else {
            "updated".also { store.updateWhatsNew(localization.id, request.whatsNew) }
        }
        val screenshots = request.screenshots?.let { sets -> replaceScreenshots(localization.id, sets) }
        return buildJsonObject {
            put("version", request.version)
            put("appStoreVersion", if (existing == null) "created" else UNCHANGED)
            put("releaseType", releaseType)
            put("build", buildOutcome)
            put("whatsNew", whatsNew)
            put("screenshots", screenshots ?: buildJsonArray { })
        }
    }

    /** The existing version, or `null` when there is none; a version this command may not change stops the run. */
    private fun editableVersion(
        store: StoreClient,
        appId: String,
        version: String,
    ): AppStoreVersionResource? {
        val existing = StoreLookups.version(store, appId, version)
        if (existing != null && existing.state !in EDITABLE_VERSION_STATES) throw notEditable(version, existing.state)
        return existing
    }

    private fun notEditable(
        version: String,
        state: String?,
    ): ProvisioningException = ProvisioningException(
        ErrorCode.VERSION_NOT_EDITABLE,
        "Version $version is ${state ?: "in an unknown state"}, which App Store Connect does not let this command change.",
        "Nothing was changed. `store status --version $version` shows the state; a version in review or released " +
            "needs a new version number, or a rejection or developer removal in App Store Connect first.",
    )

    private fun validBuild(
        store: StoreClient,
        appId: String,
        request: PrepareRequest,
    ): StoreBuild {
        val build = store.builds(appId, request.build).builds
            .firstOrNull { candidate -> candidate.number == request.build.toString() && candidate.version == request.version }
            ?: throw ProvisioningException(
                ErrorCode.BUILD_MISSING,
                "App Store Connect has no build ${request.build} of version ${request.version}.",
                "Upload it with `xcrun altool --upload-app`; `store status` lists the builds App Store Connect holds.",
            )
        if (!build.valid) {
            throw ProvisioningException(
                ErrorCode.BUILD_NOT_READY,
                "Build ${request.build} of version ${request.version} is ${build.processingState ?: "in an unknown state"}, not VALID.",
                "Wait until App Store Connect finishes processing it, then rerun; an INVALID build needs a new upload.",
            )
        }
        return build
    }

    private fun replaceScreenshots(
        localizationId: String,
        sets: Map<ScreenshotSlot, List<ScreenshotFile>>,
    ) = buildJsonArray {
        val outcomes = sets.mapValues { (slot, files) -> services.replacement.replace(localizationId, slot, files) }
        services.replacement.awaitDelivery(outcomes.values.map { replacement -> replacement.setId })
        outcomes.forEach { (slot, replacement) ->
            add(
                buildJsonObject {
                    put("displayType", slot.displayType)
                    put("count", sets.getValue(slot).size)
                    put("outcome", replacement.outcome.name.lowercase())
                },
            )
        }
    }
}
