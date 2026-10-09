# `MACOS-026`: An in-app update replaces Posato without asking "Quit Posato?"

- **Review tier:** Standard
- **Tier reason:** The change decides which terminations skip an existing
  confirmation, inside the accepted ADR 0009 rule; it does not change update
  admission, the installer lifecycle, signing, or what a session restricts.
  Escalate to High-risk if the fix needs any of them or an ADR 0004 or ADR
  0008 amendment.
- **Dependencies:** None; release 1.4, wave 1. `DOCS-005` and `RELEASE-006`
  wait for this row.
- **Integration group:** PR-MAC-UPDATE-QUIT
- **Authority:** [Release roadmap](../release-roadmap.md) revision 20, row
  `MACOS-026`; the maintainer named the row on 2026-10-09.
- **Record:** [execution record](../executions/macos-026-update-without-quit-question.md)

## Outcome

When a person chooses **Install and Relaunch** in an in-app update, Posato
quits and is replaced without the "Quit Posato?" confirmation, whatever its
schedule state. A quit that a person starts still asks exactly as today.

## Boundaries

- Start from idea 27 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md): Sparkle's
  termination reaches the same AWT quit handler as a person's quit
  (`ResidentWindow`, `onQuitRequest`), so `quitPromptFor` asks while a
  schedule is on and the update waits until someone presses **Quit**.
- [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md) "Quit during a
  session" already states that a supported update relaunch never waits on the
  confirmation; this row brings the code in line with it. No ADR changes.
- Recognize the updater's quit by a narrow signal from the updater leaf just
  before Sparkle terminates Posato for an admitted installation, in the way
  the existing power-off window exempts system terminations. A person's quit
  must keep asking in every other case, including while maintenance
  admission stays closed after a cancelled installation.
- Keep [ADR 0008](../../decisions/0008-macos-update-delivery.md) and the ADR
  0004 update contract as they are: an active pause still refuses admission,
  cleanup before Install is unchanged, and the updater never ends a session.
- Non-goals: the 1.3 to 1.4 update (the old build's own handler still asks,
  `observed` in `RELEASE-005`), the confirmation copy, window close, Sparkle UI
  or a fork, and iOS.

## Acceptance

- `AC-01` — An update from a candidate with the change to a newer candidate,
  with a schedule on and no pause running, relaunches the newer build with no
  "Quit Posato?" dialog and no press after **Install and Relaunch**.
- `AC-02` — During a pause, **Install** is still refused as today and no
  quit question appears; after **End early** the same update installs as in
  `AC-01`.
- `AC-03` — With the newer build running and a schedule on, **Quit Posato**
  from the menu and Cmd-Q still ask, and **Keep Posato open** keeps it
  running; with no schedule and no pause, quit does not ask.
- `AC-04` — After the update the schedule still starts on its own and blocks,
  and the websites and helper setup are kept.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Two notarized candidate-channel DMGs N and N+1 from the branch, with a
  throwaway update key and a candidate test feed (`apple-provisioning.md`,
  "Update feed and channels"); builds that run only in destroyed clones do
  not count toward the release floor.
- A fresh `primary` clone per run, following `features/updates.md`: install
  N, set up, add a website and a schedule, then update to N+1 for `AC-01`;
  a second run with a pause for `AC-02`; `AC-03` and `AC-04` on the relaunched
  build with `launch --adopt` and `observe`.
- A JVM test of the quit-prompt rule only if a credible failure needs
  isolation, written failing first under the testing policy.

## Decisions or blockers

- `D1` (maintainer): the roadmap row says to verify the update "during a
  pause", but ADR 0008 refuses an installation while a pause runs. The brief
  reads it as `AC-02` (refusal kept, no quit question, update after End
  early). Allowing an update during a pause would need an ADR 0008 and ADR
  0004 revision and a High-risk row instead.
- Possible blocker: Developer ID signing of the candidates on the host may
  raise a Keychain access prompt; if it does, that one approval is the
  maintainer's, outside verification.
