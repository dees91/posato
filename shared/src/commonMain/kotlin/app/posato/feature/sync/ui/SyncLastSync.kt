package app.posato.feature.sync.ui

import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoCaption
import app.posato.generated.resources.Res
import app.posato.generated.resources.sync_last_synced
import app.posato.generated.resources.sync_not_synced_yet
import org.jetbrains.compose.resources.stringResource

/** When this device last completed a sync, so a person can tell whether changes still arrive. */
@Composable
internal fun LastSync(
    epochMillis: Long?,
    format: (Long) -> String,
) {
    PosatoCaption(
        epochMillis?.let { stringResource(Res.string.sync_last_synced, format(it)) } ?: stringResource(Res.string.sync_not_synced_yet),
    )
}
