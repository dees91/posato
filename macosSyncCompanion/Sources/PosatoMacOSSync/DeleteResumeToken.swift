import CloudKit
import Foundation

/// Resume phases for the delete path, carried as the first byte of the
/// resume token so a killed request re-enters the right mode without any
/// caller-held state.
enum DeleteResumePhase: UInt8, Sendable {
  case traverse = 0
  case verify = 1
}

/// Splits a delete-path resume token into its phase and server token. A nil
/// or empty token starts a fresh traversal. Returns nil for wire garbage,
/// which the caller reports as an integrity failure.
func splitDeleteResumeToken(_ token: Data?) -> (phase: DeleteResumePhase, server: Data?)? {
  guard let token, !token.isEmpty else {
    return (.traverse, nil)
  }
  guard token.count <= SyncLimits.cursorBytes + SyncLimits.deleteResumePhaseBytes,
    let phase = DeleteResumePhase(rawValue: token[0])
  else {
    return nil
  }
  let server = token.dropFirst()
  guard server.count <= SyncLimits.cursorBytes else {
    return nil
  }
  return (phase, server.isEmpty ? nil : Data(server))
}

func encodeDeleteResumeToken(phase: DeleteResumePhase, server: Data?) -> Data {
  var token = Data([phase.rawValue])
  if let server {
    token.append(server)
  }
  return token
}
