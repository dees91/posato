import Darwin
import Foundation

public enum DurableOwnershipFailure: Error, Equatable {
  case invalidDirectory
  case invalidFile
  case invalidState
  case unavailable
}

public final class DurableOwnershipStore: OwnershipPersistence, @unchecked Sendable {
  public static let productionURL = URL(
    fileURLWithPath: "/Library/Application Support/Posato/ProxySettings/ownership-v1.plist"
  )

  private let stateURL: URL
  private let expectedOwner: uid_t
  private let maximumStateBytes = 64 * 1024

  public init(stateURL: URL = productionURL, expectedOwner: uid_t = 0) {
    self.stateURL = stateURL
    self.expectedOwner = expectedOwner
  }

  public func load() throws -> OwnershipRecord? {
    let descriptor = Darwin.open(stateURL.path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW)
    if descriptor < 0, errno == ENOENT {
      return nil
    }
    guard descriptor >= 0 else {
      throw DurableOwnershipFailure.unavailable
    }
    defer { Darwin.close(descriptor) }
    var attributes = stat()
    guard fstat(descriptor, &attributes) == 0,
      (attributes.st_mode & S_IFMT) == S_IFREG,
      attributes.st_uid == expectedOwner,
      attributes.st_nlink == 1,
      (attributes.st_mode & 0o077) == 0,
      attributes.st_size > 0,
      attributes.st_size <= maximumStateBytes
    else {
      throw DurableOwnershipFailure.invalidFile
    }
    let data = try read(descriptor: descriptor, count: Int(attributes.st_size))
    let decoder = PropertyListDecoder()
    guard let record = try? decoder.decode(OwnershipRecord.self, from: data),
      record.hasValidBounds
    else {
      throw DurableOwnershipFailure.invalidState
    }
    return record
  }

  public func save(_ record: OwnershipRecord) throws {
    guard record.hasValidBounds else {
      throw DurableOwnershipFailure.invalidState
    }
    try prepareDirectory()
    let encoder = PropertyListEncoder()
    encoder.outputFormat = .binary
    let data = try encoder.encode(record)
    guard data.count <= maximumStateBytes else {
      throw DurableOwnershipFailure.invalidState
    }
    let temporaryURL =
      stateURL
      .deletingLastPathComponent()
      .appending(path: ".ownership-v1.\(UUID().uuidString).tmp")
    let descriptor = Darwin.open(
      temporaryURL.path,
      O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW,
      0o600
    )
    guard descriptor >= 0 else {
      throw DurableOwnershipFailure.unavailable
    }
    var completed = false
    defer {
      Darwin.close(descriptor)
      if !completed {
        Darwin.unlink(temporaryURL.path)
      }
    }
    try write(data: data, descriptor: descriptor)
    guard fchmod(descriptor, 0o600) == 0, fsync(descriptor) == 0,
      rename(temporaryURL.path, stateURL.path) == 0
    else {
      throw DurableOwnershipFailure.unavailable
    }
    completed = true
    try synchronizeDirectory()
    try excludeFromBackup(url: stateURL)
  }

  public func remove() throws {
    if Darwin.unlink(stateURL.path) != 0, errno != ENOENT {
      throw DurableOwnershipFailure.unavailable
    }
    if FileManager.default.fileExists(atPath: stateURL.deletingLastPathComponent().path) {
      try synchronizeDirectory()
    }
  }

  private func prepareDirectory() throws {
    let directoryURL = stateURL.deletingLastPathComponent()
    do {
      try FileManager.default.createDirectory(
        at: directoryURL,
        withIntermediateDirectories: true,
        attributes: [.posixPermissions: 0o700]
      )
    } catch {
      throw DurableOwnershipFailure.unavailable
    }
    var attributes = stat()
    guard lstat(directoryURL.path, &attributes) == 0,
      (attributes.st_mode & S_IFMT) == S_IFDIR,
      attributes.st_uid == expectedOwner,
      (attributes.st_mode & 0o077) == 0
    else {
      throw DurableOwnershipFailure.invalidDirectory
    }
    try excludeFromBackup(url: directoryURL)
  }

  private func synchronizeDirectory() throws {
    let directory = Darwin.open(
      stateURL.deletingLastPathComponent().path,
      O_RDONLY | O_CLOEXEC | O_DIRECTORY | O_NOFOLLOW
    )
    guard directory >= 0 else {
      throw DurableOwnershipFailure.unavailable
    }
    defer { Darwin.close(directory) }
    guard fsync(directory) == 0 else {
      throw DurableOwnershipFailure.unavailable
    }
  }

  private func excludeFromBackup(url: URL) throws {
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    var mutableURL = url
    do {
      try mutableURL.setResourceValues(values)
    } catch {
      throw DurableOwnershipFailure.unavailable
    }
  }

  private func read(descriptor: Int32, count: Int) throws -> Data {
    var data = Data(count: count)
    let result = data.withUnsafeMutableBytes { buffer -> Bool in
      guard let address = buffer.baseAddress else {
        return false
      }
      var offset = 0
      while offset < count {
        let readCount = Darwin.read(descriptor, address.advanced(by: offset), count - offset)
        guard readCount > 0 else {
          return false
        }
        offset += readCount
      }
      return true
    }
    guard result else {
      throw DurableOwnershipFailure.unavailable
    }
    return data
  }

  private func write(data: Data, descriptor: Int32) throws {
    let result = data.withUnsafeBytes { buffer -> Bool in
      guard let address = buffer.baseAddress else {
        return false
      }
      var offset = 0
      while offset < data.count {
        let writeCount = Darwin.write(
          descriptor,
          address.advanced(by: offset),
          data.count - offset
        )
        guard writeCount > 0 else {
          return false
        }
        offset += writeCount
      }
      return true
    }
    guard result else {
      throw DurableOwnershipFailure.unavailable
    }
  }
}
