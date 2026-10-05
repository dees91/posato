# `DESIGN-004`: A native feel on iOS and a refined shared design system

- **Review tier:** Standard
- **Tier reason:** Shared interface, navigation presentation, design
  authority, and verification tooling across iOS and macOS; no persistence,
  synchronization, enforcement, privacy, or signing change.
- **Dependencies:** None; release 1.4, wave 1.
- **Integration group:** PR-NATIVE-FEEL (PR #137)
- **Authority:** [Release roadmap](../release-roadmap.md) revision 18, row
  `DESIGN-004`; the maintainer accepted the spike's direction on 2026-10-05.
- **Record:** [execution record](../executions/design-004-native-feel.md)

## Outcome

Posato feels like an app made for each Apple device while its interface
stays one shared Compose codebase, and `DESIGN.md` records that direction for
later work.

## Boundaries

- iOS chrome behind the platform switch: the UIKit-like screen stack, bars
  and large titles, native menus and pickers, and the tab bar that belongs to
  the destinations.
- Shared controls on every platform: system font, text buttons on the
  content edge, one field style, switch rows, list rows instead of nested
  cards, swipe actions, and press feedback without ripple on the Apple hosts.
- One native audit and one polish round whose scope the maintainer chooses.
- `posato-control` and `verify-posato` reach every changed control.
- Non-goals: Android and Linux hosts, product behavior, copy beyond labels
  the new chrome needs, and dependency upgrades.

## Acceptance

- `AC-01` — iOS navigation: push, pop, and edge swipe move whole screens with
  the bar; the swipe follows the finger and keeps its release velocity; the
  tab bar slides with the destinations and stays under the keyboard.
- `AC-02` — The shared controls listed above appear on iPhone, iPad, and the
  Mac in light and dark appearance, with no ripple on the Apple hosts.
- `AC-03` — Every audit finding the maintainer selects is resolved, and a
  repeated audit reports no new P0 or P1 finding.
- `AC-04` — `DESIGN.md` describes the adaptive direction and replaces the
  rules it supersedes; the wiki routes to it.
- `AC-05` — The existing scenarios and flows pass against the new interface.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `posato-control` scenarios on an iPhone and an iPad Simulator in both
  appearances, and on the test iPhone on a cable.
- The Mac in a Tart VM (`--vm primary`): the changed screens and flows.
- `./gradlew quality`.

## Decisions or blockers

- None.
