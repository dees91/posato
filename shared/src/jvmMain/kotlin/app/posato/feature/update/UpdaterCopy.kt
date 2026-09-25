package app.posato.feature.update

import app.posato.generated.resources.Res
import app.posato.generated.resources.update_check_now
import app.posato.generated.resources.update_consent_allow
import app.posato.generated.resources.update_consent_deny
import app.posato.generated.resources.update_consent_message
import app.posato.generated.resources.update_consent_title
import app.posato.generated.resources.update_preparing
import app.posato.generated.resources.update_refused_other
import app.posato.generated.resources.update_refused_other_instance
import app.posato.generated.resources.update_refused_session
import app.posato.generated.resources.update_refused_title
import org.jetbrains.compose.resources.getString

public data class UpdaterCopy(
    val checkForUpdates: String,
    val preparing: String,
    val refusedTitle: String,
    val refusedSession: String,
    val refusedOtherInstance: String,
    val refusedOther: String,
    val consentTitle: String,
    val consentMessage: String,
    val consentAllow: String,
    val consentDeny: String,
)

public suspend fun loadUpdaterCopy(): UpdaterCopy {
    return UpdaterCopy(
        checkForUpdates = getString(Res.string.update_check_now),
        preparing = getString(Res.string.update_preparing),
        refusedTitle = getString(Res.string.update_refused_title),
        refusedSession = getString(Res.string.update_refused_session),
        refusedOtherInstance = getString(Res.string.update_refused_other_instance),
        refusedOther = getString(Res.string.update_refused_other),
        consentTitle = getString(Res.string.update_consent_title),
        consentMessage = getString(Res.string.update_consent_message),
        consentAllow = getString(Res.string.update_consent_allow),
        consentDeny = getString(Res.string.update_consent_deny),
    )
}
