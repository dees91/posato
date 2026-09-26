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

  private let file: ProtectedPlistFile

  public init(stateURL: URL = productionURL, expectedOwner: uid_t = 0) {
    file = ProtectedPlistFile(url: stateURL, expectedOwner: expectedOwner)
  }

  public func load() throws -> OwnershipRecord? {
    guard let data = try file.load() else {
      return nil
    }
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
    let encoder = PropertyListEncoder()
    encoder.outputFormat = .binary
    try file.save(encoder.encode(record))
  }

  public func remove() throws {
    try file.remove()
  }
}

/// One root-only property list: no symbolic links, one hard link, owner-only mode, bounded size,
/// atomic durable replacement, and exclusion from backup.
struct ProtectedPlistFile: Sendable {
  let url: URL
  let expectedOwner: uid_t
  private let maximumBytes = 64 * 1024

  init(url: URL, expectedOwner: uid_t) {
    self.url = url
    self.expectedOwner = expectedOwner
  }

  func load() throws -> Data? {
    let descriptor = Darwin.open(url.path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW)
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
      attributes.st_size <= maximumBytes
    else {
      throw DurableOwnershipFailure.invalidFile
    }
    return try read(descriptor: descriptor, count: Int(attributes.st_size))
  }

  func save(_ data: Data) throws {
    guard data.count <= maximumBytes else {
      throw DurableOwnershipFailure.invalidState
    }
    try prepareDirectory()
    let temporaryURL =
      url
      .deletingLastPathComponent()
      .appending(path: ".\(url.deletingPathExtension().lastPathComponent).\(UUID().uuidString).tmp")
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
      rename(temporaryURL.path, url.path) == 0
    else {
      throw DurableOwnershipFailure.unavailable
    }
    completed = true
    try synchronizeDirectory()
    try excludeFromBackup(url: url)
  }

  func remove() throws {
    if Darwin.unlink(url.path) != 0, errno != ENOENT {
      throw DurableOwnershipFailure.unavailable
    }
    if FileManager.default.fileExists(atPath: url.deletingLastPathComponent().path) {
      try synchronizeDirectory()
    }
  }

  private func prepareDirectory() throws {
    let directoryURL = url.deletingLastPathComponent()
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
      url.deletingLastPathComponent().path,
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
