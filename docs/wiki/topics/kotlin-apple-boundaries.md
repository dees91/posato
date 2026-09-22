# Kotlin and Apple Boundaries

## Goal

The goal is Kotlin-first ownership, not elimination of Swift as a metric. A
native wrapper is justified when it makes an Apple SDK boundary safer, smaller,
or more maintainable. Shared code should never know which language implements
the leaf.

## Evidence from the PoC

`observed`: production-like PoC code primarily used common interfaces with
platform implementations injected by the application hosts. `expect`/`actual`
appeared only in test database factories. This was valid because CloudKit,
Keychain, enforcement, application lifecycle, and test fakes need runtime
ownership rather than only compile-time specialization.

`observed`: the iOS application consumed the Kotlin/Native framework and
injected Swift service implementations. The desktop application was JVM-based,
so it could not directly call native Apple frameworks through Kotlin/JVM; it
used a separate signed native helper.

`user-confirmed` (2026-08-25):
[ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
accepts one unannotated common Metro graph contract with final platform
`@DependencyGraph` implementations in `iosMain` and `jvmMain`. Each host owns
one composition root. Constructor injection and explicit platform graph inputs
are required; a service locator is not part of the application graph.

## Selection rule

### Prefer `expect`/`actual`

Use `expect`/`actual` for a small compile-time platform difference when:

- exactly one implementation exists per target;
- no runtime selection or lifecycle owner is required;
- tests do not need to inject alternatives;
- the signature remains semantic and contains no platform types;
- the implementation is a thin leaf, such as a driver factory, filesystem
  location abstraction, environment fact, or platform-specific UI leaf.

### Prefer a common interface and injection

Use an interface when:

- common tests need a fake;
- lifecycle, permissions, view controllers, processes, or entitlements own the
  implementation;
- the implementation may be unavailable or selected at runtime;
- multiple implementations or transports may exist;
- operation outcomes and failure categories are part of product behavior.

Likely examples include an enforcer, mailbox, secure store, clock, randomness,
portable recovery presenter, portable enrollment scanner, and browser
presentation capability.

### Prefer IPC

`expect`/`actual` does not replace a process boundary. If Compose Desktop runs
on the JVM and a native or privileged helper owns Apple APIs, the shared edge is
a versioned IPC contract plus a Kotlin client. The helper may be Swift,
Objective-C, C, or Kotlin/Native without changing shared product contracts.

## iOS direction

Kotlin/Native can own iOS-specific orchestration and directly call Apple APIs
that are exposed through supported Objective-C or C interop. A thin Swift leaf
remains appropriate for APIs that are Swift-only, SwiftUI-owned, awkwardly
bridged, availability-sensitive, or tightly coupled to Xcode target lifecycle.

Family Controls selections and presentation are likely native leaves because
they use opaque platform values and system-owned authorization. The shared
contract should express actions such as selecting targets, applying a policy,
clearing enforcement, and reporting action-required state rather than
exporting platform tokens.

`observed` (2026-09-22, `IOS-004`): the device noun and the iPad landscape
sidebar come from a semantic `expect fun platformDevice()` (Mac, iPhone, iPad
from the UIKit idiom) plus a common placement rule over the window aspect,
not from navigation placement or width alone. On the iOS Simulator, moving a
focused Compose text field to a different parent layout when the placement
changes ended its input session: the keyboard hid, and a stale keyboard inset
kept the bottom navigation hidden even after Done. Keeping the content's
parent stable across placements and moving only the navigation chrome
preserved the keyboard through all four orientations.

## macOS direction

Compose Desktop uses a JVM process. `user-confirmed` (2026-08-25): native
macOS enforcement APIs therefore live in a separate signed native helper behind
authenticated, versioned local IPC. JNI, JNA, and a different desktop host are
not the accepted baseline.

`user-confirmed` (2026-09-07):
[ADR 0003's window-presentation amendment](../../decisions/0003-mvp-application-architecture-baseline.md#design-001-window-presentation-amendment)
accepts one signed, bundled AppKit/JNI leaf in the desktop host for window
chrome and contrast queries, with same-process native crash risk. This narrow
presentation exception does not move enforcement, synchronization, privilege,
IPC, networking, or product policy into the JVM's native leaf.

`user-confirmed` (2026-08-26):
[ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
selects Swift for a short-lived normal-user session helper and a minimal root
proxy-settings launch daemon. Kotlin remains the product and policy owner. The
native targets contain only Apple mechanisms and structured boundary mapping.
Kotlin/Native was rejected here because it would add runtime, interop, build,
packaging, and debugging surface without sharing product policy.

`user-confirmed` (2026-08-28):
[ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
adds a second, separate Swift process: `app.posato.macos.sync` is a short-lived
normal-user CloudKit and Keychain companion launched over private inherited
pipes. It receives only bounded synchronization requests and owns no
enforcement, root privilege, listener, background agent, merge, or product
policy. Keeping it separate prevents the workspace key and cloud entitlements
from entering the larger enforcement-helper surface.

The iOS application continues to inject direct native leaves. Both platforms
implement the same platform-neutral mailbox and secure-item outcomes; Apple
records, Keychain dictionaries, account values, and native errors remain at the
edge.

## Shared contract constraints

- No `UIViewController`, `NSBundle`, Apple token, CloudKit record, Keychain
  dictionary, native error, or system-settings object in `commonMain`.
- Avoid one large `Platform` service. Split boundaries by capability and owner.
- Use explicit sealed outcomes instead of parsing error messages.
- Document what a suspending platform call means: started, accepted by the OS,
  applied, or completed.
- Keep platform implementations thin; move policy branching and state-machine
  behavior back to shared code.
- Compile every affected target and test common behavior with fakes.

`observed` (2026-09-02, Compose Multiplatform 1.10.3): Compose Desktop
exposes its semantics tree through macOS accessibility by default (disabled
only by `compose.accessibility.enable=false` or the
`COMPOSE_DISABLE_ACCESSIBILITY` environment variable). Buttons surface their
label as the accessibility description with a press action, text fields accept
focus but not a direct value write, and `testTag` is not exposed, so desktop
automation addresses elements by role, text, or tree path and types through
keyboard events. On iOS the same `testTag` is exposed as the
`accessibilityIdentifier`, so XCUITest can address tagged elements directly.
The `posato-control` driver under `tools/posato-control` relies on these
facts.

## Open decisions

- Which Apple frameworks used by the MVP are cleanly callable from
  Kotlin/Native at the selected minimum OS versions?
- Which iOS flows require a SwiftUI or Xcode-owned leaf?
- Which individual compile-time leaves satisfy the accepted narrow
  `expect`/`actual` rule when their first consumer appears?
- How will platform callbacks, cancellation, and lifecycle opportunities enter
  shared orchestration?
