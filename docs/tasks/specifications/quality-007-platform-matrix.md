# `QUALITY-007`: Verify the supported platform matrix

- **Review tier:** `standard`
- **Tier reason:** Verification and public availability wording with no product behavior change. It needs attended runs on older operating systems and one independent completed-change review of the published claims.
- **Dependencies:** `SESSION-004`, `ONBOARDING-003`, `TARGETS-006`, and `IOS-004`, all merged. Release 1.1, wave R1.1/W2, beside `MACOS-011`.
- **Integration group:** `PR-PLATFORM-MATRIX`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md) (row and coverage matrix), [limits and platforms](../../product/limits-and-platforms.md), [MVP-001 acceptance](../executions/mvp-001-end-to-end-acceptance.md), [verify-posato](../../../.agents/skills/verify-posato/SKILL.md), and the [quality contract](../../development/engineering-quality-contract.md).

## Outcome

The published support claim (macOS 15 or later on Apple silicon, iOS 18 or later) either rests on attended runs of the accepted flow on macOS 15 and iOS 18, or the availability page states exactly which part is unverified.

## Boundaries

- Run the accepted core flow on each older system:
  - onboarding;
  - websites and, where supported, applications;
  - start, block, early end, and expiry;
  - relaunch;
  - sync with the other device.
- macOS 15 runs in a virtual machine on the supported Mac. iOS 18 runs on a physical iPhone.
- Run on the final 1.1 candidates after `MACOS-011` merges, and record the exact builds.
- Update the Availability and Supported platforms text in `docs/product/limits-and-platforms.md`, and matching website copy, with the verified matrix or its stated gaps. Wording beyond that belongs to `RELEASE-003`.
- Keep the physical Mac serialized with `MACOS-011` notarized experiments. Keep VM images, device identifiers, and captures out of tracked files.
- Non-goals: product fixes, which get their own rows if a run fails; new platforms; automating the VM.

## Acceptance

- `AC-01`: Each core-flow step has a recorded result on macOS 15, or an explicit gap with its reason.
- `AC-02`: Each core-flow step has a recorded result on iOS 18, or an explicit gap with its reason.
- `AC-03`: The availability page and the website state only what these runs verified, and the README summary stays consistent.

## Verification

- Attended runs with `verify-posato` where the driver reaches the target, and manual steps where it does not. Evidence stays in ignored `build/verification/`.
- One independent completed-change review of the published wording against the evidence, then the documentation link checks.

## Decisions or blockers

- `user-confirmed`, 2026-09-23:
  - D1: the macOS 15 target is a Tart virtual machine from a macOS Sequoia image on the supported Mac. The maintainer installs Tart and signs in the Apple Account for sync.
  - D2: the maintainer provides a physical iPhone on iOS 18, connected by cable for the runs.
  - D3: run once on the final 1.1 candidates, after `MACOS-011` merges. Until then, only the VM and device are prepared.
