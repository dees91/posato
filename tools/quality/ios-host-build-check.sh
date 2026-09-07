#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/../.."
repository_directory="$PWD"
mkdir -p "$repository_directory/build"
report_directory="$(mktemp -d "$repository_directory/build/ios-host-builds.XXXXXX")"
echo "iOS host build reports: $report_directory"

build_host() {
    local configuration="$1"
    local sdk="$2"
    local destination="$3"
    local framework="$4"

    xcodebuild \
        -project iosApp/iosApp.xcodeproj \
        -scheme iosApp \
        -configuration "$configuration" \
        -sdk "$sdk" \
        -destination "$destination" \
        -derivedDataPath "$repository_directory/build/ios-host-build-derived-data" \
        CODE_SIGNING_ALLOWED=NO \
        CODE_SIGNING_REQUIRED=NO \
        OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES \
        "FRAMEWORK_SEARCH_PATHS=\"$repository_directory/shared/build/bin/$framework\"" \
        build 2>&1 | tee "$report_directory/$configuration-$sdk.log"
}

build_host Debug iphoneos 'generic/platform=iOS' iosArm64/debugFramework
build_host Release iphonesimulator 'generic/platform=iOS Simulator' iosSimulatorArm64/releaseFramework
