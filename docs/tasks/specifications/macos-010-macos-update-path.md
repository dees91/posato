# `MACOS-010`: Decide the macOS update path

- **Review tier:** `high-risk`
- **Tier reason:** The decision revises the ADR 0004 update contract, adds the application's first outbound network request outside iCloud, and touches the `PRIVACY.md` promise; a brief independent plan review precedes the comparison and the decision record receives a completed-change review.
- **Dependencies:** none; release 1.1, wave R1.1/W1. `MACOS-011` implements the accepted path.
- **Integration group:** `PR-MAC-UPDATE-DECISION`, milestone `1.1.0`. Documentation only.
- **Authority:** [release roadmap](../release-roadmap.md) (revision 1), [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md) (update path: restore proxy ownership and helper registration before the bundle is replaced; manual download clarification of 2026-09-15), [`PRIVACY.md`](../../../PRIVACY.md), [threat model](../../security/apple-mvp-threat-model.md) (`TB-08`, `T-13`), [MACOS-008 record](../executions/macos-008-developer-id-distribution.md).

## Outcome

A recorded, maintainer-accepted decision on how Posato on macOS learns about and obtains a newer release, with the proposed ADR 0004 revision, the privacy wording it needs, and a delivery plan for `MACOS-011`; no product code.

## Boundaries

- Compare three options: a check against the GitHub Release metadata with a guided quit, replace, and open; a Sparkle-style in-app installer driven from the JVM application through a native boundary with a signed appcast; and the status quo manual download.
- Criteria: what the check sends (version, OS, architecture, nothing that identifies a person or installation), frequency, a visible opt-out, feed signing and transport security, downgrade protection, the ADR 0004 restore-before-replace steps and what happens when an update is interrupted, helper and daemon compatibility, custody of any update signing key outside Git, hosting on `posato.app` or GitHub, effort, and compatibility with the resident process planned in `MACOS-012`.
- List every change the accepted option needs in ADR 0004, `PRIVACY.md`, the hosted privacy policy, and the availability page.
- Non-goals: implementing the check or installer, Mac App Store distribution, silent installation, telemetry of any kind.

## Acceptance

- `AC-01` — A decision record compares the options against the criteria with provenance labels and one recommendation.
- `AC-02` — The proposed ADR 0004 revision text and the privacy wording changes are written out, not summarized.
- `AC-03` — A `MACOS-011` delivery plan states how a notarized candidate proves the path by updating to a newer notarized build with proxy ownership restored.
- `AC-04` — The maintainer's acceptance or rejection is recorded (`user-confirmed`) before `MACOS-011` starts.

## Verification

- Independent plan review before the comparison and an independent review of the decision record.
- No build or device verification; the documents are the deliverable.

## Decisions or blockers

- **Decisions for the maintainer:** feed hosting, whether the check is on by default with an opt-out or off by default, and who holds any update signing key.
- No blocker.
