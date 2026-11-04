package app.posato.provisioning.store

import app.posato.provisioning.asc.APP_NAME
import app.posato.provisioning.asc.AppInfoClient
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.model.AppInfoResource

private const val SUBTITLE = "subtitle"
private const val PRIVACY_POLICY_URL = "privacyPolicyUrl"
private const val NAME = "name"

/**
 * The App Information fields of one locale: the subtitle and the privacy policy URL, and the name a new localization
 * receives. Only the App Information that the version being prepared brings is written; the live one never is.
 */
internal object StoreAppInfo {
    /**
     * Writes the given fields for [locale], creating its localization when absent, and returns each written field's
     * outcome. A created localization, or one App Store Connect left without a name, also gets and reports the name.
     * A field that already matches is left alone, so a rerun issues only reads.
     */
    fun update(
        appInfo: AppInfoClient,
        appId: String,
        locale: String,
        subtitle: String?,
        privacyPolicyUrl: String?,
    ): Map<String, String> {
        val wanted = listOfNotNull(subtitle?.let { SUBTITLE to it }, privacyPolicyUrl?.let { PRIVACY_POLICY_URL to it }).toMap()
        if (wanted.isEmpty()) return emptyMap()
        val info = editable(appInfo, appId)
        val localization = appInfo.localizations(info.id).firstOrNull { resource -> resource.attributes.locale == locale }
        if (localization == null && locale == STORE_LOCALE) {
            throw ProvisioningException(
                ErrorCode.LOCALIZATION_MISSING,
                "The App Information has no $STORE_LOCALE localization.",
                "Add the $STORE_LOCALE localization in App Store Connect.",
            )
        }
        if (localization == null) {
            appInfo.createLocalization(info.id, locale, wanted)
            return mapOf(NAME to "updated") + wanted.mapValues { "updated" }
        }
        val current = mapOf(SUBTITLE to localization.attributes.subtitle, PRIVACY_POLICY_URL to localization.attributes.privacyPolicyUrl)
        val name = if (localization.attributes.name.isNullOrBlank()) mapOf(NAME to APP_NAME) else emptyMap()
        val changes = name + wanted.filter { (key, value) -> current[key]?.trim() != value }
        if (changes.isNotEmpty()) appInfo.updateLocalization(localization.id, changes)
        return name.mapValues { "updated" } + wanted.mapValues { (key, _) -> if (key in changes) "updated" else UNCHANGED }
    }

    /** The App Information a new App Store version brings; without one, nothing here can change now. */
    private fun editable(
        appInfo: AppInfoClient,
        appId: String,
    ): AppInfoResource = appInfo.appInfos(appId).firstOrNull { resource -> resource.currentState in EDITABLE_VERSION_STATES }
        ?: throw ProvisioningException(
            ErrorCode.VERSION_NOT_EDITABLE,
            "The app has no editable App Information, so the subtitle and privacy policy URL cannot change now.",
            "Earlier steps of this run may already be written; a rerun is safe. App Information changes with an App Store " +
                "version that is still being prepared; `store status` shows the versions.",
        )
}
