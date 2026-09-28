# `NOTIFY-001`: Local notifications for pauses

- **Review tier:** `standard`
- **Tier reason:** New local notices and one preference on both platforms. No privileged, network, or synchronization contract changes. The permission prompt is new user-facing behavior.
- **Dependencies:** `MACOS-012` (resident process, ADR 0009); PR #92 (unified setup shells) as the base. Serialized before `ONBOARDING-004`, because both touch onboarding and setup.
- **Integration group:** `PR-LOCAL-NOTIFICATIONS`, milestone `1.2.0`.
- **Authority:** [release roadmap](../release-roadmap.md); [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md) (notifications post from the resident process); [`DESIGN.md`](../../../DESIGN.md); [`PRIVACY.md`](../../../PRIVACY.md).

## Outcome

A person gets a notice when a pause ends, and when a pause starts on another device, on iPhone and on Mac. Posato asks for notification permission once, right after the person's first pause on that device. About Posato has one switch to turn the notices off. Nothing is sent to a server.

## Boundaries

- Local notifications only: no push, no server, no new entitlement.
- **Pause over:** the end notice is scheduled ahead at the planned end, so it arrives while Posato is closed or suspended. It is withdrawn when the pause ends early.
- **Pause started:** only a pause started on another device is announced. A pause the person started on this device is not. On the Mac, the notice says to open Posato, because a received pause waits for **Resume restrictions** (ADR 0009).
- **Permission:** Posato asks once, after the first local start, never at launch. `MACOS-013` `AC-03` (no prompt at launch) stays true.
- **Preference:** a switch in About Posato, on by default. When the system has denied notifications, the switch is disabled and its caption says so.
- Non-goals: schedules (`SCHEDULE-002` reuses these notices), notification actions, and silencing other apps (`FILTER-003`).

## Acceptance

- `AC-01` — On a Mac in a Tart VM, the first pause raises the macOS permission prompt once; a short pause then shows **Pause over** at its end, and a pause ended early shows nothing.
- `AC-02` — On iPhone, the same flow (test iPhone, or the Simulator when the device is locked for automation).
- `AC-03` — Turning the switch off stops the notices; the system-denied state disables it with its caption.
- `AC-04` — Isolated tests, written failing first, cover the planning rules (local versus received starts, the first status after a launch, early end versus expiry, a moved end) and the preference and asked-once rules.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- E2E through `posato-control` in a Tart clone and on the iPhone (or Simulator), with screenshots and a recording.
- `./gradlew quality` after the last correction; one independent completed-change review.
