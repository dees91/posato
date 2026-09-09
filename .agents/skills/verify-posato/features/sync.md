# Sync with iCloud

Before linking, the Session screen offers **Sync with iCloud**. Launch and
foreground do not touch CloudKit or synchronizable Keychain on an unlinked
device. A linked device offers **Sync now** and **Remove workspace**. Exchange
opportunities also follow launch, foreground, and local exact-domain commits;
one active exchange can retain at most one queued opportunity.

## Sub-features

- `sync-consent-gate`: unlinked launch remains local-only until explicit consent.
- `sync-establish` / `sync-join`: establish or adopt the same private workspace,
  including simultaneous opt-in and waiting for a synchronizable key.
- `sync-exchange`: publish immutable pending bundles and accept remote bundles
  into the replica in both directions. Incoming operations do not yet change
  local websites, applications, or sessions (`SYNC-011` / `SYNC-012`).
- `sync-retry`: offline or uncertain outcomes preserve pending bytes; retry on
  **Sync now** or a later foreground. No timer or delivery guarantee exists.
- `sync-account-gate`: sign-out before an attempt stops both exchange legs;
  returning to the original account allows the same pending work to retry.
- `sync-remove`: confirmation deletes the workspace and undelivered changes,
  retains local websites, and permits a new consent without a console reset.
  Other devices must remove their old workspace before joining the new one.
- `sync-adopt-relaunch`: a linked relaunch attempts exchange using the adopted
  key; completion therefore exercises the key read and writer open.

## Driving it with posato-control

Read `tools/posato-control/README.md` for commands and scenario syntax. Use a
signed Mac package and a connected unlocked development-signed iPhone on the
same maintainer-owned iCloud account. `doctor` must confirm the signing identity,
profile, companion, development team, and device. The Simulator proves only
local behavior and truthful degradation without its own iCloud account.

1. Build both applications; use `build -t device --driver`, then `install`.
   `quality` restages an ad-hoc Mac package, so run `build -t desktop` after it.
   Launch preserving existing state, never with `--fresh`.
2. Capture `snapshot --format text` and a screenshot of Session. For an
   unlinked device expect **Sync with iCloud** and a local-only description;
   `select count(*) from sync_bootstrap_state` is zero on desktop.
3. Press **Sync with iCloud** once. Expect completion or **Waiting for the
   workspace key from your other device.** Retry consent on the waiting side
   after delivery. Completion is **This device completed its latest sync
   attempt. Other devices may still need to sync.**
4. Through Paused items, add a reserved synthetic domain on one device. Return
   to Session and wait for completion. Press **Sync now** on the other device.
   Repeat in the reverse direction. Local websites must remain device-local.
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
   confirm. Expect local-only with local websites retained. The peer's next
   attempt must require action. Establish a new workspace on the removing
   device, then remove the old workspace on the peer and link again. The
   peer's old anchor must never delete the newly established zone. Repeat with
   the devices reversed. Remove only the synthetic website fixtures afterward.
8. Exercise simultaneous opt-in from a state cleared through the removal UI.
   Coordinate presses against one absolute wall-clock time, allowing for iOS
   driver startup. Retry the losing side after key delivery; a completed
   exchange after relaunch proves that its adopted key opens the writer.
   XCTest log output may be buffered: calibrate startup before scheduling the
   presses, then verify their overlap from the recorded timestamps afterward.

Useful Mac queries (`db query -t desktop --sql "…"`):

```sql
select count(*) from sync_bootstrap_state;
select candidate_workspace_id is null, established_workspace_id is null
from sync_bootstrap_state;
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
zone permits remaining cleanup; a different anchor permits only the old known
key and local cleanup, with action required. An absent or unreadable anchor
stops cleanup. Never replace this flow with database deletion, app uninstall,
or console deletion: those bypass the behavior being verified.
