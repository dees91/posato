package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoHero
import app.posato.core.designsystem.PosatoIntervalArtwork
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoStatusLabel
import app.posato.core.designsystem.PosatoTitle
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.generated.resources.Res
import app.posato.generated.resources.no_session_active
import app.posato.generated.resources.session_hero_active
import app.posato.generated.resources.session_hero_finish_setup
import app.posato.generated.resources.session_hero_quiet
import app.posato.generated.resources.session_hero_room_matters
import app.posato.generated.resources.session_hero_room_you
import app.posato.generated.resources.session_hero_scheduled
import app.posato.generated.resources.session_hero_scheduled_running
import app.posato.generated.resources.session_hero_start_website
import app.posato.generated.resources.session_hero_timer_running
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionHero(
    layout: PosatoLayout,
    description: String,
    active: Boolean,
    scheduledRestricts: Boolean,
) {
    val status = when {
        scheduledRestricts -> stringResource(Res.string.session_hero_scheduled)
        active -> stringResource(Res.string.session_hero_active)
        else -> stringResource(Res.string.no_session_active)
    }
    val title = if (active) stringResource(Res.string.session_hero_room_you) else stringResource(Res.string.session_hero_room_matters)
    PosatoHero(layout = layout, artworkContent = { PosatoIntervalArtwork() }, headingContent = {
        if (platformUsesCupertinoChrome) {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                PosatoTitle(title, layout = layout)
                PosatoStatusLabel(status, tone = if (active || scheduledRestricts) PosatoTone.Positive else PosatoTone.Neutral, sentenceCase = true)
                PosatoBody(description)
            }
        } else {
            PosatoHeading(title = title, eyebrow = status.uppercase(), description = description, layout = layout)
        }
    })
}

@Composable
internal fun overviewDescription(
    active: Boolean,
    scheduledRestricts: Boolean,
    needsMacSetup: Boolean,
    hasItems: Boolean,
): String {
    return when {
        scheduledRestricts -> stringResource(Res.string.session_hero_scheduled_running)
        active -> stringResource(Res.string.session_hero_timer_running)
        needsMacSetup -> stringResource(Res.string.session_hero_finish_setup)
        !hasItems -> stringResource(Res.string.session_hero_start_website)
        else -> stringResource(Res.string.session_hero_quiet)
    }
}
