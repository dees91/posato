# `DIAGNOSTICS-001`: Accept the diagnostics and support-data boundary

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SECURITY-001`
- **Stable concurrency constraints:** Durable security and wiki authority remains serialized after the threat model.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The maintainer has accepted what Posato may record, expose, retain, and export for MVP diagnostics without collecting browsing history or sensitive synchronization material.

## Context

Later persistence, native enforcement, and synchronization tasks need a consistent way to report actionable failures while honoring the repository's privacy boundary.

## In scope

- Define permitted diagnostic event classes and support-data fields.
- Define prohibited data, redaction, retention, local visibility, and export boundaries.
- Map ordinary failure states to truthful user-facing status without sensitive payloads.
- Assign verification obligations to every later task that emits diagnostics.

## Out of scope

- Analytics, telemetry, remote logging, or crash-reporting service integration.
- Recording visited or allowed navigation events.
- Implementing diagnostic storage or UI.

## Acceptance criteria

- `AC-01` — The policy explicitly prohibits browsing history, allowed navigation events, opaque application tokens, workspace secrets, raw encrypted payloads, credentials, and private identifiers from product diagnostics.
- `AC-02` — Permitted events are minimal, purpose-bound, redacted, and assigned retention and deletion behavior.
- `AC-03` — User-visible action-required and retry states can be diagnosed without exposing protected content.
- `AC-04` — Later diagnostic producers have an explicit review and evidence obligation.
- `AC-05` — The maintainer records acceptance of the durable privacy and diagnostics authority.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Privacy and security policy review.
- Field-level prohibited-data and redaction audit.
- Roadmap consumer mapping.
- Independent completed-change review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Defines validation and redaction for runtime failures, diagnostic fields, and user-initiated support exports. |
| Authentication or authorization | no | The diagnostics-policy decision outcome does not change identity, admission, permission, or authorization decisions. |
| Secrets, signing, or credentials | yes | Explicitly excludes workspace material, credentials, profiles, opaque tokens, and protected payloads from diagnostics. |
| Personal data or diagnostics | yes | Owns minimization, prohibited fields, redaction, retention, export, and deletion boundaries. |
| Storage or migration | yes | Defines whether permitted diagnostic records persist, for how long, and how they are removed. |
| Cryptography | no | The diagnostics-policy decision outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Applies one privacy-safe field policy to application, helper, extension, and native-adapter producers without implementing them. |
| External services | no | The diagnostics-policy decision outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | no | The diagnostics-policy decision outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | no | The diagnostics-policy decision outcome does not reuse PoC code or consume external implementation evidence. |

## Decision gates

- Maintainer acceptance of the permitted diagnostic boundary is required before runtime producers are ready.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Repository instructions](../../AGENTS.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
