import Darwin
import Foundation
import PosatoMacOSServiceCore

final class BrowserDomainSession: @unchecked Sendable {
  private let payload: BrowserDomainConfigurePayload
  private var proxy: BoundedHTTPProxy?
  private var presentation = BrowserPresentationAdapter()
  private let presentationLock = NSLock()
  private let chain = ProxyChainValidator()
  private let compatibility = NetworkCompatibility(reader: SystemNetworkCompatibilityReader())
  private(set) var port: UInt16 = 0

  init(payload: BrowserDomainConfigurePayload) {
    self.payload = payload
  }

  func start() throws -> UInt16 {
    guard compatibility.activationIsAllowed() else {
      throw BrowserDomainSessionFailure.incompatible
    }
    let selected = Set(payload.domains)
    let proxy = BoundedHTTPProxy(
      selectedHosts: selected,
      sessionEndEpochMilliseconds: payload.sessionEndEpochMilliseconds,
      blockedRequestHandler: { [weak self] in
        self?.presentBlockedPage()
      }
    )
    let port = try proxy.start()
    self.proxy = proxy
    self.port = port
    do {
      try selfTest(port: port)
      try validateCandidateChain(port: port, selected: selected)
    } catch {
      stop()
      throw error
    }
    return port
  }

  func validateEffectiveChain() -> Bool {
    let settings = CFNetworkCopySystemProxySettings()?.takeRetainedValue()
    return chain.containsOnlyLoopback(
      selectedHosts: Set(payload.domains),
      port: port,
      settings: settings
    )
  }

  func stop() {
    presentationLock.lock()
    presentation.reset()
    presentationLock.unlock()
    proxy?.stop()
    proxy = nil
    port = 0
  }

  private func presentBlockedPage() {
    presentationLock.lock()
    defer { presentationLock.unlock() }
    _ = presentation.presentBlockedPage(port: port, selectedHosts: Set(payload.domains))
  }

  private func validateCandidateChain(port: UInt16, selected: Set<String>) throws {
    let live = CFNetworkCopySystemProxySettings()?.takeRetainedValue()
    let overlay = chain.overlayLoopbackSettings(live, port: port)
    guard chain.containsOnlyLoopback(selectedHosts: selected, port: port, settings: overlay)
    else {
      throw BrowserDomainSessionFailure.incompatible
    }
  }

  private func selfTest(port: UInt16) throws {
    let descriptor = try connectLoopback(port: port)
    defer { Darwin.close(descriptor) }
    try sendBlockedProbe(descriptor: descriptor, port: port)
    let response = receiveResponse(descriptor: descriptor)
    try verifyBlockedResponse(response)
  }

  private func connectLoopback(port: UInt16) throws -> Int32 {
    let descriptor = socket(AF_INET, SOCK_STREAM, 0)
    guard descriptor >= 0 else {
      throw BrowserDomainSessionFailure.unavailable
    }
    var timeout = timeval(tv_sec: 2, tv_usec: 0)
    setsockopt(
      descriptor,
      SOL_SOCKET,
      SO_RCVTIMEO,
      &timeout,
      socklen_t(MemoryLayout.size(ofValue: timeout))
    )
    var address = sockaddr_in()
    address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
    address.sin_family = sa_family_t(AF_INET)
    address.sin_port = port.bigEndian
    address.sin_addr = in_addr(s_addr: inet_addr("127.0.0.1"))
    let connected = withUnsafePointer(to: &address) { pointer in
      pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockaddrPointer in
        Darwin.connect(descriptor, sockaddrPointer, socklen_t(MemoryLayout<sockaddr_in>.size))
      }
    }
    guard connected == 0 else {
      Darwin.close(descriptor)
      throw BrowserDomainSessionFailure.unavailable
    }
    return descriptor
  }

  private func sendBlockedProbe(descriptor: Int32, port: UInt16) throws {
    let request = Data(
      "GET /blocked HTTP/1.1\r\nHost: 127.0.0.1:\(port)\r\nConnection: close\r\n\r\n".utf8
    )
    guard
      request.withUnsafeBytes({ bytes in
        Darwin.send(descriptor, bytes.baseAddress, bytes.count, 0)
      }) == request.count
    else {
      throw BrowserDomainSessionFailure.unavailable
    }
  }

  private func receiveResponse(descriptor: Int32) -> Data {
    var response = Data()
    var buffer = [UInt8](repeating: 0, count: 4_096)
    while true {
      let count = Darwin.recv(descriptor, &buffer, buffer.count, 0)
      if count <= 0 {
        break
      }
      response.append(buffer, count: count)
    }
    return response
  }

  private func verifyBlockedResponse(_ response: Data) throws {
    guard let text = String(bytes: response, encoding: .utf8),
      text.hasPrefix("HTTP/1.1 200 OK\r\n"),
      text.contains("Cache-Control: no-store"),
      text.contains("This site is paused")
    else {
      throw BrowserDomainSessionFailure.unavailable
    }
  }
}

enum BrowserDomainSessionFailure: Error {
  case incompatible
  case unavailable
}
