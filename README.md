# Posato

Posato is an open-source application intended to interrupt habitual access to
selected websites and applications across personal devices while minimizing
collected data and maintenance burden.

## Status

This repository currently contains an initial product knowledge base only. It
has no MVP application code, registered product identifiers, selected license,
release, or production-readiness claim. The accepted public name is **Posato**,
`posato.app` is the maintainer-controlled canonical domain, and `app.posato` is
the accepted stable technical root. The accepted contracts are recorded under
`docs/product/`.

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
- Apple MVP synchronization uses one **Sync with iCloud** action per
  installation and no application-level QR or cross-device approval.
- Application-layer encryption remains common to CloudKit and the later
  portable-folder transport.
- No browsing-history collection as a default product boundary.
- Android, Linux, and a user-selected portable synchronization folder remain
  later directions rather than MVP parity gates.

These are direction constraints, not a complete architecture.

## Start here

- [Wiki operating contract](docs/wiki/README.md)
- [Wiki index](docs/wiki/index.md)
- [Product framing](docs/wiki/topics/product-framing.md)
- [Accepted MVP scope](docs/product/mvp-scope.md)
- [Accepted product identity](docs/product/product-identity.md)
- [Accepted design system](DESIGN.md)
- [Feasibility results and limits](docs/wiki/topics/feasibility-results-and-limits.md)
- [MVP open questions](docs/wiki/topics/mvp-open-questions.md)
- [First MVP PR preparation plan](tasks/first-mvp-pr-preparation-plan.md)
- [First MVP PR preparation checklist](tasks/first-mvp-pr-preparation-todo.md)

## Next milestone: the first MVP code PR

The next milestone is a small pull request containing the first production
application code. Seven gates precede implementation: MVP scope, product
identity, minimal product and design baseline, architecture baseline, quality
contract, PR decomposition, and one manual Apple resource setup task.

The MVP scope, product identity, minimum brand and product design baseline, and
[MVP application architecture](docs/decisions/0003-mvp-application-architecture-baseline.md)
are accepted. The immediate next work is to accept the engineering quality
contract in Gate 5.
The accepted route and verification criteria live in the
[preparation plan](tasks/first-mvp-pr-preparation-plan.md) and
[checklist](tasks/first-mvp-pr-preparation-todo.md).

PR #1 will add fresh KMP application modules, accepted identifiers, one minimal
shared Compose screen running on macOS and iOS, small semantic platform
contracts with fakes, baseline tests, and CI. It will not implement blocking or
synchronization and will not adopt the PoC module graph as its starting point.
