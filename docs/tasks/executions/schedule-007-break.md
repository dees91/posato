# Execution: `SCHEDULE-007`

- **Brief:** [schedule-007-break.md](../specifications/schedule-007-break.md)
- **Status:** `in-review`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code through Taskflow (attempts 3 and 4), with the maintainer away under the delegation of 2026-10-31
- **Reviewer:** independent Claude Code agents for the plan, the security of the proposals, and the completed change; the hosted review agent on PR #220
- **Branch:** `cez/85d9b719` (attempt 3's `cez/caa6982a` history)
- **Updated:** 2026-11-01

## Plan

1. Proposals for the schedule rules, the ADR 0006 amendment (kinds 20 and
   21), the ADR 0004 and ADR 0009 deltas, `DESIGN.md`, `PRIVACY.md`, and the
   iPhone mechanism; an independent plan review and security review; the
   maintainer's gate before any product change (`AC-01`, `AC-02`).
2. Shared core (store, migration 15, codec, reducer, writer, host), the
   Session UI with translations, and the desktop hosts on the Mac in Tart,
   Linux, and Windows, with `posato-control flow break`.
3. The iPhone host (one shared Device Activity for the break's return,
   refused when it cannot be registered) and the Android host.
4. A serial cross-device matrix, the completed-change review,
   `./gradlew quality`, and closeout.

Real time in every device run: 5 or 10 minute breaks inside 15 to 20 minute
fixture occurrences; shared tests use injected clocks (`QUALITY-012` is not
available).

## Result

- A running scheduled occurrence offers **Take a break** beside **End
  early**: 5, 10, or 15 minutes in the same confirmation, one break per
  occurrence, all running occurrences break together, **End break** without
  confirmation, and a disabled action with its reason at the limit or while
  a manual session restricts.
- The break is a synchronized fact: mandatory kinds 20 (break) and 21
  (break end, grow-only), earliest start wins, the writer clamps a start
  more than 2 minutes ahead, and each device records the break as over
  before it re-applies. A late receiver releases only the remainder; a
  break that ended while a device was offline is never taken there.
- iPhone: attempt 4 fixed two concurrency findings red first (the shield
  clear now happens inside the composition lock; a failed re-registration
  retires pending breaks). The hosted review's P1 on PR #220 is fixed red
  first as well: an ordinary evaluation in a break's last minute kept the
  early-callback margin and re-applied the shields early; only the deadline
  callback now applies that margin.
- `posato-control`: `flow break` and `monitor`. The verification relay
  deleted a file that reached a bridged guest's iCloud Drive dataless,
  which also deleted it on the iPhone; it now leaves such a file in place
  (failing `FolderRelayTest` first).
- Deviation: the matrix ran the iPhone rows last, while the iPhone was
  locked, and measured the iPhone receipt instead of barring it at two
  minutes, since iCloud Drive delivery to the iPhone ranged from about 10 s
  to over 14 minutes (`agent-delegated`, 2026-10-31).
- Manual session received during a break on the Mac: with automatic-start
  consent it applies by itself (`MACOS-029`); without it, **Resume
  restrictions** shows at once. `DESIGN.md` and the schedule rules say so
  since 2026-11-01, decided by the owner's agent under the owner's
  delegation, pending the owner's confirmation in PR #220.

## Completed-change review

- **Plan and security reviews (attempt 3):** seven Required security
  findings on the proposals (fail-open break ends, a writer refusal that
  would freeze the writer, an iPhone deadline gap), all folded before the
  maintainer's acceptance on 2026-10-30 (`user-confirmed`).
- **Completed-change review (2026-11-01, head `5c10bce9`):** no Critical or
  Required finding. Recommended, left for the maintainer: the iPad caption
  says "iPhone" as `DESIGN.md` does; a race between arming and recording a
  break that fails closed on the phone while the break reaches the other
  devices; **End break** missing on a phone that could not register a
  received break. Optional: the extension's deadline read in its own end
  callback; a failed Mac helper clear at a break start shown as a break.
- **Hosted review on PR #220:** P1 last-minute margin, fixed (above); P2
  record, verification, and Option A wording, addressed in the same
  correction.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Serial matrix B1, B2, C1, C1b, C2, D, O, M, M2, E1, E2, A (Mac in Tart, peer Mac, test iPhone, Linux, Android XCover7, Windows) | pass | Receipt 4 s to 103 s off the iPhone; expiry within 32 s; closed-app iPhone return at most 14 s; every platform authors and receives a break (`AC-03`); limit reason after every break row and no leftover after any occurrence end (`AC-04`) |
| Row M2, approval off | pass | Resume restrictions about 47 s after receipt, manual items restricted during the break, nothing left after the natural end |
| 1.5 compatibility | cited | at `58e245de` the codec refuses kind 20; ADR 0006 accepts that a 1.5 device stops receiving and keeps restricting (`inferred`; no 1.5 build run) |
| `iosSwiftTest` after the P1 fix | pass | the new last-minute regression failed before the fix |
| `./gradlew quality` | pass | at `070752ed`; rerun by the orchestrator after the last correction |

The test-iPhone run after the P1 fix is cited in PR #220.

## Blockers and accepted risks

- Accepted (ADR 0006): a 1.5 device stops receiving once a break is taken,
  until it updates.
- iCloud Drive delivery to an iPhone in a folder workspace can take minutes;
  recorded as a transport limit, not a product defect.
- Open: the owner's confirmation of the Mac wording above.

## Final

- **Status:** `in-review`
- **Outcome:** `AC-01` to `AC-04` met by the evidence above, pending the
  owner's confirmation of the Mac wording.
