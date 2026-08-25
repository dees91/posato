# `FOUNDATION-001`: Establish the Apple application skeleton

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `APPLE-001`
- **Stable concurrency constraints:** Root build, dependency catalog, Xcode project, shared composition root, and PR #1 ownership are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A minimal Kotlin-first Compose Multiplatform application shell runs on macOS and builds for the iOS Simulator with the accepted module and platform-boundary shape.

## Context

This is the first production-code task and the first consumer of Gate 7. It establishes only the buildable shell needed by named later tasks.

## In scope

- Create the accepted shared, macOS, iOS, and native-boundary skeleton.
- Provide a minimal branded shell consistent with the design authority.
- Expose the accepted semantic platform seams with replaceable non-production behavior.
- Make the project reproducible from a clean checkout using portable configuration.

## Out of scope

- Blocking, synchronization, onboarding, persistent product data, or production platform adapters.
- Final navigation, complete component inventory, or prototype-only workbench behavior.
- Quality tooling and CI behavior owned by `QUALITY-001` and `CI-001`.

## Acceptance criteria

- `AC-01` — The macOS application launches from a clean checkout and presents the minimal branded shell.
- `AC-02` — The iOS application builds and launches in a supported simulator with the same bounded shell.
- `AC-03` — Module and source-set ownership, accepted application identifiers,
  Metro-backed platform composition roots, and narrow semantic boundaries
  match the architecture baseline without importing PoC code wholesale.
- `AC-04` — All generated sample features and dependencies without a named MVP consumer are absent.
- `AC-05` — No credential, private signing artifact, personal path, or machine-specific configuration is tracked.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Clean-checkout build and dependency graph.
- macOS runtime and iOS Simulator smoke checks.
- Visual comparison against the minimal design baseline.
- Dependency, license, provenance, and secret review.
- Independent completed-change review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | no | The Apple application skeleton outcome does not accept runtime, persisted, remote, or user-controlled input. |
| Authentication or authorization | no | The Apple application skeleton outcome does not change identity, admission, permission, or authorization decisions. |
| Secrets, signing, or credentials | yes | Must keep private signing assets, credentials, personal paths, and machine state out of the generated skeleton. |
| Personal data or diagnostics | no | The Apple application skeleton outcome does not change personal-data collection, diagnostics, retention, or export behavior. |
| Storage or migration | no | The Apple application skeleton outcome does not change durable product storage, compatibility, or migration behavior. |
| Cryptography | no | The Apple application skeleton outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Creates the Xcode host, macOS package boundary, accepted identifiers, and platform composition roots. |
| External services | no | The Apple application skeleton outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | yes | Selects and pins the first compatible production toolchain and runtime dependency set with license review. |
| PoC reuse or external provenance | yes | Uses wizard output and feasibility evidence only through reviewed provenance and sanitation boundaries. |

## Decision gates

- `APPLE-001` must be complete and the Ready to open PR #1 checkpoint must be explicitly accepted before implementation starts.
- Any generator choice remains advisory and must fit the accepted architecture and quality authorities.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Task workflow](../README.md)
