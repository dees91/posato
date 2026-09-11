# `SYNC-011`: Converge exact domains and the semantic application policy while opaque selections stay local

- **Review tier:** `high-risk`
- **Tier reason:** This is the first task that moves remote operations into
  the store that enforcement reads (`exact_domain_policy`,
  `application_policy`), so a wrong merge silently blocks or unblocks a
  website on a device that never chose it, and on iOS a reapply inside a
  session pushes the converged set into ManagedSettings without a prompt. It
  adds a persistent store with a migration, extends threat-model owner rows,
  and its physical proof needs two linked devices, offline retry, and
  workspace removal on the maintainer's account.
- **Dependencies:** completed `ONBOARDING-002`, `SYNC-010` (exchange, outbox
  authoring of exact domains, removal, explicit retry opportunities),
  `SYNC-002` and `SYNC-013` (reducer, projection, writer checkpoint),
  `TARGETS-001` and `MODEL-001` (revisioned exact-domain store, canonical
  values, 1,024-domain cap), `TARGETS-002` (singleton application group
  name), `TARGETS-003` to `TARGETS-005` (device-local mappings),
  `SESSION-002` and `SESSION-003` (frozen start set, reapply of the current
  set)
- **Integration group:** `PR-POLICY-SYNC`
- **Authority:** `SYNC-011` in MVP roadmap revision 12 (wave P4/W4.3; Gate 6
  outcome "Exact domains and semantic application policy converge while
  opaque selections remain local and status stays truthful"; decision gate
  "Conflict semantics accepted by `SYNC-001`", satisfied by the accepted
  ADR 0006; coverage row "Bidirectional physical convergence, offline/retry");
  [the MVP scope](../../product/mvp-scope.md) ("an exact domain policy
  created on one device arrives unchanged on the other"; "a semantic
  application policy synchronizes between the devices while each
  platform-specific application selection remains local"; no promise of
  eventual delivery, latency, or waking a device);
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  (domain operations "reduced from scratch in ascending global total order",
  capacity outcomes "a truthful conflict requiring action";
  `application-policy-present` "device mappings remain local",
  `application-policy-absent` "never a local mapping", the singleton policy
  "uses the greatest total-order key"; "All replicas with the same valid
  operation set derive the same synchronized projection"; "The current
  local-only 1,024-domain implementation limit is not format-1 product
  authority");
  `SYNC-010` decisions `D1` ("`SYNC-011` owns the read side, the backfill,
  and reconciling the two stores"), `D2` (removal clears bootstrap and
  replica state, never the policy tables), and `D5` (retry is an explicit
  opportunity);
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  (rejection of tampered, stale, or wrong-context input "without replacing
  the last valid local state");
  [`DESIGN.md`](../../../DESIGN.md) (the seven statuses; "A completed
  attempt makes no claim about receipt on another device"; Paused items
  "1,024 unique domains in the policy", "Failed writes keep the entire
  submitted draft", the group "created ... only when absent", "Clearing
  choices keeps group metadata", no group removal control; Session
  "Restrictions follow your current Paused items"; the sentence "Incoming
  operations remain in the replica until the later convergence slices
  connect them" retired here);
  `SESSION-003` decision `D2` (reapply applies the current set; the summary
  shows the frozen set);
  [the threat model](../../security/apple-mvp-threat-model.md) (`A-04`
  projected policy never silently reset, `T-02`, `T-03` one atomic apply,
  `T-14` removal leaves nothing behind, `T-05`/`R-01` any Apple-trusted
  installation may author, `T-10` and `A-03` opaque selections never
  synchronized, the persistent-store change rule); and the PR #43 advisory
  finding on `AppleSyncAuthoring.drain` (a transient key read drops the
  queued diff)

## Outcome

On two linked devices, a website added or removed in Paused items on one
device appears or disappears in Paused items of the other, and in its Session
summary when no session is active, after the other's next completed
exchange, unchanged; enabling the application group on one device enables it
on the other while every application selection stays on the device that made
it; a website saved before linking is authored at the first exchange after
linking, and a website added or removed on a linked device is a durable
intent that survives a failed exchange, a closed process, or a relaunch until
it is authored, so it reaches the peer at the peer's next completed exchange;
a remote change never removes a website that only this device holds; the
person reads, next to the existing action, that linking combines the websites
saved on their devices while app choices stay on each device; the seven
statuses keep their meaning and no sentence claims receipt; and the local
databases hold only what the accepted format already carries. On a second
device, the website step and the summary reflect fetched websites: joining,
fetching, and skipping manual entry shows the true saved-website count
instead of the empty state.

## Boundaries

- Convergence is a reconciliation of two stores (`D1`). The replica
  projection (`SyncReducer` over the writer checkpoint) is authoritative for
  what the workspace holds; the local policy is authoritative for this
  device's intent. Local intent is durable (`D7`): on a linked device every
  policy save records its per-domain and group-name diffs as ordered intent
  rows in the same store transaction as the save, so an unauthored addition
  or removal survives a failed exchange, a closed process, and a relaunch.
  This supersedes the volatile handoff queue accepted by `SYNC-010` `D1`.
  The authoring half of each pass authors the intents in order, skipping an
  addition the projection already holds and a removal it already lacks, and
  deletes an intent only after its operation is accepted into the local
  replica; re-authoring after a crash is a reducer no-op. A persisted base,
  the projection the reconciler last applied, then makes the apply exact:
  domains the projection gained since the base are added locally, domains it
  lost since the base are removed locally, and a domain the local policy
  holds that neither the projection nor the base holds is a pre-link extra,
  which the first exchange after linking authors. Own operations are
  accepted into the local replica at authoring, so after a completed pass
  the local shared fields, the projection, and the base agree. Without a
  base (first link, or after removal) the first pass seeds an intent for
  every local website: linking adds this device's websites to the workspace
  and removes nothing local, and a website a peer removed before this device
  linked comes back for both, which `DESIGN.md` states.
- The base advances only inside the store transaction that writes the
  merged policy, never before it, and never on a refused or failed apply;
  the intent rows and the base are cleared in the transaction that clears
  the replica on **Remove workspace** (`SYNC-010` `D2` otherwise unchanged:
  local websites and the group stay); a base or intent row that fails the
  `MODEL-001` boundary validation, or a base without a replica state row, is
  corruption: the reconciler applies nothing and reports action required. A
  crash between acceptance and apply leaves the base behind and the next
  pass recomputes the same apply; the lost-removal case (base empty, a
  website added and authored, the exchange fails, the website removed, the
  process closes) resolves because the removal intent is authored before
  anything is applied, so the projection lacks the website when the apply
  runs.
- The reconciler is part of the serialized exchange pass on an established
  workspace (`D2`), in this order: the domain intents are authored once the
  writer is open and before the publish leg, so they leave in the same pass;
  the consume leg runs; the group name is decided only after consume, so a
  fresh replica has learned the workspace's name before any local name is
  authored (`D4`); a name authored there is published by a second publish
  leg in the same pass; the apply half runs last, before the pass publishes
  its status, and only when authoring succeeded. It writes the local policy
  through the raw store, never the sync decorator, so an applied projection
  is never recorded as intent; it is serialized with user saves by one
  shared write gate held only for read, merge, and apply; it uses the
  store's compare-and-set revision with one re-read retry; it never writes a
  `sync_*` row, so the writer checkpoint stays valid; it publishes no new
  status category. A save made while an exchange is in flight records its
  intent at once and is authored by the next pass.
- Remote changes reach the screens without navigation (`D3`): the local
  policy store exposes a change signal, defaulted to empty on the interface,
  emitted only by reconciler applies and forwarded by the decorator; Paused
  items and Session re-read on it through their existing refresh requests.
  The `TARGETS-001` editor rules apply unchanged: a draft survives, an edit
  of a domain that disappeared resets, and a user save that lost the race
  shows the existing conflict copy and recovers on reload. No copy says that
  a change arrived, from whom, or when.
- The application group converges as the format defines (`D4`): a local
  group creation (the default made by a first selection or by **Enable
  selected apps**) is recorded as a name intent and decided after consume: a
  name the projection already holds is applied locally and the local default
  is discarded, never authored, because the format's greatest-key rule would
  otherwise let the default win over an existing custom name; only when the
  projection holds no name is the local name authored. A remote presence
  sets the local name and leaves selections untouched, so a device without
  selections shows the existing review reason; a remote absence, which no
  current screen can author, clears the local name and keeps every
  selection, so the existing "saved on this device, but is not yet included
  in the application group" notice and **Enable selected apps** offer the
  recovery. No mapping, token, count, or identifier of a selection is
  authored, projected, or diagnosed (`A-03`, `T-10`).
- Capacity is refused, never truncated (`D5`): when the merged local set
  would exceed the 1,024-domain local cap, the reconciler applies no domain
  change, keeps the last valid policy and base, and reports action required
  with a reason the copy names; when the projection itself carries the
  reducer's 2,048 capacity outcomes, the status reports action required with
  its own reason and the domains that did apply stay; once removals bring
  the set back under the limit, the next pass applies normally. The local
  cap and the format cap stay.
- A converged change during an active session behaves like a local edit
  (`D6`): it lands in the local policy at once, the persisted frozen start
  set is untouched, the active summary keeps showing the frozen set while
  Paused items shows the change, and enforcement follows the current set at
  its next reapply, which on iOS happens on poll loss, retry, or relaunch,
  not on a guaranteed schedule; no sentence promises that restrictions
  change at once.
- Durable intent replaces the process-owned queue (`D7`): the in-memory
  handoff and its drop on a transient failure go away, the intent rows are
  the ordered outbox source, a transient authoring failure leaves them in
  place for the next pass, and a terminal failure keeps reporting action
  required with the intent retained for an explicit retry. This closes the
  PR #43 finding by construction. A rejected bundle still pins the cursor
  (no exact refetch), which now delays visible convergence; the limit is
  carried forward and stated in the record.
- The person learns the consequence before linking (`D8`): one sentence next
  to the existing **Sync with iCloud** action, in the onboarding iCloud step
  and in the Session iCloud row when unlinked, "Linking combines the websites
  saved on your devices. App choices stay on each device." No extra
  confirmation, no time or delivery claim.
- Onboarding counts derive from the real policy (`D9`). The website step
  reads the policy store on UI-triggered entry and after own saves
  (`advance()` stays pure, the zero-reads test stands). While visible, the
  summary observes `policyChanges`: subscribe before the initial read, then
  serialize refreshes in its step-owned coroutine and cancel on exit. Failed
  reads retain the last valid count. The
  focus-clearing effect is keyed to `browser.lastReceipt`, which `accept`
  advances only for the matching pending submission id, never to the count,
  so a remote arrival never dismisses the keyboard. The website entry step
  has no live subscription. The Continue/Not-now switch and the summary
  body and count copy consume the derived count unchanged.
- The merge overlays unauthored intent (`D10`). Inside the merge
  transaction the reconciler re-reads the intent rows under the gate: a
  pending `PresentDomain` forces inclusion and a pending `RemoveDomain`
  forces exclusion of the projection-derived set. Apply them in persisted
  order, so the last intent for each domain wins and a save recorded
  between authoring and apply is never transiently reverted. The base still
  advances to the applied projection. Deferring the apply while intents pend
  is rejected: it would delay all convergence for the same race.
- Reducer capacity is action-required only at the cap (`D5` correction).
  The reason is set iff the projection carries `DOMAIN_CAPACITY` outcomes
  and the synchronized set holds 2,048 domains; below the cap the reason
  clears, because a distinct still-later presence can use the freed capacity
  and nothing is blocked. Historical outcomes below the cap are not
  surfaced. The local 1,024 refusal and its tested exit are unchanged.
  `AppleSyncState` carries the reason; `SyncStatus.message()` becomes
  reason-aware: the reason, then the instruction (remove websites on any
  device, choose Sync now, compare Paused items on both devices and add
  anything missing again) while `summary()` stays short; the onboarding
  notice and the accessible announcements inherit it through the same
  `message()` path. A domain missing from a below-cap set because of a
  historical capacity outcome surfaces no reason; that rare state is an
  accepted limit.
- The no-base pass authors domain intents after consume (`D11`). On the
  first-link and post-removal pass the pass learns the remote order first,
  then authors local extras with a durable clock already past every accepted
  remote HLC, so extras sort last with the greatest total-order key and win
  regardless of skew; the existing
  second leg publishes them in the same pass. Seeding skips domains already
  pending as intents, so a retried no-base pass authors no duplicate
  presence. Later passes keep pre-consume
  authoring: concurrent ordering there is genuine last-writer-wins with no
  union promise.
- Presentation: `D8` in Session is a caption inside the expanded iCloud
  options when unlinked, next to the existing action, with the full
  sentence; in onboarding the websites clause is woven into the existing
  iCloud content with no new panel and no repeat of the local-app-choices
  point. Signal re-reads compare policy content (domains plus group name,
  not the revision): equal content emits nothing and the reconciler skips
  the store write and the signal, a different result updates the snapshot
  without an `isLoading` flash; editor reconcile rules are unchanged.
- Write surface: `shared/src/commonMain/**/feature/sync/bootstrap/**` (the
  exchange returning the projection, the reconciler, the intent drainer
  replacing `AppleSyncAuthoring`'s channel, removal clearing intents and
  base), `feature/targets/data/**` (intent rows written inside the store
  transaction, change signal on the interface and decorator, the shared
  write gate, the base store), `feature/targets/ui/**` and
  `feature/session/ui/**` (re-read on the signal only),
  `feature/onboarding/OnboardingSteps.kt` and `feature/sync/ui/**` for the
  `D8` sentence, a new `SyncLocalPolicy.sq` plus `migrations/7.sqm` (intent
  rows and the base), both DI graphs, tests with a two-harness helper,
  `strings.xml` (the `D5` reasons and the `D8` sentence), `DESIGN.md`
  (the retired sentence, the linking rule and its re-add consequence, the
  `D8` sentence in the onboarding and Session bullets, the
  implementation-boundary sentence on cross-device promises), the
  `verify-posato` skill (`features/sync.md` sub-feature, steps 4 to 6, the
  second-install line, and the count and read-back queries;
  `features/websites.md` "synchronization ... not connected yet";
  `features/application-group.md`), `docs/security/apple-mvp-threat-model.md`
  (`SYNC-011` added to the `A-04`, `T-02`, `T-03`, and `T-14` owner rows;
  `A-03` and `T-10` unchanged), the cross-device synchronization wiki
  topic, and the wiki log. It also covers `feature/onboarding/OnboardingUiState.kt`
  and its preview provider (derived count, receipt-keyed focus), the
  reason-aware status path (`AppleSyncState`, the `SyncBootstrapSection.kt`
  message/summary wiring), and rare-state preview samples.
- Non-goal: session start, early end, and expiry convergence (`SYNC-012`);
  exact refetch of a rejected bundle; raising the local domain cap; a group
  removal control; a per-domain capacity surface beyond the status;
  provenance or removal of remotely originated websites after **Remove
  workspace** (documented limit); any "received" or "arrived" copy;
  synchronizing selections, mappings, or counts; the invalid-selection
  lifecycle (stays `open`); portable mode.

## Acceptance

- `AC-01` — With both devices linked, a website added in Paused items on one
  device appears in Paused items of the other after the other's next
  completed exchange, and a website removed disappears the same way, in both
  directions, with the canonical value unchanged; with no session active the
  Session summary shows it too; a repeated exchange adds no accepted entry on
  the Mac. Evidence: the Mac's `exact_domain_policy` count and canonical
  read-back per the websites recipe, and the iPhone's Paused items text.
- `AC-02` — Enabling the application group on one device (a first selection
  or **Enable selected apps**) sets the group on the other after its next
  completed exchange, while the Mac mapping database count and the iPhone
  selection count are unchanged by any exchange; the Session review on a
  device without selections names the existing missing-selection reason. A
  remote absence clearing the name while keeping selections is proven over
  the fakes only, because no screen authors it.
- `AC-03` — A website saved while unlinked is authored at the first exchange
  after linking; a website added or removed on a linked device while an
  exchange is failing, in flight, or after the process closed is authored at
  the next completed pass, and the lost-removal case never re-adds the
  website; linking removes no local website and no local group; after
  **Remove workspace** and a fresh link, the same holds again.
- `AC-04` — Over the fakes: intent rows are written in the same transaction
  as the save and deleted only after acceptance; the reconciler writes no
  `sync_*` row; authors nothing for a domain the projection already holds
  and no removal for a domain it already lacks; a fresh replica with a local
  default group joining a workspace that holds a custom name keeps the
  custom name on both devices; applies nothing, keeps the last valid policy
  and base, and reports action required with the right reason when the
  merged set would exceed 1,024, when the projection carries reducer
  capacity outcomes while the synchronized set holds 2,048 domains,
  or when a base or intent row is corrupt, and applies
  again once the set is back under the limit; keeps the base unchanged on
  any refused or failed apply; retries once on a revision conflict; yields
  the existing conflict outcome to a user save that loses the race without
  dropping its draft; and two harnesses exchanging in different orders
  converge to equal local policies.
- `AC-05` — The seven status categories and the existing copy are unchanged
  except the `D5` reasons and the `D8` sentence; Paused items and Session
  re-read after an apply without navigation and never after their own save;
  a change arriving during an active session leaves the persisted frozen
  start set and the active summary unchanged while Paused items shows it;
  no diagnostic, log, tracked file, or status copy carries a domain value, a
  selection, or a token, while the ignored evidence directory may hold the
  reserved synthetic fixture domains in screenshots and read-backs.
- `AC-06` — `./gradlew quality` passes with no new suppression; migration
  `7.sqm` is verified with existing rows intact; the existing website and
  first-install fixtures stay green; the skill's sync and websites recipes
  assert convergence instead of their current "remain device-local" and
  "not connected yet" sentences; the threat-model owner rows are updated in
  the same pull request.

- `AC-07` — With the policy store pre-populated by a fetch, entering the
  website step shows the fetched count, skipping manual entry keeps it, and
  the summary shows the matching saved-website count with the non-empty
  body; a store change without an own save never changes
  `browser.lastReceipt`. The test drives the holder plus the browser,
  mutates the fake store without submitting, and asserts `lastReceipt`
  unchanged; the `LaunchedEffect` keys on it. While summary is visible, later
  additions and removals refresh the count without advancing the step. Cover
  a change during the initial read, read failure, and cancellation on exit.
  The amendments also extend `AC-04` (overlay no-flicker cases
  between passes, the skewed-clock first link, the `D5` at-cap
  set/cleared transitions, historical-outcomes-below-cap silence), `AC-05`
  (the reason in expanded options, the onboarding notice, and
  announcements; refresh preservation), and `AC-06` (rare-state previews
  labeled design-review-only; the physical screenshot list).

## Verification

- `commonTest`: intent and reconciler cases over fake stores (intent
  written atomically with the save and deleted only after acceptance; the
  lost-removal case; a save during an in-flight exchange; no base: seed and
  add; remote add and remove; both sides changed on a domain; re-add after a
  remote removal; the same edit on both devices; skip rules; a fresh replica
  with a local default name joining a custom-named workspace; a local name
  authored only when the projection holds none; local-cap refusal keeps the
  base and the later exit applies; reducer capacity outcomes report their
  own reason; corrupt base or intent; revision conflict retry; store failure
  keeps policy; removal clears intents and base with the replica).
- `commonTest` over two `AppleSyncTestHarness` instances with a cursor-aware
  shared fake mailbox and distinct wall clocks: the inverted
  `AppleSyncPersistenceTest` case (replica and local policy both change),
  reordered and duplicate pages converge to equal `exact_domain_policy` and
  `application_policy` on both, a pre-link domain is authored and published
  in the first pass, a repeat exchange adds no accepted bundle, a
  mid-session arrival leaves `local_session.frozen_domains` unchanged.
- `SqlDelight` migration verification for `7.sqm`; `jvmTest` and `iosTest`
  over the real graphs (construction touches no provider; the raw store and
  the decorator share one gate; the fakes compile against the defaulted
  signal).
- Simulator, unattended: `add-website.json`, `remove-website.json`,
  `website-edit.json`, `first-install.json`, `first-install-skip.json`
  unchanged and green.
- Physical, attended, signed Mac and iPhone on one account, both linked at
  the start; record each device's websites first, because linking converges
  both to the union of their websites by design and the maintainer accepts
  that before the run; baseline counts first. Websites: add
  `design-proof-11.example` on the Mac, **Sync now** on the iPhone, `find`
  it in Paused items; add `design-proof-12.example` on the iPhone, **Sync
  now** on the Mac, count and canonical read-back; remove each on the
  opposite device and confirm disappearance; **Sync now** twice more and
  confirm the accepted count is stable. Group: with no Mac group, make a
  selection on the iPhone, **Sync now** on the Mac, `application_policy`
  count 1 and the Mac review names its selection state; the iPhone selection
  count is unchanged after every exchange. Backfill: **Remove workspace** on
  the Mac (the iPhone then removes too), add `design-proof-13.example` on
  the unlinked Mac, link the Mac, then the iPhone, **Sync now** on the
  iPhone: the domain appears there and both devices keep their websites.
  Offline: airplane mode on the iPhone, add `design-proof-14.example`,
  status retryable, reconnect, **Sync now** on both: the Mac's accepted
  count rises by the iPhone's authored bundles (two when the iPhone
  registered a fresh author), the domain appears once, and a repeated
  exchange leaves the count unchanged. Cleanup removes only the fixture
  domains. iPhone evidence is Paused items text and status; the device
  database is not readable.
- Closeout statement for the threat model: the applied base holds only
  canonical domains and the group name already stored by `MODEL-001` and
  `TARGETS-002`; remote input reaches the visible policy only after the
  existing validation and reduction, in one atomic policy-plus-base
  transaction; opaque selections remain outside every synchronized or
  applied model; `SYNC-011` joins the `A-04`, `T-02`, `T-03`, and `T-14`
  owner rows.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path
  scan.

## Decisions or blockers

- `D1` decided (`user-confirmed`, 2026-09-10, after the maintainer's
  review): durable local intent rows plus the applied base, with the
  first-link rule "linking adds this device's websites to the workspace and
  removes nothing local". The base alone cannot recover a lost removal
  (base empty, website added and authored, exchange fails, website removed,
  process closes: the projection still holds it and a base-only merge reads
  it as a remote addition), so the removal must survive as recorded intent.
  Alternatives: overwrite the local shared fields with the projection (no
  base, no intents, but unauthored edits are silently deleted); or apply
  each accepted remote operation incrementally (an earlier-total-order
  operation arriving late yields a local state different from the
  projection, which ADR 0006 forbids).
- `D2` decided (`user-confirmed`, 2026-09-10): domains authored before
  publish (except no-base passes, `D11`), consume, the group name decided after consume and published by a
  second leg, apply last; raw store behind the shared write gate held
  briefly; no replica writes. Alternative: apply inside the writer's
  acceptance transaction, which couples `feature/sync/domain` to the targets
  store and moves a `MODEL-001` compare-and-set into the sync core; ADR 0006
  step 10 does not require it, because "project visible state" there is the
  replica projection and the policy store keeps its own atomic apply.
- `D3` decided (`user-confirmed`, 2026-09-10): a change signal on the local
  policy store, emitted by reconciler applies only, consumed by the two view
  models, no new copy. Alternative: no signal; the converged website appears
  only after navigation or a manual retry.
- `D4` decided (`user-confirmed`, 2026-09-10): learn the workspace's name
  before deciding about the local one; a local default never overrides a
  name the projection holds; a local name is authored only when the
  projection holds none; a remote absence is applied while selections stay;
  existing notices are the only presentation; no removal control.
  Alternative: author the local name before consume, which lets a fresh
  device's "Applications" win over an existing custom name by the format's
  greatest-key rule.
- `D5` decided (`user-confirmed`, 2026-09-10): refuse without truncation,
  keep the base, report action required with a reason-specific message
  under the existing category, "Sync needs attention. Your devices together
  hold more than 1,024 websites. Remove websites on either device, then
  choose Sync now." for the local cap and a second reason for the reducer's
  2,048 capacity outcomes ("Sync needs attention. The shared workspace holds
  more websites than it can keep. Remove websites on any device, then choose
  Sync now."), both with a tested exit once the set is under the limit.
  Alternative: the generic "Check your iCloud account and workspace" copy,
  which is not truthful for this cause.
- `D6` decided (`user-confirmed`, 2026-09-10): no deferral, current-set
  reapply as `SESSION-003` accepted, with the record noting that iOS
  reapplies only on
  poll loss, retry, or relaunch. Alternative: hold converged changes while a
  session is active, adding state and a Mac prompt for no enforcement gain.
- `D7` decided (`user-confirmed`, 2026-09-10): intent rows written in the same
  transaction as every linked save replace the volatile queue and its drop;
  the authoring half drains them in order, deletes them after acceptance,
  and leaves them in place on any failure. This supersedes the `SYNC-010`
  `D1` acceptance of a volatile handoff and closes the PR #43 finding by
  construction. Alternative: keep the queue and recover from the
  local-minus-base difference, which cannot tell a lost removal from a
  remote addition (the `D1` scenario).
- `D8` decided (`user-confirmed`, 2026-09-10, from the maintainer's
  review): one sentence next
  to the existing action, "Linking combines the websites saved on your
  devices. App choices stay on each device.", shown in the onboarding iCloud
  step and the unlinked Session iCloud row; no confirmation. Alternative:
  the `DESIGN.md` rule only, which the person never reads.
- `D9` decided (`user-confirmed`, 2026-09-10, maintainer acceptance of
  the correction plan): website-entry and post-save reads feed its count;
  the visible summary observes the existing `D3` signal with an initial read
  and serialized refreshes, cancelled on exit. Failed reads keep the last
  valid count. Focus stays keyed to `browser.lastReceipt`, with no new UI.
  Alternative: entry-only summary reads, which miss a later completed fetch.
- `D10` decided (`user-confirmed`, 2026-09-10, maintainer acceptance of the correction plan):
  pending-intent overlay during apply as above; the base advances to the
  applied projection. Alternative: deferring the apply, rejected above.
- `D5` corrected (`user-confirmed`, 2026-09-10, maintainer acceptance of the correction plan):
  reducer-capacity reason iff `DOMAIN_CAPACITY` outcomes persist while the
  synchronized set is at 2,048; clears below the cap; copy promises only
  remove, Sync now, re-add-missing. Alternative: persistent
  action-required for any historical outcome, which has no terminating
  recovery inside format-1.
- `D11` decided (`user-confirmed`, 2026-09-10, maintainer acceptance of the correction plan): the
  no-base pass authors domain intents after consume and publishes via the
  second leg. Alternative: pre-consume authoring plus post-consume
  re-authoring, rejected as two operations per extra.
- Physical gate: the maintainer's Mac and iPhone on one account, both linked
  at the start; workspace removal, airplane mode, and any picker selection
  are attended steps agreed for the concrete run; reserved synthetic domains
  only; the union-on-link consequence is accepted before the run.
