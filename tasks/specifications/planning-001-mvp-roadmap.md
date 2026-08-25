# `PLANNING-001`: Prepare the MVP implementation roadmap

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** Gates 1 through 5
- **Stable concurrency constraints:** Preparation-gate state, roadmap routing,
  and future task ownership must have one writer during this change.
- **Roadmap reference:** Not assigned; this task creates the Gate 6 roadmap.
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Posato has a maintainer-ready candidate roadmap that decomposes the accepted
Apple MVP into small, dependency-ordered tasks and coherent pull requests
without authorizing production implementation or silently deciding details
assigned to later task plans.

## Context

Gates 1 through 5 are accepted, and the maintainer explicitly authorized Gate
6 drafting. That authorization does not accept this specification or its
roadmap output in advance. The repository also contains a disposable
interaction prototype at
revision `a081d4278cf8517c46b4322ed3c098462f153570`. That prototype is useful
flow evidence but is not production code, a replacement for `DESIGN.md`, or an
automatic acceptance of its workbench behavior and exact interaction details.

## In scope

- A complete dependency, phase, wave, parallel-lane, manual-gate, physical-test,
  and pull-request map for the accepted Apple MVP.
- Small outcome-only candidate task specifications covering the complete MVP
  outcome, PR #1, its CI deadline, Apple Task 0, and final verification.
- Explicit critical-path, write-conflict, decision-gate, and prototype-evidence
  boundaries.
- Maintained wiki synthesis and source routing for durable planning evidence.

## Out of scope

- Application scaffolding, build configuration, CI configuration, or other
  production implementation.
- Accepting the candidate roadmap or its downstream task specifications on
  behalf of the maintainer.
- Marking Gate 6 complete or opening PR #1 before maintainer acceptance.
- Changing accepted MVP scope, product identity, design, architecture, quality,
  security, privacy, or synchronization decisions.
- Promoting exact prototype geometry, fixtures, normalization rules, or Free
  play behavior into the product contract.

## Acceptance criteria

- `AC-01` — The candidate roadmap covers every accepted MVP capability and
  non-goal, fixes PR #1 to its accepted skeleton contract, and preserves the
  CI deadline and Gate 7 manual dependency.
- `AC-02` — Every proposed task has a draft outcome-only specification with
  explicit blocking dependencies, stable concurrency constraints, acceptance
  criteria, required evidence categories, review applicability, and decision
  gates.
- `AC-03` — The roadmap names the derived dependency graph, critical path,
  phases, waves, parallel lanes, write-surface conflict classes, pull-request
  grouping, manual gates, and physical-device checks.
- `AC-04` — The interaction prototype is recorded as bounded UX evidence; its
  disposable workbench behavior and unaccepted interaction details do not
  become product requirements.
- `AC-05` — Active preparation, task-workflow, design, architecture, quality,
  and wiki routing remain consistent and independently reviewed with no
  unresolved Critical or Required finding.

## Required evidence categories

- Documentation link resolution and routing.
- Roadmap coverage and dependency consistency.
- Task-specification schema and status consistency.
- Markdown and diff formatting.
- Independent plan and completed-change review.
- Acceptance-criteria and Definition of Done evidence.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | no | Documentation-only planning change; future tasks classify their own inputs. |
| Authentication or authorization | yes | The roadmap must preserve Apple trust and helper authorization decision gates without deciding their implementation. |
| Secrets, signing, or credentials | yes | Apple Task 0 must keep sensitive account and signing material outside Git. |
| Personal data or diagnostics | yes | Future tasks must preserve the accepted omission and redaction boundaries. |
| Storage or migration | yes | Local-replica and synchronization tasks require explicit dependency and migration gates. |
| Cryptography | yes | Production cryptographic choices must remain in a dedicated reviewed task. |
| Native IPC or entitlements | yes | macOS helper, iOS extension, and Apple resource work require explicit serialization and manual gates. |
| External services | yes | CloudKit and synchronizable Keychain work require bounded platform verification. |
| Dependencies or licenses | yes | Each dependency enters with its first production consumer and receives review. |
| PoC reuse or external provenance | yes | Future tasks must re-establish production ownership and record meaningful reuse provenance. |

## Decision gates

- Maintainer acceptance is required before the candidate roadmap and downstream
  task specifications become Gate 6 authority.
- Existing open product, security, native-helper, synchronization, and
  distribution questions remain with the smallest named task that needs them.

## References

- [First MVP PR preparation plan](../first-mvp-pr-preparation-plan.md)
- [First MVP PR preparation checklist](../first-mvp-pr-preparation-todo.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [Task workflow](../README.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [Design authority](../../DESIGN.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
