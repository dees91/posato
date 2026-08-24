# Blocker MVP LLM Wiki

This directory is the maintained knowledge layer for Blocker MVP. It begins
with durable conclusions from the synchronization PoC and enforcement spike
and grows alongside the MVP implementation.

## Knowledge layers

### Sources

Current product code, tests, manifests, controlled experiment results,
authoritative platform documentation, and explicit user confirmations are
source material. `sources/` contains focused digests; it does not copy complete
experiment archives.

### Working synthesis

`topics/` contains current conclusions, contradictions, constraints, open
questions, and reuse guidance. Pages distinguish observations, accepted user
directions, inference, hypotheses, and unresolved decisions.

### Durable decisions

The wiki is not decision authority. Explicitly accepted architecture, product,
security, and development decisions are promoted to the corresponding
directory under `docs/` and referenced back from the wiki.

## Required files

- `index.md` routes readers to every maintained page.
- `log.md` is append-only and records ingests, corrections, decisions, and
  material synthesis updates.
- `sources/` records provenance and scope.
- `topics/` records maintained knowledge.

Log headings use this parseable form:

```text
## [YYYY-MM-DD] type | Short title
```

## Claim labels

- `observed`: directly verified in a controlled test or current source;
- `user-confirmed`: explicitly stated or accepted by the maintainer;
- `source-claim`: asserted by a source and not independently verified;
- `inferred`: reasoned from evidence;
- `hypothesis`: proposed and awaiting a decisive test;
- `open`: unresolved;
- `superseded`: retained conclusion replaced by newer evidence.

## Source precedence

Prefer current user corrections, current reproducible observations,
authoritative platform documentation, final PoC and spike result artifacts,
accepted experiment decisions within their original scope, maintained
feasibility synthesis, and then older plans or task logs.

A PoC or spike success establishes feasibility only within its stated topology
and configuration. It does not establish production reliability, release
eligibility, arbitrary platform coverage, or an MVP requirement.

## Workflow

Before reusable work:

1. Read `index.md`.
2. Read the relevant topic and source pages.
3. Verify mutable or disputed claims against current sources.
4. If the ignored `.research/blocker` path exists, consult it only when exact
   PoC or spike code, tests, or evidence would change the answer.

After reusable work:

1. Update an existing page before creating an overlapping page.
2. Record evidence limits, contradictions, and open questions.
3. Update `index.md` when routing changes.
4. Append one entry to `log.md`.
5. Promote a conclusion into a durable decision only after explicit acceptance.

## Repository boundary

Use focused source digests and synthetic examples. Do not add credentials,
signing material, machine-local configuration, raw captures, complete shell
history, or unrelated conversations. The optional feasibility reference is not
a build or documentation dependency; record durable conclusions in this wiki.
