import Foundation

guard CommandLine.arguments.count == 1, ParentVerification.verifyParent() else {
  exit(EXIT_FAILURE)
}

do {
  guard let encoded = try readFrame() else {
    exit(EXIT_FAILURE)
  }
  var request = try SyncCodec.decode(encoded)
  defer { request.clear() }
  let dependencies = SyncDependencies(
    entitlements: SecTaskEntitlementReader(),
    accounts: CloudKitAccountBindingSource(),
    keys: WorkspaceKeyStore(),
    clouds: CloudStore(backend: CKCloudDatabase()),
  )
  var response = RequestHandler.handle(request, dependencies: dependencies)
  defer { response.clear() }
  try writeFrame(SyncCodec.encode(response))
} catch {
  exit(EXIT_FAILURE)
}
exit(EXIT_SUCCESS)
