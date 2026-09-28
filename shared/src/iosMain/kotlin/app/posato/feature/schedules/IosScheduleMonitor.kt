package app.posato.feature.schedules

public class IosMonitorSchedule(
    public val id: String,
    public val weekdays: Int,
    public val startMinute: Int,
    public val endMinute: Int,
    public val stoppedDates: List<String>,
    public val startTitle: String,
    public val startBody: String,
) {
    override fun toString(): String {
        return "IosMonitorSchedule(redacted)"
    }
}

public class IosMonitorRunning(
    public val id: String,
    public val date: String,
    public val startEpochSeconds: Long,
    public val endEpochSeconds: Long,
)

/**
 * What the Device Activity monitor needs to start and end schedules while the app is closed. Dates are
 * local `YYYY-MM-DD`; application identifiers are resolved to tokens on the Swift side.
 */
public class IosScheduleMonitorTable(
    public val schedules: List<IosMonitorSchedule>,
    public val running: List<IosMonitorRunning>,
    public val domains: List<String>,
    public val mappingIds: List<String>,
    public val noticesEnabled: Boolean,
    public val endTitle: String,
    public val endBody: String,
    /** The end of a manual session active here, or 0; the monitor says Pause over only after it. */
    public val manualSessionEndEpochSeconds: Long = 0L,
) {
    override fun toString(): String {
        return "IosScheduleMonitorTable(redacted)"
    }
}

/** Implemented in Swift: writes the App Group table, registers the activities, and reports starts the monitor announced. */
public interface IosScheduleMonitorProvider {
    public fun publish(table: IosScheduleMonitorTable)

    /** Occurrences the monitor started and announced, as `<schedule id>:<YYYY-MM-DD>`. */
    public fun startedOccurrences(): List<String>
}

/** A process without a monitor, such as a test host. */
public object UnavailableIosScheduleMonitor : IosScheduleMonitorProvider {
    override fun publish(table: IosScheduleMonitorTable): Unit = Unit

    override fun startedOccurrences(): List<String> {
        return emptyList()
    }
}

/** The schedule's own enforcement and its monitor, passed from Swift as one piece. */
internal class IosScheduleBridge(
    val enforcement: app.posato.feature.enforcement.IosEnforcement,
    val monitor: IosScheduleMonitorProvider,
)
