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

The app opens in a resizable full-content macOS window: the sidebar and body
extend behind a transparent title area, without a separate title strip.
Native traffic lights, top-region dragging, resizing, and fullscreen remain.
The window has 20 pt rounded corners, disabled in fullscreen. There is no
simulated device frame, browser container, or Compose-drawn window button.
Its bundle identifier is `app.posato.prototype.macos`.
Session and Paused items live in an icon-and-label sidebar. The minimum window
width reserves a 390 dp content area beside the sidebar; resizing changes content
density without replacing desktop navigation with a phone layout.

The JVM host sets the AWT full-content properties. A small Objective-C/JNI
bridge configures the AppKit toolbar and clips its native frame layer; stock
OpenJDK does not expose a corner-radius property. Gradle compiles the bridge
with the existing Xcode/Java toolchains only for native launch/package tasks.
Compose application resources supply the library directly in both launch modes;
JVM model tests neither compile nor load it. Packaging declares the library as
an explicit input because Compose 1.10.3 does not track `appResourcesDir`;
native-only changes therefore invalidate the app image as well as resource preparation.
Configuration is queued on AppKit
without blocking AWT, with a strong window reference retained until completion.
It adds no third-party dependency and does not integrate with production services.
Native layer behavior still needs verification on each supported macOS/runtime
combination before adoption.

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
Session and Paused items use bottom navigation with icons and visible labels.
It stays outside scrolling content and above the home-indicator safe area.
Main navigation appears only after onboarding; Paused items no longer needs Done.

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
Choose a custom duration with the **Hours** and **Minutes** wheels: scroll or
tap a neighbouring value, or use the up/down arrows for one-unit changes.
On Mac, a focused wheel also accepts keyboard arrows and Home/End. Values stop
at the boundaries, and the resolved end time updates with the selection.
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

Paused items separates **Websites** and **Apps** into a soft segmented control
with quiet inline counts and independent scroll positions. Its outer edge aligns
with the content; labels are centered with internal padding. A paper-colored
selected segment sits inside a shared mist surface, with no underline or count badges.
The whole segment is selectable, including its unselected state.
Main destinations use separate bottom/sidebar components in the same palette,
so they are distinct from the in-screen category control. Active sessions keep
Paused items disabled; inactive and disabled are distinct states.
**Add websites** is an inline field with an **Add** action;
Return or the keyboard's Go action adds without leaving the field. Paste several
domains or URLs separated by new lines or commas to add them together. Valid
exact hosts are added once, duplicates are skipped, and invalid entries remain
in the field for correction with a result summary. On Mac, Shift–Return inserts
a new line without submitting.

**Search** opens the secondary search field; **Back to adding** clears the filter
and restores the website draft. Search ignores case and surrounding spaces;
**Clear search** restores every row. Entry/search, section switching, and
the primary **Choose apps** action stay above scrolling results. Read-only selection details keep
search directly available. Open a website row to edit it, or use its
labelled options menu to edit/remove it. Application menus remove local mappings.
Returning from an editor preserves the tab, query, and list position. Choosing a
new prototype moment or relaunching deliberately resets this presentation state.

On iPhone, opening the keyboard temporarily hides the wordmark, bottom navigation,
and duplicate management heading to leave room for results. Category tabs,
entry/search, and the section action remain available. Adding keeps
the keyboard open for the next entry; switching section or opening Search
releases field focus. Submit search to dismiss the keyboard and restore the
complete header and bottom navigation, including access to prototype controls.
Additional browser previews cover filtering, no matches, empty sections, long
domains, and read-only selection details at compact and expanded widths.

## Structure and checks

`app/` contains the common reducer, fixtures, ViewModel, 16 surface compositions,
hidden workbench, deterministic previews, common regression tests, and thin
JVM/iOS entry points. `iosApp/` contains only the SwiftUI host and Xcode project.
`compose/` remains the independently runnable component catalog and library.

```sh
./gradlew :prototypeApp:jvmTest
./gradlew :prototypeApp:verifyPrototype
./gradlew quality
./gradlew :prototypeApp:verifyPrototypeHosts
```

The root quality gate includes `verifyPrototype`: formatting, Detekt, common
tests on JVM, and shared iOS Simulator compilation. It does not package the
prototype or run its Xcode project. The explicit `verifyPrototypeHosts` adds
both iOS framework links, the unsigned Simulator host, and desktop packaging.
Run that host gate before reviewing a prototype PR and after native-host,
resource, packaging, or build-wiring changes; then drive the affected native
app. Other production checks in root quality are unchanged. Common tests
replace the former Node model suite, not native rendering or accessibility QA.

The former HTML study, its earlier visual refinement, and the Node tests are
no longer kept in the repository: their archive tag was removed on 2026-09-17
before the first release. They remain visible only in the commits of pull
request #32. That refinement was visual provenance, not an accepted production
design contract.
[The root DESIGN.md](../../DESIGN.md) remains the production authority.
[The prototype design reference](DESIGN.md) describes the current mock in full.
