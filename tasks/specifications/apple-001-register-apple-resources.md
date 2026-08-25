# `APPLE-001`: Make required Apple resources available

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** none
- **Stable concurrency constraints:** Apple identifiers, capabilities, signing assets, and Xcode project ownership remain serialized through Gate 7.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The maintainer has a verified, secret-free record that the Apple account and resources required by the accepted MVP are available before production scaffolding starts.

## Context

Gate 7 is the manual prerequisite for the first production-code pull request. It prevents generator or implementation work from assuming identifiers, entitlements, containers, or signing access that do not exist.

## In scope

- Verify access to the intended Apple development team and required resource classes.
- Record stable public identifiers and capability availability needed by the accepted architecture.
- Record any missing entitlement, container, or signing prerequisite as an explicit blocker.
- Keep credentials, profiles, device identifiers, and account-specific sensitive values outside Git.

## Out of scope

- Creating the application skeleton or changing build projects.
- Requesting public distribution approval.
- Implementing iOS extensions, CloudKit, Keychain, or macOS helper behavior.

## Acceptance criteria

- `AC-01` — Every Apple resource class required for PR #1 and later MVP work has a pass or blocked result with a clearing condition.
- `AC-02` — The stable identifiers needed by the first scaffold are recorded in an accepted portable authority without credentials or machine-specific values.
- `AC-03` — A read-only Xcode/account preflight confirms the maintainer can access the intended team and resources.
- `AC-04` — Gate 7 state truthfully prevents `FOUNDATION-001` from starting when any required prerequisite is absent.
- `AC-05` — A clean checkout can discover the documented local-configuration
  contract and its read-only preflight fails clearly, without sensitive
  output, when required local configuration is absent.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Manual Apple Developer and Xcode preflight.
- Documentation and preparation-gate consistency.
- Clean-checkout local-configuration discovery and missing-configuration
  behavior.
- Secret and personal-identifier scan.
- Independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | no | The Apple resource preflight outcome does not accept runtime, persisted, remote, or user-controlled input. |
| Authentication or authorization | yes | Verifies maintainer authority over the intended Apple team and resource set without recording private account data. |
| Secrets, signing, or credentials | yes | Reviews signing and profile availability while keeping credentials, profiles, and identities outside Git. |
| Personal data or diagnostics | no | The Apple resource preflight outcome does not change personal-data collection, diagnostics, retention, or export behavior. |
| Storage or migration | no | The Apple resource preflight outcome does not change durable product storage, compatibility, or migration behavior. |
| Cryptography | no | The Apple resource preflight outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Owns identifier, entitlement, group, container, and provisioning availability required by later native targets. |
| External services | yes | Depends on Apple Developer and Xcode account state; manual preflight may block completion. |
| Dependencies or licenses | no | The Apple resource preflight outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | no | The Apple resource preflight outcome does not reuse PoC code or consume external implementation evidence. |

## Decision gates

- The maintainer must confirm the intended Apple team and stable public identifier set.
- Any unavailable required entitlement or container blocks completion until Apple makes it available or the accepted scope changes.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [First MVP PR preparation plan](../first-mvp-pr-preparation-plan.md)
- [First MVP PR preparation checklist](../first-mvp-pr-preparation-todo.md)
