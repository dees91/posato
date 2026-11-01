import DeviceActivity
import Foundation
import ManagedSettings

/// A website's window rule (TARGETS-013): open while the wall clock's `hour * 60 + minute`, modulo `period`, is below
/// `length`. Only the fixed pairs are valid; a segment with period and length 0 has no rule.
struct WebsiteWindowRule: Codable, Equatable, Hashable {
    let period: Int
    let length: Int

    static let periods: Set<Int> = [30, 60, 120]
    static let lengths: Set<Int> = [5, 10, 15]

    var isValid: Bool {
        Self.periods.contains(period) && Self.lengths.contains(length)
    }

    /// The intersection of two rules: every period divides the longer ones and every length is shorter than the
    /// shortest period, so both are open exactly when `(max period, min length)` is.
    func intersection(_ other: WebsiteWindowRule) -> WebsiteWindowRule {
        WebsiteWindowRule(period: max(period, other.period), length: min(length, other.length))
    }
}

/// The device's wall clock as windows read it, with the zone read at each evaluation (ADR 0006 amendment).
enum WindowWallClock {
    static let minute: TimeInterval = 60
    /// Two days of minutes: further than any clock change can push the next change of a rule.
    static let maximumScanMinutes = 2 * 1440

    static func wallMinute(_ time: Date, calendar: Calendar) -> Int {
        let parts = calendar.dateComponents([.hour, .minute], from: time)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }

    static func isOpen(_ rule: WebsiteWindowRule, at time: Date, calendar: Calendar) -> Bool {
        wallMinute(time, calendar: calendar) % rule.period < rule.length
    }

    /// The first instant after `time` at which the rule's openness differs from its openness at `time`. The reading
    /// changes only at whole minutes; across a clock change the minutes are read one by one, so a skipped window never
    /// occurs and a repeated one opens again.
    static func nextChange(_ rule: WebsiteWindowRule, after time: Date, calendar: Calendar) -> Date {
        let seconds = time.timeIntervalSince1970
        let minuteStart = Date(timeIntervalSince1970: (seconds / minute).rounded(.down) * minute)
        let open = isOpen(rule, at: time, calendar: calendar)
        let phase = wallMinute(time, calendar: calendar) % rule.period
        let minutes = open ? rule.length - phase : rule.period - phase
        let candidate = minuteStart.addingTimeInterval(TimeInterval(minutes) * minute)
        if offset(minuteStart, calendar) == offset(candidate, calendar), isOpen(rule, at: candidate, calendar: calendar) != open {
            return candidate
        }
        var probe = minuteStart.addingTimeInterval(minute)
        for _ in 0 ..< maximumScanMinutes {
            if isOpen(rule, at: probe, calendar: calendar) != open {
                return probe
            }
            probe = probe.addingTimeInterval(minute)
        }
        return probe
    }

    private static func offset(_ time: Date, _ calendar: Calendar) -> Int {
        calendar.timeZone.secondsFromGMT(for: time)
    }
}

/// One held website's window plan as the app hands it over: ordered segments, each until an instant (epoch
/// milliseconds, exclusive) with a rule or none. After the last segment the website is blocked.
struct WebsiteWindowPlan: Codable, Equatable {
    struct Segment: Codable, Equatable {
        let until: Int64
        let period: Int
        let length: Int

        var rule: WebsiteWindowRule? {
            period == 0 && length == 0 ? nil : WebsiteWindowRule(period: period, length: length)
        }

        var untilDate: Date {
            Date(timeIntervalSince1970: TimeInterval(until) / 1000)
        }
    }

    let domain: String
    let segments: [Segment]

    /// The segment cap: 21 running parts can cover a website, so a valid plan has at most 22 segments.
    static let maximumSegments = 22

    func isValid(endEpochMillis: Int64) -> Bool {
        guard !segments.isEmpty, segments.count <= Self.maximumSegments,
              zip(segments, segments.dropFirst()).allSatisfy({ $0.until < $1.until }),
              let last = segments.last, last.until <= endEpochMillis
        else { return false }
        return segments.allSatisfy { segment in
            (segment.period == 0 && segment.length == 0) || (segment.rule?.isValid ?? false)
        }
    }

    func segment(at time: Date) -> Segment? {
        let millis = Int64((time.timeIntervalSince1970 * 1000).rounded(.down))
        return segments.first { $0.until > millis }
    }

    func isOpen(at time: Date, calendar: Calendar) -> Bool {
        guard let rule = segment(at: time)?.rule else { return false }
        return WindowWallClock.isOpen(rule, at: time, calendar: calendar)
    }

    /// The next instant after `time` at which the plan's openness can change; nil when no segment is left.
    func nextBoundary(after time: Date, calendar: Calendar) -> Date? {
        guard let segment = segment(at: time) else { return nil }
        guard let rule = segment.rule else { return segment.untilDate }
        return min(segment.untilDate, WindowWallClock.nextChange(rule, after: time, calendar: calendar))
    }
}

/// What a manual session leaves to the monitor so its windows open and close while Posato is closed: the websites
/// the session's store holds and the valid plans, until the session ends. Written by the app under the compose lock
/// after every apply and removed with the session.
struct ManualWindowRecord: Codable, Equatable {
    static let currentVersion = 1

    let version: Int
    let endEpochMillis: Int64
    /// Every website the session's store holds, open or not: retention and the limits read this, never the filter.
    let held: [String]
    let plans: [WebsiteWindowPlan]

    var end: Date {
        Date(timeIntervalSince1970: TimeInterval(endEpochMillis) / 1000)
    }

    /// One valid plan per held website; a malformed or duplicate plan leaves its website blocked.
    var validPlans: [WebsiteWindowPlan] {
        let heldSet = Set(held)
        let grouped = Dictionary(grouping: plans, by: \.domain)
        return grouped.compactMap { domain, plans in
            guard heldSet.contains(domain), plans.count == 1, plans[0].isValid(endEpochMillis: endEpochMillis) else { return nil }
            return plans[0]
        }
        .sorted { $0.domain < $1.domain }
    }
}

/// When a held website may be left open, by one rule per website for an instant, shared by the manual session's and
/// the schedule's stores. A callback reads the clock with a closing lead: within that lead before a close it closes,
/// which only blocks more; an opening is never read early.
enum WindowEvaluation {
    /// A callback this close before a close applies it now (the accepted early close).
    static let closingLead: TimeInterval = 60
    /// A callback that cannot arm the opening itself arms this much after it, at most once per opening.
    static let fallbackDelay: TimeInterval = 20

    /// Open at `time` and, with a lead, still open `lead` later.
    static func isOpen(_ open: (Date) -> Bool, at time: Date, lead: TimeInterval) -> Bool {
        open(time) && (lead <= 0 || open(time.addingTimeInterval(lead)))
    }

    /// The nearest instant after `time` at which `open` may change, skipping a close the lead already applied.
    static func nextBoundary(
        open: (Date) -> Bool,
        next: (Date) -> Date?,
        after time: Date,
        lead: TimeInterval
    ) -> Date? {
        guard var boundary = next(time) else { return nil }
        if lead > 0, open(time), boundary.timeIntervalSince(time) <= lead {
            guard let later = next(boundary) else { return nil }
            boundary = later
        }
        return boundary
    }

    /// The manual session's websites open at `time`. Close before open: a website opens only while an activity is
    /// registered for its close, so a missing registration keeps it blocked.
    static func openManual(
        _ record: ManualWindowRecord?,
        at time: Date,
        covered: ((Date) -> Bool)?,
        lead: TimeInterval,
        calendar: Calendar
    ) -> Set<String> {
        guard let record, time < record.end, let covered else { return [] }
        var open = Set<String>()
        for plan in record.validPlans {
            let state = { (instant: Date) in plan.isOpen(at: instant, calendar: calendar) }
            guard isOpen(state, at: time, lead: lead),
                  let close = plan.nextBoundary(after: time, calendar: calendar),
                  covered(close)
            else { continue }
            open.insert(plan.domain)
        }
        return open
    }

    /// How many boundaries ahead each plan or rule contributes to the wanted list.
    static let boundariesAhead = 8

    /// The manual session's next boundaries after `time`, each plan's in order up to its last segment.
    static func manualBoundaries(_ record: ManualWindowRecord?, after time: Date, calendar: Calendar) -> [Date] {
        guard let record, time < record.end else { return [] }
        var boundaries: [Date] = []
        for plan in record.validPlans {
            var instant = time
            for _ in 0 ..< boundariesAhead {
                guard let next = plan.nextBoundary(after: instant, calendar: calendar), next <= record.end else { break }
                boundaries.append(next)
                instant = next
            }
        }
        return boundaries
    }
}

/// Which callback evaluates: a Device Activity start or end, or Posato's own evaluation.
enum MonitorBoundary {
    enum Callback {
        /// The app's own evaluation or another activity's callback.
        case evaluation
        /// An activity's start.
        case start
        /// An activity's end.
        case end
    }
}

/// TARGETS-013 G21: every upcoming window boundary, an opening or a close, is its own named Device Activity,
/// registered ahead by the app and topped up by every callback. A callback only reads the clock and registers later
/// boundaries; it never restarts or stops the activity it was called for, because re-registering an activity from its
/// own callback lost the next boundary on the device (row 2 stage A, 2026-11-01).
enum WindowActivities {
    static let prefix = "app.posato.window."
    /// Device Activity monitors at most 20 activities for an application.
    static let platformLimit = 20
    /// Kept free for the other activities a later evaluation may add (a break, a cap).
    static let reserve = 2
    /// The minimum interval Device Activity accepts.
    static let minimumInterval: TimeInterval = 15 * 60
    /// An interval ending at its boundary is used only when its start is still this far ahead.
    static let startMargin: TimeInterval = 10

    enum Shape: Equatable {
        /// `[fire - 15 min, fire]`: the end callback is the boundary.
        case endsAtFire
        /// `[fire, fire + 15 min]`: the start callback is the boundary.
        case startsAtFire
    }

    /// The activity that fires at `instant` (whole seconds).
    static func name(firingAt instant: Date) -> DeviceActivityName {
        DeviceActivityName(prefix + String(Int64(instant.timeIntervalSince1970.rounded(.down))))
    }

    static func isWindowActivity(_ activity: DeviceActivityName) -> Bool {
        activity.rawValue.hasPrefix(prefix)
    }

    /// The instant the activity's boundary callback represents, read from its name.
    static func fireInstant(of activity: DeviceActivityName) -> Date? {
        guard isWindowActivity(activity), let seconds = Int64(activity.rawValue.dropFirst(prefix.count)) else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(seconds))
    }

    /// The interval registered at `time` for a boundary at `fire`: never one whose start has passed.
    static func shape(fire: Date, at time: Date) -> Shape {
        fire.addingTimeInterval(-minimumInterval) >= time.addingTimeInterval(startMargin) ? .endsAtFire : .startsAtFire
    }

    static func schedule(fire: Date, at time: Date, calendar: Calendar) -> DeviceActivitySchedule {
        let fields: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        switch shape(fire: fire, at: time) {
        case .endsAtFire:
            return DeviceActivitySchedule(
                intervalStart: calendar.dateComponents(fields, from: fire.addingTimeInterval(-minimumInterval)),
                intervalEnd: calendar.dateComponents(fields, from: fire),
                repeats: false
            )
        case .startsAtFire:
            return DeviceActivitySchedule(
                intervalStart: calendar.dateComponents(fields, from: fire),
                intervalEnd: calendar.dateComponents(fields, from: fire.addingTimeInterval(minimumInterval)),
                repeats: false
            )
        }
    }

    /// Whether `callback` of `activity` ends what the activity stands for: every end (a non-repeating activity is
    /// over after it, even when the system delivered it early or the interval was replaced), and the start of an
    /// interval starting at its fire instant. The start of an interval ending at its boundary is an evaluation.
    static func isBoundaryCallback(_ callback: MonitorBoundary.Callback, of activity: DeviceActivityName, center: ScheduleActivityCenter, calendar: Calendar) -> Bool {
        guard let fire = fireInstant(of: activity) else { return false }
        switch callback {
        case .end:
            return true
        case .start:
            guard let schedule = center.monitoredSchedule(for: activity), let start = calendar.date(from: schedule.intervalStart) else { return true }
            return abs(start.timeIntervalSince(fire)) < 1
        case .evaluation:
            return false
        }
    }

    /// The boundaries monitored now, without the calling activity when this is its boundary callback.
    static func covering(center: ScheduleActivityCenter, calling: DeviceActivityName?, callback: MonitorBoundary.Callback, calendar: Calendar) -> [Date] {
        let consumed = calling.map { isBoundaryCallback(callback, of: $0, center: center, calendar: calendar) } ?? false
        return center.monitoredActivities.compactMap { activity in
            if activity == calling && consumed { return nil }
            return fireInstant(of: activity)
        }
    }

    /// Whether a boundary at `boundary` is covered: an activity fires at it, or no later than the fallback delay
    /// after it, or within the closing lead before it.
    static func covers(_ fires: [Date], _ boundary: Date) -> Bool {
        fires.contains { fire in
            fire >= boundary.addingTimeInterval(-WindowEvaluation.closingLead) && fire <= boundary.addingTimeInterval(WindowEvaluation.fallbackDelay)
        }
    }

    struct Outcome: Equatable {
        /// The boundaries monitored after the top-up.
        let fires: [Date]
        /// Window activities no plan wants any more or that have fired, never the caller. Only the app stops them,
        /// and only outside the compose lock: a stop delivers the activity's end, whose callback waits for that lock.
        let stale: [DeviceActivityName]
    }

    /// Registers the wanted boundaries not yet covered, in time order, within the platform's activity limit, and stops
    /// window activities no longer wanted. A boundary whose own activity is the caller (an early boundary callback) is
    /// covered by its fallback, the boundary plus 20 seconds, once: a fallback's own early callback adds nothing.
    static func topUp(
        wanted: [Date],
        center: ScheduleActivityCenter,
        calling: DeviceActivityName?,
        callback: MonitorBoundary.Callback,
        at time: Date,
        calendar: Calendar,
        log: (String) -> Void = { _ in }
    ) -> Outcome {
        var fires = covering(center: center, calling: calling, callback: callback, calendar: calendar)
        let wantedSet = Set(wanted)
        // What no plan wants any more and what has fired, never the caller; the caller decides whether to stop it.
        let stale = center.monitoredActivities.filter { activity in
            guard activity != calling, let fire = fireInstant(of: activity) else { return false }
            if fire <= time { return true }
            return !wantedSet.contains(where: { abs(fire.timeIntervalSince($0)) < 0.5 || (fire > $0 && fire.timeIntervalSince($0) <= WindowEvaluation.fallbackDelay + 0.5) })
        }
        fires.removeAll { fire in stale.contains { fireInstant(of: $0) == fire } }
        let others = center.monitoredActivities.filter { !isWindowActivity($0) }.count
        var budget = platformLimit - reserve - others - center.monitoredActivities.filter(isWindowActivity).count
        for boundary in wanted.sorted() where boundary > time {
            if covers(fires, boundary) { continue }
            guard budget > 0 else {
                log("budget")
                break
            }
            var fire = boundary
            if let calling, fireInstant(of: calling) == boundary {
                fire = boundary.addingTimeInterval(WindowEvaluation.fallbackDelay)
            } else if let calling, let own = fireInstant(of: calling), own > boundary, own.timeIntervalSince(boundary) <= WindowEvaluation.fallbackDelay {
                // The caller is this boundary's fallback, called early: nothing more is registered for it.
                log("fallback-early")
                continue
            }
            let activity = name(firingAt: fire)
            if activity == calling { continue }
            do {
                try center.startMonitoringSchedule(activity, during: schedule(fire: fire, at: time, calendar: calendar))
                fires.append(fire)
                budget -= 1
                log("registered \(activity.rawValue)")
            } catch {
                log("refused \(activity.rawValue)")
            }
        }
        return Outcome(fires: fires, stale: stale)
    }
}

/// Narrow seam over the manual session's web filter, which the monitor narrows to what is blocked now.
protocol ManualWebFilterStore: AnyObject {
    func setBlockedHosts(_ hosts: [String])
}

extension ManagedSettingsStore: ManualWebFilterStore {
    func setBlockedHosts(_ hosts: [String]) {
        webContent.blockedByFilter = hosts.isEmpty ? nil : .specific(PosatoWebDomains.domains(from: hosts))
    }
}

/// The schedule's store follows the same rule per website as the manual session's: a website open only while every
/// running part that holds it has a rule for it and is inside its window.
enum ScheduledWindows {
    /// The effective rule of each held website from the parts that hold it, or none when one of them has no rule:
    /// a part whose set no longer has the website, or has no rule for it, keeps it blocked.
    static func rules(
        held: [String: Set<String>],
        composing: [ScheduleMonitorOccurrence],
        file: ScheduleMonitorFile
    ) -> [String: WebsiteWindowRule] {
        var rules: [String: WebsiteWindowRule] = [:]
        var blocked = Set<String>()
        for occurrence in composing {
            let key = "\(occurrence.scheduleId):\(occurrence.date)"
            let current = Set(file.setItems(scheduleId: occurrence.scheduleId).domains)
            let setRules = file.setRules(scheduleId: occurrence.scheduleId)
            for domain in held[key] ?? [] {
                guard current.contains(domain), let rule = setRules[domain] else {
                    blocked.insert(domain)
                    continue
                }
                rules[domain] = rules[domain].map { $0.intersection(rule) } ?? rule
            }
        }
        blocked.forEach { rules[$0] = nil }
        return rules
    }

    /// The websites open at `time` under `rules`, with the closing lead and the close-before-open gate.
    static func open(_ rules: [String: WebsiteWindowRule], at time: Date, covered: ((Date) -> Bool)?, lead: TimeInterval, calendar: Calendar) -> Set<String> {
        guard let covered else { return [] }
        var open = Set<String>()
        for (domain, rule) in rules {
            let state = { (instant: Date) in WindowWallClock.isOpen(rule, at: instant, calendar: calendar) }
            guard WindowEvaluation.isOpen(state, at: time, lead: lead),
                  covered(WindowWallClock.nextChange(rule, after: time, calendar: calendar))
            else { continue }
            open.insert(domain)
        }
        return open
    }

    /// The next boundaries of `rules` after `time`; a part's end is its own activity's callback.
    static func boundaries(_ rules: [String: WebsiteWindowRule], after time: Date, calendar: Calendar) -> [Date] {
        var boundaries: [Date] = []
        for rule in Set(rules.values) {
            var instant = time
            for _ in 0 ..< WindowEvaluation.boundariesAhead {
                instant = WindowWallClock.nextChange(rule, after: instant, calendar: calendar)
                boundaries.append(instant)
            }
        }
        return boundaries
    }

    /// The rules of the parts running at `time` and not on a break, from what each holds now.
    static func rulesNow(
        file: ScheduleMonitorFile?,
        files: ScheduleMonitorFileStore,
        over: Set<String>,
        armed: Date?,
        at time: Date,
        calendar: Calendar
    ) -> [String: WebsiteWindowRule] {
        guard let file else { return [:] }
        let running = ScheduleMonitorRule.running(in: file, at: time, calendar: calendar)
        let releasing = ScheduleBreakWindows.releasing(file, over: over, armed: armed, at: time)
        let composing = running.filter { !releasing.contains("\($0.scheduleId):\($0.date)") }
        var held: [String: Set<String>] = [:]
        for occurrence in composing {
            held["\(occurrence.scheduleId):\(occurrence.date)"] = Set(files.held(scheduleId: occurrence.scheduleId, date: occurrence.date)?.domains ?? [])
        }
        return rules(held: held, composing: composing, file: file)
    }
}

extension ScheduleMonitorFileStore {
    /// What the running scheduled parts hold, open or not; the manual session counts it toward the limits.
    func heldByRunning(at time: Date, calendar: Calendar = ScheduleMonitor.calendar) -> Set<String> {
        guard let file = readTable() else { return [] }
        var held = Set<String>()
        for occurrence in ScheduleMonitorRule.running(in: file, at: time, calendar: calendar) {
            held.formUnion(self.held(scheduleId: occurrence.scheduleId, date: occurrence.date)?.domains ?? [])
        }
        return held
    }
}

extension ScheduleMonitorFileStore {
    private static let manualWindowsName = "manual-windows-v1.json"
    private static let maximumManualWindowBytes = 256 * 1024

    func readManualWindows() -> ManualWindowRecord? {
        guard let data = read(Self.manualWindowsName, limit: Self.maximumManualWindowBytes),
              let record = try? JSONDecoder().decode(ManualWindowRecord.self, from: data),
              record.version == ManualWindowRecord.currentVersion
        else { return nil }
        return record
    }

    func writeManualWindows(_ record: ManualWindowRecord) throws {
        try write(JSONEncoder().encode(record), fileName: Self.manualWindowsName)
    }

    func removeManualWindows() {
        try? fileManager.removeItem(at: directoryURL.appendingPathComponent(Self.manualWindowsName))
    }
}
