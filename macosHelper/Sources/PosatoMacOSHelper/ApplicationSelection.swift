import AppKit
import Foundation
import PosatoMacOSServiceCore
import Security
import UniformTypeIdentifiers

private let noNetworkValidationFlag: UInt32 = 1 << 29
private let adHocSignatureFlag: UInt32 = 0x0002

enum ApplicationChoice: CustomStringConvertible, CustomDebugStringConvertible {
  case selected([ApplicationSelectionCandidate])
  case cancelled
  case capacity
  case rejected

  var description: String {
    return "ApplicationChoice(redacted)"
  }

  var debugDescription: String {
    return description
  }
}

struct ApplicationSelectionCandidate: CustomStringConvertible, CustomDebugStringConvertible {
  let url: URL
  let displayName: String
  let bundleIdentifier: String?

  var description: String {
    return "ApplicationSelectionCandidate(redacted)"
  }

  var debugDescription: String {
    return description
  }
}

@MainActor
protocol ApplicationChoosing {
  func choose() -> ApplicationChoice
}

protocol ApplicationIdentityInspecting {
  func designatedRequirement(for url: URL) throws -> Data
}

struct AppKitApplicationChooser: ApplicationChoosing {
  func choose() -> ApplicationChoice {
    let application = NSApplication.shared
    application.setActivationPolicy(.accessory)
    application.activate()
    let panel = NSOpenPanel()
    panel.title = "Choose applications"
    panel.prompt = "Choose"
    panel.directoryURL = URL(fileURLWithPath: "/Applications", isDirectory: true)
    panel.allowedContentTypes = [.applicationBundle]
    panel.allowsMultipleSelection = true
    panel.canChooseDirectories = false
    panel.canChooseFiles = true
    panel.resolvesAliases = true
    panel.allowsOtherFileTypes = false
    panel.treatsFilePackagesAsDirectories = false
    signal(SIGTERM, SIG_IGN)
    let terminationSource = DispatchSource.makeSignalSource(signal: SIGTERM, queue: .main)
    terminationSource.setEventHandler {
      panel.cancel(nil)
      signal(SIGTERM, SIG_DFL)
      raise(SIGTERM)
    }
    terminationSource.resume()
    defer {
      terminationSource.cancel()
      signal(SIGTERM, SIG_DFL)
    }
    let response = panel.runModal()
    guard response == .OK else {
      return .cancelled
    }
    guard !panel.urls.isEmpty,
      panel.urls.count <= ApplicationSelectionLimits.maximumMappings
    else {
      return panel.urls.isEmpty ? .rejected : .capacity
    }
    var candidates: [ApplicationSelectionCandidate] = []
    candidates.reserveCapacity(panel.urls.count)
    for selectedURL in panel.urls {
      guard let candidate = candidate(for: selectedURL) else {
        return .rejected
      }
      candidates.append(candidate)
    }
    return .selected(candidates)
  }

  private func candidate(for selectedURL: URL) -> ApplicationSelectionCandidate? {
    let url = selectedURL.resolvingSymlinksInPath().standardizedFileURL
    guard url.pathExtension.lowercased() == "app",
      let values = try? url.resourceValues(forKeys: [.contentTypeKey, .localizedNameKey]),
      values.contentType?.conforms(to: .applicationBundle) == true,
      let bundle = Bundle(url: url),
      let displayName = displayName(bundle: bundle, url: url, localizedName: values.localizedName)
    else {
      return nil
    }
    return ApplicationSelectionCandidate(
      url: url,
      displayName: displayName,
      bundleIdentifier: bundle.bundleIdentifier
    )
  }

  private func displayName(
    bundle: Bundle,
    url: URL,
    localizedName: String?
  ) -> String? {
    let rawName =
      bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
      ?? bundle.object(forInfoDictionaryKey: "CFBundleName") as? String
      ?? localizedName
      ?? url.deletingPathExtension().lastPathComponent
    let name = rawName
      .precomposedStringWithCanonicalMapping
      .trimmingCharacters(in: .whitespacesAndNewlines)
    guard !name.isEmpty,
      name.utf8.count <= ApplicationSelectionLimits.maximumDisplayNameBytes,
      name.unicodeScalars.allSatisfy({ scalar in
        !CharacterSet.controlCharacters.contains(scalar)
      })
    else {
      return nil
    }
    return name
  }
}

struct SecurityApplicationIdentityInspector: ApplicationIdentityInspecting {
  func designatedRequirement(for url: URL) throws -> Data {
    var staticCode: SecStaticCode?
    guard SecStaticCodeCreateWithPath(url as CFURL, [], &staticCode) == errSecSuccess,
      let staticCode
    else {
      throw CodeSigningFailure.signatureRejected
    }
    let validationFlags = SecCSFlags(
      rawValue: kSecCSCheckAllArchitectures | kSecCSStrictValidate | noNetworkValidationFlag
    )
    guard SecStaticCodeCheckValidity(staticCode, validationFlags, nil) == errSecSuccess else {
      throw CodeSigningFailure.signatureRejected
    }
    var signingInformation: CFDictionary?
    guard
      SecCodeCopySigningInformation(
        staticCode,
        SecCSFlags(rawValue: kSecCSSigningInformation),
        &signingInformation
      ) == errSecSuccess,
      let values = signingInformation as? [String: Any],
      let flags = values[kSecCodeInfoFlags as String] as? NSNumber,
      flags.uint32Value & adHocSignatureFlag == 0
    else {
      throw CodeSigningFailure.signatureRejected
    }
    var requirement: SecRequirement?
    guard SecCodeCopyDesignatedRequirement(staticCode, [], &requirement) == errSecSuccess,
      let requirement
    else {
      throw CodeSigningFailure.signatureRejected
    }
    var data: CFData?
    guard SecRequirementCopyData(requirement, [], &data) == errSecSuccess,
      let requirementData = data as Data?,
      !requirementData.isEmpty,
      requirementData.count <= ApplicationSelectionLimits.maximumRequirementBytes
    else {
      throw CodeSigningFailure.signatureRejected
    }
    return requirementData
  }
}

@MainActor
struct ApplicationSelectionService {
  private let chooser: ApplicationChoosing
  private let inspector: ApplicationIdentityInspecting

  init(
    chooser: ApplicationChoosing = AppKitApplicationChooser(),
    inspector: ApplicationIdentityInspecting = SecurityApplicationIdentityInspector()
  ) {
    self.chooser = chooser
    self.inspector = inspector
  }

  func select() throws -> ApplicationSelectionPayload {
    switch chooser.choose() {
    case .cancelled:
      return try ApplicationSelectionPayload(outcome: .cancelled)
    case .capacity:
      return try ApplicationSelectionPayload(outcome: .capacity)
    case .rejected:
      return try ApplicationSelectionPayload(outcome: .invalidOrUnsigned)
    case .selected(let candidates):
      guard !candidates.isEmpty else {
        return try ApplicationSelectionPayload(outcome: .capacity)
      }
      let posatoIdentifier = ServiceContract.applicationIdentifier
      let posatoPrefix = "\(posatoIdentifier)."
      var identities: [SelectedApplicationIdentity] = []
      var requirements: Set<Data> = []
      identities.reserveCapacity(candidates.count)
      for candidate in candidates {
        let isPosatoBundle =
          candidate.bundleIdentifier == posatoIdentifier
          || candidate.bundleIdentifier?.hasPrefix(posatoPrefix) == true
        if isPosatoBundle {
          return try ApplicationSelectionPayload(outcome: .selfSelection)
        }
        do {
          let requirement = try inspector.designatedRequirement(for: candidate.url)
          if requirements.insert(requirement).inserted {
            identities.append(
              try SelectedApplicationIdentity(
                displayName: candidate.displayName,
                designatedRequirement: requirement
              )
            )
          }
        } catch {
          return try ApplicationSelectionPayload(outcome: .invalidOrUnsigned)
        }
      }
      guard !identities.isEmpty else {
        return try ApplicationSelectionPayload(outcome: .invalidOrUnsigned)
      }
      return try ApplicationSelectionPayload(outcome: .success, applications: identities)
    }
  }
}
