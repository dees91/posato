import Darwin
import Foundation

final class DirectTCPConnection: @unchecked Sendable {
  private struct PendingWrite {
    let data: Data
    var offset: Int
    let completion: (Error?) -> Void
  }

  private let queue: DispatchQueue
  private var descriptor: Int32 = -1
  private var readSource: DispatchSourceRead?
  private var writeSource: DispatchSourceWrite?
  private var pendingRead: (maximumLength: Int, completion: (Data?, Bool, Error?) -> Void)?
  private var pendingWrites: [PendingWrite] = []
  private var cancelled = false

  init(queue: DispatchQueue) {
    self.queue = queue
  }

  func start(
    host: String,
    port: UInt16,
    completion: @escaping @Sendable (Error?) -> Void
  ) {
    let callbackQueue = queue
    DispatchQueue.global(qos: .utility).async { [weak self] in
      let result = Result { try Self.connectSocket(host: host, port: port) }
      callbackQueue.async { [weak self] in
        guard let self else {
          if case .success(let descriptor) = result {
            Darwin.close(descriptor)
          }
          return
        }
        guard !self.cancelled else {
          if case .success(let descriptor) = result {
            Darwin.close(descriptor)
          }
          return
        }
        switch result {
        case .failure(let error):
          completion(error)
        case .success(let descriptor):
          self.configure(descriptor: descriptor)
          completion(nil)
        }
      }
    }
  }

  func send(_ data: Data, completion: @escaping (Error?) -> Void) {
    guard !cancelled, descriptor >= 0 else {
      completion(POSIXError(.ENOTCONN))
      return
    }
    pendingWrites.append(PendingWrite(data: data, offset: 0, completion: completion))
    handleWritable()
  }

  func receive(
    maximumLength: Int,
    completion: @escaping (Data?, Bool, Error?) -> Void
  ) {
    guard !cancelled, descriptor >= 0, pendingRead == nil else {
      completion(nil, false, POSIXError(.ENOTCONN))
      return
    }
    pendingRead = (maximumLength, completion)
    handleReadable()
  }

  func cancel() {
    guard !cancelled else {
      return
    }
    cancelled = true
    pendingRead = nil
    pendingWrites.removeAll()
    readSource?.cancel()
    writeSource?.cancel()
    readSource = nil
    writeSource = nil
    if descriptor >= 0 {
      Darwin.shutdown(descriptor, SHUT_RDWR)
      Darwin.close(descriptor)
      descriptor = -1
    }
  }

  private func configure(descriptor: Int32) {
    self.descriptor = descriptor
    let readSource = DispatchSource.makeReadSource(fileDescriptor: descriptor, queue: queue)
    readSource.setEventHandler { [weak self] in
      self?.handleReadable()
    }
    let writeSource = DispatchSource.makeWriteSource(fileDescriptor: descriptor, queue: queue)
    writeSource.setEventHandler { [weak self] in
      self?.handleWritable()
    }
    self.readSource = readSource
    self.writeSource = writeSource
    readSource.resume()
    writeSource.resume()
  }

  private func handleReadable() {
    guard let pendingRead, !cancelled, descriptor >= 0 else {
      return
    }
    var buffer = [UInt8](repeating: 0, count: pendingRead.maximumLength)
    let count = Darwin.recv(descriptor, &buffer, buffer.count, 0)
    if count > 0 {
      self.pendingRead = nil
      pendingRead.completion(Data(buffer.prefix(count)), false, nil)
    } else if count == 0 {
      self.pendingRead = nil
      pendingRead.completion(nil, true, nil)
    } else if errno != EAGAIN && errno != EWOULDBLOCK {
      self.pendingRead = nil
      pendingRead.completion(nil, false, Self.posixError(errno))
    }
  }

  private func handleWritable() {
    guard !cancelled, descriptor >= 0 else {
      return
    }
    while !pendingWrites.isEmpty {
      let count = pendingWrites[0].data.withUnsafeBytes { bytes -> Int in
        guard let baseAddress = bytes.baseAddress else {
          return 0
        }
        return Darwin.send(
          descriptor,
          baseAddress.advanced(by: pendingWrites[0].offset),
          bytes.count - pendingWrites[0].offset,
          0
        )
      }
      if count > 0 {
        pendingWrites[0].offset += count
        if pendingWrites[0].offset == pendingWrites[0].data.count {
          let completion = pendingWrites.removeFirst().completion
          completion(nil)
        }
      } else if count < 0, errno == EAGAIN || errno == EWOULDBLOCK {
        return
      } else {
        let completion = pendingWrites.removeFirst().completion
        completion(Self.posixError(count < 0 ? errno : EIO))
        return
      }
    }
  }

  private static func connectSocket(host: String, port: UInt16) throws -> Int32 {
    var hints = addrinfo()
    hints.ai_flags = AI_ADDRCONFIG
    hints.ai_family = AF_UNSPEC
    hints.ai_socktype = SOCK_STREAM
    hints.ai_protocol = IPPROTO_TCP
    var addresses: UnsafeMutablePointer<addrinfo>?
    guard getaddrinfo(host, String(port), &hints, &addresses) == 0, let first = addresses else {
      throw POSIXError(.EHOSTUNREACH)
    }
    defer { freeaddrinfo(first) }

    var address: UnsafeMutablePointer<addrinfo>? = first
    var lastError = EHOSTUNREACH
    while let current = address {
      if let descriptor = tryCandidate(current, lastError: &lastError) {
        return descriptor
      }
      address = current.pointee.ai_next
    }
    throw posixError(lastError)
  }

  private static func tryCandidate(
    _ candidate: UnsafeMutablePointer<addrinfo>,
    lastError: inout Int32
  ) -> Int32? {
    let descriptor = Darwin.socket(
      candidate.pointee.ai_family,
      candidate.pointee.ai_socktype,
      candidate.pointee.ai_protocol
    )
    guard descriptor >= 0 else {
      lastError = errno
      return nil
    }
    disableSignalPipe(descriptor: descriptor)
    guard makeNonBlocking(descriptor: descriptor) else {
      lastError = errno
      Darwin.close(descriptor)
      return nil
    }
    let result = Darwin.connect(
      descriptor,
      candidate.pointee.ai_addr,
      candidate.pointee.ai_addrlen
    )
    if result == 0 || (errno == EINPROGRESS && connectionCompleted(descriptor)) {
      return descriptor
    }
    lastError = errno
    Darwin.close(descriptor)
    return nil
  }

  private static func disableSignalPipe(descriptor: Int32) {
    var noSignal: Int32 = 1
    _ = withUnsafePointer(to: &noSignal) { pointer in
      Darwin.setsockopt(
        descriptor,
        SOL_SOCKET,
        SO_NOSIGPIPE,
        pointer,
        socklen_t(MemoryLayout<Int32>.size)
      )
    }
  }

  private static func makeNonBlocking(descriptor: Int32) -> Bool {
    let originalFlags = Darwin.fcntl(descriptor, F_GETFL, 0)
    guard originalFlags >= 0 else {
      return false
    }
    return Darwin.fcntl(descriptor, F_SETFL, originalFlags | O_NONBLOCK) == 0
  }

  private static func connectionCompleted(_ descriptor: Int32) -> Bool {
    var pollDescriptor = pollfd(fd: descriptor, events: Int16(POLLOUT), revents: 0)
    guard Darwin.poll(&pollDescriptor, 1, 10_000) > 0 else {
      return false
    }
    var socketError: Int32 = 0
    var length = socklen_t(MemoryLayout<Int32>.size)
    guard Darwin.getsockopt(descriptor, SOL_SOCKET, SO_ERROR, &socketError, &length) == 0 else {
      return false
    }
    if socketError != 0 {
      errno = socketError
      return false
    }
    return true
  }

  private static func posixError(_ code: Int32) -> POSIXError {
    POSIXError(POSIXErrorCode(rawValue: code) ?? .EIO)
  }
}
