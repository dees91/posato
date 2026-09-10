# `ONBOARDING-002`: Join the existing Apple workspace, wait safely for a delayed key, and finish local mappings

- **Review tier:** `high-risk`
- **Tier reason:** The join is the product's only crossing of the accepted
  membership boundary (Apple Account and iCloud Keychain trust, `TB-07`), and
  the waiting state is a public security claim: it must never read the
  remote workspace as empty, generate a replacement key, or create a
  parallel workspace (`T-04`, `R-04`). The recommended reading of `D1` lets
  a consented device re-read the anchor and key item without a new press,
  which extends when the bootstrap coordinator runs and therefore needs a
  recorded reading of ADR 0007 before implementation. The physical proof
  needs two devices, workspace removal on the maintainer's account, and an
  Apple-timed Keychain delivery nobody can force.
- **Dependencies:** completed `ONBOARDING-001` (the six-step flow, its
  `first-install-skip.json` prelude, `local_setup_state`), `SYNC-009`
  (deterministic join, `waiting-for-workspace-key`), `SYNC-010` (exchange,
  removal, explicit retry opportunities), the merged Session rows from
  PR #45 (collapsed iCloud row, `MacHelperSetupUiState`), `TARGETS-003` and
  `TARGETS-004` (device-local application selection), `QUALITY-002` and
  `QUALITY-004` (driver, provisioning gate)
- **Integration group:** `PR-SECOND-INSTALL`
- **Authority:** `ONBOARDING-002` in MVP roadmap revision 12 (wave P4/W4.2;
  Gate 6 outcome "A second installation joins the existing workspace, waits
  safely for a delayed key, and completes only its local mapping"; decision
  gate "Apple-managed Keychain approval may remain external"),
  [the MVP scope](../../product/mvp-scope.md) (primary flow steps 2 to 4:
  "Apple may require its own iCloud Keychain approval outside Blocker, but
  Blocker does not require a QR or cross-device approval"; "If CloudKit
  already contains the workspace while its synchronizable key is still
  unavailable, Blocker waits and reports that state. It does not create an
  empty or parallel workspace"; "They choose shared domains and assign local
  application selections on each device"; outcome bullet "delayed
  synchronizable-Keychain delivery produces a waiting state and never an
  empty or parallel workspace"; "Opaque platform application selections
  remain local"),
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  ("Apple may still require system-level approval or recovery before a new
  device joins iCloud Keychain. Blocker reports that prerequisite and does
  not duplicate or bypass it"; the waiting invariant must hold "under
  concurrent first runs, delayed Keychain delivery, account changes,
  restarts, and retries"; `open`: no controllable maximum propagation time,
  "The product must expose waiting and retry behavior and verify it on
  physical devices"),
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  (step 4: a missing item "produces `waiting-for-workspace-key`; it never
  generates a key, replaces the anchor, interprets the workspace as empty,
  or creates another workspace"; "Bootstrap runs only after the explicit
  **Sync with iCloud** action", read under `D1`),
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  (Apple trust is the complete membership boundary; no added ceremony;
  `R-01`),
  [`DESIGN.md`](../../../DESIGN.md) (the seven statuses; "Waiting,
  retryable, syncing, and action-required states keep their real meaning";
  "Connected to iCloud" describes linking; collapsed iCloud row whose
  expansion "never starts a helper check or sync attempt"; the summary
  "names the remaining setup and its existing route"; no time, device list,
  or success claim),
  [the threat model](../../security/apple-mvp-threat-model.md) (`TB-06`
  "represent unavailable/delayed services truthfully", `TB-07`, `T-04`,
  `R-04` names this task; an owning task "must update or explicitly remain
  within this model" when it adds a public security claim),
  `SYNC-010` decision `D5` (retry is an explicit opportunity: **Sync now**,
  launch or foreground on an established workspace, the end of a local
  commit; no timer, no background delivery),
  [the brand baseline](../../wiki/topics/brand-and-design-baseline.md)
  (state first, consequence second, action third; "Never congratulate"), and
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
  `None`, `linked` stays false, foreground performs nothing for an unlinked
  device, and a relaunch reads local-only; only another press re-reads the
  item.
- Waiting is a truthful state, not a background service. `D1` decides which
  opportunities re-read the key and whether waiting survives a relaunch.
  Whatever `D1` selects, an automatic re-check is join-only: it reads the
  zone, anchor, and item under the same binding check, never saves a zone,
  candidate, anchor, or key item, never deletes anything, and when the
  anchor is missing it returns the device to local-only and leaves creation
  to an explicit press. The explicit press keeps the full protocol.
  `observed`: the automatic path must not reuse `syncNow()`, whose
  established check publishes local-only over a waiting status on an
  unestablished device; it calls the join-only coordinator path directly.
  No timer, backoff, background delivery, push, or Keychain notification.
- Copy and actions (`D2`) follow state, consequence, action. The waiting
  notice keeps the existing sentence, names the Apple-owned prerequisite
  without a settings deep link, a time, a device name, or a success claim,
  and says the existing workspace stays unchanged. A waiting device is
  consented, so its actions never read as a new consent or a decline. The
  summary step shows waiting instead of "Saved on this device", and the
  Session row's expanded action uses the same label as the step. Changes
  announce through the existing `announceChanges` notices.
- Apple-managed Keychain approval remains external: the app names the
  prerequisite and never detects, opens, requests, or simulates it. There is
  no public API for iCloud Keychain availability, so no "iCloud Keychain is
  off" claim is made.
- "Finish local mappings" (`D3`) means the joining device completes its
  device-local part through the existing routes: the permission step or the
  Session **This Mac** row, and the Paused items application picker. A
  selection produces no sync bundle. No synced domain, policy, or
  application becomes visible on the joining device, because projection is
  `SYNC-011`; the summary's existing notice names the remaining setup.
  `observed`: authoring diffs exact domains only, so neither a picker
  selection nor the application group name reaches the outbox today.
- Accessibility rules bind the changed surfaces: labels and roles, non-color
  state cues, wrapped action rows at large text, announced state changes.
- Write surface: `shared/src/commonMain/**/feature/sync/bootstrap/**`
  (`AppleSync`, `BootstrapCoordinator`, `BootstrapResult`, phases as `D1`
  needs), `feature/sync/ui/**`, `feature/onboarding/**` (`OnboardingSteps`,
  `OnboardingScreen`, `OnboardingPreviewDataProvider`),
  `SessionScreenPreviewDataProvider`, `strings.xml`, their tests and the
  `AppleSyncTestHarness`, `DESIGN.md`, ADR 0007 (one clarifying sentence
  under `D1`), the `verify-posato` skill (`features/sync.md` join rows,
  `features/onboarding.md` second-install sub-feature, `SKILL.md` if a new
  prelude appears), `docs/wiki/topics/cross-device-synchronization.md`,
  `docs/wiki/topics/brand-and-design-baseline.md` (the onboarding diagram
  still shows "Choose local apps on this device", superseded by
  `ONBOARDING-001` `D2`), and the wiki log.
- Non-goal: domain, policy, and session convergence (`SYNC-011`,
  `SYNC-012`); a per-item mapping prompt for synced applications; backfill of
  edits made before linking; total-key-loss recovery; QR, invitation, or
  cross-device approval; a settings deep link or an in-app iCloud Keychain
  check; changes to the zone, anchor, candidate, or key-item protocol beyond
  a join-only read mode; helper, picker, or Screen Time changes; the
  accessibility captures waived for `ONBOARDING-001` (they stay with
  `RELEASE-001`); Simulator or Mac evidence of a real Apple approval prompt
  (already-trusted hardware may never show one; the record says so).

## Acceptance

- `AC-01` — On a fresh installation whose account already holds the workspace,
  one press of **Sync with iCloud** in the iCloud step either links the
  device (completed attempt, established row) or reports waiting for the
  key. While waiting, `sync_bootstrap_state` holds no row on the Mac, no zone
  save or key-item write occurs, and the other device keeps its linked
  state and accepted counts. This holds in both device orders.
- `AC-02` — Once the key item is readable, the next opportunity selected
  under `D1` turns waiting into linked plus one exchange, without a second
  consent control and without re-entering the flow. A relaunch while
  waiting shows the state `D1` defines, never success and never a second
  workspace.
- `AC-03` — With the anchor present and the item missing, every automatic
  re-check performs no create, save, or delete on the zone, anchor, key
  item, or store, proven over the fakes with call counts; with the anchor
  missing, an automatic re-check creates nothing and returns local-only; the
  explicit press keeps the accepted protocol, proven by the unchanged
  `BootstrapCoordinatorTest` cases.
- `AC-04` — The iCloud step, the summary, and the Session row show waiting
  as waiting; the waiting sentence names the Apple-owned prerequisite with
  no deep link, time, device list, or success claim; every changed sentence
  maps to an authority listed above and the independent review checks that
  mapping; a waiting device's actions never read as a new consent or a
  decline.
- `AC-05` — After joining, the device completes its permission step and a
  local application selection through the existing routes; the selection
  adds no pending or accepted bundle on the Mac, and no synced domain or
  application appears on the joining device.
- `AC-06` — `./gradlew quality` passes with no new suppression; the
  `first-install.json` and `first-install-skip.json` fixtures still pass;
  the skill documents the join rows and the fresh-database cost; evidence
  stays under the ignored `build/verification/`.

## Verification

- `commonTest`: `BootstrapCoordinatorTest` for the join-only re-check
  (anchor present, item missing: waiting, zero saves and deletes; anchor
  present, item valid and matching: established row committed; anchor
  missing: local-only, store unchanged, zero saves; persisted candidate with
  a foreign anchor: adoption without a new anchor; binding mismatch: action
  required), with the shared `FakeBootstrapKeyPort` item map as the
  "key arrives later" seam; `AppleSyncTest` for the `D1` opportunities
  (waiting device re-reads and links, one exchange after the key arrives,
  the waiting status is never replaced by local-only while the anchor
  stands, "no consent when foreground arrives then no provider is called"
  stays green, an established device is unchanged);
  `SyncBootstrapUiStateTest` for the waiting labels; onboarding holder and
  summary tests for the waiting rendering.
- `jvmTest` and `iosTest` over the real graphs: construction touches no
  provider; Session-first routing unchanged.
- Simulator, unattended: `first-install.json` and `first-install-skip.json`
  unchanged and green; the degraded iCloud row keeps its outcome.
- Physical, attended, direction A (Mac establishes, iPhone joins): Mac
  **Remove workspace**; iPhone uninstall and `install` (fresh database); Mac
  **Sync with iCloud** until linked; immediately afterwards the iPhone flow
  presses **Sync with iCloud**; expect waiting (record the observed outcome
  if the key was already readable); continue the flow, summary shows
  waiting, Session row shows waiting; the `D1` opportunity or the press
  completes the join; then the Screen Time step and a picker selection with
  Mac counts unchanged. Direction B (iPhone establishes, Mac joins): `reset
  -t desktop` with backup and restore, the same rows with
  `select count(*) from sync_bootstrap_state` at zero while waiting and one
  established row afterwards. iPhone evidence is status text plus the Mac's
  receipt, because the device database is not readable.
- Wait-window fallback (`D4`): when neither direction shows waiting
  naturally, the maintainer may turn iCloud Keychain off on the joining
  iPhone before the press and on afterwards, which is also the only way to
  observe Apple's approval prerequisite; otherwise the unit rows and the
  `SYNC-009` race evidence carry the waiting claim and the record says so.
- Closeout statement for the threat model: the join-only re-check is a
  binding-checked read path that adds no data class, key, store, or
  entitlement; the waiting copy is a truthful-state claim under `TB-06` and
  `R-04`; the model is edited only if a sentence exceeds it.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path
  scan.

## Decisions or blockers

- `D1` waiting re-check opportunities and persistence. Recommended:
  the explicit press plus a join-only re-check on foreground while the
  running process holds `WAITING_FOR_KEY`; nothing is persisted, so a
  relaunch reads local-only with **Sync with iCloud** available, and the
  press joins. This reads ADR 0007's "Bootstrap runs only after the
  explicit **Sync with iCloud** action" as governing creation and the first
  read, with one clarifying sentence added to the ADR in this pull request:
  a device that reported waiting may re-read the anchor and item on
  foreground, and that re-read creates nothing. Cost: a join-only mode in
  the coordinator and one branch in `AppleSync.onForeground`. `open`: the
  foreground signal is the existing `ON_RESUME` lifecycle effect, which on
  the Mac follows window focus; the implementer proves it with the driver
  (focus away and back) or records the press as the Mac route. Alternatives:
  explicit press only (no coordinator change, the person must return to the
  row and press again after every delivery); or a persisted waiting record
  (anchor and binding) in `sync_bootstrap_state` through a new migration so
  launch re-checks too, which every state branch of the coordinator,
  removal, and the established check must then handle.
- `D2` waiting copy and actions. Recommended: the notice reads "Waiting for
  the workspace key from your other device. Apple may ask you to approve
  iCloud Keychain on this device first. Your existing workspace stays
  unchanged."; while waiting, the step's primary action reads **Check
  again** and its quiet action **Continue** (instead of **Sync with iCloud**
  and **Not now**), the Session row's expanded action reads **Check again**,
  and both run the same attempt. Alternative: keep the current labels, which
  the sync recipe already documents as "retry consent on the waiting side",
  at the cost of a consented device being asked to consent again.
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
  Every from-empty row costs **Remove workspace** on the linked device and a
  fresh local database on the joining device; iCloud Keychain propagation is
  Apple-timed; the Screen Time prompt, any iCloud Keychain approval, and any
  settings toggle are attended steps.
