package app.posato.provisioning.store

import app.posato.provisioning.asc.ReviewClient
import app.posato.provisioning.asc.ScreenshotClient
import app.posato.provisioning.asc.StoreClient
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.model.AppResource
import app.posato.provisioning.model.AppStoreVersionResource
import app.posato.provisioning.model.LocalizationResource

/** The clients one store command uses, built once per run from the same token source. */
class StoreServices(
    val store: StoreClient,
    val screenshots: ScreenshotClient,
    val review: ReviewClient,
    val replacement: ScreenshotReplacement,
)

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
