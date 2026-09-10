# `ONBOARDING-002`: Join the existing Apple workspace, wait safely for a delayed key, and finish local mappings

- **Review tier:** `high-risk`
- **Tier reason:** The join is the product's only crossing of the accepted
  membership boundary (Apple Account and iCloud Keychain trust, `TB-07`), and
  the waiting state is a public security claim: it must never read the
  remote workspace as empty, generate a replacement key, or create a
  parallel workspace (`T-04`, `R-04`). The recommended reading of `D1` lets
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
  amended under `D1` only when `D1` is `user-confirmed`);
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
  with **Check again**)

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
  (`ONBOARDING-001` `AC-02` keeps holding). `observed`: today a found anchor
  with a missing item returns `WaitingForWorkspaceKey`, the store stays
  `None`, `linked` stays false, foreground reads only the local store for an
  unlinked device, a relaunch reads local-only, and only another press
  re-reads the item.
- Waiting is a truthful state, not a background service. `D1` decides
  whether a join-only re-check exists beyond the press. When it does, it is
  bounded as follows. It runs only while the running process holds
  `WAITING_FOR_KEY` from a press and the store reads `None`; for a persisted
  candidate or an established workspace it makes zero provider calls and
  leaves the status unchanged, so ADR 0007 step 9's single exact delete
  stays with the explicit press. It keeps the binding resolved by the
  waiting press in process memory only, resolves the current binding first,
  and on a mismatch returns the device to local-only with zero provider
  calls. It reads zone, anchor, and item under that binding and never saves
  a zone, candidate, anchor, or key item, never deletes, and never persists.
  Outcomes: zone missing or anchor missing publishes local-only and leaves
  creation to the press; item missing stays waiting; item valid and matching
  commits the established row and runs one exchange; item mismatch,
  integrity failure, account changed, unavailable, or restricted publishes
  action required exactly as the press would; retryable, unknown outcome, or
  an undetermined account leaves the status unchanged because nothing new
  was learned. It never publishes syncing and publishes only a changed
  definitive outcome. `observed`: it must not reuse `syncNow()`, whose
  established check publishes local-only over a waiting status on an
  unestablished device, and `onForeground` sits outside `guarded`, whose
  exception mapping to retryable is wrong here; the re-check needs its own
  flight-exclusive wrapper and its own sealed result so the press protocol
  and `BootstrapResult` stay untouched. No timer, backoff, background
  delivery, push, or Keychain notification exists or is added.
- Copy and actions (`D2`) follow state, consequence, action. The waiting
  notice keeps the existing first sentence, says the existing workspace
  stays unchanged, and names the Apple-owned prerequisite without a settings
  deep link, a time, a device name, a location where Apple asks, or a
  success claim. A waiting device is consented, so its actions never read as
  a new consent or a decline. The summary step shows waiting instead of
  "Saved on this device", and the Session row's expanded action uses the
  same label as the step.
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
- Accessibility rules bind the changed surfaces: labels and roles, non-color
  state cues, wrapped action rows at large text, announced state changes.
- Write surface: `shared/src/commonMain/**/feature/sync/bootstrap/**`
  (`AppleSync`, `BootstrapCoordinator`, a new join-only phase or result as
  `D1` needs), `feature/sync/ui/**`, `feature/onboarding/**`
  (`OnboardingSteps`, `OnboardingScreen`, previews),
  `SessionScreenPreviewDataProvider`, `strings.xml` (a shared
  `action_check_again`, the waiting notice), their tests and the
  `AppleSyncTestHarness`, `DESIGN.md` (the implementation-boundary sentence
  on launch and foreground, the first-install iCloud bullet, the Session
  iCloud bullet), ADR 0007 (a dated `user-confirmed` amendment section in
  the ADR 0002 convention, only under the recommended `D1`), the
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
  the bounded join-only read; helper, picker, or Screen Time changes; the
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
  state and its accepted count.
- `AC-02` — Once the key item is readable, the next opportunity selected
  under `D1` turns waiting into linked plus one exchange, without a second
  consent control and without re-entering the flow. A relaunch while
  waiting reads local-only with **Sync with iCloud** available, never
  success and never a second workspace.
- `AC-03` — Over the fakes with call counts, the join-only re-check proves
  every row of the outcome table above: zero zone saves, anchor creates, key
  creates, key deletes, and store writes in every row except the valid
  matching item, which commits exactly one established row; a persisted
  candidate and an established workspace produce zero provider calls; a
  binding mismatch produces zero provider calls and local-only. The explicit
  press keeps the accepted protocol, proven by the unchanged
  `BootstrapCoordinatorTest` cases.
- `AC-04` — The iCloud step, the summary, and the Session row show waiting
  as waiting; the waiting sentences name the Apple-owned prerequisite with
  no deep link, time, device list, location, or success claim; every changed
  sentence maps to an authority listed above and the independent review
  checks that mapping; a waiting device's actions never read as a new
  consent or a decline.
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

- `commonTest`: `BootstrapCoordinatorTest` for the join-only re-check
  through its new entry point, one case per outcome row, with the shared
  `FakeBootstrapKeyPort` item map as the "key arrives later" seam and the
  existing fake counters as the proof; the existing press cases stay
  unchanged. `AppleSyncTest` for the `D1` opportunity (waiting device
  re-reads with one zone fetch, one anchor read, one item read, and zero
  saves, creates, deletes, or persists; links and runs one exchange after
  the key arrives; the waiting status is never replaced by local-only while
  the anchor stands; "no consent when foreground arrives then no provider
  is called" stays green; an established device is unchanged).
  `SyncBootstrapUiStateTest` and the onboarding holder tests for the waiting
  status mapping; rendering and copy are proven by the platform builds and
  manual inspection, not by Compose UI tests.
- `jvmTest` and `iosTest` over the real graphs: construction touches no
  provider; Session-first routing unchanged.
- Simulator, unattended: `first-install.json` and `first-install-skip.json`
  unchanged and green; the degraded iCloud row keeps its outcome.
- Physical, attended, direction A (Mac establishes, iPhone joins): iPhone
  **Remove workspace** first if linked, then `reset -t device` (uninstall)
  and `install`; Mac **Remove workspace**, then **Sync with iCloud** until
  linked; immediately afterwards the iPhone flow presses **Sync with
  iCloud**; expect waiting and record the observed outcome when the key was
  already readable (the likely result on trusted hardware, as `SYNC-009` saw
  the winner's item readable within seconds); continue the flow, summary and
  Session row show waiting; the `D1` opportunity or the press completes the
  join; then the Screen Time step and a picker selection with Mac counts
  unchanged. Direction B (iPhone establishes, Mac joins): Mac **Remove
  workspace** while the iPhone is still linked leaves the iPhone
  action-required, so remove on the iPhone first and link it fresh; `reset
  -t desktop` with backup; the same rows with the count query at zero while
  waiting and one established row afterwards; restoring the Mac backup
  afterwards restores a row for a removed workspace, so the maintainer's Mac
  then needs **Remove workspace** and a fresh link. iPhone evidence is
  status text plus the Mac's receipt, because the device database is not
  readable.
- Wait-window fallback (`D4`): when neither direction shows waiting
  naturally, the maintainer may turn iCloud Keychain off on the joining
  iPhone before the press and on afterwards, choosing to keep saved
  passwords at the system prompt; this is the least destructive route on
  already-trusted hardware and is not guaranteed to show Apple's approval
  prompt. Otherwise the unit rows and the `SYNC-009` race evidence carry the
  waiting claim and the record says so.
- Closeout statement for the threat model, shaped by `D1`: under the press
  or the bounded re-check, the task remains within the model, naming `T-04`
  and `TB-06` for the binding-checked exact reads with no create and no
  store, and `R-04` and `TB-07` for the copy; a persisted waiting record
  would instead require a model edit under the persistent-store rule.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path
  scan.

## Decisions or blockers

- `D1` waiting re-check opportunities and persistence. Recommended:
  the explicit press plus the bounded join-only re-check on foreground while
  the running process holds `WAITING_FOR_KEY`; nothing is persisted, so a
  relaunch reads local-only with **Sync with iCloud** available, and the
  press joins. Reason: on the iPhone, Apple's approval happens in Settings,
  so the person leaves and returns, and the return is exactly when the item
  may be readable. This reads ADR 0007's "Bootstrap runs only after the
  explicit **Sync with iCloud** action" as governing creation and the first
  read; `SYNC-010` `D5` already runs the zone and anchor check on foreground
  for a linked device. The ADR receives a dated `user-confirmed` amendment
  section only when this decision is confirmed. Cost: the bounded mode in
  the coordinator, one branch in `AppleSync`, three documentation sentences,
  and on the Mac one companion launch per window focus while waiting (the
  cost `D5` already accepted for linked Macs). `open`: the foreground signal
  is the existing `ON_RESUME` lifecycle effect, which on the Mac follows
  window focus; the implementer proves it with the driver (focus away and
  back) or records the press as the Mac route. Alternatives: explicit press
  only (no coordinator change, no ADR edit, the person must return to the
  row and press again after every delivery; the acceptable fallback when no
  ADR amendment is wanted in this pull request); or a persisted waiting
  record (anchor and binding) in `sync_bootstrap_state` through a new
  migration so launch re-checks too, which every state branch of the
  coordinator, removal, and the established check must then handle, and
  which requires a threat-model edit while gaining little over the press
  that is available on relaunch anyway.
- `D2` waiting copy and actions. Recommended: the notice reads "Waiting for
  the workspace key from your other device. Your existing workspace stays
  unchanged. Apple may first ask you to approve iCloud Keychain for this
  device."; while waiting, the step's primary action reads **Check again**
  and its quiet action **Continue** (instead of **Sync with iCloud** and
  **Not now**), the Session row's expanded action reads **Check again**,
  both run the same attempt through one shared `action_check_again` string,
  and the summary line reuses "Waiting for the workspace key" from the
  Session row (the summary step receives the status, not only the linked
  flag). The `DESIGN.md` first-install and Session bullets name the waiting
  labels. This keeps one consent control because a waiting device is
  consented. Alternative: keep the current labels, which the sync recipe
  already documents as "retry consent on the waiting side", at the cost of a
  consented device being asked to consent again.
- `D3` the reading of "finish local mappings". Recommended: the joining
  device completes only the device-local part through the existing
  permission step, **This Mac** row, and Paused items picker, with no new
  copy and no mapping prompt, and the record states that synced items stay
  invisible until `SYNC-011`. Alternative: a "map the shared apps on this
  device" prompt, which cannot exist before `SYNC-011` projects the replica.
- `D4` physical evidence. Recommended: both device orders as listed, the
  wait window produced by pressing on the joining device right after the
  other device links, and the iCloud Keychain toggle on the iPhone only as
  the attended fallback the maintainer may decline; a declined fallback is
  recorded as a limit, not a failure. Alternative: one direction only, with
  the other carried by `SYNC-009` evidence.
- Physical gate: the maintainer's iPhone and Mac under one iCloud account.
  Every from-empty row costs **Remove workspace** on each linked device and
  a fresh local database on the joining device; iCloud Keychain propagation
  is Apple-timed; the Screen Time prompt, any iCloud Keychain approval, and
  any settings toggle are attended steps.
