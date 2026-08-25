# `GOVERNANCE-001`: Establish the Posato engineering workflow

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** Gate 4 architecture baseline
- **Stable concurrency constraints:** Repository routing and gate-state files
  must have one writer during this change.
- **Roadmap reference:** Not assigned; this task completes Gate 5 before the
  Gate 6 roadmap exists.
- **Risk:** medium

## Outcome

Posato has one explicit, repository-owned engineering quality contract and one
task workflow that future humans and agents can follow without relying on
conversation history or an installed external workflow skill.

## Context

The maintainer accepted ktlint, Detekt with Compose Rules, local verification,
deferred CI, small outcome-oriented tasks, independent plan and implementation
review, bounded correction loops, and task-local acceptance and Definition of
Done evidence. Gate 5 must preserve those decisions before Gate 6 decomposes
the MVP.

## In scope

- The standing engineering quality and Definition of Done authority.
- The repository-local task specification, execution, review, dependency, and
  parallel-work process.
- Reusable task specification and execution record templates.
- Mandatory repository, preparation-gate, ADR amendment, and wiki routing.

## Out of scope

- Application scaffolding or production source code.
- Exact Kotlin, Gradle, plugin, dependency, command, or CI-job implementation.
- The Gate 6 MVP roadmap and its individual implementation tasks.
- CI pipeline configuration.

## Acceptance criteria

- `AC-01` — One accepted development authority defines formatting, static
  analysis, Compose rules, compiler warnings, applicable test layers, review,
  dependency and provenance review, security and privacy applicability, local
  verification, CI timing, and the canonical Definition of Done.
- `AC-02` — One accepted task process defines source-of-truth ownership,
  outcome-only specifications, separate technical execution records, epics,
  phases, waves, dependencies, parallel constraints, task-to-PR mapping, review
  loops, and final evidence.
- `AC-03` — Reusable templates enforce immutable accepted specifications and
  auditable plans, reviews, applicability, acceptance evidence, and Definition
  of Done evidence.
- `AC-04` — Active repository routing consistently marks Gate 5 complete, Gate
  6 next, and CI as a separate PR #1 task due before PR #1 merge or the first
  parallel implementation wave, whichever is earlier.
- `AC-05` — An independent agent approves the corrected plan and another
  independent agent approves the completed documentation after all blocking
  findings are resolved.

## Required evidence categories

- Documentation link resolution.
- Active-authority and gate-state consistency.
- Markdown and diff formatting.
- Independent plan and completed-change review.
- Acceptance-criteria and Definition of Done evidence.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | no | Documentation-only governance change. |
| Authentication or authorization | no | No runtime authorization behavior changes. |
| Secrets, signing, or credentials | no | Review must still confirm that no sensitive or machine-specific values entered the diff. |
| Personal data or diagnostics | no | Existing privacy boundaries are routed but not changed. |
| Storage or migration | no | No product storage or migration changes. |
| Cryptography | no | No cryptographic contract or implementation changes. |
| Native IPC or entitlements | no | Existing architecture is routed but not changed. |
| External services | no | No service integration changes. |
| Dependencies or licenses | no | No dependency or license changes; external documentation is referenced only. |
| PoC reuse or external provenance | no | No PoC artifact or code is reused. |

## Decision gates

- None. The maintainer explicitly accepted the workflow and quality decisions
  represented by this revision.

## References

- [First MVP PR preparation checklist](../first-mvp-pr-preparation-todo.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Repository instructions](../../AGENTS.md)
