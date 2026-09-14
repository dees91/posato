# Execution: `RELEASE-001`

- **Brief:** [First-release readiness](../specifications/release-001-first-release-readiness.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code session
- **Reviewers:** independent plan-review agent; independent completed-change review agent
- **Branch:** `docs/release-001-readiness-brief` (PR #54)
- **Updated:** 2026-09-14

## Observed starting point

- Reviewed revision base: `main` at `0d33be3` (MVP-001 accepted on `105ddda`). The repository was private, had no license, notice, security, contributing, or privacy documents, and its README was an internal status page with stale implementation claims.
- Distribution gaps were already visible: development-only signing without a secure timestamp or notarization, a Release iOS configuration without Family Controls, no production CloudKit schema, App Store Connect record, application icon, or privacy manifests.

## Decisions (`user-confirmed`, 2026-09-14)

- Channels, license, reporting routes, contribution policy, external-action ownership, local verification, and privacy hosting are recorded in the brief. The privacy text is accepted, with `privacy@posato.app` as its contact.
- **Published history:** session-link trailers are removed by an authorized history rewrite and the seven task-share links are removed from pull-request bodies before the repository becomes public; the maintainer's author e-mail and the nine pull-request attachments are published unchanged.
- **Roadmap revision 15:** `MACOS-008`, `IOS-003`, `SYNC-017`, `DESIGN-002`, `PRIVACY-001`, the maintainer-added `DOCS-001` showcase README (screenshots and a Remotion-rendered demo, modeled on the maintainer's other project READMEs), and `RELEASE-002`, which owns the final ready verdict and the public-distribution gate.

## Plan

1. **Exposure audit (AC-01).** Mirror every branch, tag, and pull-request ref; scan contents and commit metadata; list GitHub-hosted issues, pull requests, comments, runs, and artifacts read-only; record categories and prepared decisions only.
2. **Licenses and name (AC-01).** Resolve shipped runtime dependencies and their licenses, add `LICENSE`, `NOTICE`, and third-party notices, and search public trademark databases.
3. **Handover sweep (AC-05).** Collect every obligation earlier authorities assign to `RELEASE-001`.
4. **Clean checkout (AC-02).** Run `./gradlew quality` on a clean clone without maintainer state and record the environment.
5. **Public documents (AC-03).** Rewrite the README; add security, contributing, issue-template, and privacy documents reconciled with accepted authorities; verify the README build steps.
6. **Channel audit (AC-04).** Compare current artifacts and configuration with dated official Apple requirements.
7. **Verdict and roadmap (AC-05).** Fill the table, give the verdict, and add the accepted roadmap revision 15 rows.
8. **Close out.** Independent completed-change review, wiki update, push to PR #54; no hosted review for documentation.

## Obligation table

| Obligation | AC | Evidence | Result | Owner / next action |
| --- | --- | --- | --- | --- |
| Refs, metadata, and trailers free of credentials, private data, and private URLs | AC-01 | Mirror of 34 branches, 1 tag, 56 pull refs, 457 commits (2026-09-14): no tokens, private-key bodies, real team, issuer, or device identifiers, personal paths, or blobs over 1 MB; flagged key headers, secret-like strings, and identifier-shaped values are test fixtures; no feasibility-research code, traces, or runners are tracked, and the research checkout is referenced only as optional read-only provenance | pass except the rows below | — |
| Session-link trailers removed from published history | AC-01 | six commits on `main` carry session-link trailers | blocked | `RELEASE-002`: authorized history rewrite before the visibility change |
| Maintainer personal e-mail in commit metadata | AC-01 | two author identities; the maintainer's personal address is on most commits | accepted by the maintainer | — |
| GitHub-hosted issues, pull requests, comments, and workflow logs safe to publish | AC-01 | 0 issues, 54 pull requests, 36 workflow runs; logs contain only hosted-runner paths; 32 of 34 artifacts expired, 2 expire 2026-09-21; review-bot links point to public settings pages | pass except the rows below | — |
| Private task links in pull-request bodies | AC-01 | seven login-gated task-share links in pull-request bodies | blocked | `RELEASE-002`: remove the links before the visibility change |
| Attachments in pull requests | AC-01 | nine uploaded attachments in three pull requests | accepted by the maintainer | — |
| Dependency, asset, and notice inventory; `LICENSE` and `NOTICE` | AC-01 | 171 resolved runtime artifacts across macOS JVM and iOS: Apache-2.0 except one MIT library, confirmed from published metadata; native Skia (BSD-3-Clause) and SQLite (public domain) payloads and the bundled OpenJDK runtime (GPL-2.0 with Classpath Exception, `legal` notices present) attributed in `THIRD_PARTY_NOTICES.md`; unmodified Apache-2.0 `LICENSE` and a `NOTICE` added; no tracked assets | pass for the repository | `MACOS-008`, `IOS-003`: full notices inside application bundles; release JDK vendor |
| Product-name trademark search | AC-01 | 2026-09-14: official EUIPO TMview, WIPO Brand Database, and USPTO search refuse automated reads (JavaScript or CAPTCHA); web index shows no exact mark, nearest similar marks in unrelated goods | blocked for automated search | maintainer: manual search in the official databases |
| Clean checkout passes local gates; environment and release verification policy | AC-02 | Fresh clone of `e2f701f` without `local.properties` or the research checkout: `./gradlew quality` passed in 5 min 16 s (Swift suite 135 tests, 6 skipped). Environment: Java 17 launcher with the build-provisioned JDK 21, Xcode 26.6, Android SDK through `ANDROID_HOME`, no Gradle user properties; the README documents each prerequisite, and the development guide names the SDK but not `ANDROID_HOME`. Release verification stays local (CI disabled) | pass | `RELEASE-002`: local release checklist for signed candidates |
| README accurate, links and build-from-source steps work | AC-03 | README rewritten for users and contributors: verified MVP-001 capabilities, disclosed limits, deployment targets, unavailable channels, credential-free build steps verified on the clean clone (quality, iOS Simulator build, desktop run task present), signed builds tied to team-registered identifiers; local links resolve | pass | `DOCS-001`: showcase README |
| Security, contribution, issue-template, and privacy documents agree with the product; privacy text accepted | AC-03 | `SECURITY.md`, `CONTRIBUTING.md`, bug and question-or-idea templates without personal fields, and `PRIVACY.md` reconciled with the threat model, diagnostics policy (no capture or logging in shipped code), ADR 0005, `MACOS-004` limits, and MVP scope; privacy text accepted on 2026-09-14; the policy discloses that local data except iPhone app choices can be included in device backups and survives moving the Mac app to the Trash, and that with sync on the change history (including removed websites and past session times) stays locally and encrypted in iCloud until Remove workspace, because format 1 has no compaction (ADR 0006) | pass | — |
| Private vulnerability reporting and Issues enabled | AC-03 | repository settings | blocked until the repository is public | maintainer |
| Privacy policy hosted on `posato.app` with the `privacy@posato.app` contact | AC-03 | domain owned; text and contact decided | blocked until hosted and the mailbox exists | `PRIVACY-001` with the maintainer |
| macOS Developer ID, notarization, versioning, bundled notices, install/update/removal | AC-04 | Apple notarization requirements checked 2026-09-14 (Developer ID certificate, hardened runtime, secure timestamp, no `get-task-allow`, signed nested code, `notarytool`, stapling). Current packaging: Apple Development or ad-hoc only, `--timestamp=none`, hardened runtime on, JIT entitlement only; version `1.0.0` hard-coded; no updater; removal follows ADR 0004, dragging to Trash is not a supported uninstall | blocked | `MACOS-008` |
| iOS App Store: Release configuration, Family Controls distribution, versioning, privacy manifest and label, icon | AC-04 | Checked 2026-09-14: uploads since 2026-04-28 need Xcode 26 and the iOS 26 SDK; Family Controls distribution is approved per bundle identifier, including extensions; privacy manifests declare required-reason APIs. Current state: Release configuration lacks the Family Controls entitlement and compilation flag, so enforcement is unavailable; version `1.0.0` (1) hard-coded; no application icon, privacy manifest, or App Store Connect record; iOS reinstall behavior still open from `IOS-001` | blocked | `IOS-003`, `DESIGN-002`, `PRIVACY-001`; maintainer requests Family Controls distribution |
| CloudKit production schema, quota, and retention; Keychain environment | AC-04 | Development builds use the CloudKit Development environment; no production schema is deployed and quota or retention behavior is unmeasured; Keychain group unchanged between environments (source claim, to verify with release signing) | blocked | `SYNC-017`; maintainer deploys the schema in the CloudKit Console |
| Encryption and export-compliance declarations | AC-04 | Apple requires `ITSAppUsesNonExemptEncryption` or an equivalent App Store Connect answer; Posato implements application-layer encryption (ADR 0006), so the exemption must be assessed and may need an annual self-classification; no declaration exists | blocked | `IOS-003`, `MACOS-008`, without legal opinion |
| Supported platform matrix (current and previous major versions) | AC-04 | Deployment targets macOS 15 on arm64 and iOS 18; physical evidence covers one Mac on macOS 26.5.2 with Safari 26.5.2 and Chrome Stable 152, and one iPhone whose iOS version was not recorded; previous major versions are unverified; README states the targets without a verified-matrix claim | blocked | `RELEASE-002`: dated matrix on macOS 26 and 15 and iOS 26 and 18 |
| `RELEASE-001` handovers from earlier authorities | AC-05 | Swept `docs/` 2026-09-14. ADR 0004 channel compatibility: Developer ID keeps the non-sandboxed helper, pass. ADR 0005 and `MACOS-004` Automation, port, proxy, Private Relay, IP-address, and presentation disclosures: in README, `SECURITY.md`, and `PRIVACY.md`, pass. Diagnostics policy: no capture store, export, or production logging ships, platform diagnostics disclosed, pass. Transferred: `MACOS-003` signing and notarization and ADR 0004 update delivery to `MACOS-008`; `SYNC-007`, `SYNC-008`, `SYNC-010` production schema, quota, and retention to `SYNC-017`; `ONBOARDING-001` release-build permission persistence, `ONBOARDING-002` accessibility captures, and the dated browser matrix to `RELEASE-002`; `TB-08`/`T-13` signing and update review to `MACOS-008` and `RELEASE-002` | pass for disclosures; rest transferred | named rows |
| MVP-001 evidence reconciled; residual risks R-01 to R-06 rechecked | AC-05 | MVP-001 accepted on `105ddda`; later commits change no product code (only documentation, verification recipes, and a verification fixture), so its evidence applies with its recorded limits and development signing only. Public documents disclose R-01 (Apple Account trust), R-02 (removable, not administrator-resistant), R-03 (Apple metadata, best-effort delivery), R-04 (no remote wipe or key-loss recovery), and R-06 (browser, port, proxy, content, presentation limits) and make no memory-erasure claim (R-05) | pass | — |
| Final verdict on candidate artifacts owned by a named row | AC-05 | Roadmap revision 15 accepted: `RELEASE-002` owns the final verdict and the public-distribution gate, depends on `MACOS-008`, `IOS-003`, `SYNC-017`, `DESIGN-002`, `PRIVACY-001`, and `DOCS-001`; `IOS-003` owns the Family Controls distribution gate | pass | `RELEASE-002` |

## High-risk plan review

- **Verdict:** `changes-required` (independent agent, 2026-09-14); no Critical.
- **Required findings:** (R1) the exposure audit missed pull-request refs, commit metadata and trailers, private URLs, and GitHub-hosted content; (R2) obligations earlier authorities hand to `RELEASE-001` were not collected; (R3) encryption and export-compliance declarations were missing; (R4) no row owned the final ready verdict on candidate artifacts.
- **Resolution:** all folded into the brief and plan with the recommended points; the maintainer approved the corrected plan on 2026-09-14 (`user-confirmed`).

## Result

- **Verdict: blocked** for revision `0d33be3` plus this change, Developer ID on macOS and the App Store on iOS, deployment targets macOS 15 (arm64) and iOS 18. Repository hygiene, licensing, clean-checkout build, public documents, disclosures, and residual-risk rechecks pass; distribution signing, iOS Release enforcement, production CloudKit, store assets, privacy publication, the platform matrix, history cleanup, and trademark confirmation are blocked with named owners.
- Added `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, `PRIVACY.md`, `SECURITY.md`, `CONTRIBUTING.md`, and bug-report and question-or-idea templates; rewrote the README; updated the brief and roadmap revision 15. No product code changed, and no external write action was taken against Apple or GitHub.
- Deviation: official trademark databases refused automated searches, so that obligation stays with the maintainer.

## Completed-change review

- **Verdict:** `changes-required` (independent agent, 2026-09-14); no Critical.
- **Required findings:** (R1) the README overstated how long blocking lasts on macOS and iOS; (R2) the privacy text implied that removing Posato deletes local data; (R3) the development guide linked a README section the rewrite removed.
- **Resolution:** README discloses that Mac blocking needs Posato running and an approved resume, that paused Mac apps are quit, and the iPhone expiry limits; the privacy text discloses device backups and data left after moving the Mac app to the Trash; the link points to the new section. Recommended points folded: question and idea template, three record wording corrections, Simulator and Java requirements.
- **Re-review:** `changes-required` for one Required point (the backup sentence omitted settings, sync state, and Mac app choices); corrected, with the template wording and Java requirement. No Critical or Required finding remains.
- **Hosted review pass 1** (`d007274`): one P1, retained synchronized change history was undisclosed in the privacy text; accepted and corrected as a disclosure, without implementing compaction. The independent correction review required naming change times, scheduled session ends, and early ends; corrected, with backup and device-registration wording and a wiki lifecycle note. No second hosted pass by default (documentation only).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Rebase onto `0d33be3`, `git diff --check` | pass | only this task's documents and the roadmap differ from `main` |
| History mirror and GitHub-hosted content scans | pass with recorded decisions | categories and counts only; comparisons against real identifiers matched nothing |
| Clean clone `./gradlew quality` | pass | `e2f701f`, 5 min 16 s |
| README iOS Simulator build and desktop run task | pass | clean clone; build succeeded |
| Apple requirements (notarization, privacy manifests, Family Controls, upload SDK, export compliance) | checked 2026-09-14 | official pages; CloudKit schema page unreadable, recorded as a source claim |
| Diff scans for private data; local link check | pass | no personal paths, identifiers, or private URLs |

## Final

- **Status:** `done`
- **Outcome:** blocked verdict with named owners; public documents ready for the repository; publication remains a separate maintainer action after `RELEASE-002`.
