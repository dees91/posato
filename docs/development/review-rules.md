# Review Rules

These rules apply to every reviewer of a change in this repository: the
independent completed-change review, a plan review, and a hosted review such
as `@codex review`. Implementing agents do not need them in context; they
follow the process in [`AGENTS.md`](../../AGENTS.md#code-review) and the
[task workflow](../tasks/README.md).

- Report only actionable defects introduced by the reviewed diff. Map P0 to
  `Critical` and P1 to `Required`; do not turn advisory preferences or
  pre-existing out-of-scope work into blocking findings.
- Apply the testing policy in `AGENTS.md` and the
  [engineering quality contract](engineering-quality-contract.md#tests-and-runtime-checks).
  Prefer E2E proof and request isolated tests only for a named important
  failure that existing stronger coverage misses. Do not request tests for
  static UI rendering, copy, theme mapping, or framework wiring.
- Flag credentials, personal paths, wholesale PoC reuse, and violations of the
  accepted product, architecture, security, privacy, or process boundaries.
  `.research/blocker` must remain read-only evidence and an optional checkout.
- Decline findings that need an actor with write access to the app-private
  database file or schema or a compromised operating system (accepted limit
  `R-02`), defensive checks that duplicate schema constraints, memory zeroing
  beyond owned key material and plaintext buffers whose clearing removes the
  last in-memory copy (`R-05`), and per-type `toString()` redaction reports
  for a family already covered by the enumerated redaction test. Reply with
  the rule reference.
