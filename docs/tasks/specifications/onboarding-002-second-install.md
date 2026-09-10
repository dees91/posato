# `ONBOARDING-002`: Join the existing Apple workspace, wait safely for a delayed key, and finish local mappings

- **Status:** Implemented; independent completed-change review approved on
  2026-09-10; both physical joins verified (see the execution record).
- **Decisions:** `D1`–`D4` are `user-confirmed` (2026-09-10), including the
  corrections below. Verification results belong to the execution record.
- **Review tier:** `high-risk`
- **Tier reason:** The join is the product's only crossing of the accepted
  membership boundary (Apple Account and iCloud Keychain trust, `TB-07`), and
  the waiting state is a public security claim: it must never read the
  remote workspace as empty, generate a replacement key, or create a
  parallel workspace (`T-04`, `R-04`). The accepted `D1` lets
  a consented device re-read the anchor and key item without a new press,
  which extends when the bootstrap coordinator runs and amends a
  `user-confirmed` ADR. The physical proof needs two devices, workspace
  removal on the maintainer's account, and an Apple-timed Keychain delivery
  nobody can force.
- **Dependencies:** completed `ONBOARDING-001` (the six-step flow, its
  `first-install-skip.json` prelude, `local_setup_state`), `SYNC-009`
  (deterministic join, `waiting-for-workspace-key`), `SYNC-010` (exchange,
  removal, explicit retry opportunities), the merged Session rows from
  PR #45 (collapsed iCloud row), `TARGETS-003` and `TARGETS-004`
  (device-local application selection), `QUALITY-002` and `QUALITY-004`
  (driver, provisioning gate)
- **Integration group:** `PR-SECOND-INSTALL`
- **Authority:** `ONBOARDING-002` in MVP roadmap revision 12 (wave P4/W4.2;
  Gate 6 outcome "A second installation joins the existing workspace, waits
  safely for a delayed key, and completes only its local mapping"; decision
  gate "Apple-managed Keychain approval may remain external");
  [the MVP scope](../../product/mvp-scope.md) (primary flow steps 2 to 4
  and the outcome bullet "delayed synchronizable-Keychain delivery produces
  a waiting state and never an empty or parallel workspace");
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  ("Apple may still require system-level approval or recovery before a new
  device joins iCloud Keychain. Blocker reports that prerequisite and does
  not duplicate or bypass it"; `source-claim`: approval may run through an
  existing trusted device, a passcode, or an Apple recovery flow; `open`:
  no controllable propagation time, waiting and retry must be verified on
  physical devices);
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  (step 1 exact binding match for any local attempt; step 4 a missing item
  "produces `waiting-for-workspace-key`; it never generates a key, replaces
  the anchor, interprets the workspace as empty, or creates another
  workspace"; step 9 the losing candidate deletes exactly its own item;
  "Bootstrap runs only after the explicit **Sync with iCloud** action",
  the dated amendment required by accepted `D1` precedes behavioral changes);
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  (Apple trust is the complete membership boundary; `R-01`);
  [`DESIGN.md`](../../../DESIGN.md) (the seven statuses keep their real
  meaning; "Connected to iCloud" describes linking; expanding the iCloud row
  never starts an attempt; the summary "names the remaining setup and its
  existing route"; no time, device list, or success claim);
  [the threat model](../../security/apple-mvp-threat-model.md) (`TB-06`,
  `TB-07`, `T-04`, `R-04` names this task; the persistent-store and
  public-claim change rule);
  `SYNC-010` decision `D5` (retry is an explicit opportunity, no timer, no
  background delivery);
  [the quality contract](../../development/engineering-quality-contract.md)
  (`user-confirmed`: no Compose UI tests for static rendering or copy);
  [the brand baseline](../../wiki/topics/brand-and-design-baseline.md)
  (state first, consequence second, action third; "Never congratulate"); and
  the prototype key-wait surface in `prototypes/mvp-interaction-flow`
  (`source-claim` design precedent: "Check whether Apple is asking you to
  approve this device in Settings. Your existing workspace stays unchanged."
  with **Check again**); and [Apple Support 109016](https://support.apple.com/en-gb/109016)
  (`source-claim`, checked 2026-09-10: a device may need
  approval from another device or a device passcode before Keychain data
  becomes available; turning iCloud Keychain off keeps its data on the
  device, and the keep-or-delete passwords question belongs to signing out
  of iCloud)

## Outcome

On a fresh installation whose Apple Account already holds the Posato
workspace, the same **Sync with iCloud** press joins that workspace; while
the synchronizable key has not reached the device, the iCloud step, the
summary, and the Session iCloud row report waiting for the key, the existing
workspace stays untouched, no zone, candidate, anchor, key item, or bootstrap
row is created, and the join completes on a later check without a second
consent; afterwards the device completes only its own permission and local
application selection through the existing routes; and both device orders,
Mac joining and iPhone joining, are proven physically.

## Boundaries

- The join path is the accepted protocol and stays unchanged: the press runs
  one bootstrap attempt (zone fetch, anchor read, exact Keychain item read
  under the same binding), adoption commits the established row, and nothing
  reaches iCloud or the synchronizable Keychain before the press
  (`ONBOARDING-001` `AC-02` keeps holding). `observed` before this change: a found anchor
  with a missing item returns `WaitingForWorkspaceKey`, the store stays
  `None`, `linked` stays false, foreground reads only the local store for an
  unlinked device, a relaunch reads local-only, and only another press
  re-reads the item.
- `D1` applies to a fresh join: an explicit **Sync with iCloud** attempt
  found an existing anchor, its exact key item was missing, and the local
  bootstrap store remained `None`. Retain that attempt's account binding
  and anchor context in process memory only. This continuation is distinct
  from the general `WAITING_FOR_KEY` status, which can also occur with a
  persisted candidate or an established workspace. Those existing paths
  retain their explicit bootstrap or linked-exchange behavior and do not
  acquire an automatic candidate-cleanup path or a no-op retry button.
- While this continuation exists, **Check again** and foreground both use
  the same bounded join-only operation; neither calls the creating
  bootstrap. Under the existing serialization boundary, re-read the local
  store and require `None`, then resolve the current account binding and
  compare it with the consented binding before workspace/key access. Read
  only the exact zone, anchor, and item under the expected binding, with
  existing native preflight/postflight checks. Require the anchor context
  to match the original waiting attempt; a replacement workspace under the
  same account also ends that attempt. Never create a zone, candidate,
  anchor, or key item, and never delete on this route.
- While the key is missing, no bootstrap row is written. A valid matching
  item permits the normal established-row commit; only after that commit
  succeeds does normal synchronization run. The check itself is read-only
  until adoption; the ensuing exchange has its existing persistence and
  mailbox effects. The outcome table below applies to both entry points.
  It does not prohibit ordinary `SYNCING`/completed/error statuses after
  establishment. Tests separate the check's effects from the exchange's.

  | Check outcome | State and next action |
  | --- | --- |
  | No continuation | No join-only provider calls; preserve the existing route for the actual local state |
  | Local candidate or established row | No join-only account/cloud/key calls; discard the stale continuation and reconcile through the existing state-specific route |
  | Local-store corruption | Clear continuation; action required; no provider calls or adoption |
  | Local-store storage failure | Retain continuation; retryable; no provider calls or adoption |
  | Current account binding differs | Clear continuation; action required; zero workspace/key calls; a later **Sync with iCloud** press is new consent |
  | Account unavailable/restricted, provider account changed, malformed or mismatched item, changed anchor context | Clear continuation; action required; no adoption, creation, or deletion |
  | Exact zone or anchor definitively absent | Clear continuation; local-only; offer **Sync with iCloud** for an explicit new setup |
  | Exact key item absent | Keep continuation and waiting; no writes |
  | Retryable/unknown provider result or undetermined account | Keep continuation and last waiting state; no adoption |
  | Valid item matching the retained anchor and binding | Commit established once, clear continuation, then request one normal exchange |
  | Established-row commit fails | No exchange or linked success; corruption ends the continuation with action required; storage failure retains it with retryable status; re-read local state before retrying |

- Retry routing follows the retained continuation, not only the displayed
  `WAITING_FOR_KEY` status. A retryable storage failure still uses bounded
  **Check again**, never a creating bootstrap. After cancellation or an
  uncertain commit, re-read the store under serialization: an exact matching
  established row resumes the existing linked route without another commit;
  `None` may resume the same bounded check; corruption or a conflicting
  workspace stops with action required. Never overwrite a different local
  workspace while reconciling a stale continuation.
- Keep the continuation and the local-state recheck within the serialized
  operation, so a queued foreground event cannot use a stale attempt after
  consent, adoption, or removal. Coalesce overlapping foreground checks;
  do not queue a check per window-focus event. A manual check while one is
  running does not start a duplicate. Preserve cancellation and the existing
  account-boundary checks. No timer, background delivery, push, Keychain
  notification, new persistent waiting record, or schema migration is added.
  Restart forgets the continuation and shows local-only with explicit
  **Sync with iCloud**; this is an accepted UX limitation of this iteration.
- Copy and actions (`D2`) follow state, consequence, action. The waiting
  state shows a short status (the existing waiting sentence) and a separate
  explanation that says the existing workspace stays unchanged, names the
  Apple-owned prerequisite without a settings deep link, a time, a device
  name, a location where Apple asks, or a success claim, and in the step
  adds that setup can continue while waiting. The person has done their
  part, so **Continue** stays the primary action and **Check again** is
  secondary; neither reads as a new consent or a decline. The summary
  reports the locally saved choices and the pending synchronization as two
  separate facts. The Session screen keeps the PR #45 layout: the collapsed
  iCloud row carries the waiting summary and **Check again** appears only
  after expanding.
- Apple-managed Keychain approval remains external: the app names the
  prerequisite and never detects, opens, requests, or simulates it. There is
  no public API for iCloud Keychain availability, so no "iCloud Keychain is
  off" claim is made.
- "Finish local mappings" (`D3`) means the joining device completes its
  device-local part through the existing routes: the permission step or the
  Session **This Mac** row, and the Paused items application picker. No
  synced domain, policy, or application becomes visible on the joining
  device, because projection is `SYNC-011`; the summary's existing notice
  names the remaining setup. `observed`: authoring diffs exact domains only
  and the only production caller is the domain store's replace, so neither
  a picker selection nor the application group name reaches the outbox.
  This is an implementation stage: the person's outcome "my choices appeared
  on the other device" still depends on `SYNC-011`, and nothing here claims
  it.
- Accessibility rules bind the changed surfaces: labels and roles, non-color
  state cues, wrapped action rows at large text, announced state changes.
- Write surface: `shared/src/commonMain/**/feature/sync/bootstrap/**`
  (`AppleSync`, `BootstrapCoordinator`, a new join-only phase or result as
  `D1` needs), `feature/sync/ui/**`, `feature/onboarding/**`
  (`OnboardingSteps`, `OnboardingScreen`, previews),
  `PosatoApplication`, `SessionScreen`, and `SessionOverviewContent` for
  narrow accessibility callback wiring to the existing Mac announcement
  bridge, plus desktop `Main.kt` if the callback is renamed or shared;
  no new native announcement mechanism or helper behavior is needed.
  `SessionScreenPreviewDataProvider`, `strings.xml` (a shared
  `action_check_again`, the waiting notice), their tests and the
  `AppleSyncTestHarness`, `DESIGN.md` (the implementation-boundary sentence
  on launch and foreground, the first-install iCloud bullet, the Session
  iCloud bullet), ADR 0007 (a dated `user-confirmed` amendment section in
  the ADR 0002 convention, required by accepted `D1` before behavioral
  changes), the
  `verify-posato` skill (`features/sync.md` join rows and its "launch and
  foreground do not touch CloudKit" sentence, `features/onboarding.md`
  second-install sub-feature and its "exactly one bootstrap attempt"
  gotcha), `docs/wiki/topics/cross-device-synchronization.md`,
  `docs/wiki/topics/brand-and-design-baseline.md` (the onboarding diagram
  still shows "Choose local apps on this device", superseded by
  `ONBOARDING-001` `D2`), and the wiki log.
- Non-goal: domain, policy, and session convergence (`SYNC-011`,
  `SYNC-012`); a per-item mapping prompt for synced applications; backfill of
  edits made before linking; total-key-loss recovery; QR, invitation, or
  cross-device approval; a settings deep link or an in-app iCloud Keychain
  check; changes to the zone, anchor, candidate, or key-item protocol beyond
  the bounded join-only check and normal adoption; helper, picker, or Screen
  Time changes; the
  `SYNC-010` retryable-key-read follow-up (same files, serialised after this
  pull request); the accessibility captures waived for `ONBOARDING-001`
  (they stay with `RELEASE-001`); evidence of a real Apple approval prompt
  (already-trusted hardware may never show one; the record says so).

## Acceptance

- `AC-01` — On a fresh installation whose account already holds the workspace,
  one press of **Sync with iCloud** in the iCloud step either links the
  device (completed attempt, established row) or reports waiting for the
  key. While waiting, the joining device's store stays `None`: proven by
  `select count(*) from sync_bootstrap_state` at zero on the Mac in
  direction B, and by the iPhone's status text plus the unit rows in
  direction A; in both directions the established device keeps its linked
  workspace and existing accepted data.
- `AC-02` — Once the key item is readable, the next check, foreground under
  `D1` or **Check again**, turns waiting into linked plus one exchange,
  without a second consent control and without re-entering the flow. While
  waiting, neither route can create a workspace: with the anchor gone or
  the account changed, the check stops the join, shows local-only or action
  required, and a new setup needs an explicit **Sync with iCloud** press. A
  relaunch while waiting reads local-only with **Sync with iCloud**
  available, never success and never a second workspace.
- `AC-03` — Over the fakes with call counts, the join-only re-check proves
  every outcome above through both manual and foreground entry points.
  No check creates or deletes workspace resources; a missing key writes no
  bootstrap row; verified adoption commits once before requesting exchange.
  Binding or anchor changes cannot switch the waiting attempt to another
  workspace. Candidate and established paths retain their existing retry
  behavior. Concurrent opportunities cannot duplicate adoption/exchange or
  revive a cleared continuation. Existing explicit-bootstrap protocol tests
  remain green; test the actual state and provider effects, not UI labels.
- `AC-04` — The iCloud step, the summary, and the Session row show waiting
  as waiting, as a short status plus a separate explanation; the sentences
  name the Apple-owned prerequisite with no deep link, time, device list,
  location, or success claim; **Continue** is primary and **Check again**
  secondary in the step, and the Session row shows **Check again** only
  when expanded; the summary states the local save and the pending sync
  separately; every changed sentence maps to an authority listed above and
  the independent review checks that mapping.
- `AC-05` — After joining, the device completes its permission step and a
  local application selection through the existing routes; with no website
  added in that window, the Mac's pending and accepted counts stay
  unchanged, and no synced domain or application appears on the joining
  device.
- `AC-06` — `./gradlew quality` passes with no new suppression; the
  `first-install.json` and `first-install-skip.json` fixtures still pass;
  the skill documents the join rows and the fresh-database cost; evidence
  stays under the ignored `build/verification/`.

## Verification

- `commonTest`: coordinator tests cover the outcome table using delayed-item
  fakes, retained account/anchor context, store failures, and counters for
  reads, forbidden creation/deletion, and the established commit. Integration
  tests exercise **both** manual recheck and foreground after account change,
  anchor replacement/removal, and later key delivery. Verify no-consent
  foreground still touches no provider; repeated or overlapping opportunities
  produce one adoption/exchange; cancellation and cleared continuations do
  not resume stale work; candidate and established waits still recover
  through their existing routes. Holder tests cover command routing,
  busy/completion state, and summary status; static copy/rendering uses
  previews and native inspection, not Compose UI tests.
- `jvmTest` and `iosTest` over the real graphs: construction touches no
  provider; Session-first routing unchanged.
- Simulator, unattended: `first-install.json` and `first-install-skip.json`
  unchanged and green; the degraded iCloud row keeps its outcome.
- Physical, attended, direction A (Mac establishes, iPhone joins): iPhone
  **Remove workspace** first if linked, then `reset -t device` (uninstall)
  and `install`; Mac **Remove workspace**, then **Sync with iCloud** until
  linked; immediately afterwards the iPhone flow presses **Sync with
  iCloud**; record either immediate linking or an observed wait. If waiting
  is visible,
  inspect the step, use Continue through the local permission/website steps,
  and inspect the summary and Session while it remains observable; never
  require the user to re-enter completed onboarding. Exercise manual recheck
  and foreground when an actual wait window permits them. Complete local
  permissions during onboarding or through their existing later routes,
  then select apps in Paused items after the join. Compare quiescent Mac
  counts before and after that selection, with no domain edit in between.
- Physical direction B (iPhone establishes, Mac joins): Mac **Remove
  workspace** while the iPhone is still linked leaves the iPhone
  action-required, so remove on the iPhone first and link it fresh; `reset
  -t desktop` with backup; the same rows with the count query at zero while
  waiting and one established row afterwards; restoring the Mac backup
  afterwards restores a row for a removed workspace, so the maintainer's Mac
  then needs **Remove workspace** and a fresh link. iPhone evidence is
  status text plus the Mac's receipt, because the device database is not
  readable.
- `user-confirmed` run correction (2026-09-10): current Mac/iPhone data is
  disposable development state. Workspace removal, resets, and reinstall are
  approved without restoring a backup. Leave both devices linked afterwards;
  this overrides the backup/restore steps above for this verification run.
- Wait-window fallback (`D4`): immediate key availability proves immediate
  joining, not waiting. If waiting cannot be observed, mark its physical
  row not observed and cite unit coverage; do not claim that earlier
  `SYNC-009` evidence verifies the new retry implementation. An iCloud
  Keychain off/on cycle on the joining iPhone is optional, attended, and
  needs agreement at the time of the run. It does not guarantee a missing
  app key or an Apple approval prompt. Apple documents local retention when
  Keychain is turned off and a keep/delete choice when signing out; record
  the actual system messages without predicting a particular prompt.
  Do not sign out, reset encrypted data, or delete passwords for this recipe.
- Accessibility: inspect changed status and actions on both native hosts.
  Automatic checks must not repeatedly announce an unchanged waiting state.
  Manual checks must expose progress and completion even when the key is
  still absent; do not replace the truthful wait with a fabricated error.
  Confirm spoken delivery in an attended VoiceOver run where reachable;
  snapshots or PR #45's Mac-helper check are not evidence for iCloud speech.
- Closeout statement for the threat model, shaped by `D1`: under the press
  or the bounded re-check, the task remains within the model, naming `T-04`
  and `TB-06` for binding-checked exact reads, no resource creation/deletion,
  and established-row persistence only after verified adoption; `R-04` and
  `TB-07` cover the copy. A persisted waiting record
  would instead require a model edit under the persistent-store rule.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path
  scan.

## Accepted decisions and implementation readiness

`user-confirmed` (2026-09-10): the maintainer accepted the reviewed direction
and requested a brief ready for implementation. These decisions supersede the
original alternatives; no repeat D1–D4 approval is needed.

- `D1`: manual recheck and foreground resume the same consented fresh join,
  with the account/anchor and side-effect boundaries above. Persist only the
  successful adoption, not waiting. The weaker restart UX is accepted for
  this iteration; durable waiting is deferred, not dismissed. The first
  implementation step adds a dated ADR 0007 amendment describing the
  permitted continuation and adoption before changing application behavior.
  Foreground is an opportunity after returning from system setup, not a
  claim about where Apple presents approval. Prove the existing `ON_RESUME`
  signal on each host; if Mac focus does not deliver it, document manual
  recheck as the verified Mac route rather than inventing a lifecycle hook.
- `D2`: waiting status: "Waiting for the workspace key from your other
  device." Supporting explanation: "Your existing workspace stays unchanged.
  Apple may first ask you to approve iCloud Keychain for this device."
  Onboarding also says "You can continue setup while you wait." **Continue**
  is primary and **Check again** secondary for the pending fresh join.
  Session retains the collapsed iCloud status row; its recheck appears only
  when expanded. Expansion starts no request. The summary presents locally
  saved choices and the waiting sync state separately. Exiting a failed
  continuation restores an explicit consent action, never a creating
  bootstrap behind **Check again**. Existing candidate/linked waits retain
  their state-specific recovery behavior; do not route them into a fresh-join
  operation that cannot act on their state.
- `D3`: finish only the device-local permission and application selection
  through existing routes. No mapping prompt or shared-item visibility is
  introduced; the complete cross-device user outcome still needs `SYNC-011`.
- `D4`: verify joining in both device orders. Record immediate joining and
  observed waiting separately. Optional Keychain toggling is not a mandatory
  gate; declining it leaves an explicit evidence limit.
- Preparation is complete when the independent plan re-review has no open
  Critical/Required finding. Implementation and applicable runtime checks
  are complete; the execution record retains the physical waiting limit.
  The implementing agent owns authority updates,
  checks, and the completed-change review in the execution record.
- Physical runs require the maintainer's available Mac and iPhone on the
  same account. Agree on destructive workspace cleanup and any optional
  settings change for the concrete run; plan acceptance does not perform
  those actions. The overlapping `SYNC-010` follow-up stays serialized after
  this task. Add the single wiki-log entry only at PR closeout.
