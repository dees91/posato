# `GOVERNANCE-002`: Streamline the engineering workflow

- **Review tier:** `standard`
- **Tier reason:** Meaningful governance documentation with no runtime or external-system change.
- **Dependencies:** `GOVERNANCE-001`, `PLANNING-001`
- **Integration group:** Governance revision 2
- **Authority:** Maintainer-approved plan, 2026-08-25

## Outcome

Posato keeps strong engineering checks while removing speculative task
documents, duplicated evidence, and review ceremony that does not improve the
software.

## Boundaries

- Introduce Trivial, Standard, and High-risk review tiers.
- Keep future Gate 6 work as roadmap stubs and create concise briefs just in time.
- Use one shared execution and review cycle for the three PR #1 milestones.
- Replace APPLE-001 tooling with a manual checklist and concise record.
- Preserve accepted product, architecture, security, privacy, and MVP scope.
- Do not create application code or Apple account resources.

## Acceptance

- `AC-01` — Active instructions, workflow, templates, quality contract, Gate 6 routing, and preparation gates describe the proportional process consistently.
- `AC-02` — The roadmap retains all 36 task IDs, outcomes, dependencies, waves, and integration groups without inactive full specifications.
- `AC-03` — APPLE-001 records the seven public identifiers, capability/profile needs, privacy boundary, manual steps, results, and blockers without scripts or configuration layers.
- `AC-04` — Links, roadmap IDs and dependencies, wiki structure, formatting, and scoped secret checks pass.

## Verification

- Documentation links, roadmap consistency, strict wiki lint, `git diff --check`, and a scoped sensitive-data scan.
- One independent completed-change review focused on consistency and needless complexity.

## Decisions or blockers

- The maintainer explicitly authorized this governance correction and the archival commit.
