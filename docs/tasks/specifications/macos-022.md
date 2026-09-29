# MACOS-022 — Restore browser pause-page presentation

- **Status:** Active; separately authorized by the maintainer on 2026-09-28.
- **Review tier:** High-risk: hardened-runtime entitlements and helper process lifecycle.
- **Dependencies:** ADR 0004 and ADR 0005; reproduced report in PR #106.
- **Integration:** Separate fix PR based on PR #106; release remains unassigned.

## Outcome

A blocked HTTPS navigation in a supported foreground browser reaches the local
pause page after Automation consent, while denial remains effective without consent.

## Boundaries

Keep the existing exact-host policy, loopback route, supported browsers, IPC
validation, leases, and restore behavior. Do not intercept TLS, log current URLs,
grant Automation to the privileged daemon, or navigate an unrelated tab.

## Acceptance

1. Chrome and Safari foreground blocked navigation reaches the local page with consent.
2. Denied Automation preserves enforcement and does not repeatedly interrupt navigation.
3. An unrelated foreground page is unchanged; session cleanup restores access.
4. The helper and its responsible parent carry the Apple Events entitlement for the existing
   browser role; the privileged daemon stays entitlement-free. Packaging checks stay exact.

## Verification

Use the already failing fresh-Tart Chrome reproduction as the regression baseline.
Run the repaired build only in Tart through posato-control, covering consent,
denial, browser switching, and session end. Run native tests and aggregate quality.
Retain raw evidence under ignored build/verification and obtain independent plan
and completed-change reviews. No host installation or personal browser state is used.
