# First MVP PR Preparation Plan

## Status

- **Accepted direction:** 2026-08-24
- **Current milestone:** complete the first production pull-request increment
- **Active checklist:**
  [first-mvp-pr-preparation-todo.md](first-mvp-pr-preparation-todo.md)
- **Next work:** return PR #1 to the maintainer for the merge decision
- **Accepted Gate 6 authority:** [mvp-roadmap.md](mvp-roadmap.md)

## Objective

Close seven short decision and setup gates, then open a small production-code
pull request that establishes the application skeleton without implementing
blocking or synchronization.

The first MVP pull request means the first pull request containing code intended
to evolve into the released product. Feasibility code is evidence and a source
of selectively reviewed ideas, not the production baseline.

## Starting point

- `observed`: the Apple synchronization PoC passed its bounded physical and
  failure matrix.
- `observed`: the macOS and iOS enforcement spike passed its bounded domain and
  application rows.
- `user-confirmed`: Kotlin Multiplatform and Compose Multiplatform are the
  product direction.
- `user-confirmed`: shared product logic and orchestration are Kotlin-first,
  with Apple APIs behind small semantic boundaries.
- `observed`: `main` contains the initial knowledge base and no application
  implementation.
- `user-confirmed`: PoC and spike code remain reference material rather than a
  foundation to clean up incrementally.

See [feasibility results and limits](../docs/wiki/topics/feasibility-results-and-limits.md)
and the [reuse inventory](../docs/wiki/topics/poc-reuse-inventory.md) for the
evidence boundary.

## Gate dependency graph

```text
MVP scope ─────────┬──> product/design baseline ─┐
                  ├──> architecture baseline ───┼──> quality baseline
Product identity ─┴──────────────────────────────┘          │
                                                            v
                                                   PR decomposition
                                                            │
                                                            v
                                                   Apple Task 0
                                                            │
                                                            v
                                                Ready to open PR #1
```

The MVP scope, **Posato** product identity, minimum brand and product design
baseline, and [MVP architecture baseline][architecture-baseline] are accepted.
The maintainer controls `posato.app`, `app.posato` is the stable technical root,
and [DESIGN.md](../DESIGN.md) is the design authority. The
[engineering quality contract](../docs/development/engineering-quality-contract.md)
and [task workflow](README.md) are accepted. The
[Gate 6 MVP roadmap](mvp-roadmap.md) is accepted at revision 2 and retains
future work as concise task stubs. Gate 7 Apple resource registration is
complete. The Ready to open PR #1 checkpoint is complete and explicitly
accepted; the production skeleton and local quality gate are implemented on the
active PR #1 branch. Architecture details deferred by ADR 0003 remain with the
smallest named task that requires them.

[architecture-baseline]: ../docs/decisions/0003-mvp-application-architecture-baseline.md

## Seven gates

1. Define the MVP scope and explicit non-goals.
2. Select the minimum product identity and stable technical namespace.
3. Accept a minimal product and design baseline.
4. Accept the production architecture baseline needed by the first slices.
5. Accept the engineering quality contract.
6. Decompose the MVP into small vertical pull requests.
7. Complete the one manual Apple resource setup task.

The detailed acceptance criteria, dependencies, and verification steps are in
[the preparation checklist](first-mvp-pr-preparation-todo.md).

## PR #1 contract

The first production-code pull request is deliberately small. It should add:

- fresh application modules that do not use the PoC module graph as their
  starting point;
- the accepted application and target identifiers;
- one minimal shared Compose screen running in the macOS and iOS applications;
- small semantic platform contracts, with test fakes when behavior requires
  them;
- behavior-focused tests when applicable, formatting, static checks, and
  working CI for the introduced targets.

It must not implement domain blocking, application blocking, synchronization,
enrollment, recovery, or production platform helpers. Those capabilities arrive
later as independently reviewable vertical slices.

## Execution rules

- Do not start PR #1 implementation until every gate in
  `tasks/first-mvp-pr-preparation-todo.md` is complete and the ready checkpoint
  is explicitly accepted.
- Do not create a broad architecture spike. Resolve detailed Kotlin/Native,
  Swift, helper, extension, and transport choices in the first production slice
  that needs them.
- Require review before every merge, including generated project scaffolding.
- Follow the proportional review tiers in `tasks/README.md`: self-check
  Trivial changes, one completed-change review for Standard work, and add a
  brief plan review only for named High-risk work.
- Reuse PoC code only after provenance, licensing, architecture, security, and
  test review.
- Keep Apple credentials, signing identities, profiles, and account-specific
  values out of Git.
- Keep each implementation pull request independently buildable and
  reviewable; there is no single "implement MVP" pull request.
- Treat `FOUNDATION-001`, `QUALITY-001`, and `CI-001` as three roadmap
  milestones in one shared PR #1 brief, execution record, and review cycle.
  CI must complete before PR #1 merges or the first parallel implementation
  wave begins, whichever happens first.

## Deliberately deferred

- Final logo and complete visual identity.
- Android and Linux parity.
- Production blocking and synchronization implementation.
- App Store or direct-distribution release work.
- Decisions that no accepted near-term vertical slice depends on.
