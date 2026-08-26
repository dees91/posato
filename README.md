# Posato

Posato is an open-source application intended to interrupt habitual access to
selected websites and applications across personal devices while minimizing
collected data and maintenance burden.

## Status

This repository contains the accepted product knowledge base and the first
production application skeleton. The skeleton is not a complete MVP, release,
or production-readiness claim. The accepted public name is **Posato**,
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
- [Engineering quality contract](docs/development/engineering-quality-contract.md)
- [Task workflow](tasks/README.md)
- [Feasibility results and limits](docs/wiki/topics/feasibility-results-and-limits.md)
- [MVP open questions](docs/wiki/topics/mvp-open-questions.md)
- [First MVP PR preparation plan](tasks/first-mvp-pr-preparation-plan.md)
- [First MVP PR preparation checklist](tasks/first-mvp-pr-preparation-todo.md)

## Active milestone: the first MVP code PR

The first production pull request is active. Its seven preparation gates and
ready checkpoint are complete. `FOUNDATION-001` has added the Apple-only
application shell, `QUALITY-001` has added `./gradlew quality`, and the local
`CI-001` implementation is awaiting its first hosted GitHub Actions run.

The MVP scope, product identity, minimum brand and product design baseline,
[MVP application architecture](docs/decisions/0003-mvp-application-architecture-baseline.md),
and [engineering quality contract](docs/development/engineering-quality-contract.md)
are accepted. [Gate 6 MVP roadmap revision 2](tasks/mvp-roadmap.md) retains
future work as concise task stubs.
The accepted route and verification criteria live in the
[preparation plan](tasks/first-mvp-pr-preparation-plan.md) and
[checklist](tasks/first-mvp-pr-preparation-todo.md).

PR #1 uses fresh KMP application modules, accepted identifiers, and one minimal
shared Compose screen running on macOS and iOS. It has one aggregate local
quality gate and a credential-free CI workflow. It does not implement blocking
or synchronization and does not adopt the PoC module graph as its starting
point. The current static shell has no business behavior that warrants an
automated test.

Gate 5 did not configure CI. The accepted roadmap groups `FOUNDATION-001`,
`QUALITY-001`, and `CI-001` into one PR #1 brief, execution record, and
completed-change review. The first hosted CI run must pass before PR #1 merges
or any parallel implementation wave begins.
