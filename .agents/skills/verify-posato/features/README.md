# Posato verification map

This directory is the maintained source for verifying the user-facing behavior
of Posato. Read the index before driving the app, then use the matching
feature file as the recipe.

## Baseline preconditions

- Build the CLI with `./gradlew :posato-control:installDist` and use
  `PC=tools/posato-control/build/install/posato-control/bin/posato-control`.
- Pick one target: `sim` (booted `iPhone 17`, app installed, no permissions
  needed), `desktop` (Accessibility and Screen Recording granted to the agent's
  host application), or `device` (connected, unlocked iPhone and
  `posato.apple.developmentTeam` in the ignored `local.properties`).
- Launch through the CLI (`launch -t <target>`), wait for the `Paused items`
  button (`wait --for exists --text "Paused items" --role button`), and
  require `doctor -t <target>` to report `ok: true`. The app opens on the
  `Session` destination after every launch and relaunch.
- Before a Websites or Applications recipe, switch destination with `tap
  --text "Paused items" --role button` and wait for `Add website`; the
  Sessions recipe starts on `Session` and needs at least one website first.
- Start recipes from a state with no website named `example.com` and no
  active session; the Simulator can start from `launch --fresh`, the desktop
  keeps the developer's real data, so remove what you add and end what you
  start instead of resetting.
- Never drive an instance that this run did not launch.

## Driving conventions

- Every recipe names the exact `posato-control` command; keep labels, flags,
  and quoted copy unchanged.
- Address controls by visible copy plus `--near-text` for anything that
  appears once per row (`Edit`, `Remove`); address text fields by role and the
  section header above them (`--role textField --near-text "Websites"` or
  `"Applications"`), never by label or index.
- On iOS finish text entry with `--submit` (or `"submit": true` in a
  scenario) before tapping controls below the field; the keyboard hides them
  and Compose drops their labels. After a rejected submit the keyboard stays
  open, so relaunch before the next step.
- Prefer `run --scenario <file>` over many single commands on iOS.
- Restore data after a mutation. Do not remove proof artifacts during cleanup.

## Proof and skip reporting

- Capture the user action and the resulting state, not only the final screen.
- UI proof is a `snapshot` (or a `wait` naming the element) plus a
  `screenshot` from the run directory.
- Mutation proof includes a read-only second view of the stored value:
  `db query` on the desktop and Simulator, `find` after a relaunch on the
  iPhone.
- Record the feature ID, the target, and the run id with every artifact; cite
  the run directory, never paste screenshots or paths into tracked files.
- Report an unreachable path with the attempted command and the failing
  check. Do not report a skipped entry point as verified through another
  target.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the
user-visible behavior. It then uses exactly four H2 sections in this order.

1. `Sub-features` lists short IDs with one line for each behavior.
2. `How to get to it (user POV)` lists every user entry point.
3. `Driving it with posato-control` starts with `Preconditions:` and uses
   labeled bullets that pair each user action with an exact command and an
   observable result.
4. `Gotchas` lists traps that can waste or invalidate a verification run.

Keep implementation details out of the map. Name only user paths, stable
handles, required state, commands, and observable proof.

## Features

- [Websites](./websites.md) covers adding, rejecting, editing, removing, and
  persisting exact domains on every target.
- [Application group](./application-group.md) covers naming, editing,
  removing, and persisting the single application group.
- [macOS application mappings](./macos-application-mappings.md) covers the
  desktop-only application picker and its limits for scripted verification.
- [iOS application mappings](./ios-application-mappings.md) covers the
  device-only Family Controls picker, its access states, and the manual steps
  the driver cannot reach.
- [Sessions](./sessions.md) covers setting up, reviewing, starting, ending
  early, and expiring one manual session on every target, and what survives a
  relaunch.
