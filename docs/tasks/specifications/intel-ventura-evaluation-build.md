# Intel Ventura evaluation build

- **Review tier:** `high-risk`
- **Tier reason:** The candidate changes native packaging, Developer ID signing, and notarization for another architecture and deployment target.
- **Dependencies:** PR #106 merged; Intel support decision remains open under `MACOS-015`.
- **Integration group:** Manual evaluation build; no release publication.
- **Authority:** Direct maintainer request on 2026-09-29; `MACOS-015` roadmap stub and ADR 0003.

## Outcome

A locally delivered, notarized x86-64 DMG is available for the maintainer's 2019 MacBook Air running macOS 13 Ventura to evaluate.

## Boundaries

- Preserve the existing arm64/macOS 15 production path and version; use a separate candidate build.
- Include an x86-64 Java runtime, native JVM libraries, Swift helper and daemon, sync companion, native UI leaves, and Sparkle framework.
- Do not publish a release, update feed, or support claim, and do not run or install Posato on the maintainer's host Mac.

## Acceptance

- `AC-01` — The candidate's app and all bundled Mach-O code include x86-64, with app and helper minimum system versions no higher than macOS 13.
- `AC-02` — Nested signatures, Developer ID profile, notarization tickets, and Gatekeeper assessment pass for the DMG.
- `AC-03` — The DMG and its SHA-256 digest are placed on the Desktop without replacing an existing file.
- `AC-04` — The evaluation limits and exact candidate identity are recorded; Ventura runtime behavior remains open until observed on macOS 13.
- `AC-05` — The candidate never reads the stable Sparkle feed; its packaged feed URL, public key, and disabled automatic checks are verified.

## Verification

- Build and verify the architecture and deployment target of every packaged Mach-O binary.
- Inspect the packaged Sparkle settings and confirm they cannot offer an arm64/macOS 15 release.
- Run focused Gradle package checks, `./gradlew quality`, and the notarized DMG verification chain after the final correction.
- If available, smoke-launch the candidate in a Tart guest through `posato-control`; this checks packaging only, since the guest is not Intel Ventura.

## Decisions or blockers

- ADR 0003 still limits production support to arm64/macOS 15. This candidate gathers evidence for `MACOS-015` and does not amend that decision.
