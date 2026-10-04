package app.posato.provisioning.store

import app.posato.provisioning.asc.AppInfoClient
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException

private const val MAX_SUBTITLE = 30

/** The en-US App Store subtitle: its input check, made before any request, and its update. */
object StoreSubtitle {
    fun input(value: String): String {
        val text = value.trim()
        val problem = when {
            text.isEmpty() -> "--subtitle is empty."
            text.length > MAX_SUBTITLE -> "--subtitle is longer than App Store Connect's $MAX_SUBTITLE characters."
            else -> null
        }
        if (problem != null) {
            throw ProvisioningException(ErrorCode.RELEASE_INPUT_INVALID, problem, "Correct the input; nothing was sent to App Store Connect.")
        }
        return text
    }

    /**
     * Writes [subtitle] into the App Information that is still editable and returns the outcome. A new App Store
     * version brings its own copy, so the live one is never changed; without an editable copy the run stops.
     */
    fun update(
        appInfo: AppInfoClient,
        appId: String,
        subtitle: String,
    ): String {
        val info = appInfo.appInfos(appId).firstOrNull { resource -> resource.currentState in EDITABLE_VERSION_STATES }
            ?: throw ProvisioningException(
                ErrorCode.VERSION_NOT_EDITABLE,
                "The app has no editable App Information, so the subtitle cannot change now.",
                "Earlier steps of this run may already be written; a rerun is safe. The subtitle changes with an App Store version " +
                    "that is still being prepared; `store status` shows the versions.",
            )
        val localization = appInfo.localizations(info.id).firstOrNull { resource -> resource.attributes.locale == STORE_LOCALE }
            ?: throw ProvisioningException(
                ErrorCode.LOCALIZATION_MISSING,
                "The App Information has no $STORE_LOCALE localization.",
                "Add the $STORE_LOCALE localization in App Store Connect.",
            )
        if (localization.attributes.subtitle?.trim() == subtitle) return UNCHANGED
        appInfo.updateLocalization(localization.id, mapOf("subtitle" to subtitle))
        return "updated"
    }
}
