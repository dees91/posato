# Product Baseline

This directory will contain the accepted MVP scope, product identity, design
baseline, user flows, platform support contract, and non-goals.

The current working synthesis remains in `docs/wiki/`. It must not be treated
as an accepted specification until promoted here through an explicit decision.

## Current milestone

`user-confirmed` (2026-08-24): the next milestone is the first pull request
containing production application code. Before implementation, close seven
gates:

1. MVP scope and non-goals;
2. minimum product identity and stable technical namespace;
3. minimum product and design baseline;
4. MVP architecture baseline;
5. engineering quality contract;
6. ordered vertical pull-request roadmap;
7. manual Apple resource setup for the accepted target graph.

Gates 1 through 4 are accepted in the [MVP scope](mvp-scope.md),
[product identity](product-identity.md), and
[product and design baseline](design-baseline.md). The canonical tool-neutral
design contract is [DESIGN.md](../../DESIGN.md), and the accepted architecture
authority is [ADR 0003](../decisions/0003-mvp-application-architecture-baseline.md).
Gate 5, the engineering quality contract, is the first incomplete gate. The
active execution contract is the
[first MVP PR preparation plan](../../tasks/first-mvp-pr-preparation-plan.md)
and its [gate checklist](../../tasks/first-mvp-pr-preparation-todo.md). Accepted
product outputs are promoted back into this directory; architecture decisions
live under `docs/decisions/`.
