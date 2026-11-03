package app.posato.feature.targets.domain

/**
 * A website's window rule (ADR 0006 kind 22): during a pause the website is open while the local clock's
 * reading, hour * 60 + minute, modulo [periodMinutes] is below [lengthMinutes]. Only the fixed choices exist.
 */
internal class WebsiteWindow private constructor(
    val periodMinutes: Int,
    val lengthMinutes: Int,
) {
    /** Whether the window is open at a wall-clock reading of [wallMinute] (0-1439). */
    fun isOpenAt(wallMinute: Int): Boolean {
        return wallMinute % periodMinutes < lengthMinutes
    }

    /**
     * The single rule open exactly when both are. Every period divides the longer ones and every length is
     * shorter than the shortest period, so it is the longer period with the shorter length.
     */
    fun intersect(other: WebsiteWindow): WebsiteWindow {
        return WebsiteWindow(maxOf(periodMinutes, other.periodMinutes), minOf(lengthMinutes, other.lengthMinutes))
    }

    override fun equals(other: Any?): Boolean {
        return other is WebsiteWindow && other.periodMinutes == periodMinutes && other.lengthMinutes == lengthMinutes
    }

    override fun hashCode(): Int {
        return 31 * periodMinutes + lengthMinutes
    }

    override fun toString(): String {
        return "WebsiteWindow(redacted)"
    }

    companion object {
        val PERIODS: List<Int> = listOf(30, 60, 120)
        val LENGTHS: List<Int> = listOf(5, 10, 15)

        /** Every rule a person can choose, period first. */
        val ALL: List<WebsiteWindow> = PERIODS.flatMap { period -> LENGTHS.map { length -> WebsiteWindow(period, length) } }

        fun of(
            periodMinutes: Int,
            lengthMinutes: Int,
        ): WebsiteWindow? {
            return if (periodMinutes in PERIODS && lengthMinutes in LENGTHS) WebsiteWindow(periodMinutes, lengthMinutes) else null
        }
    }
}
