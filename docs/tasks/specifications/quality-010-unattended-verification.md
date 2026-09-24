# `QUALITY-010`: Verify every task without the maintainer

- **Review tier:** `high-risk`
- **Tier reason:** The task automates privileged system prompts, holds test-account credentials outside Git, adds a second Apple Account with its own CloudKit data, and changes the verification driver that every later task relies on. It needs a brief independent plan review before implementation and one independent completed-change review per stage.
- **Dependencies:** None blocking. `QUALITY-007` shares the Tart macOS image; `MACOS-011` Stage 2 shares the physical Mac and may touch the driver. Started from the backlog on 2026-09-24 by maintainer decision, ahead of any release composition.
- **Integration group:** `PR-UNATTENDED-VERIFICATION`, no milestone.
- **Authority:** [release roadmap](../release-roadmap.md) (backlog row), [wiki idea 10](../../wiki/topics/mvp-open-questions.md#post-mvp-feature-ideas-for-discovery), [verify-posato](../../../.agents/skills/verify-posato/SKILL.md), [`tools/posato-control/README.md`](../../../tools/posato-control/README.md), [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md), [threat model](../../security/apple-mvp-threat-model.md), [`PRIVACY.md`](../../../PRIVACY.md), [quality contract](../../development/engineering-quality-contract.md), and the [task workflow](../README.md).

## Outcome

An agent can verify a Posato change end to end without the maintainer: on macOS inside Tart virtual machines on the supported Mac, and on a dedicated physical test iPhone, both signed in to a dedicated test Apple Account. After one-time setup, the verification driver handles every system prompt, permission, and picker, and it observes actual website and application blocking and unblocking rather than only application state.

## Boundaries

- Stage 1, one-time setup and go/no-go measurements:
  - create the dedicated test Apple Account (maintainer-owned step with a short checklist; the agent prepares everything else) and keep its credentials in the host Keychain, read at run time, never in tracked files;
  - build a golden Tart macOS image with the test account signed in, the one-time approvals granted, and a snapshot the driver can restore;
  - measure and record go or no-go for: CloudKit and iCloud Keychain for the sync companion inside a VM; deterministic VNC keyboard and pointer control of SecurityAgent, System Settings, and Gatekeeper; Screen Time consent and any passcode prompt through XCUITest on the test iPhone; coordinate taps in the out-of-process application picker; and the network-service-switch scenarios with the VM's single interface.
- Stage 2, driver automation, only after a Stage 1 go: extend `posato-control` and the `verify-posato` skill so the recorded recipes run unattended on the VM and the test iPhone, including a second VM as the Mac-to-Mac sync peer.
- Stage 3, observed blocking (absorbs `QUALITY-006`): the driver proves that a paused website and a paused application are actually blocked during a session and reachable after it, on both targets.
- Development builds use the CloudKit Development environment; the test account never touches the maintainer's private data or the Production container beyond what a release verification already does.
- Keep VM images, snapshots, device identifiers, account values, and captures out of tracked files. Do not weaken ADR 0004 authorization rules; VM input is hardware input to the guest.
- Non-goals: hosted CI (`QUALITY-009`), golden UI tests (`QUALITY-008`), product fixes found on the way (own rows), and Intel or macOS 14 coverage (`MACOS-015`).

## Acceptance

- `AC-01`: Every Stage 1 measurement has a recorded go or no-go with its evidence, and a no-go names the fallback or the stated gap.
- `AC-02`: The test Apple Account exists, is separate from the maintainer's account, and the driver reads its credentials from the host Keychain without any tracked or logged secret.
- `AC-03`: A restored golden VM runs the accepted core flow (onboarding, websites and applications, start, block, early end, expiry, relaunch, sync with a peer) through the driver with no attended step.
- `AC-04`: The dedicated test iPhone runs the same flow through the driver, including Screen Time consent and the application picker, with no attended step.
- `AC-05`: Both targets record observed blocking and unblocking of a website and an application, and the `verify-posato` feature map names the recipes.

## Verification

- Stage evidence lives in ignored `build/verification/`, with run directories cited from the execution record.
- Driver and skill changes get focused tests where they parse, decide, or validate; `./gradlew quality` after the last correction.
- Independent plan review before Stage 1 implementation; one independent completed-change review per stage.

## Decisions or blockers

- `user-confirmed`, 2026-09-24: start now from the backlog without a roadmap planning step; the row stays in the backlog table and gains a release later.
- `user-confirmed`, 2026-09-24: no test Apple Account exists yet; creating it is one of the first Stage 1 steps.
- `user-confirmed`, 2026-09-24: a dedicated test iPhone exists and moves to the test Apple Account as its trusted device. The maintainer's private iPhone is not the test device.
- `user-confirmed`, 2026-09-24: the golden VM runs macOS 26, created from an IPSW on this Mac; the `QUALITY-007` macOS 15 image stays separate.
- `user-confirmed`, 2026-09-24: this task sets up App Store Connect API access to register the VMs and regenerate development profiles.
