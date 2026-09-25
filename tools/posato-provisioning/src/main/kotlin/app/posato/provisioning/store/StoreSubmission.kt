package app.posato.provisioning.store

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.model.AppStoreVersionResource
import app.posato.provisioning.model.ReviewSubmissionItemResource
import app.posato.provisioning.model.ReviewSubmissionResource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DRAFT_STATE = "READY_FOR_REVIEW"
private const val UNRESOLVED_STATE = "UNRESOLVED_ISSUES"
private val SUBMITTED_STATES = setOf("WAITING_FOR_REVIEW", "IN_REVIEW")
private const val COMPLETE = "COMPLETE"

/**
 * Version states App Review accepts a submission for. Any other state (processing, pending release, released,
 * replaced) is refused before a submission is created, so a rerun cannot leave an empty draft behind.
 */
private val SUBMITTABLE_VERSION_STATES = setOf(
    "PREPARE_FOR_SUBMISSION",
    "READY_FOR_REVIEW",
    "DEVELOPER_REJECTED",
    "REJECTED",
    "METADATA_REJECTED",
    "INVALID_BINARY",
)

private const val REJECTED_ITEM_STATE = "REJECTED"

private class OpenSubmission(
    val resource: ReviewSubmissionResource,
    val items: List<ReviewSubmissionItemResource>,
) {
    val state: String? get() = resource.attributes.state

    val versionIds: Set<String> get() = items.mapNotNull { item -> item.versionId }.toSet()

    /** Whether it holds anything besides this version, including items that are not versions at all. */
    fun holdsOtherThan(versionId: String): Boolean = items.any { item -> item.versionId != versionId }

    /** The version's items App Review rejected and that are not yet marked as corrected. */
    fun unresolvedItems(versionId: String): List<ReviewSubmissionItemResource> = items.filter { item ->
        item.versionId == versionId &&
            item.attributes.resolved != true &&
            (item.attributes.state == REJECTED_ITEM_STATE || item.attributes.resolved == false)
    }
}

/**
 * Submits one iOS App Store version to App Review.
 *
 * A submission is assembled in three writes (create, add the version, mark it submitted), and a run can stop between
 * any two. A rerun therefore reuses an unsubmitted submission rather than creating a second one, which App Store
 * Connect would refuse, and it does nothing when the version is already waiting for or in review. After a rejection
 * the submission holding the version stays open with unresolved issues; its rejected item is marked resolved and the
 * submission is resubmitted, never replaced. A reusable submission that also holds anything else (another version,
 * an App Event, a custom product page) is refused before any write, so nothing is submitted that was not asked for.
 */
class StoreSubmission(
    private val services: StoreServices
) {
    fun submit(version: String): JsonObject {
        val store = services.store
        val app = StoreLookups.app(store)
        val resource = StoreLookups.requireVersion(store, app.id, version)
        val open = openSubmissions(app.id)
        val submitted = open.firstOrNull { submission -> submission.state in SUBMITTED_STATES && resource.id in submission.versionIds }
        if (submitted != null) return result(version, "already-submitted", submitted.state)
        requireReady(resource, version)
        val reused = reusable(open, resource.id)
        val submissionId = reused?.resource?.id ?: services.review.create(app.id).id
        if (reused == null || resource.id !in reused.versionIds) services.review.addVersion(submissionId, resource.id)
        if (reused?.state == UNRESOLVED_STATE) reused.unresolvedItems(resource.id).forEach { item -> services.review.resolveItem(item.id) }
        val state = services.review.submit(submissionId).attributes.state
        val outcome = when (reused?.state) {
            null -> "submitted"
            UNRESOLVED_STATE -> "resubmitted"
            else -> "submitted-existing-draft"
        }
        return result(version, outcome, state)
    }

    /**
     * The open submission this run must finish instead of creating one: the unresolved submission that already holds
     * the version, or else the unsubmitted draft. Any other open submission would make App Store Connect refuse a new
     * one, and a reusable submission that holds anything besides this version would submit it too, so both stop the
     * run before a write.
     */
    private fun reusable(
        open: List<OpenSubmission>,
        versionId: String,
    ): OpenSubmission? {
        val unresolved = open.filter { submission -> submission.state == UNRESOLVED_STATE }
        val rejected = unresolved.firstOrNull { submission -> versionId in submission.versionIds }
        val candidate = rejected ?: open.firstOrNull { submission -> submission.state == DRAFT_STATE }
        val blocker = when {
            rejected == null && unresolved.isNotEmpty() -> "A submission with unresolved issues holds another item."
            candidate != null && candidate.holdsOtherThan(versionId) -> "An open submission also holds an item other than this version."
            else -> null
        }
        if (blocker != null) {
            throw ProvisioningException(
                ErrorCode.SUBMISSION_NOT_READY,
                blocker,
                "Resolve, remove, or cancel that submission in App Store Connect, then rerun `store submit`.",
            )
        }
        return candidate
    }

    private fun openSubmissions(appId: String): List<OpenSubmission> = services.review.openSubmissions(appId).map { submission ->
        OpenSubmission(submission, services.review.items(submission.id))
    }

    /** Refuses a version App Review would reject on arrival: no build, no screenshots, or screenshots still processing. */
    private fun requireReady(
        resource: AppStoreVersionResource,
        version: String,
    ) {
        val problems = buildList {
            if (resource.state !in SUBMITTABLE_VERSION_STATES) add("its state is ${resource.state ?: "unknown"}")
            if (services.store.attachedBuild(resource.id) == null) add("no build is attached")
            val screenshots = services.store.localizations(resource.id)
                .flatMap { localization -> services.screenshots.sets(localization.id) }
                .flatMap { set -> services.screenshots.screenshots(set.id) }
            if (screenshots.isEmpty()) add("it has no screenshots")
            val pending = screenshots.count { screenshot -> screenshot.deliveryState != COMPLETE }
            if (pending > 0) add("$pending screenshot(s) are not COMPLETE")
        }
        if (problems.isNotEmpty()) {
            throw ProvisioningException(
                ErrorCode.SUBMISSION_NOT_READY,
                "Version $version is not ready for review: ${problems.joinToString("; ")}.",
                "Run `store prepare --version $version ...`, then `store status --version $version`, and rerun once both are clear.",
            )
        }
    }

    private fun result(
        version: String,
        outcome: String,
        state: String?,
    ): JsonObject = buildJsonObject {
        put("version", version)
        put("submission", outcome)
        put("state", state)
    }
}
