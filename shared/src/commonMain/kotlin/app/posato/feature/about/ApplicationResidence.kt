package app.posato.feature.about

import kotlinx.coroutines.flow.StateFlow

/**
 * How Posato stays on a desktop whose host keeps it running in the background (Linux, LINUX-003): starting at login
 * and quitting on purpose. Hosts that offer this elsewhere (the Mac's This Mac options) pass none.
 */
public interface ApplicationResidence {
    public val opensAtLogin: StateFlow<Boolean>

    public fun setOpensAtLogin(enabled: Boolean)

    public fun quit()
}

/** What the host adds to About Posato: update checks and the background options, each optional. */
internal data class AboutHosting(
    val updates: ApplicationUpdates?,
    val residence: ApplicationResidence?,
)
