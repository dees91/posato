package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.WindowStatus
import app.posato.feature.enforcement.nextBoundary
import app.posato.feature.enforcement.status
import app.posato.feature.enforcement.unionWindows
import app.posato.feature.enforcement.validPlans
import app.posato.feature.enforcement.wwwCounterpart
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.generated.resources.Res
import app.posato.generated.resources.session_window_more
import app.posato.generated.resources.session_window_open_until
import app.posato.generated.resources.session_window_opens_at
import app.posato.generated.resources.session_window_row_open
import app.posato.generated.resources.session_window_row_opens
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One paused website a window opens in the running pause: blocked until [time], or open until [time]. */
@Immutable
internal data class SessionWindowRow(
    val domain: String,
    val opens: Boolean,
    val time: String,
) {
    override fun toString(): String {
        return "SessionWindowRow(redacted)"
    }
}

/**
 * Each paused website with a window left in the running pause, soonest first. Where a manual session and a
 * scheduled pause both run, a website opens only while both are open for it, as the device enforces it. A
 * website and its `www` form show once.
 */
internal fun sessionWindowStatuses(
    manual: EnforcementRequest?,
    scheduled: EnforcementRequest?,
    nowEpochMillis: Long,
    zone: ScheduleZone,
): List<Pair<String, WindowStatus>> {
    val plans = when {
        manual != null && scheduled != null -> manual.unionWindows(scheduled)
        else -> (manual ?: scheduled)?.validPlans().orEmpty()
    }
    val domains = plans.mapTo(mutableSetOf()) { plan -> plan.domain }
    return plans
        .filterNot { plan -> plan.domain.startsWith("www.") && wwwCounterpart(plan.domain) in domains }
        .mapNotNull { plan -> plan.status(nowEpochMillis, zone)?.let { status -> plan.domain to status } }
        .sortedWith(compareBy({ (_, status) -> status.epochMillis() }, { (domain, _) -> domain }))
}

/** When the rows next change: the earliest window boundary of the running pause's plans. */
internal fun nextWindowRowChange(
    manual: EnforcementRequest?,
    scheduled: EnforcementRequest?,
    nowEpochMillis: Long,
    zone: ScheduleZone,
): Long? {
    val plans = when {
        manual != null && scheduled != null -> manual.unionWindows(scheduled)
        else -> (manual ?: scheduled)?.validPlans().orEmpty()
    }
    return plans.mapNotNull { plan -> plan.nextBoundary(nowEpochMillis, zone) }.minOrNull()
}

private fun WindowStatus.epochMillis(): Long {
    return when (this) {
        is WindowStatus.OpensAt -> epochMillis
        is WindowStatus.OpenUntil -> epochMillis
    }
}

/** The rows, read again at each window boundary so they change exactly when a website opens or closes. */
@Composable
internal fun rememberSessionWindowRows(
    manual: EnforcementRequest?,
    scheduled: EnforcementRequest?,
    clock: SessionClock,
    zone: ScheduleZone,
    timeFormat: SessionTimeFormat,
): List<SessionWindowRow> {
    val now by produceState(clock.currentEpochMillis(), manual, scheduled) {
        while (true) {
            val current = clock.currentEpochMillis()
            value = current
            val next = nextWindowRowChange(manual, scheduled, current, zone) ?: return@produceState
            delay((next - current).coerceIn(MIN_WAIT_MILLIS, MAX_WAIT_MILLIS))
        }
    }
    return sessionWindowStatuses(manual, scheduled, now, zone).map { (domain, status) ->
        when (status) {
            is WindowStatus.OpensAt -> SessionWindowRow(domain, opens = true, time = timeFormat.formatTime(status.epochMillis, now))
            is WindowStatus.OpenUntil -> SessionWindowRow(domain, opens = false, time = timeFormat.formatTime(status.epochMillis, now))
        }
    }
}

/** Under the running parts: the three soonest websites with a window, and "and 2 more", which opens the rest. */
@Composable
internal fun SessionWindowRowsSection(
    rows: List<SessionWindowRow>,
    shown: Boolean = true,
) {
    if (!shown || rows.isEmpty()) {
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val listed = if (expanded) rows else rows.take(SHOWN_ROWS)
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        listed.forEach { row -> SessionWindowLine(row) }
        val hidden = rows.size - listed.size
        if (hidden > 0) {
            PosatoButton({ expanded = true }, style = PosatoButtonStyle.Quiet, controlId = "session_window_more") {
                Text(pluralStringResource(Res.plurals.session_window_more, hidden, hidden))
            }
        }
    }
}

@Composable
private fun SessionWindowLine(row: SessionWindowRow) {
    val state = if (row.opens) {
        stringResource(Res.string.session_window_opens_at, row.time)
    } else {
        stringResource(Res.string.session_window_open_until, row.time)
    }
    val spoken = if (row.opens) {
        stringResource(Res.string.session_window_row_opens, row.domain, row.time)
    } else {
        stringResource(Res.string.session_window_row_open, row.domain, row.time)
    }
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
    ) {
        Text(row.domain, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        PosatoCaption(state)
    }
}

private const val SHOWN_ROWS: Int = 3
private const val MIN_WAIT_MILLIS: Long = 250L
private const val MAX_WAIT_MILLIS: Long = 60_000L
