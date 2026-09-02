import Combine
import CryptoKit
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

struct StoredApplicationMapping: Codable, Equatable {
    let slot: Int
    let token: Data
}

private struct StoredApplicationMappings: Codable {
    let version: Int
    let mappings: [StoredApplicationMapping]
}

struct ApplicationMappingsStore {
    private static let version = 1
    private static let maximumMappings = 64
    private static let maximumFileBytes = 1_048_576
    private static let maximumTokenBytes = 65_536

    let fileURL: URL

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
        return stored.mappings.sorted { $0.slot < $1.slot }
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
              Set(mappings.map(\.slot)).count == mappings.count,
              Set(mappings.map(\.token)).count == mappings.count,
              mappings.allSatisfy({ mapping in
                  (1...Self.maximumMappings).contains(mapping.slot) &&
                      !mapping.token.isEmpty &&
                      mapping.token.count <= Self.maximumTokenBytes
              }) else {
            throw ApplicationMappingsStoreError.corruption
        }
    }
}

enum ApplicationMappingsStoreError: Error {
    case corruption
}

final class IosFamilyControlsApplicationMappingsProvider: NSObject, IosApplicationMappingsProvider {
    weak var presenter: UIViewController?

    private let storeFactory: () throws -> ApplicationMappingsStore
    private var pickerController: UIViewController?
    private var chooseCompletion: ((IosApplicationMappingsResponse) -> Void)?

    init(
        storeFactory: @escaping () throws -> ApplicationMappingsStore = { try ApplicationMappingsStore.live() }
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
        guard pickerController == nil, chooseCompletion == nil else {
            completion(response(outcome: .pickerFailure))
            return
        }
        chooseCompletion = completion
        operation.install { [weak self] in
            self?.performOnMain {
                self?.completeChoose(with: self?.response(outcome: .cancelled), dismissPicker: true)
            }
        }
        guard chooseCompletion != nil else { return }
        let continueWithPicker = {
            guard self.chooseCompletion != nil else { return }
            guard self.isAuthorizationApproved else {
                self.completeChoose(with: self.loadResponse())
                return
            }
            self.presentPicker()
        }
        if AuthorizationCenter.shared.authorizationStatus == .notDetermined {
            Task { @MainActor in
                do {
                    try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                    continueWithPicker()
                } catch let error as FamilyControlsError {
                    self.completeChoose(with: self.authorizationFailureResponse(error))
                } catch {
                    self.completeChoose(with: self.response(outcome: .unavailable, access: .unavailable))
                }
            }
        } else {
            continueWithPicker()
        }
#endif
    }

    private func presentPicker() {
        guard chooseCompletion != nil else { return }
        guard let presenter else {
            completeChoose(with: response(outcome: .pickerFailure))
            return
        }
        let initialTokens: Set<ApplicationToken>
        do {
            let mappings = try validatedMappings(try storeFactory().load())
            initialTokens = Set(try mappings.map { mapping in
                try JSONDecoder().decode(ApplicationToken.self, from: mapping.token)
            })
        } catch ApplicationMappingsStoreError.corruption {
            completeChoose(with: response(outcome: .corruption))
            return
        } catch {
            completeChoose(with: response(outcome: .storageFailure))
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
        controller.presentationController?.delegate = self
        presenter.present(controller, animated: true)
    }

    private func finishPicker(result: ApplicationPickerResult) {
        guard chooseCompletion != nil else { return }
        if case .invalidSelection = result {
            completeChoose(with: response(outcome: .invalidSelection), dismissPicker: true)
            return
        }
        guard case let .selected(tokens) = result else {
            completeChoose(with: response(outcome: .cancelled), dismissPicker: true)
            return
        }
        guard isAuthorizationApproved else {
            completeChoose(with: loadResponse(), dismissPicker: true)
            return
        }
        do {
            let store = try storeFactory()
            let current = try store.load()
            let updated = try reconcile(tokens: tokens, into: current)
            try store.save(updated)
            completeChoose(with: response(outcome: .success, mappings: updated), dismissPicker: true)
        } catch ApplicationMappingsStoreError.corruption {
            completeChoose(with: response(outcome: .corruption), dismissPicker: true)
        } catch ApplicationMappingsProviderError.capacity {
            completeChoose(with: response(outcome: .capacity), dismissPicker: true)
        } catch {
            completeChoose(with: response(outcome: .storageFailure), dismissPicker: true)
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
        guard updated.count + newData.count <= 64 else {
            throw ApplicationMappingsProviderError.capacity
        }
        var availableSlots = Array(1...64).filter { slot in !Set(updated.map(\.slot)).contains(slot) }
        for tokenData in newData {
            updated.append(StoredApplicationMapping(slot: availableSlots.removeFirst(), token: tokenData))
        }
        return updated.sorted { $0.slot < $1.slot }
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
        return loadResponse(outcome: .unavailable, access: .unavailable)
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
        with response: IosApplicationMappingsResponse?,
        dismissPicker: Bool = false
    ) {
        guard let response, let completion = chooseCompletion else { return }
        chooseCompletion = nil
        let controller = pickerController
        pickerController = nil
        if dismissPicker {
            controller?.dismiss(animated: true)
        }
        completion(response)
    }

    private func response(
        outcome: IosApplicationMappingsOutcome,
        access: IosApplicationMappingsAccess? = nil,
        mappings: [StoredApplicationMapping] = []
    ) -> IosApplicationMappingsResponse {
        let references = mappings.map { mapping in
            IosApplicationMappingReference(identifier: identifier(for: mapping.token), slot: Int32(mapping.slot))
        }
        return IosApplicationMappingsResponse(
            outcome: outcome,
            access: access ?? currentAccess(),
            mappings: references
        )
    }

    func identifier(for token: Data) -> String {
        return SHA256.hash(data: token).map { String(format: "%02x", $0) }.joined()
    }

    func validatedMappings(_ mappings: [StoredApplicationMapping]) throws -> [StoredApplicationMapping] {
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
        completeChoose(with: response(outcome: .cancelled))
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
