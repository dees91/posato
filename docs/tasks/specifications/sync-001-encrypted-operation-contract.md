# `SYNC-001`: Accept the encrypted operation and convergence contract

- **Review tier:** `high-risk`
- **Tier reason:** The task selects cryptographic, untrusted-input, replay,
  compatibility, and deterministic convergence boundaries used by every
  synchronization transport.
- **Dependencies:** `SECURITY-001`
- **Integration group:** `PR-SYNC-FORMAT`
- **Authority:** [MVP roadmap revision 2](../mvp-roadmap.md) and maintainer
  activation on 2026-08-28

## Outcome

The maintainer can accept one implementable contract for bounded encrypted
bundles, signed immutable Apple MVP operations, canonical encoding, versioning,
validation order, automatic author registration, and deterministic convergence.

## Boundaries

- Cover exact-domain policy, semantic application policy, and active-session
  intent for the accepted one-Mac, one-iPhone Apple workspace.
- Preserve Apple trust as the complete MVP membership boundary while retaining
  device-local signing identities and authenticated operation authorship.
- Use `.research/blocker` only for exact feasibility behavior, test cases, and
  rejected design traps; do not adopt its portable membership operations,
  schedules, dependency versions, identifiers, or prototype wire format.
- Specify the minimum transport-neutral contract needed by `SYNC-002` and
  `SYNC-003`; defer provider integration, storage schema, bootstrap mechanics,
  retry policy, UI, and portable workspace completeness or migration.
- Do not add production code, dependencies, vectors, schemas, or claim
  production cryptographic verification in this decision task.

## Acceptance

- `AC-01` — The proposal closes the MVP operation vocabulary, conflict order,
  author-registration rule, replay and sequence behavior, and convergence
  invariants without introducing a Posato approval ceremony.
- `AC-02` — The envelope and operation contract bounds every field and payload,
  authenticates all required context, defines nonce and signature behavior,
  and rejects invalid input before changing valid state.
- `AC-03` — Canonical encoding, algorithm and format versioning, compatibility,
  and validation order are precise enough for independent cross-target vectors.
- `AC-04` — Primitive and provider choices are justified against current target,
  maintenance, licensing, and security evidence without inheriting the PoC
  dependency selection by default.
- `AC-05` — The maintainer explicitly accepts the reviewed proposal before it
  becomes an accepted authority or its conclusions enter maintained synthesis.

## Verification

- Traceability review against the accepted trust decision, threat model,
  roadmap, synchronization synthesis, and exact feasibility format, reducer,
  rejection, convergence, and cross-target vector evidence.
- Documentation links, `git diff --check`, scoped sensitive-data scan, and
  independent high-risk plan and completed-change reviews.

## Decisions or blockers

- Maintainer security and architecture acceptance of the reviewed proposal is
  required to complete the task.
