package app.posato.desktop.resident

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import app.posato.desktop.PresenceMenuModel
import app.posato.desktop.presenceMenuModel
import app.posato.feature.presence.PresenceCopy
import app.posato.feature.presence.PresenceMenu
import kotlinx.coroutines.delay

/**
 * Publishes the status menu for [menu] whenever it changes, and once a minute while [ticking], so the minutes left
 * stay current without refreshing an idle icon on a timer.
 */
@Composable
internal fun StatusMenuRefreshEffect(
    menu: PresenceMenu,
    copy: PresenceCopy,
    ticking: Boolean,
    publish: (PresenceMenuModel) -> Unit,
) {
    val latestPublish by rememberUpdatedState(publish)
    LaunchedEffect(menu, ticking) {
        do {
            latestPublish(presenceMenuModel(menu, copy, System.currentTimeMillis(), ::formatClockTime))
            if (ticking) delay(MENU_REFRESH_MILLIS)
        } while (ticking)
    }
}

private const val MENU_REFRESH_MILLIS = 60_000L
