# Execution: `MVP-001`

- **Brief:** [End-to-end MVP acceptance](../specifications/mvp-001-end-to-end-acceptance.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code session with the maintainer attending both devices
- **Reviewers:** independent plan-review agent; independent Standard review of the correction; independent closeout review and re-review
- **Branch:** `docs/mvp-001-acceptance-brief` (PR #53)
- **Updated:** 2026-09-14

## Observed starting point

- Product sources at `966c786` (SYNC-012). A defect found in phase A led to correction `105ddda`; every result row below ran on signed builds of `105ddda` unless marked.
- Inherited limits: ONBOARDING-002 never observed a physical key wait; SYNC-012 used an already linked pair without selected applications; IOS-002 needs sessions of at least 15 minutes for suspended expiry.

## Decisions (`user-confirmed`, 2026-09-14)

- **D1 fixture:** from empty without restoring prior development data; the pair ends linked with fixtures removed.
- **D2 order:** Mac installs first and establishes; iPhone joins immediately afterwards.
- **D3 applications:** one built-in selected application and one built-in control application per device; tracked text names categories only.
- **D4 key wait:** the join was immediate; the maintainer accepted physical key waiting as `not observed`.
- **Correction:** fix the removal defect first, as a correction commit in PR #53.
- **Closeout:** no hosted review; recipe corrections land in this PR.

## Plan and plan review

1. Independent High-risk plan review, maintainer approval, `quality`, signed builds.
2. Phases A–G: from-empty removal and reset; first and second install; policy; Mac-start/iPhone-end and iPhone-start/relaunch/Mac-end sessions; normal expiry; missed offline peer; cleanup.
3. On a defect: stop, no state repair, scoped correction with regression tests, rebuild, repeat the path.

- **Plan review:** `changes-required`, no Critical. Required: (R1) a desktop reset deletes the removed-workspace tombstone, so phase B needs a stop rule on the establishing Mac; (R2) the iPhone selects its application first so group authorship and selection-only counts are meaningful; (R3) prove restrictions active before claiming cleanup after relaunch and expiry. All folded with the recommended points; the maintainer approved the corrected plan.

## Result

"Cleanup confirmed" means the maintainer loaded both targets in every tested browser and opened each selected application without termination or shield. Where the iPhone scenario could not reach Sync now (see verification tooling), the maintainer pressed it as the exchange opportunity.

| Phase | Outcome and evidence (`build/verification/runs/`) | AC |
| --- | --- | --- |
| A | On `966c786` the iPhone **Remove workspace** ended "Sync did not finish" twice while ordinary exchanges completed (`095926-77a2`, `100422-0573`): defect, see correction. On `105ddda` one press ended local-only (`103610-c34f`). Deviation: the still-linked Mac showed waiting for the key instead of the planned action required (`103711-b136`); its removal still ended local-only in about 20 s, bootstrap 1→0 (`103756-9ef0`). Desktop reset to zero (`103828-bb69`); iPhone uninstall and install (`103850-b81d`). | fixture |
| B | Mac **Sync with iCloud** completed in about 50 s, bootstrap 0→1, stop rule passed (`104116-1c09`). iPhone pressed one second later and was completed at its first read (`104206-dacb`): immediate join, key wait `not observed`. Native Screen Time consent allowed. Deviation: the iPhone website step offered only Continue because the synced website already existed. Both summaries report one website and iCloud connected (`104340-3250`, `104828-c14e`). | AC-01 |
| C | `example.net` from iPhone and `example.com` from Mac converged on both (`105043-2ebb`; Mac SQL). The iPhone selection created `Applications`, received by the Mac (`105442-6032`). The Mac selected through its native picker, mapping 1, pending/accepted unchanged after exchange; each device shows only its own selection (`105722-15ca`, `105815-668b`). | AC-02 |
| D1 | Mac 25-minute start with attended prompt (`110049-4dbf`); iPhone received after Sync now. `user-confirmed`: Safari and Chrome on Mac and Safari on iPhone deny both targets and load `example.org`; the Mac terminates the selected application while its control runs; the iPhone shields the selected application while its control opens. Deviation: the iPhone driver then lost automation, so the planned iPhone early end was missed and the session expired at 11:25 (`112800-93a7`, SQL not early); cleanup confirmed on both, iPhone checked before opening Posato (`113118-1416`). The path was repeated on a new session: Mac start (`113143-b4a0`), iPhone receive, blocking confirmed, iPhone early end (`113646-61a3`); after iPhone Sync now the Mac converged to ended early (`114014-c419`); cleanup confirmed. | AC-03, AC-04 |
| D2 | iPhone 25-minute start (`114601-e7ea`), Mac receive and Resume with attended prompt (`114635-692d`), blocking confirmed on both. Relaunch of both: Mac SQL keeps start 11:42:29 and end 12:07:29, Resume offered and reapplied (`115110-e5e3`, `115123-bef2`); iPhone shows end 12:07 and stays restricted (`115201-4023`); blocking reconfirmed. Mac early end (`115528-be70`); iPhone converged after Sync now; cleanup confirmed. | AC-03–AC-05 |
| E | iPhone review screen confirmed 15 minutes, deadline 12:17:45 in Mac SQL (`120335-85da`); Mac received and resumed (`120934-2f96`); blocking confirmed; iPhone in background, Mac on Paused items (`121017-d99e`). After the deadline cleanup was confirmed on both before Posato was reopened; reopening shows ended with no revival (`122301-507e`, `122321-f3b8`). | AC-04, AC-05 |
| F | iPhone offline. Mac 5-minute session to 12:31:02 (`122609-874f`); app terminated before and reopened after the deadline: inactive with no Resume (`123154-d42f`). iPhone reconnected and synced: still inactive, targets load, selected application usable (`123531-c20b`). | AC-04, AC-05 |
| G | Websites and selections removed through the UI; Mac websites 0, mappings 0, pending 0, still linked; both devices show no active session and the iPhone shows 0 websites and 0 applications after relaunch (`123738-0463`, `123938-a5da`, `125329-b088`). Final `user-confirmed` check: no website or application restriction remains on either device. `Applications` remains the recorded leftover. | AC-05 |

## Correction `105ddda`

- **Defect (`observed`):** the iOS native removal pass drains at most ten one-record change pages per call and returned retryable with a resume token; the iOS adapter called it once, so a populated zone needed several presses, each reporting "Sync did not finish". The macOS adapter already resumes up to ten calls. SYNC-015 added the page bound after its physical gate.
- **Fix:** the Swift bridge reports `Incomplete` when work remains after resumable progress; the iOS adapter resumes up to ten calls for record deletion and the anchorless sweep; genuine retryable failures still end after one call.
- **Tests:** six new adapter tests; the four loop tests failed before the loop and all pass after it. Swift expectations and two Swift tests cover the page bound and a failure without progress.
- **Review:** independent Standard review approved with no Critical or Required. Optional O1 (sweep single-call test) and O2 (token contract comment) were folded; O3 (a pre-existing extra press after a late token expiry) was declined. Physical proof: phase A row on `105ddda`.

## Completed-change review

- **Correction:** approved; see above.
- **Closeout documentation:** first review `changes-required`, no Critical. Required: application names instead of categories (D3) and missing evidence that no restriction remains after cleanup; both corrected, the latter with the final maintainer check. Recommended wording on deviations, relaunch end time, test count, verification scope and status folded. Re-review approved with no Critical or Required; its AC-01 wording point was folded.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` at `966c786` | pass | before signed builds |
| iOS adapter tests before and after the loop | fail 4 of 27, then pass | red/green for the correction |
| `./gradlew quality` after the last correction | pass | Swift suite 135 tests, 6 skipped, 0 failures |
| Signed desktop and device builds of `105ddda`, `doctor` | pass | only the always-unknown helper background warning |
| Physical phases A–G | pass on `105ddda` except the unobserved key wait; phase A failed on `966c786` and was corrected | table above; browser, application and shield rows `user-confirmed` |
| iPhone driver Sync now sequence with `textContains` | pass | `130803-3608` |
| Corrected `first-install.json` on the Simulator | pass | `131042-f1a0`; one website, no bootstrap row |

## Observations and limits

- **Copy:** a Mac launched without window activation shows "Not connected · optional" and offers Sync with iCloud until the window resumes; after peer removal the Mac waiting text says the existing workspace stays unchanged; a Mac that received a session without closing shows "Restrictions stopped when the app closed."
- **Verification tooling:** failed iPhone Sync now scrolls during the run came from a scenario key typo (`text-contains`), which the iOS driver ignores; with `textContains` the documented sequence works. The repository fixture `first-install.json` had the same typo and is corrected. The iPhone driver loses automation while the phone locks. After Enable in first install a second macOS helper stays alive, so the picker needs `--process <pid>`. Recipes record these points.
- **Limits:** key waiting not observed; the helper was already approved, so Enable returned enabled without a prompt; the iPhone expiry callback time was not measured; one Mac and one iPhone, no reboot inside a session.

## Final

- **Status:** `done`
- **Outcome:** MVP-001 accepted by the maintainer on `105ddda` with the limits above: AC-02–AC-05 passed, and AC-01 passed except the delayed-key case, which was `not observed` and accepted (D4). No release-readiness claim; `RELEASE-001` owns it.
