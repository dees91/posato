package app.posato.provisioning.asc

import java.security.PrivateKey
import java.time.Clock
import java.time.Duration

/** Re-mint before the token actually expires so a slow request cannot cross the boundary mid-flight. */
private val REFRESH_MARGIN: Duration = Duration.ofSeconds(60)

/**
 * One token per process, minted on first use.
 *
 * Nothing is written to disk: a cached credential on a developer machine outlives the reason it was created, and a
 * command that runs for seconds has no need of one.
 */
class TokenSource(
    private val keyId: String,
    private val issuerId: String,
    private val privateKey: PrivateKey,
    private val clock: Clock,
) {
    private var cached: MintedToken? = null

    fun token(): String {
        val current = cached
        if (current != null && clock.instant().isBefore(current.expiresAt.minus(REFRESH_MARGIN))) return current.value
        val minted = JsonWebToken.mint(keyId, issuerId, privateKey, clock)
        cached = minted
        return minted.value
    }
}
