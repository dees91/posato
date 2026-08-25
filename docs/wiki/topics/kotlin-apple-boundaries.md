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

## macOS direction

Compose Desktop currently implies a JVM process. Native macOS APIs therefore
need one of these mechanisms:

1. a separate native helper with local IPC;
2. a native library reached through JNI or JNA;
3. a different desktop target or host architecture.

The PoC proved the first option with Swift. The first relevant MVP slice may
evaluate a Kotlin/Native helper and retain a small Swift or Objective-C shim
only where required. That evaluation should be part of a production vertical
slice, not a throwaway language-count experiment.

The decision must consider packaging, signatures, privileges, crash recovery,
ABI risk, process authentication, update compatibility, debugging, and the
cost of operating JVM and native runtimes.

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

## Open decisions

- Which Apple frameworks used by the MVP are cleanly callable from
  Kotlin/Native at the selected minimum OS versions?
- Which iOS flows require a SwiftUI or Xcode-owned leaf?
- Can a Kotlin/Native macOS helper replace most PoC Swift without making
  build, packaging, and IPC more complex?
- Which boundaries need interfaces and which are simple enough for
  `expect`/`actual`?
- How will platform callbacks, cancellation, and lifecycle opportunities enter
  shared orchestration?
