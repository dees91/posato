# Posato native interaction prototype

A disposable Compose Multiplatform UX study with synthetic, in-memory data.
It uses the [prototype design system](compose/README.md) and is isolated from
the production applications. It does not block websites or apps, access iCloud,
request Apple permissions, or persist anything.

## Run on macOS

Use the repository's Java 21 and Xcode setup on an Apple Silicon Mac running
macOS 15 or later:

```sh
./gradlew :prototypeApp:run
```

For a standalone app with a bundled runtime:

```sh
./gradlew :prototypeApp:createDistributable
./gradlew :prototypeApp:runDistributable
```

The app opens in a normal resizable macOS window. There is no simulated device
frame, browser container, or custom replacement for the system title bar.
Its bundle identifier is `app.posato.prototype.macos`.

## Run on iPhone

Open `iosApp/PosatoPrototype.xcodeproj`, select the shared `PosatoPrototype`
scheme and an iPhone Simulator, then Run. Xcode builds the shared Kotlin
framework through the repository's Gradle wrapper.

The host targets iOS 18 or later and uses bundle identifier
`app.posato.prototype.ios`. For a physical iPhone, choose your local development
team in Xcode and select the device. Signing credentials are not stored in this
repository. Simulator verification does not establish physical-device support.

The content fills the iPhone screen. Compose owns safe-area and keyboard insets;
the SwiftUI host does not add a second set of insets.

## Prototype controls

Long-press the **posato** wordmark to open controls. Its accessibility custom
action is **Open prototype controls**. On Mac, **Command–Shift–P** toggles the
panel. Use **Close controls**, Escape on Mac, or the sheet's dismissal gesture
on iPhone to return to the app.

- **Moments:** ready, long list, first visit, active session, recovery, and waiting for a key.
- **Walkthrough:** four strict guided scenarios, progress, and restart.
- **Free play:** independently exercise an action; prerequisites may be prepared.
- **State:** inspect workspace, permission, items, session, and local sync state.
- **Appearance:** system/light/dark, stronger contrast, and larger text.

Applying a moment, walkthrough step, or mock event closes controls. Switching
tabs or appearance keeps them open. Simply opening or closing the panel, or
resizing across the compact/expanded breakpoint, keeps the current editor draft.
Choosing a new moment or scenario intentionally replaces it.

Each installation starts ready and has independent memory-only state. Relaunch
resets it; the Mac and iPhone do not communicate. The demo clock is fixed at
17:45. Durations accept whole minutes from 5 to 1,440, with 25/45/60 presets.
Expiry, permission changes, key delivery, sync outcomes, and the paused message
are explicit mock events, not background platform work.

### Long-list study

Choose **Long list · 50 websites** to load 50 synthetic domains and four local
applications. Use Session and Paused items normally; choosing **Ready** restores
the small fixture. Compact and expanded previews also cover the long-list ready,
review, active, and management surfaces.

Session keeps its primary action above a two-row selection summary. Open either
row to inspect the complete selection in a read-only sheet on iPhone or a dialog
on Mac. **Close list** returns without changing the selection or session.

Paused items separates **Websites** and **Apps** into counted tabs with their own
scroll positions. Website search ignores case and surrounding spaces; **Clear
search** restores every row. Search, section switching, and Add website/Choose
apps stay above the scrolling results. Open a website row to edit it, or use its
labelled options menu to edit/remove it. Application menus remove local mappings.
Returning from an editor preserves the tab, query, and list position. Choosing a
new prototype moment or relaunching deliberately resets this presentation state.

On compact screens, opening the keyboard temporarily removes the wordmark and
duplicate management heading to leave room for results. The app navigation,
section tabs, search, and section action remain available. Submit search to
dismiss the keyboard and restore the complete header, including prototype controls.
Additional browser previews cover filtering, no matches, empty sections, long
domains, and read-only selection details at compact and expanded widths.

## Structure and checks

`app/` contains the common reducer, fixtures, ViewModel, 16 surface compositions,
hidden workbench, deterministic previews, common regression tests, and thin
JVM/iOS entry points. `iosApp/` contains only the SwiftUI host and Xcode project.
`compose/` remains the independently runnable component catalog and library.

```sh
./gradlew :prototypeApp:jvmTest
./gradlew :prototypeApp:verifyIosPrototype
./gradlew :prototypeApp:verifyPrototype
./gradlew quality
```

The module gate checks formatting, Detekt, common tests on JVM, both iOS
frameworks, an unsigned Simulator host, and desktop packaging. The root quality
gate includes it. Common tests replace the former Node model suite; they do not
establish native rendering or accessibility conformance.

The former HTML study and Node tests remain recoverable at Git revision
`566bdb6`. Its refinement at `bfc4a49` is visual provenance, not an accepted
production design contract. [DESIGN.md](../../DESIGN.md) remains authoritative.
