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

@Composable
internal fun SessionHero(
    layout: PosatoLayout,
    description: String,
    active: Boolean,
    scheduledRestricts: Boolean,
) {
    val status = when {
        scheduledRestricts -> "Scheduled pause"
        active -> "Session active"
        else -> "No session active"
    }
    val title = if (active) "A little room.\nJust for you." else "Room for what matters."
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

internal fun overviewDescription(
    active: Boolean,
    scheduledRestricts: Boolean,
    needsMacSetup: Boolean,
    hasItems: Boolean,
): String {
    return when {
        scheduledRestricts -> "A scheduled pause is running on this device."
        active -> "Your session timer is running on this device."
        needsMacSetup -> "Finish setting up blocking, then choose when to take a pause."
        !hasItems -> "Start with a website or app you’d like a little space from."
        else -> "A quiet pause is ready when you are. Choose a little space from the things that pull you away."
    }
}
