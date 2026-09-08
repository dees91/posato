import CryptoKit
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

    private static var defaultCapable: Bool {
#if targetEnvironment(simulator) || !POSATO_FAMILY_CONTROLS_DEVELOPMENT
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
            try ApplicationMappingsStore.liveMigrated().load()
        }
    ) {
        self.storeFactory = storeFactory
        self.authorization = authorization
        self.isCapable = isCapable
        self.storedMappings = storedMappings
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
            self.storeFactory().clearAll()
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
        let expectedFilter: WebContentSettings.FilterPolicy? =
            request.domains.isEmpty ? nil : .specific(Set(request.domains.map(WebDomain.init(domain:))))
        let expectedApplications: Set<ApplicationToken>? = tokens.isEmpty ? nil : tokens
        let store = storeFactory()
        store.blockedWebFilter = expectedFilter
        store.shieldedApplications = expectedApplications
        guard store.blockedWebFilter == expectedFilter,
              store.shieldedApplications == expectedApplications
        else {
            store.clearAll()
            return .platformFailure
        }
        return .applied
    }

    private func authorizationRefusal() -> IosEnforcementOutcome? {
        authorization().refusal
    }

    private func performOnMain(_ action: @escaping () -> Void) {
        if Thread.isMainThread {
            action()
        } else {
            DispatchQueue.main.async(execute: action)
        }
    }
}
