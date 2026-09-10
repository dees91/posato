# `SYNC-011`: Converge exact domains and the semantic application policy while opaque selections stay local

- **Review tier:** `high-risk`
- **Tier reason:** This is the first task that moves remote operations into
  the store that enforcement reads (`exact_domain_policy`,
  `application_policy`), so a wrong merge silently blocks or unblocks a
  website on a device that never chose it, and on iOS a mid-session reapply
  pushes the converged set into ManagedSettings without a prompt. It adds a
  persistent store with a migration, extends the threat model's owner rows
  (`T-02`, `T-03`), and its physical proof needs two linked devices, offline
  retry, and workspace removal on the maintainer's account.
- **Dependencies:** completed `ONBOARDING-002` (join and waiting),
  `SYNC-010` (exchange, outbox authoring of exact domains, removal, explicit
  retry opportunities), `SYNC-002` and `SYNC-013` (reducer, projection,
  writer checkpoint), `TARGETS-001` and `MODEL-001` (revisioned exact-domain
  store, canonical values, 1,024-domain cap), `TARGETS-002` (singleton
  application group name), `TARGETS-003` to `TARGETS-005` (device-local
  mappings), `SESSION-002` and `SESSION-003` (frozen start set, reapply of
  the current set)
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
  (one immutable operation per bundle; `domain-present`/`domain-absent`
  reduced from scratch in total order `(HLC, author, operation)`, capacity
  outcomes "a truthful conflict requiring action"; `application-policy-present`
  "Replaces the shared semantic policy; device mappings remain local",
  `application-policy-absent` "never a local mapping"; the singleton policy
  "uses the greatest total-order key"; "All replicas with the same valid
  operation set derive the same synchronized projection"; the 2,048-domain
  projection versus "The current local-only 1,024-domain implementation limit
  is not format-1 product authority");
  `SYNC-010` decisions `D1` ("`SYNC-011` owns the read side, the backfill,
  and reconciling the two stores"), `D2` (removal clears bootstrap and
  replica state, "never `exact_domain_policy`, `application_policy`, or
  `local_session`"), and `D5` (retry is an explicit opportunity);
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  (rejection of tampered, stale, or wrong-context input "without replacing
  the last valid local state");
  [`DESIGN.md`](../../../DESIGN.md) (the seven statuses, "A completed
  attempt makes no claim about receipt on another device"; Paused items
  websites "1,024 unique domains in the policy", "Failed writes keep the
  entire submitted draft"; applications "A successful nonempty selection
  creates the singleton Applications group only when absent", "Clearing
  choices keeps group metadata"; the sentence "Incoming operations remain in
  the replica until the later convergence slices connect them" retired here;
  Session "Restrictions follow your current Paused items");
  `SESSION-003` decision `D2` (reapply keeps applying the current set; the
  summary shows the frozen set);
  [the threat model](../../security/apple-mvp-threat-model.md) (`T-02`,
  `T-03` deterministic full reduction and one atomic apply, `T-05`/`R-01`
  any Apple-trusted installation may author, `T-10` and `A-03` opaque
  selections never synchronized, the persistent-store change rule);
  [the iOS synthesis](../../wiki/topics/ios-enforcement.md) ("An iOS opaque
  selection remains local and is not treated as a portable application
  identifier or automatic macOS match"); and the PR #43 advisory finding on
  `AppleSyncAuthoring.drain` (a transient key read drops the queued diff)

## Outcome

On two linked devices, a website added or removed in Paused items on one
device appears or disappears in Paused items and the Session summary of the
other after that device's next exchange, unchanged; enabling or removing the
application group on one device enables or removes it on the other while
every application selection stays on the device that made it; a website
saved before linking, or whose authoring failed, is authored at the next
reconciliation after linking and reaches the peer; a remote change never
removes a website that only this device holds; the seven statuses keep their
meaning and no sentence claims receipt; and the shared local databases hold
only what the accepted format already carries.

## Boundaries

- Convergence is a reconciliation of two stores, not an overwrite (`D1`).
  The replica projection (`SyncReducer` over the writer checkpoint) is
  authoritative for what the workspace holds; the local policy is
  authoritative for what this device has not yet authored. A persisted base,
  the shared fields the reconciler last applied, makes the three-way merge
  exact: domains and the group name that changed remotely since the base are
  applied locally, and those that changed locally since the base are
  authored as operations. Own operations are accepted into the local replica
  at authoring, so after a reconciliation the local shared fields, the
  projection, and the base agree. Without a base (first link, or after
  removal) every local website and the local group name are authored and
  every projected item is added; linking never removes anything local.
- The reconciler runs inside the existing serialized exchange (`D2`): after
  each exchange pass on an established workspace, whether or not a page
  arrived, and after the outbox drain, under the flight mutex, before the
  pass publishes its status. It writes the local policy through the raw
  store, never through the sync decorator, so an applied projection is never
  re-enqueued as a local diff; it is serialized with user saves through one
  shared write gate and uses the store's compare-and-set revision with one
  re-read retry. It never writes a `sync_*` table, so the writer checkpoint
  stays valid, and it publishes no new status: pending, syncing, completed,
  retryable, and action required keep their `SYNC-010` meaning. A crash
  between acceptance and apply leaves the base behind, and the next
  reconciliation recomputes the same merge, so apply is idempotent.
- The base is one singleton row added by migration `7.sqm`: the applied
  canonical domains and the applied group name, the same data classes the
  policy tables already hold, cleared by **Remove workspace** together with
  the replica (`SYNC-010` `D2` otherwise unchanged: local websites and the
  group stay). Reopening after a restore validates the row at the database
  boundary like the policy tables (`MODEL-001`).
- Remote changes reach the screens without navigation (`D3`): the local
  policy store exposes a change signal, and the Paused items and Session
  view models re-read on it through their existing refresh requests. The
  `TARGETS-001` editor rules apply unchanged: a draft survives, an edit of a
  domain that disappeared resets, and a save that lost the race shows the
  existing "Paused items changed before this update finished" copy and
  recovers on reload. No copy says that a change arrived, from whom, or when.
- The application group converges as the format defines (`D4`): the outbox
  diff covers the group name, including the default created by a first
  selection and by **Enable selected apps**; a remote presence sets the local
  name and leaves selections untouched, so a device without selections shows
  the existing review reason; a remote absence clears the local name and
  keeps every selection, so the existing "saved on this device, but is not
  yet included in the application group" notice and **Enable selected apps**
  offer the recovery. No mapping, token, count, or identifier of a selection
  is ever authored, projected, or diagnosed (`A-03`, `T-10`).
- Capacity is refused, never truncated (`D5`): when the merged local set
  would exceed the 1,024-domain local cap, the reconciler applies no domain
  change, keeps the last valid policy, and publishes action required; the
  local cap and the format cap stay as they are.
- A converged change during an active session behaves like a local edit
  (`D6`): it lands in the local policy at once, the frozen start set is not
  touched, and enforcement follows the current set at its next reapply, as
  `SESSION-003` `D2` accepted and the Session caption already states.
- The outbox keeps the head change until it is authored or fails terminally
  (`D7`), closing the PR #43 advisory finding; the reconciliation also
  recovers a diff lost by process exit. A rejected bundle still pins the
  cursor (no exact refetch), which now delays visible convergence; the limit
  is carried forward and stated in the record.
- Write surface: `shared/src/commonMain/**/feature/sync/bootstrap/**`
  (`AppleSync`, `AppleSyncAuthoring`, `AppleMailboxExchange` returning the
  projection, a new reconciler), `feature/targets/data/**` (store change
  signal, the shared write gate, the base store), `feature/targets/ui/**`
  and `feature/session/ui/**` (re-read on change only), a new
  `SyncAppliedPolicy.sq` plus `migrations/7.sqm`, both DI graphs (the raw
  store reaches `AppleSync`), tests with a two-harness helper, `DESIGN.md`
  (the retired sentence, the linking rule, the cross-device sentence in the
  implementation boundary), the `verify-posato` skill (`features/sync.md`
  sub-feature and steps 4 to 6, `features/application-group.md`, the
  count-only queries for `exact_domain_policy`, `application_policy`, and
  `local_policy_metadata.revision`), `docs/security/apple-mvp-threat-model.md`
  (`SYNC-011` added to the `T-02` and `T-03` owner rows; `A-03` and `T-10`
  unchanged), `docs/wiki/topics/cross-device-synchronization.md`, and the
  wiki log.
- Non-goal: session start, early end, and expiry convergence (`SYNC-012`);
  exact refetch of a rejected bundle; raising the local domain cap; a
  per-domain capacity conflict surface beyond the status; provenance or
  removal of remotely originated websites after **Remove workspace**
  (documented limit); any "received" or "arrived" copy; synchronizing
  application selections, mappings, or counts; the invalid-selection
  lifecycle (stays `open`); portable mode; the validation-layer collapse
  deferred by `SYNC-013`.

## Acceptance

- `AC-01` — With both devices linked, a website added in Paused items on one
  device appears in Paused items and the Session summary of the other after
  the other's next exchange, and a website removed disappears the same way,
  in both directions, with the canonical value unchanged; a repeated exchange
  adds no accepted entry on the Mac. Evidence: the Mac's
  `exact_domain_policy` count and the iPhone's Paused items text.
- `AC-02` — Enabling the application group on one device (a first selection
  or **Enable selected apps**) sets the group on the other, and removing it
  clears the group on the other, while the Mac mapping database count and the
  iPhone selection count are unchanged by any exchange; the Session review
  on a device without selections names the existing missing-selection
  reason, never a blocked or invented item.
- `AC-03` — A website saved while unlinked, and a website whose authoring
  was dropped, are authored at the next reconciliation after linking and
  reach the peer; linking removes no local website and no local group;
  after **Remove workspace** and a fresh link, the same holds again.
- `AC-04` — Over the fakes: the reconciler writes no `sync_*` row, authors
  nothing for an item the projection already holds, applies nothing and
  publishes action required when the merged set would exceed 1,024, keeps
  the last valid policy on any store failure, retries once on a revision
  conflict, and yields the existing conflict outcome to a user save that
  loses the race without dropping its draft; two harnesses exchanging in
  random order converge to equal local policies.
- `AC-05` — The seven statuses and their copy are unchanged; Paused items
  and Session re-read after an apply without navigation; a change arriving
  during an active session leaves the persisted frozen start set unchanged
  and is enforced at the next reapply; no diagnostic, log, or evidence file
  carries a domain value, a selection, or a token.
- `AC-06` — `./gradlew quality` passes with no new suppression; migration
  `7.sqm` is verified with existing rows intact; the existing website and
  first-install fixtures stay green; the skill's sync recipe asserts
  convergence instead of its current "remain device-local" step; the threat
  model owner rows are updated in the same pull request.

## Verification

- `commonTest`: reconciler cases over `FakeBootstrapStore` and a fake policy
  store (no base: backfill and add; remote add and remove; local add and
  remove since the base; both sides changed; group present and absent both
  ways; capacity refusal; revision conflict retry; store failure keeps
  policy; removal clears the base); `AppleSyncAuthoring` head retention on a
  null writer; `SyncReducer` unchanged.
- `commonTest` over two `AppleSyncTestHarness` instances with a shared fake
  mailbox: the inverted `AppleSyncPersistenceTest` case (the replica and the
  local policy both change), reordered and duplicate pages converge to equal
  `exact_domain_policy` and `application_policy` on both, a pre-link domain
  is authored on the first exchange, a repeat exchange adds no accepted
  bundle, a mid-session arrival leaves `local_session.frozen_domains`
  unchanged.
- `SqlDelight` migration verification for `7.sqm` (empty database, database
  with policy rows, database with an established workspace); `jvmTest` and
  `iosTest` over the real graphs (construction touches no provider; the raw
  store and the decorator share one gate).
- Simulator, unattended: `add-website.json`, `remove-website.json`,
  `website-edit.json`, `first-install.json`, `first-install-skip.json`
  unchanged and green.
- Physical, attended, signed Mac and iPhone on one account, both linked,
  baseline counts first: add `design-proof-11.example` on the Mac, **Sync
  now** on the iPhone, `find` it in Paused items; add `design-proof-12.example`
  on the iPhone, **Sync now** on the Mac, count and `select canonical_domain`
  per the websites recipe; remove each on the opposite device and confirm
  disappearance; repeat **Sync now** twice and confirm the accepted count is
  stable. Group: clear the Mac group if present, make a selection on the
  iPhone (group created), **Sync now** on the Mac, `application_policy`
  count 1 and the Mac review names the existing selection state; remove the
  group on the Mac, **Sync now** on the iPhone, its selection count
  unchanged. Backfill: **Remove workspace** on the Mac (the iPhone then
  removes too), add `design-proof-13.example` on the unlinked Mac, link the
  Mac, link the iPhone, the domain appears on the iPhone and the Mac's
  websites are intact. Offline: airplane mode on the iPhone, add
  `design-proof-14.example`, status retryable, reconnect, **Sync now** on
  both, one acceptance on the Mac. Cleanup restores the original websites
  and groups. iPhone evidence is Paused items text and status; the device
  database is not readable.
- Closeout statement for the threat model: the applied base holds only
  canonical domains and the group name already stored by `MODEL-001` and
  `TARGETS-002`; remote input reaches the visible policy only after the
  existing validation, reduction, and one atomic apply; opaque selections
  remain outside every synchronized or applied model; `SYNC-011` joins the
  `T-02` and `T-03` owner rows.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path
  scan.

## Decisions or blockers

- `D1` reconciliation model. Recommended: the three-way merge against a
  persisted applied base described above, with the first-link rule "linking
  adds this device's websites and group to the workspace and removes nothing
  local". Alternatives: overwrite the local shared fields with the projection
  (no base table, but a website saved before linking or whose authoring
  failed is silently deleted, and pre-link removals cannot be told from
  remote removals); or apply each accepted remote operation incrementally to
  the local store (no base table, but an earlier-total-order operation
  arriving late produces a different local state than the projection, which
  ADR 0006 forbids).
- `D2` placement and store ownership. Recommended: the reconciler is part of
  the `AppleSync` exchange pass under the flight mutex, writes through the
  raw local store behind the shared write gate, and never touches replica
  tables. Alternative: apply inside the writer's acceptance transaction,
  which makes the local policy and the replica one transaction but couples
  `feature/sync/domain` to the targets store, extends the checkpoint the
  writer verifies, and moves a `MODEL-001` compare-and-set into the sync
  core; rejected for scope.
- `D3` screen refresh. Recommended: a change signal on the local policy store
  consumed by the two view models, with no new copy. Alternative: no signal;
  the converged website appears only after navigation or a manual retry.
- `D4` application group. Recommended: author the group name in both
  directions including the default, and apply a remote absence while keeping
  selections, with the existing notices as the only presentation.
  Alternative: author only names typed by the person, which no current
  screen offers, so the MVP outcome bullet would stay unmet.
- `D5` capacity. Recommended: refuse and report action required; caps
  unchanged. Alternative: raise the local cap to 2,048, which changes
  `TARGETS-001`, `MODEL-001`, and the Paused items limit copy.
- `D6` active session. Recommended: no deferral, current-set reapply as
  accepted by `SESSION-003`. Alternative: hold converged changes while a
  session is active, adding state and a Mac prompt for no enforcement gain.
- `D7` outbox head retention and the pinned cursor. Recommended: fix the
  drop in this pull request because the file is touched; carry the pin
  forward as a stated limit. Alternative: leave both to a later task.
- Physical gate: the maintainer's Mac and iPhone on one account, both
  linked at the start; workspace removal, airplane mode, and any picker
  selection are attended steps agreed for the concrete run; reserved
  synthetic domains only.
