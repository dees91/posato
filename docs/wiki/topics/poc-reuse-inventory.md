# PoC Reuse Inventory

## Reuse policy

`user-confirmed`: MVP code starts fresh. PoC code is evidence and a
source of narrowly reusable ideas, not the production baseline.

Reuse means preserving proven behavior intentionally. It does not mean copying
an experimental module graph, development identity, harness, or platform
assumption.

## Inventory

| PoC area | Default treatment | Useful knowledge | Required MVP change |
| --- | --- | --- | --- |
| Immutable operation model, hybrid logical clock, and deterministic reducer | Adapt or rewrite with tests | Convergence rules, idempotency, tie-breaking, and property-test cases | Define production domain types, clocks, serialization, and compatibility policy |
| Semantic platform contracts and failure categories | Redesign and adapt | Small capability boundaries and structured failure semantics | Derive interfaces from MVP use cases and keep native types behind platform boundaries |
| SQLDelight local replica | Reimplement from concepts and tests | Atomic apply, deduplication, materialized state, and restart behavior | Design the production schema, migrations, transactions, and retention policy |
| Encrypted envelope and deterministic vectors | Reference and selectively port tests | End-to-end encryption boundary and cross-runtime interoperability technique | Select reviewed primitives, providers, formats, versioning, and key handling |
| Synchronization coordinator, retry, failure injection, and lifecycle status | Adapt behavior | Pull/push ordering, progress reporting, retryable versus integrity failures | Define real lifecycle ownership, backoff, cancellation, observability, and user-facing status |
| Enrollment, recovery, membership, revocation, and deletion flows | Conditional product input | Explicit lifecycle states and failure cases | Include only accepted MVP scope and redesign user promises and security model |
| Compose probe screens and application entry points | Reference only | Proof that full runtimes must be reachable through normal application paths | Build product UI, navigation, accessibility, state ownership, and design system from scratch |
| Desktop local process protocol and authenticated helper boundary | Redesign with reference tests | Narrow IPC, caller authentication, and failure propagation | Choose production process ownership, installation, authorization, protocol versioning, and recovery |
| Native CloudKit and secure-storage adapters | Reference as platform leaves | Practical Apple API call shapes and error mapping | Re-evaluate direct Kotlin/Native versus minimal Swift and implement current production contracts |
| macOS proxy, system settings, browser presentation, and application observation | Reference mechanisms and test cases | Feasible enforcement path and browser-specific separation | Design safe installation, coexistence, persistence, update, uninstall, and support matrix |
| iOS Family Controls and Managed Settings adapters | Reference mechanisms | Opaque selection ownership and shield application | Define product authorization, extensions, schedules, invalid-selection behavior, and entitlement plan |
| Runners, hard-coded synthetic targets, temporary grants, signing data, captures, and cleanup artifacts | Leave with the experiments | Execution chronology only | Create fresh test fixtures and automation with no machine-specific state or experiment credentials |

## Reuse workflow

For each MVP slice:

1. read the relevant wiki topic;
2. state the new production contract and acceptance criteria;
3. use the ignored `.research/blocker` reference only when exact PoC or spike
   code or evidence is needed locally;
4. identify the smallest algorithm, test case, or native mechanism worth
   preserving;
5. rewrite or port it under current naming and architecture;
6. record provenance when copied code or a distinctive test fixture survives;
7. run normal review and current platform verification.

The repository must remain understandable and buildable when
`.research/blocker` is absent.

## Code provenance

No source code was copied during the initial repository seed.

If later work copies a meaningful fragment, the pull request must identify:

- the feasibility source revision and path;
- whether the fragment is copied, translated, or behaviorally reimplemented;
- applicable authorship or licensing information;
- the production changes and tests added around it.

## Reuse traps

- Do not treat passing probe UI as product UX.
- Do not leak Apple framework types into shared product logic for convenience.
- Do not promote hard-coded development identities or ports into defaults.
- Do not assume a locally authorized helper is distributable.
- Do not keep a prototype wire or encrypted format merely because fixtures
  already exist.
- Do not make the research reference a hidden build or documentation
  dependency.
- Do not repeat experiment execution chronology in maintained product docs.
