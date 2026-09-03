# Posato iOS driver

`PosatoDriver.xcodeproj` is a standalone Xcode project that lets the
`posato-control` CLI drive the installed Posato iOS application on a Simulator
or a connected iPhone. It contains two targets:

- `PosatoDriverHost` — an empty SwiftUI application (`app.posato.ios.driverhost`)
  that exists only because a UI-testing bundle needs a host.
- `PosatoDriverUITests` — the UI-testing bundle (`app.posato.ios.driver`).
  Its single test, `DriverTests/testRunScenario`, attaches to the application
  under test by bundle identifier, executes the scenario it receives through
  the environment, and reports the outcome as XCTest attachments. Step
  failures never fail the test; the CLI reads `result.json` instead.

The project is not part of `./gradlew quality`; only its Swift sources are
linted there through `:posato-control:swiftFormatCheck`.

## How the CLI invokes it

1. Build once per destination:

   ```sh
   xcodebuild -project tools/posato-control/ios-driver/PosatoDriver.xcodeproj \
     -scheme PosatoDriver -destination "platform=iOS Simulator,id=<udid>" \
     -derivedDataPath build/verification/derived-data/driver-simulator \
     CODE_SIGNING_ALLOWED=NO build-for-testing
   ```

   For a physical device use `-destination "generic/platform=iOS"`,
   `-allowProvisioningUpdates`, and pass `DEVELOPMENT_TEAM` from the ignored
   `local.properties`. The build writes an `.xctestrun` file under
   `Build/Products/` in the derived-data directory.

2. Run a scenario without rebuilding:

   ```sh
   TEST_RUNNER_POSATO_SCENARIO_B64="$(base64 < scenario.json | tr -d '\n')" \
   TEST_RUNNER_POSATO_BUNDLE_ID=app.posato.ios \
   xcodebuild test-without-building -xctestrun <path to .xctestrun> \
     -destination "platform=iOS Simulator,id=<udid>" \
     -resultBundlePath build/verification/<run>.xcresult \
     -only-testing:PosatoDriverUITests/DriverTests/testRunScenario
   ```

3. Export the attachments from the result bundle:

   ```sh
   xcrun xcresulttool export attachments --path <run>.xcresult --output-path <dir>
   ```

## Environment contract

`xcodebuild` forwards every variable prefixed with `TEST_RUNNER_` to the test
runner with the prefix removed.

| Variable | Meaning |
| --- | --- |
| `POSATO_SCENARIO_B64` | Base64 of the scenario JSON document. When absent the driver runs a built-in ping scenario (one snapshot) and reports `SCENARIO_MISSING`. |
| `POSATO_BUNDLE_ID` | Bundle identifier of the application under test; defaults to `app.posato.ios`. |

The scenario and result schemas are defined in
`PosatoDriverUITests/ScenarioModels.swift` and shared with the Kotlin CLI.
Queries support `id`, `text`, `textContains`, `role`, `index`, `within`
(the deepest element of the given role that contains the anchor), and
`near` (the match closest to the anchor with vertical distance weighted three
times, so a control on the anchor's own row wins over the neighbouring row;
Compose list rows on iOS expose no container, so `near` is the way to address
a row's button).
If `terminateExisting` is `false` and the application is already running, the
driver activates the running instance instead of relaunching it.

## Attachment names

| Attachment | Content |
| --- | --- |
| `result.json` | `ScenarioResult`: overall `ok`, one entry per executed step, and the first error. |
| `screenshot-<stepIndex>-<name>.png` | Screen capture produced by a `screenshot` step. |
| `snapshot-<stepIndex>-<name>.json` | Accessibility tree produced by a `snapshot` step. |
| `failure-<stepIndex>-screenshot.png` | Screen capture taken when a step fails (`onFailure.screenshot`). |
| `failure-<stepIndex>-snapshot.json` | Accessibility tree taken when a step fails (`onFailure.snapshot`). |

Snapshot nodes carry `role`, `platformRole`, `id`, `label`, `value`,
`placeholder`, `enabled`, `focused`, `frame` (`x`, `y`, `w`, `h` in points),
and `children`. Empty strings are omitted.
