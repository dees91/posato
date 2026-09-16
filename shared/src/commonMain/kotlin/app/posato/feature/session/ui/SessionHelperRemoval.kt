package app.posato.feature.session.ui

import app.posato.feature.session.domain.LocalSessionStatus

internal fun SessionUiState.blocksHelperRemoval(): Boolean {
    return status is LocalSessionStatus.Active || isStarting || enforcementBusy
}
