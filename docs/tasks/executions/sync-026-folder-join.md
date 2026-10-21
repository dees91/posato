# Execution: `SYNC-026`

- **Brief:** [Join a folder workspace in two steps, in either order](../specifications/sync-026-folder-join.md)
- **Status:** `in progress`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `sync-026-folder-join`, rebased onto `main` `31063a8c` after #183 merged

## Plan

1. `FolderSyncControls` gains a read-only `contents()` (no folder, empty,
   holds a workspace, unavailable) from the folder's anchor, the same read
   the pairing accept already does, and `scansCodes` (a phone, where the
   system camera opens join links).
2. A pure `FolderJoinFlow`: the step view from folder, contents, pending
   code, and the last result; and a `FolderJoiner` that accepts a code,
   waits and retries on not found or unavailable with a growing delay up to
   10 minutes, and stops on terminal results. Tests first.
3. `SyncBootstrapUiState` holds the pending code (scanned or typed); the
   folder section runs the joiner while it is shown and the folder and code
   are present.
4. Rebuild `FolderOptions`: two numbered steps, the folder chip with
   **Change**, the scan hint (phones), the code field with **Join**, the
   waiting note, errors under the field, **Create the workspace** for an
   empty folder, and the calm linked state.
5. `posato-control` flows adapted; Android runs; Linux run after the VM is
   free; `DESIGN.md`; review.

## Decisions

Agent decisions under the maintainer's delegation unless marked otherwise.

- A folder that holds a workspace whose key this device already has, while
  nothing is linked (a join that stored the key, then Posato stopped before
  linking), links once when the folder is read; the link after a join waits
  for an attempt already running instead of skipping it.
- The Create step keeps the sentence on what linking combines and that app
  choices stay on each device.
- The scan hint says "this phone's camera" on every device with a share
  sheet, an iPad included; a wording for tablets is left to `I18N-001`.

## Result

- **Tests:** `FolderJoinFlowTest` (commonTest) failed on the stub
  (`1c5fee8e` then the failing-test commit: 7 of 9 failed; the two that
  passed are guards) and passes after the implementation.
- **Product:** `FolderSyncControls.contents()` reads the folder's anchor
  without writing; `FolderJoinState` keeps the pending code (a scanned link
  stays until used, so a rebuilt screen keeps it), the folder contents, and
  the waiting join; `FolderJoinStepsView` draws the steps; the linked row is
  the folder chip, **Add a device**, and quiet **Sync now** and **Remove
  workspace**.
- **Tooling:** `flow folder link` presses **Change** and **Create
  workspace**; `join` only waits; `remove` reads the unlinked steps; the
  outcome read checks the screen before scrolling and always reads once
  before its deadline (the Android timeouts came from scrolling away from a
  control already on screen). verify-posato `folder-sync.md` and the
  posato-control README follow.
- **Limits:** a typed code is not kept when Android rebuilds the screen (a
  theme change); a scanned code is.

## Review

- Independent agent, 2026-10-21: no Critical; four Required, all fixed test
  first: a removed workspace's copy reads as such before any code is used;
  a key a join stored links (the link waits for a running attempt, and a
  folder whose key is already here links when read); a code can be typed
  before the folder; the scan-first and expired runs are on the final head.
  Recommended taken: a reading state without **Join**, a re-read while
  Create is shown and after an unlinked sync, the join only while Posato is
  in front, one live container and spoken errors, the code and draft kept
  across a rebuilt screen (and the row kept open), dead strings removed, the
  linking sentence in Create, `Step n` marker labels.

## Checks

Product head `e8adff8d` (later commits change only `posato-control` and
this record).

| Check | Result |
| --- | --- |
| `./gradlew quality` | passed on the review fixes; `qualityLint` on `e8adff8d` |
| `FolderJoinFlowTest`, `FolderJoinStateTest` | failed on `cc3e9d7a` (6 of 15), pass after |

Verified e8adff8d Android create run 20261021-194539-7e2b (Galaxy XCover7, empty folder, Create workspace, linked).
Verified e8adff8d Android scan first run 20261021-195033-0b84 (link opened before the folder; joined once the folder was chosen).
Verified e8adff8d Android typed first run 20261021-195325-f35c (code typed before the folder, offer held back: waiting kept across two theme changes, joined once it arrived).
Verified e8adff8d Android removed copy run 20261021-195834-8ddb (step 2 explains, no Join).
Verified e8adff8d Android expired code run 20261021-200020-856e (error under the field).
Verified e8adff8d iPhone scan first run 20261021-200427-bce3 (test iPhone, step 2 received with step 1 open).

Earlier iPhone runs on `3c6cfe7c`, before the review: waiting
`20261021-153124-5f55`, create `20261021-153201-37bd`, code step
`20261021-153700-d8ce`, typed join `20261021-153743-5b59`, linked row
`20261021-153805-04e1`.

Screenshots (light / dark): scan first `195023-4102` / `195029-e26e`;
waiting `195436-c83d` / `195442-23d0`; removed copy `195810-d712`; expired
`200107-bcf6`; earlier code step, create, and linked frames in the table of
the first pass.

`AC-03` (a Linux clone joining by a typed code) and the desktop flow runs
wait for a VM.
