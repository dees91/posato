# Execution: `MVP-001`

- **Brief:** [End-to-end MVP acceptance](../specifications/mvp-001-end-to-end-acceptance.md)
- **Status:** `active`; execution plan awaiting independent High-risk plan review
- **Review tier:** `high-risk`
- **Implementer:** Claude Code session with the maintainer attending both devices
- **Reviewer:** pending (different agent)
- **Branch:** `docs/mvp-001-acceptance-brief` (PR #53)
- **Updated:** 2026-09-14

## Observed starting point

- Tested revision: product sources at `f0d68e4` (SYNC-012 merged); this branch adds documentation only. Every result row names its revision; a correction moves only the rows it repeats.
- Both `doctor` checks pass in the provisioned worktree; the signed desktop package and the device driver are not yet built there.
- Inherited limits: ONBOARDING-002 observed immediate joining in both directions, never a physical key wait. SYNC-012 used an already linked pair with no selected applications and persisted ten minutes where the fixture asked for five. IOS-002 reports sessions under 15 minutes as `below-platform-minimum`, so suspended expiry needs at least 15 minutes. The macOS picker refuses a fixed set of system-critical identifiers (such as Finder, Dock, Control Center, and System Settings) and `/System/Library/CoreServices/` bundles; the chosen built-in applications are outside that set.

## Decisions (`user-confirmed`, 2026-09-14)

- **D1 fixture:** start from empty without restoring prior development data. The pair ends linked to the new workspace with fixtures removed.
- **D2 order:** Mac installs first and establishes; iPhone joins immediately afterwards. One direction.
- **D3 applications:** one built-in application selected and one built-in control application on each device. Tracked evidence names categories only.
- **D4 key wait:** if the join is immediate, the waiting row is `not observed` and goes to a specific maintainer acceptance decision at closeout; no extra attempts and no private-state tampering.

## Fixtures and ownership

| Fixture | Owner and scope |
| --- | --- |
| `example.com` | Target website authored on Mac |
| `example.net` | Target website authored on iPhone |
| `example.org` | Unselected control website, never added |
| Selected and control applications | One pair per device, chosen through the native route (D3) |
| Workspace and local data | Created from empty by this run (D1) |
| `Applications` group | Created by the first selection; clearing selections keeps it, so it remains an owned leftover |

Allowed destructive setup, only through product and driver paths:

1. **Remove workspace** from the iPhone, then from the Mac, through the iCloud row confirmation. The Mac is expected to report action required for the missing anchor first; its removal then clears only its own key and local state. That is the documented path, not a defect to repair.
2. `reset -t desktop --yes`; the driver keeps the deleted databases under the run's `backup/desktop/`, which is not restored (D1).
3. `reset -t device --yes` (uninstall), then `install` of the same revision.

The desktop reset also deletes the local removed-workspace tombstone, so a reset Mac no longer refuses the old anchor while CloudKit still surfaces it. Stop rule for phase B: the establishing Mac passes only with a completed status and `sync_bootstrap_state` 0→1. Waiting, retryable, or action required on the Mac stops the run for escalation and is never D4 evidence.

Forbidden: database writes, injected product state, Keychain or helper surgery, CloudKit Console deletion, Login Items changes, clock changes. Known carried state: the Mac helper stays registered and approved and the root-owned proxy ownership record is untouched, so the Mac permission step can read enabled rather than exercise Enable.

## Plan

1. Complete independent High-risk plan review of this record; resolve Critical and Required findings; obtain maintainer approval.
2. Run `./gradlew quality` on the tested revision, then `build -t desktop` (signed, after the ad-hoc restage) and rerun `doctor -t desktop`, then `build -t device --driver` and `install -t device`.
3. Execute phases A–G below with the maintainer attending. Capture a driver run for each action and its resulting state.
4. On a defect: stop the affected path, do not repair state, make a scoped correction with a regression test, rerun `./gradlew quality` and affected suites, rebuild both signed apps, and repeat that path on the new revision.
5. Close out once: results per acceptance criterion, independent completed-change review, one wiki-log entry, and a topic update only for a durable conclusion.

## Physical sequence and AC-to-evidence mapping

Evidence classes: `observed` for driver trees, screenshots, and read-only Mac SQL from the sync and session recipes; `user-confirmed` for browser, application, and shield outcomes reported by the maintainer. A timer, button, or linked caption alone never proves an outcome.

| Phase | Actions | Required evidence | AC |
| --- | --- | --- | --- |
| A. From empty | Record categorical pre-state and no active session; perform setup steps 1–3. | Both devices local-only after removal; Mac first-install flow after reset; iPhone first-install flow after install. | fixture |
| B. First and second install | Mac: first-install flow; **Sync with iCloud** must complete (stop rule above) before continuing through Mac setup, `example.com`, and summary, because changes made before linking are not backfilled. iPhone presses **Sync with iCloud** in its fresh flow as soon as the Mac completes, grants Screen Time consent, and chooses **Not now** at the website step. | Mac `sync_bootstrap_state` 0→1 and completed status; iPhone completed or waiting UI (Continue primary, Check again, collapsed Session status), then completed; one exchange on each; no second workspace. Join timing recorded as immediate or waiting (D4). | AC-01 |
| C. Policy | No session is active. iPhone adds `example.net`; **Sync now** on both. iPhone selects its application first, which creates the group; Mac **Sync now**. Then take the Mac pending/accepted baseline, Mac selects its application, and exchanges again. | Identical website lists on both (UI; Mac SQL); `application_policy` count 1 on the Mac before its own selection; group name on both; Mac mapping count 1 with pending/accepted counts unchanged from the baseline; neither device shows the other's selection. | AC-02 |
| D1. Mac start, iPhone end | Mac starts 25 minutes with attended administrator confirmation; iPhone receives through foreground or **Sync now**. iPhone ends early; Mac exchanges. | Mac Safari and Chrome, and iPhone Safari, deny both targets and load `example.org`; the Mac selected application is terminated while its control runs; the iPhone selected application is shielded while its control opens. After the end, both targets load in all three browsers and each selected application opens and stays open past the grace period. | AC-03, AC-04 |
| D2. iPhone start, relaunch, Mac end | iPhone starts 25 minutes; Mac receives, Resume, attended authorization. Relaunch both apps; on the Mac press Resume with attended authorization again. Mac ends early; iPhone receives. | Same enforcement evidence before relaunch. After relaunch: the same end time (Mac SQL, iPhone UI); Mac offers Resume and, after authorization, one target and the selected application are blocked again; iPhone still restricted. D1's cleanup evidence after the end. | AC-03, AC-04, AC-05 |
| E. Normal expiry | iPhone starts at least 15 minutes; Mac receives and resumes. Confirm restrictions before waiting. iPhone in background; Mac away from Session. | Before waiting: the actual deadline, and one target plus the selected application blocked on each device. After the deadline: the maintainer confirms website and application access on both before reopening Posato; reopening shows inactive with no revival. | AC-04, AC-05 |
| F. Missed peer | Maintainer takes iPhone offline. Mac starts a short session with attended confirmation, terminates before the read deadline, and reopens after it. Only then does the iPhone reconnect; Posato is not foregrounded on it while online before that. iPhone exchanges. | Actual Mac deadline; Mac inactive after reopen with no Resume offered; iPhone stays inactive after exchange with the targets loading and the selected application usable. | AC-04, AC-05 |
| G. Cleanup | Remove fixture websites and application selections through the UI. | No active session or restriction on either device; Mac fixture website and mapping rows zero; iPhone lists empty after relaunch; `Applications` group retained as the recorded leftover; pair still linked. | AC-05 |

Durations are taken from the review screen and read-only deadline, not from tap counts. No delivery time is promised; exchange opportunities are foreground and **Sync now**.

## High-risk plan review

- **Verdict:** `changes-required` (independent agent, 2026-09-14); no Critical.
- **Required findings:** (R1) a desktop reset deletes the removed-workspace tombstone, so the establishing Mac could adopt a still-visible old anchor and show a false wait; (R2) application selection order decides which device authors the group and whether the selection-only count check means anything; (R3) cleanup after relaunch and expiry was claimed without first proving restrictions were active.
- **Resolution:** R1 phase B stop rule and completion-before-website; R2 iPhone selects first, Mac baseline after exchange; R3 re-blocking after Mac Resume in D2 and pre-wait blocking in E. Recommended points folded: iPhone presses on Mac completion and defers its website; expected Mac anchor-missing removal; phase F reconnect after the deadline; retained group; corrected refusal set; concrete browser and application evidence; no session during picker work; `doctor` after the signed build.
- **Approval:** the maintainer approved the corrected plan on 2026-09-14 (`user-confirmed`); execution authorized.

## Result

- Pending execution.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `doctor -t desktop`, `doctor -t device` | pass | provisioning only; package and driver not yet built |
| `./gradlew quality` at `f0d68e4` product sources | pass | aggregate gate before signed builds; leaves an ad-hoc desktop package |
| `build -t desktop`, `doctor -t desktop`, `build -t device --driver` | pass | signed package staged; only the always-unknown helper background check remains a warning; device install waits for plan approval |

## Blockers and accepted risks

- Execution requires the attended, unlocked Mac and iPhone on the same Apple Account.
- Physical key waiting may remain `not observed` (D4).

## Final

- **Status:** pending
- **Outcome:** pending
