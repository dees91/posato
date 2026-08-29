// swift-tools-version: 6.2

import PackageDescription

let swiftLintPlugins = Package.Dependency.package(
  url: "https://github.com/SimplyDanny/SwiftLintPlugins",
  exact: "0.65.1"
)

let package = Package(
  name: "PosatoMacOSHelper",
  platforms: [.macOS(.v15)],
  products: [
    .executable(name: "PosatoMacOSHelper", targets: ["PosatoMacOSHelper"]),
    .executable(
      name: "PosatoProxySettingsDaemon",
      targets: ["PosatoProxySettingsDaemon"]
    ),
  ],
  dependencies: [swiftLintPlugins],
  targets: [
    .target(name: "PosatoMacOSServiceCore"),
    .executableTarget(
      name: "PosatoMacOSHelper",
      dependencies: ["PosatoMacOSServiceCore"]
    ),
    .executableTarget(
      name: "PosatoProxySettingsDaemon",
      dependencies: ["PosatoMacOSServiceCore"]
    ),
    .testTarget(
      name: "PosatoMacOSServiceCoreTests",
      dependencies: ["PosatoMacOSServiceCore"]
    ),
  ]
)
