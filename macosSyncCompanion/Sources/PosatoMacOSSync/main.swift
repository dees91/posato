import Foundation

guard CommandLine.arguments.count == 1, ParentVerification.verifyParent() else {
  exit(EXIT_FAILURE)
}

do {
  guard let encoded = try readFrame() else {
    exit(EXIT_FAILURE)
  }
  #if POSATO_VERIFICATION
    if var request = try VerificationCodec.decode(encoded) {
      defer { request.clear() }
      let database = CKCloudDatabase()
      var response = VerificationHandler.deleteZone(
        request,
        dependencies: VerificationDependencies(
          entitlements: SecTaskEntitlementReader(),
          environment: SelfVerificationEnvironment(),
          accounts: CloudKitAccountBindingSource(),
          clouds: CloudStore(backend: database),
          zones: database,
        )
      )
      defer { response.clear() }
      try writeFrame(VerificationCodec.encode(response))
      exit(EXIT_SUCCESS)
    }
  #endif
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
