package app.posato.feature.presence

import app.posato.generated.resources.Res
import app.posato.generated.resources.presence_accessibility_enforcing
import app.posato.generated.resources.presence_accessibility_idle
import app.posato.generated.resources.presence_accessibility_not_enforcing
import app.posato.generated.resources.presence_close_window
import app.posato.generated.resources.presence_end_session_early
import app.posato.generated.resources.presence_first_close_enforcing
import app.posato.generated.resources.presence_first_close_not_enforcing
import app.posato.generated.resources.presence_first_close_ok
import app.posato.generated.resources.presence_login_item
import app.posato.generated.resources.presence_login_item_supporting
import app.posato.generated.resources.presence_maintenance
import app.posato.generated.resources.presence_no_session
import app.posato.generated.resources.presence_not_enforcing
import app.posato.generated.resources.presence_open
import app.posato.generated.resources.presence_quit
import app.posato.generated.resources.presence_quit_confirm
import app.posato.generated.resources.presence_quit_enforcing
import app.posato.generated.resources.presence_quit_keep
import app.posato.generated.resources.presence_quit_not_enforcing
import app.posato.generated.resources.presence_quit_title
import app.posato.generated.resources.presence_resume_restrictions
import app.posato.generated.resources.presence_start_session
import app.posato.generated.resources.session_active_until
import app.posato.generated.resources.session_remaining_minutes
import app.posato.generated.resources.session_remaining_soon
import org.jetbrains.compose.resources.getString

public data class PresenceCopy(
    val noSession: String,
    val activeUntil: String,
    val minutesLeft: String,
    val lessThanAMinute: String,
    val notEnforcing: String,
    val maintenance: String,
    val startSession: String,
    val endSessionEarly: String,
    val resumeRestrictions: String,
    val open: String,
    val quit: String,
    val closeWindow: String,
    val accessibilityEnforcing: String,
    val accessibilityNotEnforcing: String,
    val accessibilityIdle: String,
    val quitTitle: String,
    val quitEnforcing: String,
    val quitNotEnforcing: String,
    val quitKeep: String,
    val quitConfirm: String,
    val firstCloseEnforcing: String,
    val firstCloseNotEnforcing: String,
    val firstCloseOk: String,
    val loginItem: String,
    val loginItemSupporting: String,
)

public suspend fun loadPresenceCopy(): PresenceCopy {
    return PresenceCopy(
        noSession = getString(Res.string.presence_no_session),
        activeUntil = getString(Res.string.session_active_until),
        minutesLeft = getString(Res.string.session_remaining_minutes),
        lessThanAMinute = getString(Res.string.session_remaining_soon),
        notEnforcing = getString(Res.string.presence_not_enforcing),
        maintenance = getString(Res.string.presence_maintenance),
        startSession = getString(Res.string.presence_start_session),
        endSessionEarly = getString(Res.string.presence_end_session_early),
        resumeRestrictions = getString(Res.string.presence_resume_restrictions),
        open = getString(Res.string.presence_open),
        quit = getString(Res.string.presence_quit),
        closeWindow = getString(Res.string.presence_close_window),
        accessibilityEnforcing = getString(Res.string.presence_accessibility_enforcing),
        accessibilityNotEnforcing = getString(Res.string.presence_accessibility_not_enforcing),
        accessibilityIdle = getString(Res.string.presence_accessibility_idle),
        quitTitle = getString(Res.string.presence_quit_title),
        quitEnforcing = getString(Res.string.presence_quit_enforcing),
        quitNotEnforcing = getString(Res.string.presence_quit_not_enforcing),
        quitKeep = getString(Res.string.presence_quit_keep),
        quitConfirm = getString(Res.string.presence_quit_confirm),
        firstCloseEnforcing = getString(Res.string.presence_first_close_enforcing),
        firstCloseNotEnforcing = getString(Res.string.presence_first_close_not_enforcing),
        firstCloseOk = getString(Res.string.presence_first_close_ok),
        loginItem = getString(Res.string.presence_login_item),
        loginItemSupporting = getString(Res.string.presence_login_item_supporting),
    )
}
