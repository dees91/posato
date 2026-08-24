# Agent Instructions

## Scope and language

These instructions apply to the entire repository. This repository is the
product home for the working-name Blocker MVP. Completed feasibility work
lives in a separate reference repository and is not production code.

Write all agent-authored repository content in English, including source,
tests, plans, documentation, wiki pages, identifiers, comments, and commit
messages. Preserve another language only in a user-supplied immutable artifact
when its inclusion has been explicitly approved.

## Mandatory wiki routing

Before reusable product, platform, privacy, architecture, security, or
experiment work:

1. Read [`docs/wiki/README.md`](docs/wiki/README.md).
2. Read [`docs/wiki/index.md`](docs/wiki/index.md).
3. Open the relevant topic and source pages before drawing a conclusion.

After work produces a durable conclusion, correction, decision candidate,
experiment result, or open question, update the relevant page under
`docs/wiki/topics/` or `docs/wiki/sources/`, update `docs/wiki/index.md` when
page routing changes, and append a parseable entry to `docs/wiki/log.md` using
`## [YYYY-MM-DD] type | Short title`.

The wiki is maintained synthesis, not decision authority. Do not promote an
inference or PoC choice into an ADR, product requirement, plan, or
source file without explicit user acceptance. Durable accepted decisions live
under `docs/decisions/`, `docs/product/`, or `docs/security/` as appropriate.

## Feasibility research reference

Maintainer checkouts may provide the ignored path `.research/blocker`, pointing
to the repository used for the synchronization PoC and enforcement spike. When
present, agents may read its code, tests, reports, maintained wiki, and Git
history to verify an exact feasibility claim or evaluate reuse. Search that
path explicitly because tools may not follow links automatically.

Treat `.research/blocker` as read-only evidence. Do not import experiment
traces, one-off runners, credentials, signing configuration, local paths,
captures, or machine-specific state into product sources or documentation. The
repository and its wiki must remain understandable and buildable when
`.research/blocker` is absent.

PoC code may inform a new implementation, test, or contract, but it may not be
imported wholesale. Re-establish ownership, API shape, tests, security, and
production quality in this repository and record meaningful provenance in the
change description.

## Provenance labels

Use these labels when provenance changes meaning:

- `observed`: directly verified in current code or a controlled experiment;
- `user-confirmed`: explicitly accepted or corrected by the user;
- `source-claim`: asserted by a source but not independently verified here;
- `inferred`: reasoned from evidence;
- `hypothesis`: proposed and awaiting a test;
- `open`: unresolved;
- `superseded`: retained history replaced by a newer conclusion.

Prefer current user corrections, current reproducible observations,
authoritative platform documentation, final PoC and spike result artifacts,
and then older synthesis. Keep contradictions and evidence limits visible.

## Repository safety and privacy

- Do not add credentials, tokens, private keys, provisioning profiles, device
  identifiers, personal paths, private URLs, raw captures, or unrelated
  conversations.
- Use synthetic fixtures and portable configuration examples.
- Do not record browsing history or allowed navigation events in product
  diagnostics unless a later explicit privacy decision changes that boundary.
- Do not claim production readiness, platform coverage, privacy, security, or
  distribution eligibility beyond verified evidence.
- Before the first release, require a separate readiness review covering Git
  history, license, notices, clean-clone setup, CI, security reporting, and
  privacy.

## MVP implementation boundary

Production code starts only after the MVP scope, product identity, minimal
design baseline, architecture baseline, quality gates, and required Apple
identifiers are explicitly accepted. The intended direction is Kotlin-first
Kotlin Multiplatform with Compose Multiplatform and narrow semantic platform
boundaries. Exact modules, dependencies, helper implementation, and native
ownership remain decisions rather than assumptions inherited from the PoC.

The active preparation route to the first production-code pull request is
[`tasks/first-mvp-pr-preparation-plan.md`](tasks/first-mvp-pr-preparation-plan.md),
with gate state in
[`tasks/first-mvp-pr-preparation-todo.md`](tasks/first-mvp-pr-preparation-todo.md).
Continue the first incomplete gate unless the user explicitly changes the
milestone. Do not scaffold application code until the "Ready to open PR #1"
checkpoint is complete and explicitly accepted.
