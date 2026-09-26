package app.posato.provisioning.asc

import app.posato.provisioning.model.RelationshipRef
import app.posato.provisioning.model.ReviewSubmissionItemResource
import app.posato.provisioning.model.ReviewSubmissionResource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PLATFORM = "IOS"

/**
 * The submission states in which a submission is still open: being assembled, queued, under review, or returned by
 * App Review with unresolved issues. A rejected submission stays open and is resubmitted rather than replaced.
 */
private val OPEN_SUBMISSION_STATES: List<String> = listOf("READY_FOR_REVIEW", "WAITING_FOR_REVIEW", "IN_REVIEW", "UNRESOLVED_ISSUES")

/** The App Review submission operations: find an open submission, assemble one, and submit it. */
class ReviewClient(
    executor: AscRequestExecutor
) {
    private val documents = AscDocuments(executor)

    fun openSubmissions(appId: String): List<ReviewSubmissionResource> = documents.list(
        "reviewSubmissions",
        listOf(
            "filter[app]" to appId,
            "filter[platform]" to PLATFORM,
            "filter[state]" to OPEN_SUBMISSION_STATES.joinToString(","),
        ),
        ReviewSubmissionResource.serializer(),
        STORE_LISTING_HINT,
    )

    /** `include` is what makes App Store Connect fill in which version each item carries. */
    fun items(submissionId: String): List<ReviewSubmissionItemResource> = documents.list(
        "reviewSubmissions/$submissionId/items",
        listOf("include" to "appStoreVersion"),
        ReviewSubmissionItemResource.serializer(),
        STORE_LISTING_HINT,
    )

    fun create(appId: String): ReviewSubmissionResource = documents.write(
        HttpMethod.POST,
        "reviewSubmissions",
        JsonApi.resource(
            type = "reviewSubmissions",
            attributes = buildJsonObject { put("platform", PLATFORM) },
            relationships = mapOf("app" to RelationshipRef(appId, "apps")),
        ),
        ReviewSubmissionResource.serializer(),
    )

    fun addVersion(
        submissionId: String,
        versionId: String,
    ): ReviewSubmissionItemResource = documents.write(
        HttpMethod.POST,
        "reviewSubmissionItems",
        JsonApi.resource(
            type = "reviewSubmissionItems",
            relationships = mapOf(
                "reviewSubmission" to RelationshipRef(submissionId, "reviewSubmissions"),
                "appStoreVersion" to RelationshipRef(versionId, "appStoreVersions"),
            ),
        ),
        ReviewSubmissionItemResource.serializer(),
    )

    /** Marks a rejected item as corrected, which App Review requires before a submission with unresolved issues is resubmitted. */
    fun resolveItem(itemId: String): ReviewSubmissionItemResource = documents.write(
        HttpMethod.PATCH,
        "reviewSubmissionItems/$itemId",
        JsonApi.resource(type = "reviewSubmissionItems", id = itemId, attributes = buildJsonObject { put("resolved", true) }),
        ReviewSubmissionItemResource.serializer(),
    )

    fun submit(submissionId: String): ReviewSubmissionResource = documents.write(
        HttpMethod.PATCH,
        "reviewSubmissions/$submissionId",
        JsonApi.resource(type = "reviewSubmissions", id = submissionId, attributes = buildJsonObject { put("submitted", true) }),
        ReviewSubmissionResource.serializer(),
    )
}
