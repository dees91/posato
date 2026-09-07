package app.posato.feature.session.ui

import androidx.compose.runtime.Composable
import app.posato.generated.resources.Res
import app.posato.generated.resources.session_remaining_minutes
import app.posato.generated.resources.session_remaining_soon
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun remainingText(remainingMillis: Long): String {
    return if (remainingMillis < MILLIS_PER_MINUTE) {
        stringResource(Res.string.session_remaining_soon)
    } else {
        stringResource(Res.string.session_remaining_minutes, (remainingMillis / MILLIS_PER_MINUTE).toInt())
    }
}

private const val MILLIS_PER_MINUTE: Long = 60_000L
