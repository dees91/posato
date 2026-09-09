import Foundation

enum ZoneFetchNative: Equatable, Sendable {
  case found
  case missing
  case retryable
  case unknownOutcome
}

enum ZoneSaveNative: Equatable, Sendable {
  case created
  case alreadyExists
  case retryable
  case unknownOutcome
}

enum AnchorReadNative: Equatable, Sendable {
  case found(Data)
  case missing
  case retryable
  case unknownOutcome
  case integrityFailure
}

enum AnchorCreateNative: Equatable, Sendable {
  case created
  case conflict
  case retryable
  case unknownOutcome
  case integrityFailure
}

enum BundleSaveNative: Equatable, Sendable {
  case created
  case identical
  case conflict
  case retryable
  case unknownOutcome
  case integrityFailure
}

struct ChangePageNative: Equatable, Sendable {
  var bundleIdentifier: Data?
  var bundle: Data?
  var moreComing: Bool
  var cursor: Data
}

enum ChangeFetchNative: Equatable, Sendable {
  case tokenExpired
  case page(ChangePageNative)
  case zoneMissing
  case retryable
  case unknownOutcome
  case integrityFailure
}

enum ZoneDeleteNative: Equatable, Sendable {
  case deletedAndAbsent
  case retryable
  case unknownOutcome
}
