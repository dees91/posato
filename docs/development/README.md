# Development Baseline

This directory will contain the accepted toolchain, module map, code-quality
rules, test strategy, CI contract, local setup, and Definition of Done.

PoC tool versions and module boundaries are evidence, not automatic MVP
requirements. No production build has been scaffolded yet.

The accepted module, target, Metro, native-helper, extension, deployment, and
generator-import boundaries are in
[ADR 0003](../decisions/0003-mvp-application-architecture-baseline.md). Gate 5
is the first incomplete preparation gate and will establish the engineering
quality contract used to verify PR #1.

## First production pull request

`user-confirmed` (2026-08-24): PR #1 is a small production skeleton. It adds:

- fresh KMP application modules independent of the PoC module graph;
- accepted application and target identifiers;
- one minimal shared Compose screen running in the macOS and iOS applications;
- small semantic platform contracts with test fakes;
- baseline tests, formatting, static checks, and CI for the introduced targets.

PR #1 does not implement website blocking, application blocking,
synchronization, enrollment, recovery, or production helpers. Implementation
starts only after all seven gates and the ready checkpoint in the
[first MVP PR preparation checklist](../../tasks/first-mvp-pr-preparation-todo.md)
are complete and explicitly accepted.
