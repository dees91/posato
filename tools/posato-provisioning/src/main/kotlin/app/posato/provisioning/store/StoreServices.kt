package app.posato.provisioning.store

import app.posato.provisioning.asc.AppInfoClient
import app.posato.provisioning.asc.OPEN_SUBMISSION_STATES
import app.posato.provisioning.asc.ReviewClient
import app.posato.provisioning.asc.ScreenshotClient
import app.posato.provisioning.asc.Sleeper
import app.posato.provisioning.asc.StoreClient
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.model.AppResource
import app.posato.provisioning.model.AppStoreVersionResource
import app.posato.provisioning.model.LocalizationResource
import app.posato.provisioning.model.ReviewSubmissionItemResource
import app.posato.provisioning.model.ReviewSubmissionResource

internal const val UNCHANGED = "unchanged"

/** The version states in which App Store Connect accepts metadata, build, and screenshot changes. */
internal val EDITABLE_VERSION_STATES = setOf(
    "PREPARE_FOR_SUBMISSION",
    "DEVELOPER_REJECTED",
    "REJECTED",
    "METADATA_REJECTED",
    "INVALID_BINARY",
)

/** The review submission states in which App Review holds a submission: queued or under review. */
internal val SUBMITTED_STATES = setOf("WAITING_FOR_REVIEW", "IN_REVIEW")

/**
 * The clients one store command uses, built once per run from the same token source. [pause] waits between the
 * polls of a command that has to see App Store Connect finish something it started.
 */
class StoreServices(
    val store: StoreClient,
    val screenshots: ScreenshotClient,
    val review: ReviewClient,
    val replacement: ScreenshotReplacement,
    val pause: Sleeper,
    val appInfo: AppInfoClient,
)

/** One review submission with its items, which say which versions (or other material) it carries. */
internal class OpenSubmission(
    val resource: ReviewSubmissionResource,
    val items: List<ReviewSubmissionItemResource>,
) {
    val state: String? get() = resource.attributes.state

    val versionIds: Set<String> get() = items.mapNotNull { item -> item.versionId }.toSet()

    /** Whether it holds anything besides this version, including items that are not versions at all. */
    fun holdsOtherThan(versionId: String): Boolean = items.any { item -> item.versionId != versionId }
}

/** Lookups every store command shares, each failing with the category an agent can branch on. */
internal object StoreLookups {
    fun app(store: StoreClient): AppResource = store.app(IOS_BUNDLE_ID) ?: throw ProvisioningException(
        ErrorCode.APP_MISSING,
        "App Store Connect has no app record for $IOS_BUNDLE_ID.",
        "Create the app record in App Store Connect; this tool does not create one.",
    )

    fun version(
        store: StoreClient,
        appId: String,
        version: String,
    ): AppStoreVersionResource? = store.versions(appId).firstOrNull { resource -> resource.attributes.versionString == version }

    fun requireVersion(
        store: StoreClient,
        appId: String,
        version: String,
    ): AppStoreVersionResource = version(store, appId, version) ?: throw ProvisioningException(
        ErrorCode.VERSION_MISSING,
        "App Store Connect has no iOS App Store version $version.",
        "Run `store prepare --version $version ...` first.",
    )

    /** The app's review submissions in [states], by default every open one, each with its items. */
    fun submissions(
        review: ReviewClient,
        appId: String,
        states: Collection<String> = OPEN_SUBMISSION_STATES,
    ): List<OpenSubmission> = review.submissions(appId, states).map { submission -> OpenSubmission(submission, review.items(submission.id)) }

    fun localization(
        store: StoreClient,
        versionId: String,
    ): LocalizationResource = store.localizations(versionId).firstOrNull { resource -> resource.attributes.locale == STORE_LOCALE }
        ?: throw ProvisioningException(
            ErrorCode.LOCALIZATION_MISSING,
            "The App Store version has no $STORE_LOCALE localization.",
            "Add the $STORE_LOCALE localization in App Store Connect; a new version normally copies it from the previous one.",
        )
}
