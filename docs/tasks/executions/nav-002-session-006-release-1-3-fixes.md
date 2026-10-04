# `NAV-002` and `SESSION-006` execution

- **Status:** complete; ready for review

## Plan

1. `NAV-002`: reproduce the crash on `main` through a `posato-control`
   scenario, find where the second disposal comes from, and correct
   `PosatoNavStack` without changing the pinned libraries.
2. `SESSION-006`: replace the **Paused items** wording in shown strings and
   hide the default-set summary while only a scheduled pause restricts.
3. Verify on Simulators, the test iPhone, and a Tart VM, then run
   `./gradlew quality`.

## Result

- `NAV-002`:
  - Cause (`observed` in the crash report and the library sources):
    - A set's screen holds its own stack inside the Pause sets stack.
      `rememberNavigationEventDispatcherOwner` gives every stack a child
      back dispatcher and disposes it when the stack leaves the
      composition.
    - Navigation 3 keeps a removed screen as unused movable content and
      discards it at the end of a later recomposition. By then, switching
      destinations had disposed the Pause sets dispatcher, which disposes
      its children too.
    - The late discard disposed the set screen's dispatcher a second time,
      and `NavigationEventDispatcher.dispose()` throws for a disposed
      dispatcher or parent.
    - androidx-main has the same behavior (`source-claim` from the
      upstream sources), so an upgrade does not help.
  - Fix: `PosatoNavStack` now owns the stack's dispatcher and disposes it
    only while no enclosing stack has been disposed. It finds the
    enclosing stack through the existing back dispatcher local, which only
    a stack provides below the root, so no new composition local is
    needed. Back handling and enablement are unchanged.
- `SESSION-006`:
  - Shown strings that still said **Paused items** now name **Pause sets**
    or "your pause sets". The running session's caption says that added
    items pause now and removed ones stay paused until the session ends.
  - While only a scheduled pause restricts, Session no longer shows the
    default set's **Selected items**. Each running part's caption names
    its set. Enforcement already used each schedule's set, so only the
    display was wrong.
  - Unused string resources keep their old text; they are out of scope.

## Checks

- Regression on `main` bd24944 (2026-10-03): the crash scenario (Pause sets,
  open Focus, Back to pause sets, Schedules) crashed 3 of 4 runs on an
  iPhone 17 Pro Max Simulator with the `IllegalStateException` above. The
  iPad Pro 13-inch Simulator crash report shows the same stack.
- After the change (bd24944 plus this diff). The first runs used a draft
  that kept the enclosing stack in a new composition local; detekt rejected
  it, and every check below was repeated on the final code, apart from the
  Simulator runs and the scheduled pause, whose code did not change
  afterwards in ways they exercise:
  - `AC-01`:
    - 10 of 10 on the iPhone and 10 of 10 on the iPad Simulator, with no
      new crash report (draft, runs `20261003-094205-8e94` to
      `20261003-094323-4a9b`);
    - on the test iPhone (`-t device`, 2026-10-04), 10 of 10 with the same
      scenario ending at **Add schedule**, with one app process throughout
      (runs `rel13fix2-device-1` to `rel13fix2-device-10`).
  - `AC-02`:
    - iOS: the edge swipe back from a set's screen and from the schedule
      editor, each followed by a destination switch, on the Simulator and
      on the test iPhone (run `rel13fix2-device-swipe`).
    - Mac in the `primary` Tart VM, with the unified setup done:
      - Command-[ (`vm press cmd-[`), the trackpad swipe (`swipe-back`),
        and **Back to pause sets** each returned to the set list;
      - **Schedules** opened afterwards in every round;
      - Posato kept running, with no crash report in the guest.
  - `AC-03` (Mac VM, 2026-10-04):
    - A 45-minute session with Focus showed the final caption, "...
      Removed items stay paused until this session ends." (run
      `20261004-131313-0db4`; the iPhone shows the same in run
      `20261004-131523-01bf`).
    - A daily plan, Evening reading, used the Evening set and started at
      12:55. Session then showed "Evening reading (schedule), Set: Evening"
      and no **Selected items** (draft, run `rel13fix-scheduled`). On
      `main`, the same state showed Focus's counts (the `DOCS-004` capture
      `mac-scheduled-active`).
  - A package rebuilt on the host reaches the VM only through `vm sync`;
    `launch` alone kept the previous build.
- `./gradlew quality`: passed on the final code.

## Review

The independent completed-change review found nothing Critical or Required.
It checked the replacement against the library's
`rememberNavigationEventDispatcherOwner`, which takes the same three steps,
and confirmed that a cascaded disposal already cleans up every nested
dispatcher, so the skipped call leaks nothing. Of its Recommended findings,
the caption no longer says removed items stay paused "until this pause
ends": while a schedule overlaps a manual session they are released when the
session's part ends, so it now says "until this session ends". The GitHub
Projects mirror gained both rows. The Optional finding about setting
`isEnabled` before the first frame is declined: the library has the same
window, and this row only fixes the disposal.
