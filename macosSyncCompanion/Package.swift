// swift-tools-version: 6.2

import PackageDescription

let swiftLintPlugins = Package.Dependency.package(
  url: "https://github.com/SimplyDanny/SwiftLintPlugins",
  exact: "0.65.1"
)

let package = Package(
  name: "PosatoMacOSSync",
  platforms: [.macOS(.v15)],
  products: [
    .executable(name: "PosatoMacOSSync", targets: ["PosatoMacOSSync"])
  ],
  dependencies: [swiftLintPlugins],
  targets: [
    .executableTarget(name: "PosatoMacOSSync"),
    .testTarget(
      name: "PosatoMacOSSyncTests",
      dependencies: ["PosatoMacOSSync"],
    ),
  ]
)
