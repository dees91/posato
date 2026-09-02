package app.posato.feature.sync.domain

import app.posato.feature.sync.data.DurableClockState

internal fun reserveLocalClocks(
    current: DurableClockState,
    wallTime: Long,
    count: Int,
): List<HybridLogicalClock>? {
    var next = when {
        current.isExhausted -> null
        wallTime > current.last.physicalMillis -> HybridLogicalClock(wallTime, 0)
        else -> current.last.successor()
    }
    val result = mutableListOf<HybridLogicalClock>()
    repeat(count) { index ->
        next?.let(result::add)
        if (index < count - 1) next = next?.successor()
    }
    return result.takeIf { clocks -> clocks.size == count }
}
