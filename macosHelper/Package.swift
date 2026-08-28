// swift-tools-version: 6.2

import PackageDescription

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
