# Blocker MVP

Blocker MVP is the working-name repository for an open-source application that
aims to interrupt habitual access to selected websites and applications across
personal devices while minimizing collected data and maintenance burden.

## Status

This repository currently contains an initial product knowledge base only. It
has no MVP application code, accepted MVP scope, final product name, registered
product identifiers, selected license, release, or production-readiness claim.

Two completed feasibility efforts inform the project:

- an Apple-first synchronization PoC showed that a Kotlin Multiplatform and
  Compose Multiplatform application can synchronize encrypted local-first state
  through narrow CloudKit and Keychain adapters on one physical Mac and one
  physical iPhone;
- an Apple enforcement spike showed bounded domain and application blocking on
  the same platform categories using a local macOS proxy and native application
  controls, plus iOS Family Controls and Managed Settings.

Both results were bounded experiments, not production architecture or release
qualification. Their exact limits are documented in
[`docs/wiki/`](docs/wiki/index.md).

## Intended direction

- Apple-first MVP sequencing for macOS and iOS.
- Kotlin Multiplatform and Compose Multiplatform.
- Kotlin-first domain, orchestration, and shared UI.
- Narrow semantic boundaries around native Apple APIs.
- No product-operated user-data backend or in-app provider credentials.
- No browsing-history collection as a default product boundary.
- Android, Linux, and a user-selected portable synchronization folder remain
  later directions rather than MVP parity gates.

These are direction constraints, not a complete architecture or feature scope.

## Start here

- [Wiki operating contract](docs/wiki/README.md)
- [Wiki index](docs/wiki/index.md)
- [Product framing](docs/wiki/topics/product-framing.md)
- [Feasibility results and limits](docs/wiki/topics/feasibility-results-and-limits.md)
- [MVP open questions](docs/wiki/topics/mvp-open-questions.md)

## Before the first MVP code PR

The repository still needs explicit decisions for MVP scope, working product
identity, minimal design, production architecture, quality gates, Apple target
identifiers, and licensing. The first code PR should follow those decisions and
must not adopt the PoC module graph or probe surfaces as a production baseline.
