# TEST-AUDIT: Prefer E2E proof and remove redundant tests

- **Review tier:** Standard
- **Tier reason:** Test deletion and standing guidance need independent coverage review.
- **Dependencies:** Existing verification recipes and local quality gate.
- **Integration group:** One test-audit change.
- **Authority:** Explicit maintainer request, 2026-09-26.

## Outcome

Agents use E2E proof for complex features and retain isolated tests only for
credible failures that stronger existing proof misses.

## Boundaries

- Audit shared code, native applications, and tooling in parallel read-only lanes.
- Delete tests only after recording their actual assertions and remaining proof.
- Update AGENTS.md and the quality contract together.
- Preserve independent security, protocol, migration, and failure-path contracts.
- Do not change product behavior, release rows, or quality-gate exclusions.

## Acceptance

- E2E-first guidance requires repeatable artifacts and forbids unit tests written
  after their implementation. Isolated work starts with a failure inventory.
- Every deletion has a named stronger keeper or explains why no contract exists.
- Remaining focused suites pass and independent review finds no lost contract.
- Report the aggregate gate and applicable unattended application checks honestly.

## Verification

- Baseline and final local quality gate, focused retained suites, and diff checks.
- Use posato-control for affected E2E proof, macOS in Tart and iOS on the test iPhone.
- Keep run artifacts under ignored build/verification/.
