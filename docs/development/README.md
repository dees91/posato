# Development Baseline

The accepted [engineering quality contract](engineering-quality-contract.md)
is the standing authority for code quality, tests, review, dependencies,
security and privacy applicability, local verification, CI timing, and the
Definition of Done. Task planning and evidence follow the
[repository task workflow](../../tasks/README.md).

PoC tool versions and module boundaries are evidence, not automatic MVP
requirements. No production build has been scaffolded yet.

The accepted module, target, Metro, native-helper, extension, deployment, and
generator-import boundaries are in
[ADR 0003](../decisions/0003-mvp-application-architecture-baseline.md). Gates
5 and 6 are accepted. Gate 7, the manual Apple resource checklist, is the first
incomplete preparation gate.

## First production pull request

`user-confirmed` (2026-08-24): PR #1 is a small production skeleton. It adds:

- fresh KMP application modules independent of the PoC module graph;
- accepted application and target identifiers;
- one minimal shared Compose screen running in the macOS and iOS applications;
- small semantic platform contracts with test fakes;
- baseline tests, formatting, static checks, and CI for the introduced targets.

Gate 5 defines the required CI outcome but does not configure a pipeline.
`FOUNDATION-001`, `QUALITY-001`, and `CI-001` share one PR #1 brief,
execution record, and completed-change review. CI must complete before PR #1
merges or the first parallel implementation wave begins, whichever happens
first.

PR #1 does not implement website blocking, application blocking,
synchronization, enrollment, recovery, or production helpers. Implementation
starts only after all seven gates and the ready checkpoint in the
[first MVP PR preparation checklist](../../tasks/first-mvp-pr-preparation-todo.md)
are complete and explicitly accepted.
