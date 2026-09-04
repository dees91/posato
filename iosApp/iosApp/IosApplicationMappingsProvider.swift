import Combine
import FamilyControls
import ManagedSettings
import PosatoShared
import SwiftUI
import UIKit

final class ApplicationMappingsOperation: IosApplicationMappingsOperation {
    private let lock = NSLock()
    private var cancellation: (() -> Void)?
    private var isCancelled = false

    init(cancellation: (() -> Void)? = nil) {
        self.cancellation = cancellation
    }

    func install(_ cancellation: @escaping () -> Void) {
        lock.lock()
        if isCancelled {
            lock.unlock()
            cancellation()
        } else {
            self.cancellation = cancellation
            lock.unlock()
        }
    }

    func cancel() {
        lock.lock()
        isCancelled = true
        let handler = cancellation
        cancellation = nil
        lock.unlock()
        handler?()
    }
}

final class ApplicationMappingsObservation: IosApplicationMappingsObservation {
    private var cancellation: (() -> Void)?

    init(cancellation: @escaping () -> Void) {
        self.cancellation = cancellation
    }

    func cancel() {
        cancellation?()
        cancellation = nil
    }
}

final class ApplicationMappingsChooseSession {
    private var generation: UInt64 = 0
    private var completion: ((IosApplicationMappingsResponse) -> Void)?

    var isActive: Bool {
        completion != nil
    }

    func begin(
        replacingActiveWith replacement: IosApplicationMappingsResponse,
        _ completion: @escaping (IosApplicationMappingsResponse) -> Void
    ) -> UInt64 {
        if isActive {
            complete(generation, with: replacement)
        }
        generation += 1
        self.completion = completion
        return generation
    }

    func isCurrent(_ generation: UInt64) -> Bool {
        generation == self.generation && completion != nil
    }

    @discardableResult
    func complete(_ generation: UInt64, with response: IosApplicationMappingsResponse) -> Bool {
        guard isCurrent(generation), let completion else {
            return false
        }
        self.completion = nil
        completion(response)
        return true
    }
}

enum ApplicationMappingsPresentationGate {
    static func canPresent(from presenter: UIViewController) -> Bool {
        presenter.presentedViewController == nil &&
            presenter.view.window != nil &&
            presenter.transitionCoordinator == nil &&
            !presenter.isBeingPresented &&
            !presenter.isBeingDismissed
    }

    static func present(
        _ controller: UIViewController,
        from presenter: UIViewController,
        animated: Bool = true,
        completion: @escaping (Bool) -> Void
    ) {
        guard canPresent(from: presenter) else {
            completion(false)
            return
        }
        presenter.present(controller, animated: animated) {
            completion(presenter.presentedViewController === controller)
        }
    }
}

struct StoredApplicationMapping: Codable, Equatable {
    let token: Data
}

private struct StoredApplicationMappings: Codable {
    let version: Int
    let mappings: [StoredApplicationMapping]
}

struct ApplicationMappingsStore {
    private static let version = 1
    static let maximumMappings = 64
    static let appGroupIdentifier = "group.app.posato.ios.session"
    private static let maximumFileBytes = 1_048_576
    private static let maximumTokenBytes = 65_536

    let fileURL: URL

    static func liveMigrated(
        fileManager: FileManager = .default,
        groupContainer: URL? = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupIdentifier
        )
    ) throws -> ApplicationMappingsStore {
        guard let groupContainer else {
            return try live(fileManager: fileManager)
        }
        return try migrate(
            privateStore: live(fileManager: fileManager),
            groupContainer: groupContainer,
            fileManager: fileManager
        )
    }

    static func migrate(
        privateStore: ApplicationMappingsStore,
        groupContainer: URL,
        fileManager: FileManager = .default
    ) throws -> ApplicationMappingsStore {
        let groupDirectory = groupContainer.appendingPathComponent("ApplicationMappings", isDirectory: true)
        let groupStore = ApplicationMappingsStore(
            fileURL: groupDirectory.appendingPathComponent("mappings-v1.json")
        )
        if fileManager.fileExists(atPath: groupStore.fileURL.path) {
            _ = try groupStore.load()
            if fileManager.fileExists(atPath: privateStore.fileURL.path) {
                try? fileManager.removeItem(at: privateStore.fileURL)
            }
            return groupStore
        }
        if fileManager.fileExists(atPath: privateStore.fileURL.path) {
            let current = try privateStore.load()
            try writeGroupCopy(current, groupDirectory: groupDirectory, groupURL: groupStore.fileURL, fileManager: fileManager)
            try fileManager.removeItem(at: privateStore.fileURL)
        } else {
            try writeGroupCopy([], groupDirectory: groupDirectory, groupURL: groupStore.fileURL, fileManager: fileManager)
        }
        return groupStore
    }

    private static func writeGroupCopy(
        _ mappings: [StoredApplicationMapping],
        groupDirectory: URL,
        groupURL: URL,
        fileManager: FileManager
    ) throws {
        try fileManager.createDirectory(
            at: groupDirectory,
            withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
        )
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableDirectory = groupDirectory
        try mutableDirectory.setResourceValues(values)
        let data = try JSONEncoder().encode(StoredApplicationMappings(version: version, mappings: mappings))
        try data.write(to: groupURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        guard try Data(contentsOf: groupURL) == data,
              try ApplicationMappingsStore(fileURL: groupURL).load() == mappings
        else {
            try? fileManager.removeItem(at: groupURL)
            throw ApplicationMappingsStoreError.migrationFailed
        }
    }

    static func live(fileManager: FileManager = .default) throws -> ApplicationMappingsStore {
        let applicationSupport = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return try create(
            in: applicationSupport
            .appendingPathComponent("Posato", isDirectory: true)
            .appendingPathComponent("ApplicationMappings", isDirectory: true),
            fileManager: fileManager
        )
    }

    static func create(
        in directory: URL,
        fileManager: FileManager = .default
    ) throws -> ApplicationMappingsStore {
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableDirectory = directory
        try mutableDirectory.setResourceValues(values)
        return ApplicationMappingsStore(fileURL: directory.appendingPathComponent("mappings-v1.json"))
    }

    func load() throws -> [StoredApplicationMapping] {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return []
        }
        let attributes = try FileManager.default.attributesOfItem(atPath: fileURL.path)
        guard let size = attributes[.size] as? NSNumber,
              size.intValue <= Self.maximumFileBytes else {
            throw ApplicationMappingsStoreError.corruption
        }
        let data = try Data(contentsOf: fileURL)
        let stored: StoredApplicationMappings
        do {
            stored = try JSONDecoder().decode(StoredApplicationMappings.self, from: data)
        } catch is DecodingError {
            throw ApplicationMappingsStoreError.corruption
        }
        guard stored.version == Self.version else {
            throw ApplicationMappingsStoreError.corruption
        }
        try validate(stored.mappings)
        return stored.mappings
    }

    func save(_ mappings: [StoredApplicationMapping]) throws {
        try validate(mappings)
        let stored = StoredApplicationMappings(version: Self.version, mappings: mappings)
        let data = try JSONEncoder().encode(stored)
        guard data.count <= Self.maximumFileBytes else {
            throw ApplicationMappingsStoreError.corruption
        }
        try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
    }

    private func validate(_ mappings: [StoredApplicationMapping]) throws {
        guard mappings.count <= Self.maximumMappings,
              Set(mappings.map(\.token)).count == mappings.count,
              mappings.allSatisfy({ mapping in
                  !mapping.token.isEmpty &&
                      mapping.token.count <= Self.maximumTokenBytes
              }) else {
            throw ApplicationMappingsStoreError.corruption
        }
    }
}

enum ApplicationMappingsStoreError: Error {
    case corruption
    case migrationFailed
}

final class IosFamilyControlsApplicationMappingsProvider: NSObject, IosApplicationMappingsProvider {
    weak var presenter: UIViewController?

    private let storeFactory: () throws -> ApplicationMappingsStore
    private let chooseSession = ApplicationMappingsChooseSession()
    private var pickerController: UIViewController?
    private var pickerGeneration: UInt64 = 0

    init(
        storeFactory: @escaping () throws -> ApplicationMappingsStore = { try ApplicationMappingsStore.liveMigrated() }
    ) {
        self.storeFactory = storeFactory
    }

    func load(completion: @escaping (IosApplicationMappingsResponse) -> Void) {
        performOnMain {
            completion(self.loadResponse())
        }
    }

    func choose(completion: @escaping (IosApplicationMappingsResponse) -> Void) -> IosApplicationMappingsOperation {
        let operation = ApplicationMappingsOperation()
        performOnMain {
            self.beginChoose(operation: operation, completion: completion)
        }
        return operation
    }

    func remove(
        identifier: String,
        completion: @escaping (IosApplicationMappingsResponse) -> Void
    ) {
        performOnMain {
            do {
                let store = try self.storeFactory()
                let current = try self.validatedMappings(try store.load())
                let retained = current.filter { self.identifier(for: $0.token) != identifier }
                try store.save(retained)
                completion(self.response(outcome: .success, mappings: retained))
            } catch ApplicationMappingsStoreError.corruption {
                completion(self.response(outcome: .corruption))
            } catch {
                completion(self.response(outcome: .storageFailure))
            }
        }
    }

    func clear(completion: @escaping (IosApplicationMappingsResponse) -> Void) {
        performOnMain {
            do {
                try self.storeFactory().save([])
                completion(self.response(outcome: .success))
            } catch {
                completion(self.response(outcome: .storageFailure))
            }
        }
    }

    func observeInvalidations(handler: @escaping () -> Void) -> IosApplicationMappingsObservation {
#if targetEnvironment(simulator) || !POSATO_FAMILY_CONTROLS_DEVELOPMENT
        return ApplicationMappingsObservation(cancellation: {})
#else
        let observation: AnyCancellable = performOnMainSync {
            AuthorizationCenter.shared.$authorizationStatus
                .dropFirst()
                .receive(on: DispatchQueue.main)
                .sink { _ in handler() }
        }
        return ApplicationMappingsObservation { observation.cancel() }
#endif
    }

    private func beginChoose(
        operation: ApplicationMappingsOperation,
        completion: @escaping (IosApplicationMappingsResponse) -> Void
    ) {
#if targetEnvironment(simulator) || !POSATO_FAMILY_CONTROLS_DEVELOPMENT
        completion(response(outcome: .unavailable, access: .unavailable))
#else
        guard pickerController == nil else {
            completion(response(outcome: .pickerFailure))
            return
        }
        let generation = chooseSession.begin(
            replacingActiveWith: response(outcome: .pickerFailure),
            completion
        )
        operation.install { [weak self] in
            guard let self else { return }
            self.performOnMain {
                self.completeChoose(
                    generation,
                    with: self.response(outcome: .cancelled),
                    dismissPicker: true
                )
            }
        }
        guard chooseSession.isCurrent(generation) else { return }
        if AuthorizationCenter.shared.authorizationStatus == .notDetermined {
            Task { @MainActor in
                do {
                    try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                    self.continueWithPicker(generation: generation)
                } catch let error as FamilyControlsError {
                    self.completeChoose(generation, with: self.authorizationFailureResponse(error))
                } catch {
                    self.completeChoose(generation, with: self.response(outcome: .pickerFailure))
                }
            }
        } else {
            continueWithPicker(generation: generation)
        }
#endif
    }

    private func continueWithPicker(generation: UInt64) {
        guard chooseSession.isCurrent(generation) else { return }
        guard isAuthorizationApproved else {
            completeChoose(generation, with: loadResponse(outcome: .accessChanged, access: currentAccess()))
            return
        }
        presentPicker(generation: generation)
    }

    private func presentPicker(generation: UInt64) {
        guard chooseSession.isCurrent(generation) else { return }
        guard let presenter else {
            completeChoose(generation, with: response(outcome: .pickerFailure))
            return
        }
        let initialTokens: Set<ApplicationToken>
        do {
            let mappings = try validatedMappings(try storeFactory().load())
            initialTokens = Set(try mappings.map { mapping in
                try JSONDecoder().decode(ApplicationToken.self, from: mapping.token)
            })
        } catch ApplicationMappingsStoreError.corruption {
            completeChoose(generation, with: response(outcome: .corruption))
            return
        } catch {
            completeChoose(generation, with: response(outcome: .storageFailure))
            return
        }
        let controller = UIHostingController(
            rootView: ApplicationPickerView(
                initialTokens: initialTokens,
                completion: { [weak self] result in
                    self?.finishPicker(result: result)
                }
            )
        )
        controller.modalPresentationStyle = .formSheet
        pickerController = controller
        pickerGeneration = generation
        controller.presentationController?.delegate = self
        ApplicationMappingsPresentationGate.present(controller, from: presenter) { [weak self] presented in
            guard let self else { return }
            self.performOnMain {
                if !presented {
                    self.completeChoose(generation, with: self.response(outcome: .pickerFailure))
                }
            }
        }
    }

    private func finishPicker(result: ApplicationPickerResult) {
        let generation = pickerGeneration
        guard chooseSession.isCurrent(generation) else { return }
        if case .invalidSelection = result {
            completeChoose(generation, with: response(outcome: .invalidSelection), dismissPicker: true)
            return
        }
        guard case let .selected(tokens) = result else {
            completeChoose(generation, with: response(outcome: .cancelled), dismissPicker: true)
            return
        }
        guard isAuthorizationApproved else {
            completeChoose(
                generation,
                with: loadResponse(outcome: .accessChanged, access: currentAccess()),
                dismissPicker: true
            )
            return
        }
        do {
            let store = try storeFactory()
            let current = try store.load()
            let updated = try reconcile(tokens: tokens, into: current)
            try store.save(updated)
            completeChoose(generation, with: response(outcome: .success, mappings: updated), dismissPicker: true)
        } catch ApplicationMappingsStoreError.corruption {
            completeChoose(generation, with: response(outcome: .corruption), dismissPicker: true)
        } catch ApplicationMappingsProviderError.capacity {
            completeChoose(generation, with: response(outcome: .capacity), dismissPicker: true)
        } catch {
            completeChoose(generation, with: response(outcome: .storageFailure), dismissPicker: true)
        }
    }

    private func reconcile(
        tokens: Set<ApplicationToken>,
        into mappings: [StoredApplicationMapping]
    ) throws -> [StoredApplicationMapping] {
        let decoder = JSONDecoder()
        let encoder = JSONEncoder()
        let existing = try mappings.map { mapping in
            (mapping, try decoder.decode(ApplicationToken.self, from: mapping.token))
        }
        var updated = existing
            .filter { _, token in tokens.contains(token) }
            .map(\.0)
        let existingTokens = existing.map(\.1)
        let newData = try tokens
            .filter { token in !existingTokens.contains(token) }
            .map { token in try encoder.encode(token) }
            .sorted { identifier(for: $0) < identifier(for: $1) }
        guard updated.count + newData.count <= ApplicationMappingsStore.maximumMappings else {
            throw ApplicationMappingsProviderError.capacity
        }
        for tokenData in newData {
            updated.append(StoredApplicationMapping(token: tokenData))
        }
        return updated.sorted { identifier(for: $0.token) < identifier(for: $1.token) }
    }

    private func loadResponse() -> IosApplicationMappingsResponse {
        do {
            let mappings = try validatedMappings(try storeFactory().load())
#if targetEnvironment(simulator) || !POSATO_FAMILY_CONTROLS_DEVELOPMENT
            return response(outcome: .unavailable, access: .unavailable, mappings: mappings)
#else
            return response(outcome: .success, access: currentAccess(), mappings: mappings)
#endif
        } catch ApplicationMappingsStoreError.corruption {
            return response(outcome: .corruption)
        } catch {
            return response(outcome: .storageFailure)
        }
    }

    private func currentAccess() -> IosApplicationMappingsAccess {
        let status = AuthorizationCenter.shared.authorizationStatus
        if status == .approved {
            return .ready
        }
        if status == .notDetermined {
            return .authorizationRequired
        }
        if status == .denied {
            return .authorizationDenied
        }
        return .unavailable
    }

    private var isAuthorizationApproved: Bool {
        return AuthorizationCenter.shared.authorizationStatus == .approved
    }

    private func authorizationFailureResponse(_ error: FamilyControlsError) -> IosApplicationMappingsResponse {
        if case .authorizationCanceled = error {
            return response(outcome: .cancelled)
        }
        if case .restricted = error {
            return loadResponse(outcome: .accessChanged, access: .restricted)
        }
        if case .invalidAccountType = error {
            return loadResponse(outcome: .accessChanged, access: .restricted)
        }
        if case .authenticationMethodUnavailable = error {
            return loadResponse(outcome: .accessChanged, access: .restricted)
        }
        // Remaining cases, such as a network failure, are transient. The unavailable
        // outcome is reserved for builds without the capability, so reporting it here
        // would both state something false and hide the action that retries.
        return response(outcome: .pickerFailure)
    }

    private func loadResponse(
        outcome: IosApplicationMappingsOutcome,
        access: IosApplicationMappingsAccess
    ) -> IosApplicationMappingsResponse {
        do {
            let mappings = try validatedMappings(try storeFactory().load())
            return response(outcome: outcome, access: access, mappings: mappings)
        } catch ApplicationMappingsStoreError.corruption {
            return response(outcome: .corruption)
        } catch {
            return response(outcome: .storageFailure)
        }
    }

    private func completeChoose(
        _ generation: UInt64,
        with response: IosApplicationMappingsResponse,
        dismissPicker: Bool = false
    ) {
        guard chooseSession.isCurrent(generation) else { return }
        let controller = pickerController
        let ownsPicker = pickerGeneration == generation
        if ownsPicker {
            pickerController = nil
            pickerGeneration = 0
        }
        if dismissPicker && ownsPicker, let controller {
            controller.dismiss(animated: true) { [weak self] in
                self?.chooseSession.complete(generation, with: response)
            }
        } else {
            chooseSession.complete(generation, with: response)
        }
    }

    private func response(
        outcome: IosApplicationMappingsOutcome,
        access: IosApplicationMappingsAccess? = nil,
        mappings: [StoredApplicationMapping] = []
    ) -> IosApplicationMappingsResponse {
        let references = mappings.map { mapping in
            IosApplicationMappingReference(identifier: identifier(for: mapping.token))
        }
        return IosApplicationMappingsResponse(
            outcome: outcome,
            access: access ?? currentAccess(),
            mappings: references
        )
    }

    func identifier(for token: Data) -> String {
        ApplicationTokenIdentity.identifier(for: token)
    }

    func validatedMappings(_ mappings: [StoredApplicationMapping]) throws -> [StoredApplicationMapping] {
        try ApplicationTokenIdentity.validatedMappings(mappings)
    }

    private func performOnMain(_ action: @escaping () -> Void) {
        if Thread.isMainThread {
            action()
        } else {
            DispatchQueue.main.async(execute: action)
        }
    }

    private func performOnMainSync<T>(_ action: () -> T) -> T {
        if Thread.isMainThread {
            return action()
        }
        return DispatchQueue.main.sync(execute: action)
    }
}

extension IosFamilyControlsApplicationMappingsProvider: UIAdaptivePresentationControllerDelegate {
    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        guard presentationController.presentedViewController === pickerController else { return }
        completeChoose(pickerGeneration, with: response(outcome: .cancelled))
    }
}

private enum ApplicationMappingsProviderError: Error {
    case capacity
}

private enum ApplicationPickerResult {
    case cancelled
    case selected(Set<ApplicationToken>)
    case invalidSelection
}

private struct ApplicationPickerView: View {
    @State private var selection: FamilyActivitySelection
    let completion: (ApplicationPickerResult) -> Void

    init(
        initialTokens: Set<ApplicationToken>,
        completion: @escaping (ApplicationPickerResult) -> Void
    ) {
        var initialSelection = FamilyActivitySelection()
        initialSelection.applicationTokens = initialTokens
        _selection = State(initialValue: initialSelection)
        self.completion = completion
    }

    var body: some View {
        NavigationStack {
            FamilyActivityPicker(selection: $selection)
                .navigationTitle("\(selection.applicationTokens.count) selected")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { completion(.cancelled) }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") {
                            if selection.categoryTokens.isEmpty && selection.webDomainTokens.isEmpty {
                                completion(.selected(selection.applicationTokens))
                            } else {
                                completion(.invalidSelection)
                            }
                        }
                    }
                }
        }
    }
}
