package app.posato.feature.session.ui

import app.posato.feature.session.domain.SessionActionRequired
import app.posato.feature.session.domain.SessionSetupFailure
import app.posato.generated.resources.Res
import app.posato.generated.resources.session_action_access_required
import app.posato.generated.resources.session_action_mappings_failed
import app.posato.generated.resources.session_action_mappings_required
import app.posato.generated.resources.session_action_no_items
import app.posato.generated.resources.session_end_failed
import app.posato.generated.resources.session_error_corruption
import app.posato.generated.resources.session_error_load
import app.posato.generated.resources.session_review_description
import app.posato.generated.resources.session_setup_description
import app.posato.generated.resources.session_setup_error_long
import app.posato.generated.resources.session_setup_error_past
import app.posato.generated.resources.session_setup_error_short
import app.posato.generated.resources.session_start_failed
import org.jetbrains.compose.resources.StringResource

internal fun SessionSetupFailure?.setupMessage(): StringResource {
    return when (this) {
        SessionSetupFailure.TOO_SHORT -> Res.string.session_setup_error_short
        SessionSetupFailure.TOO_LONG -> Res.string.session_setup_error_long
        SessionSetupFailure.NOT_IN_FUTURE -> Res.string.session_setup_error_past
        null -> Res.string.session_setup_description
    }
}

internal fun SessionActionRequired?.actionMessage(): StringResource {
    return when (this) {
        SessionActionRequired.NO_EFFECTIVE_ITEMS -> Res.string.session_action_no_items
        SessionActionRequired.MAPPINGS_NOT_CHOSEN -> Res.string.session_action_mappings_required
        SessionActionRequired.ACCESS_REQUIRED -> Res.string.session_action_access_required
        SessionActionRequired.MAPPINGS_LOAD_FAILED -> Res.string.session_action_mappings_failed
        null -> Res.string.session_review_description
    }
}

internal fun SessionOperationFailure?.operationMessage(): StringResource {
    return when (this) {
        SessionOperationFailure.CORRUPTED_SESSION -> Res.string.session_error_corruption

        SessionOperationFailure.START_FAILED -> Res.string.session_start_failed

        SessionOperationFailure.END_FAILED -> Res.string.session_end_failed

        SessionOperationFailure.LOAD_FAILED,
        null -> Res.string.session_error_load
    }
}
