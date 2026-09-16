# `DOCS-001`: Turn the README into a showcase for Posato

- **Review tier:** `standard`
- **Tier reason:** Public-facing documentation and media change no product behavior; an independent check still covers claim accuracy against verified behavior, synthetic media, and links.
- **Dependencies:** completed `RELEASE-001`, `DESIGN-002` (icons and store screenshots), and, for final UI, `DESIGN-003` and `MACOS-009` (all merged by `7a3aee7`). `WEB-001` follows this row.
- **Integration group:** `PR-SHOWCASE-README`, roadmap wave Release/R2.
- **Authority:** [MVP roadmap](../mvp-roadmap.md) (revision 15 maintainer request), [README](../../../README.md), [DESIGN.md](../../../DESIGN.md) (voice and identity), [privacy policy](../../../PRIVACY.md), [RELEASE-001 record](../executions/release-001-first-release-readiness.md) (disclosed limits), [store listing](../../store/en-US/listing.md).

## Outcome

The root README draws a newcomer in within its first screen: the Posato identity, a short animated demo of pausing websites and apps on Mac and iPhone, and one sentence on what it does. It then routes to a quick start, how it works with screenshots, limits, privacy, and documentation, without losing any accurate claim or limit the current README states.

## Boundaries

- Model the structure on the maintainer's `skill-manager` and `rfid-store-simulator` READMEs: hero demo GIF, a link to the full walkthrough video, quick start, how it works with screenshots, documentation and support, license and notices. Draft and review the prose with the `show-me` and `clarity` skills.
- Posato has no public download yet: the quick start stays build-from-source and the status stays pre-release until `RELEASE-002`; no store badge or download link points at an unavailable channel.
- Keep every limit and privacy statement the current README makes (Mac blocking needs Posato running, browser and port coverage, iPhone expiry, best-effort sync, no key-loss recovery); move detail behind links rather than dropping it.
- Media come from the real apps through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) with synthetic websites, apps, and account state only; no personal data, device names, or notifications. Track the Remotion project under `video/` with pinned versions, and the rendered GIF and screenshots at a small, documented size; the full walkthrough is uploaded by the maintainer as a GitHub attachment.
- Non-goals: the `posato.app` site (`WEB-001`), store metadata changes, product or copy changes inside the apps, new documentation pages beyond routing.

## Acceptance

- `AC-01` — The first screen of the README shows the identity, the hero demo, and a one-sentence value statement, and routes to the quick start and full walkthrough.
- `AC-02` — Every capability and limit claim matches `MVP-001`, the release rows, and `PRIVACY.md`; nothing claims availability that does not exist.
- `AC-03` — The Remotion project renders the hero GIF and the walkthrough reproducibly from tracked sources and captures; tracked media stay within the documented size budget.
- `AC-04` — Screenshots and video show only synthetic data, and every local link and image path resolves.

## Verification

- Render the video from a clean `video/` install and record the commands and output sizes.
- Link and image-path check; review the README on GitHub's renderer in the pull request.
- Independent completed-change review against the current README, `MVP-001`, and `PRIVACY.md`; `./gradlew quality` only if tooling outside `video/` changes.

## Decisions or blockers

- **Blocker (maintainer):** uploading the full walkthrough as a GitHub attachment and accepting the final README.
- **Resolved (2026-09-16):** the maintainer confirmed individual use; Remotion's free license permits individuals to create videos, including commercial work. Verify the pinned package's license during implementation.
