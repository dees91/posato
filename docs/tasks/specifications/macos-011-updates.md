# `MACOS-011`: Deliver the accepted macOS update path

- **Review tier:** `high-risk`
- **Tier reason:** Third-party installation, update signing, new network requests, and enforcement cleanup must remain safe across cancellation and process restart.
- **Dependencies:** `MACOS-010`, merged in PR #74; release 1.1, wave R1.1/W2. The initial proof stage may run beside `ONBOARDING-003` under the maintainer's 2026-09-23 preparation/delegation decision.
- **Integration group:** `PR-MAC-UPDATES`, milestone `1.1.0`. Stage 1 (the safety proof) merged in PR #76; Stage 2 delivers the rest of this brief in one pull request.
- **Authority:** [ADR 0008](../../decisions/0008-macos-update-delivery.md), [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md), [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md), [threat model](../../security/apple-mvp-threat-model.md), [PRIVACY.md](../../../PRIVACY.md), [release roadmap](../release-roadmap.md), and [quality contract](../../development/engineering-quality-contract.md).

## Outcome

A notarized Mac candidate can obtain and install a newer notarized candidate through the accepted Sparkle path while preserving user state and proving that enforcement cannot run while bundle replacement remains possible.

## Boundaries

- ADR 0008 is the complete delivery contract, including its required scenario matrix. Prove admission, cancellation, and crash/relaunch recovery before full integration; a failed proof blocks delivery.
- Use the separate native updater leaf and Kotlin-owned admission described there. Preserve helper authorization, synchronization ownership, disabled-service state, and the existing window leaf's networking prohibition.
- Keep the accepted consent, signed-feed/archive, local version comparison, request-data, key-custody, and GitHub hosting rules. Review and pin Sparkle during implementation; this brief selects no new version or exception.
- Apply the staged authority amendments with reviewed delivery. Publish public policy and availability wording only with the verified feature, coordinated with `RELEASE-003`, which owns the stable release.
- Non-goals: private Sparkle APIs, a fork, a custom installer, weaker fallback, silent downloads/installations, telemetry, resident/menu-bar behavior, or creating signing keys during this preparation.

## Acceptance

- `AC-01`: The initial notarized A-to-B experiment proves atomic no-active-session admission, confirmed cleanup, and closed enforcement admission across concurrent local/sync/retry intent, cancellation, quit/crash, another instance, and relaunch. Reopening requires positive safe-release evidence and native compatibility/readiness checks per ADR 0008.
- `AC-02`: Consent, opt-out, manual checking, explicit download/install, and active-session refusal follow ADR 0008. Measured requests, headers, cookies, redirects, and release notes stay within its privacy boundary.
- `AC-03`: Invalid/missing signatures, tampering, same/older builds, unsupported platforms, network failures, and uncertain cleanup cannot admit replacement or bypass enforcement safety. Required binaries and DMGs pass signing, notarization, packaging, and notice checks.
- `AC-04`: A successful update preserves local data and sync configuration, leaves a disabled helper disabled, and admits enabled enforcement only after ready/Idle and existing authorization. All ADR 0008 acceptance scenarios have reproducible evidence or an explicit delivery blocker.
- `AC-05`: The repeated local release process produces the signed feed and final DMG together; test candidates use a separate feed and never become latest stable. Reviewed authorities and release handoff match the verified behavior.

## Verification

- Independent plan review before implementation and independent completed-change review before merge; final `./gradlew quality` after the last correction.
- Focused admission/recovery/state and native-boundary tests with synthetic fixtures, packaging checks, and the full [ADR 0008 scenario matrix](../../decisions/0008-macos-update-delivery.md#macos-011-delivery-plan-and-acceptance).
- Drive notarized A-to-B candidates with [verify-posato](../../../.agents/skills/verify-posato/SKILL.md), including cancellation/restart and native ownership checks. Measure request behavior using controlled evidence. Keep captures and identifiers in ignored `build/verification/` and preserve local user state.

## Decisions or blockers

- `user-confirmed`, 2026-09-23: prepare a worktree, brief, and draft PR for delegated implementation; the first stage is the ADR 0008 safety proof.
- `user-confirmed`, 2026-09-23: the Stage 1 test feed runs on a local loopback server, test candidates use a separate throwaway Ed25519 key, and Stage 1 is production-quality code without consent UI. A failed proof marks the pull request blocked.
- `observed`, 2026-09-23: exact installer-job absence in every launchd domain, no `Autoupdate` process from this bundle, an on-disk bundle identity matching the running signed build, and service revalidation established safe release on notarized candidates. See the execution record; an aborted cycle alone is still never sufficient.
- `user-confirmed`, 2026-09-23: open Stage 2 as one task on this row; do not split it into smaller rows.
- `user-confirmed`, 2026-09-23, Stage 2 decisions:
  - D1: a public GitHub prerelease with a separate `appcast-test.xml` hosts
    the real-GitHub request measurement and the test feed. It never becomes
    latest and is deleted after measurement.
  - D2: set a fixed `Accept-Language: en` through `SPUUpdater.httpHeaders`.
  - D3: a native consent prompt after first-run setup, "Check for Updates…" in
    the application menu, and the automatic-check toggle and manual check in
    About Posato. No new destination.
  - D4: the maintainer creates the production key and its encrypted backup at
    Stage 2 intake. Stage 2 candidates embed the production public key.
- Confirm signing/notarization access and private-key custody at implementation intake. Account-owned actions use a short maintainer checklist when needed.
