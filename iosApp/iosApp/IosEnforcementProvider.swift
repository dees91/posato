import CryptoKit
import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings
import PosatoShared

enum IosEnforcementStoreName {
    static let posato = PosatoManagedSettingsStore.name
}

protocol IosEnforcementSettingsStore: AnyObject {
    var blockedWebFilter: WebContentSettings.FilterPolicy? { get set }
    var shieldedApplications: Set<ApplicationToken>? { get set }
    func clearAll()
}

extension ManagedSettingsStore: IosEnforcementSettingsStore {
    var blockedWebFilter: WebContentSettings.FilterPolicy? {
        get {
            webContent.blockedByFilter
        }
        set {
            webContent.blockedByFilter = newValue
        }
    }

    var shieldedApplications: Set<ApplicationToken>? {
        get {
            shield.applications
        }
        set {
            shield.applications = newValue
        }
    }

    func clearAll() {
        clearAllSettings()
    }
}

enum ApplicationTokenIdentity {
    static func identifier(for token: Data) -> String {
        SHA256.hash(data: token).map { String(format: "%02x", $0) }.joined()
    }

    static func validatedMappings(_ mappings: [StoredApplicationMapping]) throws -> [StoredApplicationMapping] {
        let decoder = JSONDecoder()
        do {
            for mapping in mappings {
                _ = try decoder.decode(ApplicationToken.self, from: mapping.token)
            }
        } catch {
            throw ApplicationMappingsStoreError.corruption
        }
        return mappings
    }

    static func token(matching identifier: String, in mappings: [StoredApplicationMapping]) -> Data? {
        mappings.first { self.identifier(for: $0.token) == identifier }?.token
    }
}

enum EnforcementAuthorization {
    case approved
    case notDetermined
    case denied
    case restricted

    init(status: FamilyControls.AuthorizationStatus) {
        if status == .approved {
            self = .approved
            return
        }
        if #available(iOS 26.4, *), status == .approvedWithDataAccess {
            self = .approved
            return
        }
        if status == .notDetermined {
            self = .notDetermined
        } else if status == .denied {
            self = .denied
        } else {
            self = .restricted
        }
    }

    var refusal: IosEnforcementOutcome? {
        switch self {
        case .approved:
            return nil
        case .notDetermined:
            return .authorizationRequired
        case .denied:
            return .authorizationDenied
        case .restricted:
            return .restricted
        }
    }
}

final class IosManagedSettingsEnforcer: NSObject, IosEnforcementProvider {
    private let storeFactory: () -> IosEnforcementSettingsStore
    private let authorization: () -> EnforcementAuthorization
    private let isCapable: Bool
    private let storedMappings: () throws -> [StoredApplicationMapping]
    private let scheduleStore: () -> ScheduleShieldStore?
    private let composeLock: ScheduleMonitorFileStore?
    private let center: () -> ScheduleActivityCenter
    private let now: () -> Date
    private let calendar: () -> Calendar
    /// Window activities the last write found stale, stopped once the compose lock is released.
    private var pendingStale: [DeviceActivityName] = []

    private static var defaultCapable: Bool {
#if targetEnvironment(simulator) || !POSATO_FAMILY_CONTROLS
        return false
#else
        return true
#endif
    }

    init(
        storeFactory: @escaping () -> IosEnforcementSettingsStore = {
            ManagedSettingsStore(named: IosEnforcementStoreName.posato)
        },
        authorization: @escaping () -> EnforcementAuthorization = {
            EnforcementAuthorization(status: AuthorizationCenter.shared.authorizationStatus)
        },
        isCapable: Bool = defaultCapable,
        storedMappings: @escaping () throws -> [StoredApplicationMapping] = {
            try ApplicationMappingSets.liveMigrated().loadAllSets()
        },
        scheduleStore: @escaping () -> ScheduleShieldStore? = { nil },
        composeLock: ScheduleMonitorFileStore? = nil,
        center: @escaping () -> ScheduleActivityCenter = { DeviceActivityCenter() },
        now: @escaping () -> Date = Date.init,
        calendar: @escaping () -> Calendar = { ScheduleMonitor.calendar }
    ) {
        self.storeFactory = storeFactory
        self.authorization = authorization
        self.isCapable = isCapable
        self.storedMappings = storedMappings
        self.scheduleStore = scheduleStore
        self.composeLock = composeLock
        self.center = center
        self.now = now
        self.calendar = calendar
    }

    func apply(
        request: IosEnforcementRequest,
        completion: @escaping (IosEnforcementOutcome) -> Void
    ) {
        performOnMain {
            completion(self.applyOnMain(request: request))
        }
    }

    func clear(handler: @escaping (IosEnforcementOutcome) -> Void) {
        performOnMain {
            guard self.isCapable else {
                handler(.unavailable)
                return
            }
            if let files = self.composeLock {
                let stale = files.withComposeLock { () -> [DeviceActivityName] in
                    files.removeManualWindows()
                    self.storeFactory().clearAll()
                    // The session's boundaries are no longer wanted; the schedule's stay.
                    return ScheduleMonitorEvents.windowTopUp(
                        file: files.readTable(), over: files.breaksOver(), files: files, center: self.center(), calling: nil,
                        callback: .evaluation, at: self.now(), calendar: self.calendar()
                    ).stale
                }
                ScheduleMonitorEvents.stopStale(stale, center: self.center())
            } else {
                self.storeFactory().clearAll()
            }
            handler(.cleared)
        }
    }

    func status(handler: @escaping (IosEnforcementOutcome) -> Void) {
        performOnMain {
            guard self.isCapable else {
                handler(.unavailable)
                return
            }
            if let refusal = self.authorizationRefusal() {
                handler(refusal)
                return
            }
            let store = self.storeFactory()
            if store.blockedWebFilter != nil || !(store.shieldedApplications?.isEmpty ?? true) {
                handler(.applied)
            } else {
                handler(.cleared)
            }
        }
    }

    private func applyOnMain(request: IosEnforcementRequest) -> IosEnforcementOutcome {
        guard isCapable else {
            return .unavailable
        }
        if let refusal = authorizationRefusal() {
            return refusal
        }
        if request.domains.isEmpty && request.mappingIds.isEmpty {
            return .nothingToEnforce
        }
        let tokens: Set<ApplicationToken>
        do {
            let mappings = try ApplicationTokenIdentity.validatedMappings(storedMappings())
            let decoder = JSONDecoder()
            var resolved = Set<ApplicationToken>()
            for identifier in request.mappingIds {
                guard let data = ApplicationTokenIdentity.token(matching: identifier, in: mappings),
                      let token = try? decoder.decode(ApplicationToken.self, from: data)
                else {
                    return .selectionMissing
                }
                resolved.insert(token)
            }
            tokens = resolved
        } catch {
            return .platformFailure
        }
        let store = storeFactory()
        let windows = request.windows.map { plan in
            WebsiteWindowPlan(
                domain: plan.domain,
                segments: plan.segments.map {
                    WebsiteWindowPlan.Segment(until: $0.untilEpochMillis, period: Int($0.periodMinutes), length: Int($0.lengthMinutes))
                }
            )
        }
        if let lock = composeLock {
            let outcome = lock.withComposeLock {
                write(domains: request.domains, tokens: tokens, windows: windows, endEpochMillis: request.sessionEndEpochMillis, to: store, files: lock)
            }
            ScheduleMonitorEvents.stopStale(pendingStale, center: center())
            pendingStale = []
            return outcome
        }
        return write(domains: request.domains, tokens: tokens, windows: [], endEpochMillis: 0, to: store, files: nil)
    }

    /// Writes the session's share. With window plans, the record goes to the App Group first, the one boundary
    /// activity is armed, and the web filter blocks what is held except what is open now; without plans, or when
    /// the record cannot be written, every held website is blocked and no record is left behind.
    private func write(
        domains: [String],
        tokens: Set<ApplicationToken>,
        windows: [WebsiteWindowPlan],
        endEpochMillis: Int64,
        to store: IosEnforcementSettingsStore,
        files: ScheduleMonitorFileStore?
    ) -> IosEnforcementOutcome {
        let share = withinShare(domains: domains, tokens: tokens, store: store)
        var blocked = share.domains
        if let files {
            let plans = windows.filter { share.domains.contains($0.domain) }
            let record = ManualWindowRecord(version: ManualWindowRecord.currentVersion, endEpochMillis: endEpochMillis, held: share.domains, plans: plans)
            if plans.isEmpty || (try? files.writeManualWindows(record)) == nil {
                files.removeManualWindows()
            } else {
                let time = now()
                let calendar = calendar()
                // Close before open: every boundary ahead is registered before a website opens.
                let topUp = ScheduleMonitorEvents.windowTopUp(
                    file: files.readTable(), over: files.breaksOver(), files: files, center: center(), calling: nil, callback: .evaluation,
                    at: time, calendar: calendar
                )
                pendingStale = topUp.stale
                let open = WindowEvaluation.openManual(record, at: time, covered: topUp.covered, lead: WindowEvaluation.closingLead, calendar: calendar)
                blocked = share.domains.filter { !open.contains($0) }
            }
        }
        let expectedFilter: WebContentSettings.FilterPolicy? =
            blocked.isEmpty ? nil : .specific(webDomains(from: blocked))
        let expectedApplications: Set<ApplicationToken>? = share.tokens.isEmpty ? nil : share.tokens
        store.blockedWebFilter = expectedFilter
        store.shieldedApplications = expectedApplications
        guard store.blockedWebFilter == expectedFilter,
              store.shieldedApplications == expectedApplications
        else {
            files?.removeManualWindows()
            store.clearAll()
            return .platformFailure
        }
        return .applied
    }

    /// What the session may pause beside a scheduled pause, with the monitor's rule: what the store already pauses
    /// stays, a session that pauses nothing yet takes what fits in alphabetical order, and additions come all
    /// together or wait for room. Without a schedule store, the request as it is.
    private func withinShare(
        domains: [String],
        tokens: Set<ApplicationToken>,
        store: IosEnforcementSettingsStore
    ) -> (domains: [String], tokens: Set<ApplicationToken>) {
        guard let schedule = scheduleStore() else { return (domains, tokens) }
        let encoder = JSONEncoder()
        let pausedTokens = store.shieldedApplications ?? []
        var byData: [Data: ApplicationToken] = [:]
        for token in tokens.union(pausedTokens) {
            if let data = try? encoder.encode(token) {
                byData[data] = token
            }
        }
        let encoded = { (set: Set<ApplicationToken>) in Set(set.compactMap { try? encoder.encode($0) }) }
        var pausedHosts = Set<String>()
        if case let .specific(current) = store.blockedWebFilter {
            pausedHosts = Set(current.compactMap(\.domain))
        }
        // A website open in its window is still held: an open window frees no room.
        if let record = composeLock?.readManualWindows(), now() < record.end {
            pausedHosts.formUnion(record.held)
        }
        let part = PausePlanner.Part(
            id: "session", start: .distantPast,
            currentDomains: Set(domains), currentTokens: encoded(tokens),
            heldDomains: pausedHosts, heldTokens: encoded(pausedTokens)
        )
        // What scheduled parts on a break hold is not applied now but returns at the break's end, so it stays occupied.
        let onBreak: (domains: Set<String>, tokens: Set<Data>) = composeLock?.heldDuringBreak(at: Date()) ?? (domains: [], tokens: [])
        // A scheduled website open in its window is still held: an open window frees no room.
        let running = composeLock?.heldByRunning(at: now(), calendar: calendar()) ?? []
        let occupiedDomains: Set<String> = Set(schedule.shieldedHosts).union(onBreak.domains).union(running)
        let occupiedTokens: Set<Data> = encoded(schedule.shieldedTokens).union(onBreak.tokens)
        let plan = PausePlanner.plan([part], occupiedDomains: occupiedDomains, occupiedTokens: occupiedTokens)
        return (plan.domains.sorted(), Set(plan.tokens.compactMap { byData[$0] }))
    }

    private func authorizationRefusal() -> IosEnforcementOutcome? {
        authorization().refusal
    }

    private func webDomains(from hosts: [String]) -> Set<WebDomain> {
        PosatoWebDomains.domains(from: hosts)
    }

    private func performOnMain(_ action: @escaping () -> Void) {
        if Thread.isMainThread {
            action()
        } else {
            DispatchQueue.main.async(execute: action)
        }
    }
}
