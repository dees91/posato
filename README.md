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
- [First MVP PR preparation plan](tasks/first-mvp-pr-preparation-plan.md)
- [First MVP PR preparation checklist](tasks/first-mvp-pr-preparation-todo.md)

## Next milestone: the first MVP code PR

The next milestone is a small pull request containing the first production
application code. Seven gates precede implementation: MVP scope, product
identity, minimal product and design baseline, architecture baseline, quality
contract, PR decomposition, and one manual Apple resource setup task.

The immediate next work is to define the MVP scope and minimum product identity.
The accepted route and verification criteria live in the
[preparation plan](tasks/first-mvp-pr-preparation-plan.md) and
[checklist](tasks/first-mvp-pr-preparation-todo.md).

PR #1 will add fresh KMP application modules, accepted identifiers, one minimal
shared Compose screen running on macOS and iOS, small semantic platform
contracts with fakes, baseline tests, and CI. It will not implement blocking or
synchronization and will not adopt the PoC module graph as its starting point.
