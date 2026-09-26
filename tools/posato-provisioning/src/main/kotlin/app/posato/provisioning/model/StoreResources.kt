package app.posato.provisioning.model

import kotlinx.serialization.Serializable

/** The App Store Connect app record. `bundleId` is compared exactly, because the listing filter is a contains match. */
@Serializable
data class AppAttributes(
    val bundleId: String? = null,
)

@Serializable
data class AppResource(
    val id: String,
    val attributes: AppAttributes = AppAttributes(),
)

@Serializable
data class AppStoreVersionAttributes(
    val platform: String? = null,
    val versionString: String? = null,
    val appVersionState: String? = null,
    val appStoreState: String? = null,
    val releaseType: String? = null,
)

@Serializable
data class AppStoreVersionResource(
    val id: String,
    val attributes: AppStoreVersionAttributes = AppStoreVersionAttributes(),
) {
    /** `appVersionState` replaced the deprecated `appStoreState`; either may be the only one a response carries. */
    val state: String? get() = attributes.appVersionState ?: attributes.appStoreState
}

@Serializable
data class BuildAttributes(
    val version: String? = null,
    val processingState: String? = null,
    val uploadedDate: String? = null,
    val expired: Boolean? = null,
)

@Serializable
data class BuildRelationships(
    val preReleaseVersion: ToOne = ToOne(),
)

@Serializable
data class BuildResource(
    val id: String,
    val attributes: BuildAttributes = BuildAttributes(),
    val relationships: BuildRelationships = BuildRelationships(),
) {
    val valid: Boolean get() = attributes.processingState == "VALID" && attributes.expired != true
}

@Serializable
data class PreReleaseVersionAttributes(
    val version: String? = null,
)

@Serializable
data class PreReleaseVersionResource(
    val id: String,
    val attributes: PreReleaseVersionAttributes = PreReleaseVersionAttributes(),
)

@Serializable
data class LocalizationAttributes(
    val locale: String? = null,
    val whatsNew: String? = null,
)

@Serializable
data class LocalizationResource(
    val id: String,
    val attributes: LocalizationAttributes = LocalizationAttributes(),
)

@Serializable
data class ScreenshotSetAttributes(
    val screenshotDisplayType: String? = null,
)

@Serializable
data class ScreenshotSetResource(
    val id: String,
    val attributes: ScreenshotSetAttributes = ScreenshotSetAttributes(),
)

@Serializable
data class AssetDeliveryError(
    val code: String? = null,
)

@Serializable
data class AssetDeliveryState(
    val state: String? = null,
    val errors: List<AssetDeliveryError> = emptyList(),
)

@Serializable
data class UploadHeader(
    val name: String? = null,
    val value: String? = null,
)

/** One part of an upload App Store Connect asks for. Every field is validated before anything is sent. */
@Serializable
data class UploadOperation(
    val method: String? = null,
    val url: String? = null,
    val offset: Long? = null,
    val length: Long? = null,
    val requestHeaders: List<UploadHeader> = emptyList(),
)

@Serializable
data class ScreenshotAttributes(
    val fileName: String? = null,
    val fileSize: Long? = null,
    val sourceFileChecksum: String? = null,
    val assetDeliveryState: AssetDeliveryState? = null,
    val uploadOperations: List<UploadOperation>? = null,
)

@Serializable
data class ScreenshotResource(
    val id: String,
    val attributes: ScreenshotAttributes = ScreenshotAttributes(),
) {
    val deliveryState: String? get() = attributes.assetDeliveryState?.state
}

@Serializable
data class ReviewSubmissionAttributes(
    val platform: String? = null,
    val state: String? = null,
)

@Serializable
data class ReviewSubmissionResource(
    val id: String,
    val attributes: ReviewSubmissionAttributes = ReviewSubmissionAttributes(),
)

@Serializable
data class ReviewSubmissionItemRelationships(
    val appStoreVersion: ToOne = ToOne(),
)

@Serializable
data class ReviewSubmissionItemAttributes(
    val state: String? = null,
    val resolved: Boolean? = null,
)

/**
 * One item of a review submission. Items that are not App Store versions (an App Event, a custom product page) have no
 * `appStoreVersion` linkage, so [versionId] is `null` for them; they are kept rather than dropped so a caller can tell
 * that a submission holds something other than the version it is about to submit.
 */
@Serializable
data class ReviewSubmissionItemResource(
    val id: String,
    val attributes: ReviewSubmissionItemAttributes = ReviewSubmissionItemAttributes(),
    val relationships: ReviewSubmissionItemRelationships = ReviewSubmissionItemRelationships(),
) {
    val versionId: String? get() = relationships.appStoreVersion.data?.id
}
