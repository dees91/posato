#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/../.."
repository_directory="$PWD"
mkdir -p "$repository_directory/build"
report_directory="$(mktemp -d "$repository_directory/build/ios-swift-tests.XXXXXX")"
simulator_id=""

cleanup() {
    if [[ -n "$simulator_id" ]]; then
        xcrun simctl shutdown "$simulator_id" >/dev/null 2>&1 || true
        xcrun simctl delete "$simulator_id"
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

simulator_id="$(xcrun simctl create Posato-Swift-Tests com.apple.CoreSimulator.SimDeviceType.iPhone-17)"
echo "Swift test reports: $report_directory"

xcode_arguments=(
    -project iosApp/iosApp.xcodeproj
    -scheme iosApp
    -configuration Debug
    -sdk iphonesimulator
    -destination "platform=iOS Simulator,id=$simulator_id"
    -derivedDataPath "$repository_directory/build/ios-swift-derived-data"
    -parallel-testing-enabled NO
    CODE_SIGNING_ALLOWED=NO
    CODE_SIGNING_REQUIRED=NO
    OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES
    "FRAMEWORK_SEARCH_PATHS=\"$repository_directory/shared/build/bin/iosSimulatorArm64/debugFramework\""
)

xcodebuild "${xcode_arguments[@]}" build-for-testing 2>&1 | tee "$report_directory/build.log"

ditto \
    shared/build/kotlin-multiplatform-resources/aggregated-resources/iosSimulatorArm64/composeResources \
    build/ios-swift-derived-data/Build/Products/Debug-iphonesimulator/Posato.app/compose-resources/composeResources

xcodebuild "${xcode_arguments[@]}" \
    -resultBundlePath "$report_directory/tests.xcresult" \
    test-without-building 2>&1 | tee "$report_directory/tests.log"
