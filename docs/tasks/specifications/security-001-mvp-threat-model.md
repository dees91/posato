# `SECURITY-001`: Establish the Apple MVP threat model

- **Review tier:** `high-risk`
- **Tier reason:** The task defines sensitive-data handling, exposed trust boundaries, and security residual risks that constrain later implementation.
- **Dependencies:** none
- **Integration group:** PR-SECURITY
- **Authority:** [MVP roadmap revision 2](../mvp-roadmap.md) and maintainer activation on 2026-08-26

## Outcome

The maintainer can accept one concise security authority that names the Apple
MVP assets, data classes, trust boundaries, attacker assumptions, threats,
required mitigations, residual risks, and downstream owners.

## Boundaries

- Cover only the accepted Apple MVP on one supported Mac and one supported iPhone.
- Preserve the accepted product, architecture, privacy, and Apple-workspace trust boundaries.
- Use `.research/blocker` as read-only feasibility evidence without promoting PoC mechanisms or claims wholesale.
- Route concrete controls to existing roadmap tasks instead of designing their implementations here.
- Do not select cryptographic primitives or formats, define diagnostic fields,
  settle helper privilege and browser coexistence, or perform release readiness.
- Do not claim administrator resistance, metadata anonymity, guaranteed cloud delivery, remote wipe, or production readiness.

## Acceptance

- `AC-01` — The proposed authority classifies every MVP asset and data class by purpose, location, synchronization scope, and required protection.
- `AC-02` — Every in-scope trust boundary and attacker capability maps to actionable threats and mandatory control outcomes.
- `AC-03` — Every threat has a current mitigation or named downstream owner, plus an explicit residual risk or release blocker.
- `AC-04` — The authority is consistent with accepted product and architecture decisions and clearly separates PoC evidence from production requirements.
- `AC-05` — The maintainer explicitly accepts the proposed authority before the task is marked done or its conclusions enter the maintained wiki.

## Verification

- Traceability review against the accepted scope, ADRs, roadmap, relevant wiki pages, and exact PoC/spike evidence.
- Documentation links, `git diff --check`, scoped sensitive-data scan, and one independent completed-change review.

## Decisions or blockers

- Maintainer acceptance of the reviewed proposal is required to complete the task.
