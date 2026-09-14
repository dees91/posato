# Execution: `RELEASE-001`

- **Brief:** [First-release readiness](../specifications/release-001-first-release-readiness.md)
- **Status:** `active`; plan approved, audit starting
- **Review tier:** `high-risk`
- **Implementer:** Claude Code session
- **Reviewer:** independent plan-review agent
- **Branch:** `docs/release-001-readiness-brief` (PR #54)
- **Updated:** 2026-09-14

## Observed starting point

- Reviewed revision base: `main` at `0d33be3` (MVP-001 accepted on `105ddda`). The repository is private; it has no license, notice, security, contributing, or privacy documents, and the README is an internal status page with stale implementation claims.
- Preliminary read-only inventory: no committed signing files, `local.properties`, large binaries, or personal paths in local history. Six commits on `main` carry session-link trailers, and GitHub holds pull-request refs, so the audit scope is wider than local history.
- Distribution gaps already visible: macOS signing supports only ad-hoc or Apple Development without a secure timestamp and has no notarization or versioning scheme; the Release iOS configuration omits Family Controls, so enforcement is unavailable there; Family Controls distribution, a production CloudKit schema, an App Store Connect record, an application icon, and privacy manifests do not exist.

## Decisions (`user-confirmed`, 2026-09-14)

- Channels, license, reporting routes, contribution policy, external-action ownership, local verification, and privacy hosting are recorded in the brief.
- **Session-link trailers:** rewrite history to remove them before the repository becomes public, as a separately authorized action owned by the follow-up publication row; not performed in this task.

## Plan

1. **Exposure audit (AC-01).** Fetch every remote branch, tag, and `refs/pull/*` into a temporary clone. Scan contents and commit metadata (authors, committers, trailers) for credentials, private keys, private URLs, personal paths, e-mail addresses, team, key, issuer and device identifiers, and provisioning names; list issues, pull requests, comments, and workflow runs and artifacts read-only with `gh`. Record categories and counts only. Each finding gets a prepared maintainer decision; history rewrite or content deletion is a separate authorized action. Review "Blocker" and research-checkout references for public readability.
2. **Licenses and name (AC-01).** Resolve runtime dependencies per shipped module with Gradle dependency reports and read their declared licenses; include Swift build plugins and the Gradle wrapper. Add the unmodified Apache-2.0 `LICENSE` and a `NOTICE` with the decided copyright and required attributions. Search EUIPO, USPTO, and WIPO public databases for the product name; record the date, terms, classes, and any blocked search, without clearance language.
3. **Handover sweep (AC-05).** Grep `docs/` for obligations assigned to `RELEASE-001` (including platform matrix, network and Automation disclosures, shipped diagnostic defaults, CloudKit quota and retention, release-build permission persistence, accessibility captures, residual risks R-01 to R-06) and give each a table row or a named follow-up row.
4. **Clean checkout (AC-02).** Clone a committed revision into a temporary directory without `local.properties` or the research checkout and run `./gradlew quality`. Record JDK, Xcode selection, Android SDK Platform 36, and Gradle user properties as documented prerequisites or blockers; document credential-free checks separately from signed-build prerequisites and record the local release verification policy.
5. **Public documents (AC-03).** Rewrite `README.md`; add `SECURITY.md` pointing to private vulnerability reporting, `CONTRIBUTING.md` (Issues yes, pull requests not accepted), and issue templates that warn against domains, application names, device models, screenshots, logs, and crash reports and route vulnerabilities privately; add `PRIVACY.md` as the policy text for `posato.app`, not presented as hosted until it is. Reconcile every statement with the threat model, diagnostics policy, MVP scope, and MVP-001 evidence; the maintainer accepts the privacy text before merge. Follow the README build steps on the clean clone.
6. **Channel audit (AC-04).** Read-only comparison of packaging, signing, entitlements, configurations, versioning and build numbers, bundled notices, CloudKit and Keychain environments, encryption and export-compliance declarations, the supported platform matrix, and install/update/removal paths with dated official Apple requirements. Each gap becomes a blocker with an owner.
7. **Verdict and roadmap (AC-05).** Fill the table, give the verdict, and propose roadmap revision 15 rows (`MACOS-008`, `IOS-003`, `SYNC-017`, `DESIGN-002`, `PRIVACY-001`, `RELEASE-002`) for maintainer acceptance, with `RELEASE-002` owning the final verdict on candidate artifacts, the history rewrite, and the public-distribution gate.
8. **Close out.** Independent completed-change review, one wiki-log entry and a topic update for durable conclusions, push to PR #54. Documentation-only change, so no hosted review. Rebase before merge; union any wiki-log conflict with PR #52.

## Obligation table

| Obligation | AC | Evidence | Result | Owner / next action |
| --- | --- | --- | --- | --- |
| Refs, metadata, and trailers free of credentials, private data, and private URLs | AC-01 | pending | pending | pending |
| Session-link trailers removed from published history | AC-01 | six commits on `main` (preliminary) | blocked | `RELEASE-002`: authorized history rewrite before the visibility change |
| GitHub-hosted issues, pull requests, comments, and workflow logs safe to publish | AC-01 | pending | pending | pending |
| Dependency, asset, and notice inventory; `LICENSE` and `NOTICE` | AC-01 | pending | pending | pending |
| Product-name trademark search | AC-01 | pending | pending | pending |
| Clean checkout passes local gates; environment and release verification policy | AC-02 | pending | pending | pending |
| README accurate, links and build-from-source steps work | AC-03 | pending | pending | pending |
| Security, contribution, issue-template, and privacy documents agree with the product; privacy text accepted | AC-03 | pending | pending | pending |
| Private vulnerability reporting and Issues enabled | AC-03 | pending | blocked until the repository is public | maintainer |
| Privacy policy hosted on `posato.app` | AC-03 | pending | pending | maintainer |
| macOS Developer ID, notarization, versioning, bundled notices, install/update/removal | AC-04 | pending | pending | pending |
| iOS App Store: Release configuration, Family Controls distribution, versioning, privacy manifest and label, icon | AC-04 | pending | pending | pending |
| CloudKit production schema, quota, and retention; Keychain environment | AC-04 | pending | pending | pending |
| Encryption and export-compliance declarations | AC-04 | pending | pending | pending |
| Supported platform matrix (current and previous major versions) | AC-04 | pending | pending | pending |
| `RELEASE-001` handovers from earlier authorities | AC-05 | pending | pending | pending |
| MVP-001 evidence reconciled; residual risks R-01 to R-06 rechecked | AC-05 | pending | pending | pending |
| Final verdict on candidate artifacts owned by a named row | AC-05 | pending | pending | pending |

## High-risk plan review

- **Verdict:** `changes-required` (independent agent, 2026-09-14); no Critical.
- **Required findings:** (R1) the exposure audit missed pull-request refs, commit metadata and trailers, private URLs, and GitHub-hosted content; (R2) obligations earlier authorities hand to `RELEASE-001` were not collected; (R3) encryption and export-compliance declarations were missing; (R4) no row owned the final ready verdict on candidate artifacts.
- **Resolution:** R1 plan step 1 and AC-01 widened with prepared decisions; R2 handover sweep step and AC-05; R3 added to AC-04 and the table; R4 AC-05 and step 7 name `RELEASE-002`. Recommended points folded: settings-owned reporting rows, issue-template content, maintainer acceptance of the privacy text, recorded clean-clone environment, bundled notices, versioning and CloudKit quota rows, blocked trademark searches allowed.
- **Approval:** the maintainer approved the corrected plan on 2026-09-14 (`user-confirmed`).

## Result

- Pending execution.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Rebase onto `0d33be3`, `git diff --check` | pass | only the roadmap row link and this task's documents differ from `main` |

## Final

- **Status:** pending
- **Outcome:** pending
