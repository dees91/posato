# Sync with iCloud

Expand the **iCloud** row on Session to reach **Sync with iCloud** before
linking, or **Sync now** and **Remove workspace** afterwards. The collapsed
row always names the current state; opening it never starts an exchange.
Launch and foreground do not touch CloudKit or synchronizable Keychain
before consent. After an explicit fresh join finds a missing workspace key,
foreground and **Check again** may continue that same account/workspace in
process memory. Missing keys cause reads only; verified adoption commits the
established row and permits ordinary exchange. Restart forgets this waiting
attempt and offers explicit **Sync with iCloud** again. A linked device offers
**Sync now** and **Remove workspace**. Exchange
opportunities also follow launch, foreground, and local policy/session commits;
one active exchange can retain at most one queued opportunity.

## Sub-features

- `sync-consent-gate`: unlinked launch remains local-only until explicit
  consent.
- `sync-establish` / `sync-join`: establish or adopt the same private workspace,
  including simultaneous opt-in and waiting for a synchronizable key.
- `sync-exchange`: publish immutable pending bundles and accept remote bundles
  into the replica in both directions. A completed exchange converges exact
  domains and the application group name into the visible policies while
  application selections stay local. Bounded session starts and deliberate ends
  also converge; each receiver applies its own authorized selections. A Mac
  that needs administrator confirmation offers Resume restrictions. The timer
  alone does not prove that restrictions were applied.
- `sync-retry`: offline or uncertain outcomes preserve pending bytes; retry on
  **Sync now** or a later foreground. No delivery-time guarantee exists.
- `sync-session-time`: while the host can run, accepted future starts become
  eligible without another exchange or a Session-screen subscription. Relaunch
  restores the accepted projection locally after the existing workspace/key
  gates. Observed expiry is terminal through restart and clock rollback.
- `sync-account-gate`: sign-out before an attempt stops both exchange legs;
  returning to the original account allows the same pending work to retry.
- `sync-remove`: confirmation deletes the workspace and undelivered changes,
  retains local websites, and permits a new consent without a console reset.
  Other devices must remove their old workspace before joining the new one.
  A device that removed a workspace refuses to re-adopt that same identifier
  while CloudKit still surfaces it; **Sync with iCloud** reports retryable
  through the existing unlinked copy until the old anchor is gone or a
  different workspace is visible.
- `sync-adopt-relaunch`: a linked relaunch attempts exchange using the adopted
  key; completion therefore exercises the key read and writer open.

## Session convergence verification

Use the [session recipe](sessions.md) on the already linked pair. Start on
Mac, let iPhone receive through foreground or Sync now, and capture actual
restriction evidence on the receiver. End from iPhone and exchange on Mac;
verify both cleanup outcomes. Repeat with iPhone starting and Mac receiving,
including the attended Resume confirmation. Repeat after relaunch and with
one peer offline through start/end, then reconnect: an ended or expired
session must never be applied. Test ordinary expiry independently on each
peer; a short foreground test does not replace an iPhone suspended interval
meeting the scheduler minimum. Keep physical run directories in the execution
record and distinguish timer, action-required, and actual enforcement proof.

## Driving it with posato-control

Read `tools/posato-control/README.md` for commands and scenario syntax. Use a
signed Mac package and a connected unlocked development-signed iPhone on the
same maintainer-owned iCloud account. `doctor` must confirm the signing
identity,
profile, companion, development team, and device. The Simulator proves only
local behavior and truthful degradation without its own iCloud account.

The rows start collapsed when Session is recreated, including after a relaunch
or a return from Paused items. Expand iCloud again before addressing its
buttons. Snapshot-verify the expansion first: tapping an already-expanded
header collapses it again. On a compact iPhone (13 mini and similar), the
expanded actions sit below the tab bar. `waitFor` does not scroll. After
expanding, run an unscoped `scrollTo` for the action label, then tap. Compose
drops labels of clipped controls until they are on screen; the iOS driver
swipes the screen when the only scroll view is the full-window wrapper, so
do not pin `within` to the Session heading.

```json
{"action":"scrollTo","query":{"text":"Remove workspace","role":"button"},"timeoutSeconds":20}
```

Replace the action label with **Sync with iCloud**, **Sync now**, or
**Check again** as appropriate.

1. Build both applications; use `build -t device --driver`, then `install`.
   `quality` restages an ad-hoc Mac package, so run `build -t desktop` after it.
   Launch preserving existing state, never with `--fresh`.
2. Capture `snapshot --format text` and a screenshot of Session. Expand the
   iCloud row with `tap --text-contains "iCloud," --role button`. For an
   unlinked device expect **Sync with iCloud** and a local-only description;
   `select count(*) from sync_bootstrap_state` is zero on desktop.
3. Press **Sync with iCloud** once. Expect completion or **Waiting for the
   workspace key from your other device.** A pending fresh join offers
   **Check again**, and returning to the app also offers a bounded check;
   neither creates a workspace. A changed account/workspace ends that
   attempt and restores the explicit consent action. Completion is **This device
   completed its latest sync
   attempt. Other devices may still need to sync.**
4. Through Paused items, add a reserved synthetic domain on one device. Return
   to Session and wait for completion. Press **Sync now** on the other device.
   Repeat in the reverse direction. Both devices must converge to the union
   of their websites: after each **Sync now**, the peer shows the new domain
   in Paused items and the read-only queries below agree on both devices.
   On Mac, compare counts before and after each step using the read-only
   queries below; registrations are also bundles, so establish the baseline
   before adding the domain. A repeat exchange must not add accepted entries.
5. For offline retry, ask the maintainer to disconnect the authoring target
   (airplane mode with Wi-Fi off on iPhone). Add a domain; the save stays local
   while sync reports retryable. Reconnect, press **Sync now**, and verify one
   acceptance on the peer. Never alter system connectivity without coordination.
6. For the account gate, arrange pending work offline, then ask the maintainer
   to sign out before the next attempt. Expect action required, unchanged
   pending/accepted counts and cursor state. Restore the original account and
   retry, then verify one peer acceptance. Run in both directions. Device DB
   access is unavailable; Mac reception and device status are the evidence.
7. Press **Remove workspace**, inspect the destructive confirmation, and
   confirm. Expect local-only with local websites retained: removal deletes
   the anchor and every bundle record inside the exact zone and keeps the
   zone, which the app never deletes. A peer whose next attempt finds the
   zone but no anchor must require action; its own removal then skips record
   deletion and clears only its key and local state. CloudKit may keep
   surfacing the old records for several minutes. Until the timed proof
   below passed 2026-09-12 in both shapes and directions, so the settle rule
   is retired and re-establish timing is unconstrained: on every matrix run
   the record purge was visible in under a minute and the press completed
   with a fresh workspace. If **Sync with iCloud** is pressed while that old
   anchor is still visible, this device must not report completed and must
   not gain an established row (`sync_bootstrap_state` stays zero;
   `sync_removed_workspace` is at least one and does not increase during
   the refused presses). Retry the same action until the old anchor is gone,
   then a fresh establish succeeds and survives a further wait. A peer still
   linked to the old workspace must remove it before joining the new one.
   Repeat with the devices reversed. Remove only the synthetic website
   fixtures afterward.
8. For a timed re-link after removal, start with both devices linked. On the
   Mac, remove the workspace, add `design-proof-16.example` while unlinked,
   then press **Sync with iCloud** within three minutes while the iPhone is
   still linked. Per press record wall-clock time, device, status, whether
   the peer was still linked, press count, and the Mac counts
   (`sync_bootstrap_state`, `sync_removed_workspace`, `sync_accepted_bundle`).
   The iPhone must show action required; remove there, link, and confirm the
   website arrives. Then run the both-removed shape: remove on both devices,
   re-establish, and confirm the website arrives. In both shapes, after a
   wait of at least ten minutes, both devices still complete an exchange
   with the Mac holding one established row, and the Mac's accepted count
   stays stable across repeat exchanges. Then run the whole matrix with the
   devices reversed. A run that loses the website or the row is a failure,
   not a fallback. Cleanup removes only the fixture domain; both devices
   end linked to the newest workspace. Observed 2026-09-12 (workspaces B,
   C, D): a still-linked peer either requires action on a different anchor
   or waits for the workspace key until iCloud Keychain delivers it (up to
   about 20 minutes on the first run); both converge through remove there,
   link, and confirm the website arrives.
9. Exercise simultaneous opt-in from a state cleared through the removal UI.
   Coordinate presses against one absolute wall-clock time, allowing for iOS
   driver startup. Retry the losing side after key delivery; a completed
   exchange after relaunch proves that its adopted key opens the writer.
   XCTest log output may be buffered: calibrate startup before scheduling the
   presses, then verify their overlap from the recorded timestamps afterward.

## Second-install join and delayed key

Run attended on the signed Mac and connected iPhone under the same account,
with no active session. Agree on exact workspace cleanup before the run.
Opening the iCloud row is never consent. Use `tap --text "Check again"
--role button` only after inspecting the row; other setup sections can offer
that label too, so scope the selector to the iCloud content when necessary.

- Direction A: remove the iPhone's old workspace first if linked; reset and
  reinstall its app. Remove the Mac's old workspace, then explicitly link
  the Mac. Immediately press Sync with iCloud in the iPhone's fresh flow.
- Direction B: remove the Mac's old workspace, then the iPhone's old workspace;
  link the iPhone fresh. Back up/reset the Mac through the driver and perform
  its fresh-install join. Never restore an old bootstrap row as proof of a join.
- Record immediate linking or an observed wait. While actually waiting,
  inspect Continue primary, Check again secondary, the summary's separate
  saved-choice/sync facts, and the collapsed Session iCloud status. Continue
  can advance setup while a key check runs. Inspect manual progress/completion,
  including a repeated wait; automatic unchanged waits must not chatter.
- Use manual Check again and a real leave/return foreground opportunity when
  a wait window permits them. On Mac, verify whether actual focus changes
  deliver the existing resume signal; otherwise record manual checking as
  the verified route. Capture screenshots and accessibility snapshots.
- On the joining Mac, `select count(*) from sync_bootstrap_state` is zero
  while waiting and one after adoption. Device DB access is unavailable:
  iPhone status and Mac receipt are the physical evidence there. Check that
  account/anchor loss, candidate recovery, and storage/cancellation outcomes
  are covered by unit tests; do not manufacture them in a live database.
- Complete permissions through their existing routes and select local apps
  in Paused items. After exchange settles, compare Mac pending/accepted counts
  before and after selection, with no domain edit. Synced items remain
  invisible until SYNC-011; picker selections remain local.
- If delivery is immediate, label physical waiting **not observed**. Unit
  tests prove the new delayed-key path; earlier PoC runs do not substitute
  for its physical evidence. Keychain off/on on the joining iPhone is optional
  and requires attended agreement. It does not guarantee a missing app key
  or approval prompt. Record actual messages; do not sign out, reset encrypted
  data, or delete passwords for this recipe.
- Restore backed-up Mac local data afterwards. An old backup can reference a
  removed workspace: restore usability through explicit Remove workspace and
  fresh consent, coordinating the peer's old-workspace removal first.

Useful Mac queries (`db query -t desktop --sql "…"`):

```sql
select count(*) from sync_bootstrap_state;
select candidate_workspace_id is null, established_workspace_id is null
from sync_bootstrap_state;
select count(*) from sync_removed_workspace;
select count(*) from sync_pending_bundle;
select count(*) from sync_accepted_bundle;
select count(*) from sync_staged_bundle;
select transport_progress is null from sync_replica_state;
```

Never select key material, bindings, anchors, identifiers, bundle bytes, or
cursor bytes. Keep screenshots, logs, snapshots, and run output under ignored
`build/verification/runs/`. Record categorical outcomes and counts only. The
phone database cannot be inspected, so do not claim its row counts from UI.

## Evidence limits and failure behavior

Status has seven categories: local-only, pending, syncing, completed local
attempt, retryable, waiting for key, and action required. The caption contains
no synchronization time, value count, history, or promise of peer receipt.
A screenshot of a button alone does not prove the outcome.

An expired CloudKit token restarts fetch from the first page once per attempt,
retaining accepted state. A second expiry is retryable. Adapter and common
tests own this injected boundary. Both adapters lack exact refetch; a rejected
bundle pins the cursor and requires action. Mid-operation account changes are
also an adapter-test boundary, not a physically observable driver row.

Local exact-domain save returns after committing locally and handing off its
ordered changes; it does not wait for network exchange. A linked device opens
its writer on demand. Authoring failure preserves the local save and reports
action required; key waiting or unavailability retains its specific status.
The handoff is volatile until outbox authoring, so process exit can lose an
unauthored diff while retaining local websites. Changes made before linking or
whose authoring fails are not backfilled. Tests own these interim limits and
the exclusion of old queued changes after removal and re-linking.

Removal stops at an uncertain account, zone, key, or storage outcome. A missing
zone permits remaining cleanup; a missing anchor permits only the own key
and local cleanup ending local-only; a different anchor permits only the old
known key and local cleanup, with action required. An unreadable anchor
stops cleanup. Never replace this flow with database deletion, app uninstall,
or console deletion: those bypass the behavior being verified.
