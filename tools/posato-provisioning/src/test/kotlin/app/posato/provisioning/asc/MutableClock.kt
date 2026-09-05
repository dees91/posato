package app.posato.provisioning.asc

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock a test moves by hand, so token reuse and expiry are checked without waiting. */
class MutableClock(
    var now: Instant
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
