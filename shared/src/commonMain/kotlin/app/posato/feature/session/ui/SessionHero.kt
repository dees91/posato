package app.posato.feature.session.ui

import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoHero
import app.posato.core.designsystem.PosatoIntervalArtwork
import app.posato.core.designsystem.PosatoLayout

@Composable
internal fun SessionHero(
    layout: PosatoLayout,
    description: String,
    active: Boolean,
    scheduledRestricts: Boolean,
) {
    PosatoHero(layout = layout, artworkContent = { PosatoIntervalArtwork() }, headingContent = {
        PosatoHeading(
            title = if (active) "A little room.\nJust for you." else "Room for what matters.",
            eyebrow = when {
                scheduledRestricts -> "SCHEDULED PAUSE"
                active -> "SESSION ACTIVE"
                else -> "NO SESSION ACTIVE"
            },
            description = description,
            layout = layout,
        )
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
