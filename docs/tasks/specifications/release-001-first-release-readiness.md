# `RELEASE-001`: Establish an evidence-based first-release readiness verdict

- **Execution record:** [RELEASE-001 execution](../executions/release-001-first-release-readiness.md)
- **Review tier:** `high-risk`
- **Tier reason:** Public repository exposure, signing, distribution, privacy disclosures, and release artifacts cross security and irreversible publication boundaries.
- **Dependencies:** completed `MVP-001` (accepted on `105ddda`, merged as `0d33be3`, with its recorded limits).
- **Integration group:** `PR-RELEASE-READINESS`, roadmap wave Release/R1.
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [AGENTS.md](../../../AGENTS.md), [quality contract](../../development/engineering-quality-contract.md#release-readiness-boundary), [MVP scope](../../product/mvp-scope.md), [product identity](../../product/product-identity.md), [architecture baseline](../../decisions/0003-mvp-application-architecture-baseline.md), [macOS helper ADR](../../decisions/0004-macos-helper-ownership-and-lifecycle.md), [threat model](../../security/apple-mvp-threat-model.md), and [diagnostics policy](../../security/diagnostics-and-support-data.md).

## Outcome

Every first-release obligation has verified evidence or an explicit blocker with an owner, yielding a ready or blocked verdict for a named source revision, distribution channel, and supported platform matrix. The public repository documents exist and agree with the verified product. A blocked verdict does not authorize release; a named follow-up row owns the final verdict on candidate artifacts.

## Boundaries

- Evaluate existing Apple MVP behavior and accepted limits. Post-MVP features, stronger bypass resistance, new recovery promises, and broader platform coverage are outside scope; development signing and a passing device pair do not establish distribution eligibility.
- This task audits and writes public documentation. Engineering gaps found for the chosen channels become separate roadmap rows accepted by the maintainer; they are not implemented here.
- Rewrite the root [README](../../../README.md) as the entry point for users and contributors: product purpose, verified capabilities and limits, supported platforms, installation, and quick start, with links to development, privacy, security reporting, license, and support. Keep detailed engineering instructions in their existing guides and historical feasibility material behind links.
- Actions against Apple and GitHub are read-only here. No public release, repository visibility or settings change, history rewrite, credential revocation, certificate or profile creation, Apple submission, or production CloudKit change follows from this brief. Keep secrets, personal identifiers, and raw audit or device evidence out of tracked files.
- GitHub CI stays disabled. Record the local release verification policy; do not enable CI or treat absent hosted checks as passing evidence.

## Acceptance

- `AC-01` — Audit everything a visibility change would publish: every remote branch, tag, and pull-request ref, commit metadata and trailers, and GitHub-hosted issues, pull requests, comments, and workflow logs or artifacts. Check for credentials, private data or URLs, machine-specific material, and improper PoC reuse; prepare a maintainer decision for each finding. Inventory source, dependency, asset, and third-party licenses; add the Apache-2.0 `LICENSE` and a `NOTICE`; record a dated public trademark-database search for the product name without clearance claims.
- `AC-02` — A clean clone of a committed revision builds and passes the documented local gates without the optional research checkout or undeclared maintainer state; the environment used is recorded as documented prerequisites or blockers, and the local release verification policy is recorded.
- `AC-03` — The rewritten README accurately describes the verified product, marks unavailable distribution paths, and provides a working build-from-source quick start plus the documentation links above. `SECURITY.md`, `CONTRIBUTING.md`, issue templates, and privacy policy text agree with implemented data handling and accepted authorities, covering collection, synchronization, retention, deletion, diagnostics/export, and recovery limits without claiming browsing-history collection, remote wipe, total-key-loss recovery, or absent guarantees. The maintainer accepts the privacy text before merge.
- `AC-04` — For Developer ID with notarization on macOS and the App Store on iOS, compare current signing, entitlements, Family Controls distribution, CloudKit and Keychain environments with quota and retention, packaging, versioning and build numbers, notices inside shipped bundles, privacy manifests and label, store assets, encryption and export-compliance declarations, the supported platform matrix, and install/update/removal paths against dated official Apple requirements. Every gap is a blocker with an owner.
- `AC-05` — Collect every obligation earlier authorities hand to `RELEASE-001` into the table or transfer it to a named follow-up row; reconcile MVP-001 evidence and recheck residual risks R-01 to R-06. Produce a reviewed pass/blocked table with an owner and next action per blocker. Roadmap revision 15 proposes the follow-up rows, names the row that gives the final verdict on candidate artifacts, and repoints the public-distribution gate and coverage rows to it.

## Verification

- Before consequential work, write the short execution plan and obtain independent High-risk plan review. Tie the audit to actual refs, files, resolved dependencies, and commands; record sanitized categories rather than raw findings.
- Run `./gradlew quality` on a clean clone without `local.properties`; follow the README build-from-source steps there using only documented prerequisites; check every README capability claim against MVP-001 evidence and every link.
- Recheck Apple distribution requirements against official sources and date them; do not infer approval from a development profile. Physical verification of candidate artifacts belongs to the follow-up row that produces them; reuse MVP-001 evidence only with its revision and limits.
- Complete independent change and readiness review and a concise execution record with the obligation table, decisions, checks, and verdict.

## Decisions (`user-confirmed`, 2026-09-14)

- Public open-source repository with official builds; macOS through Developer ID with notarization; iOS through the App Store; Apache-2.0 with copyright "Piotr Krawczyk and Posato contributors".
- Security reports through GitHub private vulnerability reporting and bugs and support through GitHub Issues, both enabled by the maintainer when the repository becomes public; external pull requests are not accepted for now.
- Session-link trailers in published history are removed by an authorized history rewrite before the repository becomes public; the follow-up publication row owns it.
- Follow-up rows use the App Store Connect API where possible; the maintainer requests Family Controls distribution, operates the CloudKit Console, and changes repository visibility. Verification stays local. The privacy policy is hosted on the maintainer-owned `posato.app` domain; hosting is a prerequisite for the App Store row.
