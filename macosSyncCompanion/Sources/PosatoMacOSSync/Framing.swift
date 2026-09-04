import Foundation

enum PipeFailure: Error {
  case invalidFrame
}

func readExactly(count: Int) throws -> Data? {
  var data = Data()
  while data.count < count {
    let chunk = FileHandle.standardInput.readData(ofLength: count - data.count)
    if chunk.isEmpty {
      if data.isEmpty {
        return nil
      }
      throw PipeFailure.invalidFrame
    }
    data.append(chunk)
  }
  return data
}

func readFrame() throws -> Data? {
  guard let prefix = try readExactly(count: 4) else {
    return nil
  }
  let length = prefix.reduce(UInt32(0)) { value, byte in
    (value << 8) | UInt32(byte)
  }
  guard length > 0, length <= SyncLimits.maximumFrameBytes else {
    throw PipeFailure.invalidFrame
  }
  return try readExactly(count: Int(length))
}

func writeFrame(_ frame: Data) throws {
  guard frame.count <= SyncLimits.maximumFrameBytes else {
    throw PipeFailure.invalidFrame
  }
  var length = UInt32(frame.count).bigEndian
  let prefix = withUnsafeBytes(of: &length) { bytes in
    Data(bytes)
  }
  try FileHandle.standardOutput.write(contentsOf: prefix)
  try FileHandle.standardOutput.write(contentsOf: frame)
}
