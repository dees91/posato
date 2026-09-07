import Foundation

// Blocking test I/O must not occupy Swift Concurrency workers needed by the
// Dispatch work it awaits. A dedicated thread also avoids Dispatch pool starvation.
// Keep assertions in the awaiting test so Swift Testing retains their test context.
func runBlockingTestOperation<Value: Sendable>(
  _ operation: @escaping @Sendable () throws -> Value
) async throws -> Value {
  try await withCheckedThrowingContinuation { continuation in
    Thread.detachNewThread {
      continuation.resume(with: Result(catching: operation))
    }
  }
}
