# ADR 0006: Apple MVP Encrypted Operation and Convergence Contract

## Status

- **Status:** Accepted
- **Date:** 2026-08-28
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

## TARGETS-013 set domain window amendment

Accepted: proposed on 2026-11-01 (`TARGETS-013`), passed an independent
security review after fixes on 2026-11-01, decided on 2026-11-01 by the
owner's agent under the owner's delegation of 2026-10-31, and `user-confirmed` by the owner on 2026-11-03 in the `TARGETS-013` pull request (#221). `TARGETS-013`
implements it. The product rules are in
[pause set rules](../product/pause-sets-decisions.md#website-windows-targets-013).

- **Supersedes.**
  - "kinds 1-21 are mandatory, and unknown kinds 22-127 are rejected"
    (`SCHEDULE-007` amendment) now reads: kinds 1-22 are mandatory, and
    unknown kinds 23-127 are rejected.
  - "A running part never releases an item early" and the local retention
    rule of the `SCHEDULE-003` amendment keep their meaning for the **held**
    set. A window is the one bounded exception for enforcement: it never
    removes an item from a part's retention record or from the host-limit
    count; it only lets the host leave a held domain unblocked while the
    window is open (below).
- **New mandatory kind in format 1.** The payload is canonical as below:
  - `22` `set-domain-window`: 16-byte set identifier (validated like kinds
    12-18), then the kind 2 domain payload, then `u8` period minutes and
    `u8` length minutes. The pair is exactly one of: period 30, 60, or 120
    with length 5, 10, or 15 (a rule); or period 0 with length 0 (**none**,
    which clears the rule). Any other pair, including one zero and one
    non-zero value, is rejected as `invalid-operation`. Total 23 to 273
    bytes. Cross-target golden vectors pin every valid pair, the none pair,
    each refused pair, an invalid set identifier, and trailing bytes;
    reducer vectors pin a rule ordered before and after a removal of the
    same key.
- **Reduction.**
  - The register is `(set, domain)`. Among kind 22 operations for one key,
    the greatest in total order wins; a none winner means no rule.
  - **A removal clears the rule.** The register's winner is ignored when it
    orders before the greatest kind 15 for the same `(set, domain)` (kind 3
    for the first set counts as kind 15 there). So a website that is removed
    and added again projects no rule until a kind 22 that orders after the
    removal, whichever device or release authored the removal; a removal
    from a 1.5 device or an import clears it too. Kind 14 (and 2) resets
    nothing, and kind 22 neither adds nor removes a domain.
  - The projection exposes a rule only for a domain that is present in a
    live set; a rule for an absent domain or a set that is not live projects
    nothing.
  - Kind 22 adds no capacity outcome: the registers that project are bounded
    by the present `(set, domain)` pairs.
- **Writers.**
  - A writer authors a rule only for a domain that is present in a live set
    on the writer, with a pair from the fixed sets.
  - Removing a website needs no kind 22: the reduction above clears its
    rule. `inferred`: a rule authored on another device that orders after the
    removal (concurrently, or by a device that had not received the
    removal) applies to the website once it is added again; it is visible in
    the set's website row.
  - Clearing authors the none pair. A writer never authors a rule for a
    domain it is removing.
  - **First link.** A device's local rules are published at first link only
    for keys whose website is present in the workspace's set and that hold no
    kind 22 in the workspace, after the domain and set operations of the
    link, so the workspace's rules win, as for set names and the default. A
    local-only rule for a key the workspace already rules is dropped here,
    and the workspace's rule applies.
  - **Import.** An import authors its removals before its additions and its
    rules after its additions (kind 15, then kind 14, then kind 22), so an
    imported rule survives its own replace.
- **Evaluation (bounded release).** Let `held(p)` be a running part's
  restriction under the `SCHEDULE-003` amendment (its set's current
  resolution plus its retention record). Let `wall(t)` be the local
  wall-clock reading at instant `t` in the device's current time zone,
  `hour * 60 + minute` (0-1439), never the minutes elapsed since midnight.
  For a held website `w` at instant `t`:
  - a website is its stored exact domain together with its `www` form
    (`TARGETS-006`); all stored entries that match the same website form one
    class;
  - a part `p` that covers `w` is **open for `w`** only when every entry of
    `w`'s class that `p` holds has a projected rule `(period, length)` in
    `p`'s set and `wall(t) mod period < length` for each of them;
  - `w` is **open** when every running part that covers it is open for it.
    A covering part with no rule for an entry of the class (including a part
    that holds it only through its retention record, after it left its set)
    keeps `w` blocked;
  - the host enforces `blockedNow = held - open(t)`. Retention, the
    host-limit count, the reservation of the `SCHEDULE-003` amendment, and
    the items shown as not paused yet are computed on `held` alone; `open`
    never feeds back into them, so an open window frees no capacity.
  - Because every period divides the larger ones and every length is
    shorter than the shortest period, the intersection of the rules of
    several covering parts is the single rule `(max period, min length)`.
    The effective state per domain is therefore at most one rule.
- **Rule arrival in a running part.** A kind 22 applies to running parts
  when it is reduced, like an addition. A rule added or edited during a
  running part opens a domain only inside its window; a none, a removed
  domain, or a removed set closes an open window at the next evaluation,
  which hosts perform at once on a change. A break of the `SCHEDULE-007`
  amendment is independent: while a part is on a break its items are not
  handed to the host, and its windows do not matter; at the break's end the
  host enforces `blockedNow` again.
- **Bound.** The evaluation counts one website on one device as open only
  inside windows of at most 15 minutes in any 30-minute aligned interval of
  that device's wall clock, and only while every covering part has a rule.
  No window extends a part, releases an item after the part ends, or
  releases an item that a part with no rule holds. This bounds when a host
  lets **new** connections through on a device that evaluates on time. It
  does not bound:
  - an iPhone or iPad whose close callback the system delays or never
    delivers: the website stays open until the next extension callback, the
    end of the part, or the app's next launch (the product rules' timing
    tolerance covers delays up to 60 seconds; a missed close is a stated
    leak);
  - connections already open, and addresses cached during a window, on
    Windows, Linux, and Android, which can outlast the window;
  - a removed or narrowed rule on a device that has not received the change
    or cannot apply it yet: a closed iPhone, and a Linux or Windows device
    whose application process is terminated, keep the last rules they
    applied until Posato runs there again or the pause ends.
- **Local clock.** Windows follow each replica's own wall clock and time
  zone, as schedules do, through `wall(t)`. On a skipped local hour the
  windows inside it do not occur and no window moves: the next window is
  the next aligned time that exists on the clock. On a repeated hour a
  window opens once for each wall-clock occurrence, except where a host
  cannot arm an unambiguous deadline for the second occurrence (iPhone and
  iPad, product rules), which then skips it and only blocks more. A clock set
  forward or back moves the windows on that device only; this is not a
  security bound, as for schedules.
- **One calculation, shared vectors.** The window test, the segment
  evaluation, and the next opening are implemented in shared Kotlin, the two
  JVM helpers, the Mac helper, and the iPhone extension. One cross-target
  vector file pins them: instants in named zones (including a daylight
  saving change forward and back), rules, segments, the expected open or
  blocked state, and the next opening. Every implementation reads it in a
  test written failing first.
- **Compatibility.** `inferred` from the `observed` 1.2 and 1.3 behavior
  (`AppleSync.exchangeLegsOrHalt`, `AppleMailboxExchange.acceptPage`): a 1.5
  or 1.6-pre device rejects kind 22 as `invalid-operation` and stops
  receiving once any device in the workspace sets or clears a rule, until it
  updates. It still publishes its local changes before it fetches, and it
  keeps restricting every website with no window. A 1.5 installation that
  opens a database holding kind 22 (a downgrade) fails closed at snapshot
  validation. Release 1.6 accepts this, and its notes say "Update Posato on
  your other devices to keep them in sync."
- **Optional range not used.** Kinds 128-255 must never carry restriction
  semantics. A window changes what a running part enforces, and a replica
  that ignored a none would keep a removed window open, so kind 22 is
  mandatory.
- **Security notes.** Workspace-key holders can already author any operation
  (`R-01`), including kind 11 and session end, which end a part everywhere.
  A rule is a new, smaller capability beside those: on a device that
  evaluates on time, it lets new connections reach one held website for at
  most 15 minutes in any 30 during a running part, only where every covering
  part has a rule, and the person sees the rule in the set's website row and
  in Session. The residuals under Bound (iPhone callbacks, open connections
  and caches, devices that have not applied a removal) can extend that; a
  rule that a key holder authors and then removes keeps working on such a
  device until Posato runs there or the pause ends. Removing the rule closes
  the window on every device that receives and applies it. Retention and the
  host limits are unchanged, so a rule cannot make room for or drop any
  other item. No new
  key, identifier, or diagnostic crosses a boundary.
- **Threat model.** Asset `A-01` includes window rules (which website in
  which set opens, how often, and for how long); they stay out of
  diagnostics like set contents. They travel in the pause set export file
  (product rules, Export and import).

## SCHEDULE-007 schedule break amendment

Accepted: `user-confirmed`: accepted by the maintainer on 2026-10-30
(proposed 2026-10-30, `SCHEDULE-007`; security review passed after fixes on
2026-10-30). `SCHEDULE-007` implements it. The product rules are in
[schedule rules](../product/schedules-decisions.md#breaks-schedule-007).

- **Supersedes.**
  - "kinds 1-19 are mandatory, and unknown kinds 20-127 are rejected"
    (`SCHEDULE-003` amendment) now reads: kinds 1-21 are mandatory, and
    unknown kinds 22-127 are rejected.
  - "Replicas with that projection, the same evaluation instant `t`, and
    the same local terminal-expiry facts" now also requires the same local
    break-over markers and the same local break facts. The synchronized
    projection itself (the break winner per occurrence and the break-end
    set) depends only on the operation set; a device's active break also
    reads its own local break facts (the press instant) and markers.
- **New mandatory kinds in format 1.** The payloads are canonical as below:
  - `20` `schedule-break`: 16-byte schedule identifier (UUIDv4); `u16` year
    2000-2100, `u8` month 1-12, `u8` valid day (the kind 10 date); `i64`
    start epoch milliseconds in the format-1 instant range; `u8` minutes,
    exactly 5, 10, or 15. Total 29 bytes.
  - **Start consistency rule.** The start must not be after the physical
    time of the operation's own HLC. This is the first format-1 rule that
    ties a payload field to the operation header; both the encoder and the
    decoder enforce it, and a decoder rejects a violation as
    `invalid-operation`. It is a deterministic consistency rule, not a
    security bound: a workspace-key holder chooses the HLC. The security
    bound is the evaluation below.
  - **Writers.** Writers author kind 20 only for an occurrence that was
    running on the writer at the press, and reject a date more than 400
    days after the author's local date, as for kind 10. The authored start
    is the earlier of the instant the device recorded at the press and the
    physical time of the HLC value reserved for the operation. The writer
    applies this clamp after reservation and before preparation, so every
    break intent is authored and an honest writer never produces an invalid
    start. A clamped start only moves the window earlier, which releases
    less. The local fact keeps the press instant.
  - `21` `schedule-break-end`: same payload as kind 10. Writers author it
    only for an occurrence whose break they hold and whose break has not
    ended locally.
- **Reduction.**
  - Kinds 20 and 21 are ignored for a removed schedule identifier, like
    kinds 10 and 11.
  - **Break used.** An occurrence `(identifier, date)` has used its break
    when the applicable set, or the replica's local facts, hold any kind 20
    for it, whatever its start, and whether or not the occurrence is
    running.
  - **Winner.** Among kind 20 operations for the same `(identifier, date)`,
    the one with the earliest start is the break; equal starts fall to the
    smallest total order. The projection exposes only the winner.
  - **End.** Kinds 21 build a grow-only set of `(identifier, date)`. Any
    copy, in any order, ends that occurrence's break; it is never undone.
    A kind 21 without a kind 20 has no effect, and a later-arriving kind 20
    for that key is ended on arrival.
  - These are projection facts only. No operation announces an occurrence,
    and a break neither starts, extends, ends, nor shortens one.
- **Evaluation (the clock invariant).** A replica evaluates at its own
  instant `t` against its own occurrence for the same `(identifier, date)`,
  as for skip and end. The break of a running occurrence is **active** only
  when all hold:
  - the occurrence is active under the existing rules at `t` (not skipped,
    not ended, not removed, inside its interval, no local natural end);
  - the break's winner exists and `start <= t < start + minutes`;
  - no kind 21 exists for the key; and
  - no local **break-over marker** exists for the key.

  The replica commits the break-over marker for a key, before it applies
  the restrictions again or exposes the break as over, when an evaluation
  first observes any of: `t >= start + minutes`; a kind 21 for the key; or a
  winner whose `start > t + 2 minutes` (future-start skew bound). The marker
  is local, never synchronized or diagnosed, keyed by `(identifier, date)`
  without a timestamp, and dropped with the observed natural ends of that
  date (after about a day). On iPhone and iPad the monitor extension records
  the marker itself when its break callback re-applies, and the app imports
  it before it evaluates. Consequences:
  - **Replay.** A duplicate or re-delivered kind 20 is a no-op; a kind 20
    whose window has passed when it arrives is never turned into a new
    local window.
  - **Past start.** A late receiver releases only `[t, start + minutes)`.
    If that is empty, it releases nothing.
  - **Future start.** A start up to 2 minutes after the receiver's `t`
    releases once the receiver's clock reaches it, for at most `minutes`. A
    start further ahead never releases on that receiver.
  - **Clock rollback.** A clock set back into a window whose end this device
    already observed does not release again. A clock set forward ends the
    break early, which only restricts more.
  - **Occurrence end.** An occurrence that ends, by plan, skip, **End
    early**, edit, or removal, ends its break with it; no fact for it can
    release anything for another date or a later occurrence.
  - **Bound.** On one device, the instants at which the evaluation counts
    the break as active lie within one window of at most `minutes` (at most
    15), inside that device's own running occurrence. A host that re-applies
    late (a closed iPhone, whose callbacks the platform does not time) can
    stay released after that window, never after the occurrence ends.
  - **Local and synced start.** A device evaluates the winner over its own
    local fact (the press instant) and the synchronized facts together, so
    when its authored start was clamped earlier, the earlier start wins
    there too.
- **Limits during a break.** While a part is on a break, the items it holds
  (its retention record, and on iPhone and iPad the held record the monitor
  wrote) still count toward the host's limits as occupied but are not
  applied. A part that starts or grows during the break, and a manual
  session that becomes active during it, admit only what fits beside them,
  so the returning part always fits.
- **Local persistence.** A writer stores its local break and break-end
  intent in the same local transaction as the operation it authors, as for
  kinds 10 and 11, so a restart before the first sync keeps them. One press
  that breaks several running occurrences (overlap option A) records every
  key's intent in one local transaction; the writer then authors one kind 20
  per key, as **End early** authors one kind 11 per running key. A local
  break fact is never removed by materialization or by the outcome of its
  intent. At first link a writer authors kind 20 and kind 21 for its local
  breaks from yesterday on, as it does for skips and ends, with the start
  clamped as above.
- **Codec and store limits.** The date validation of the codec and the
  writer's 400-day authoring bound stay separate contracts, as for kind 10.
  Cross-target golden vectors pin every invariant (each length, a start
  after the HLC physical time, a start outside the instant range, an
  invalid date, trailing bytes).
- **Occurrence lifetime.** A break changes only which items a host enforces
  while it is active. The occurrence key, pin, retention record, notices,
  observed natural end, and the published running pause are unchanged.
  The local retention record of a part is kept through its break, so items
  retained for the part return at the break's end.
- **Compatibility.** `inferred` from the `observed` 1.2 and 1.3 behavior
  (`AppleSync.exchangeLegsOrHalt`, `AppleMailboxExchange.acceptPage`): a 1.5
  device rejects kinds 20 and 21 as `invalid-operation` and stops receiving
  once any device in the workspace takes a break, until it updates. It
  still publishes its local changes before it fetches, and it keeps
  restricting (it never sees a break). A 1.5 installation that opens a 1.6
  database holding kinds 20 or 21 (a downgrade) fails closed at snapshot
  validation. Release 1.6 accepts this, as release 1.3 did, and its notes
  say "Update Posato on your other devices to keep them in sync."
- **Optional range not used.** Kinds 128-255 must never carry restriction or
  integrity semantics. A break changes what a mandatory occurrence enforces,
  and a replica that dropped kind 21 but kept kind 20 would release past an
  **End break**, so both are mandatory.
- **Security notes.** Workspace-key holders can already author any operation
  (`R-01`), including kind 11, which ends an occurrence everywhere. A break
  releases less than that: one occurrence, one window of at most 15
  minutes (a host that re-applies late, such as a closed iPhone, can stay
  released after it), never outside the occurrence's interval on the
  receiver, and **End break** or
  **End early** restore or end it on any device. No new capability, key, or
  identifier crosses a boundary.
- **Threat model.** Asset `A-01` includes break facts (which occurrence took
  a break, its start and length, and whether it was ended early); they stay
  out of diagnostics like skip and end facts, and out of the pause set
  export file.

## SCHEDULE-003 pause set operations amendment

Accepted: `user-confirmed`: accepted by the maintainer on 2026-09-30
(proposed 2026-09-30, `SCHEDULE-003`; security review passed after fixes on
2026-09-30). `SCHEDULE-004` implements it. The
product rules are in [pause set rules](../product/pause-sets-decisions.md).

- **Supersedes.**
  - "kinds 1-11 are mandatory" and "unknown kinds 12-127" now read: kinds
    1-19 are mandatory, and unknown kinds 20-127 are rejected.
  - "The all-zero singleton application-policy identifier is the only
    exception" now reads: the all-zero value is also the identifier of the
    **first set**, and neither use is interpreted as a UUID.
  - "Policy resolution during a session remains current-state based" now
    reads: a session or schedule occurrence resolves the current state of
    its set, plus the local retention below. No operation embeds a snapshot.
  - The Bounds row "at most 2,048 present domains" now counts unique domains
    across live sets.
  - "A later removal does not retroactively apply an earlier capacity
    outcome" keeps its meaning for `domain-absent`. A `set-remove` removes
    its set's domain operations from the reduction whatever the order, so a
    domain refused earlier in another set can become present.
  - "Replicas with that projection, the same evaluation instant `t`, and the
    same local terminal-expiry facts" now also requires the same local
    retention records.
- **Set identifiers.** A set identifier is a UUIDv4 or the all-zero first
  set. Kinds 2, 3, 6, and 8 refer to the first set. Every set identifier in
  kinds 12-18 is validated as one of the two.
- **New mandatory kinds in format 1.** The payloads are canonical as below:
  - `12` `set-put`: 16-byte set identifier; `u16` name length and a 1-80
    byte name without control characters, normalized to NFC by the writer.
    Decoders validate UTF-8 and control characters, not NFC.
  - `13` `set-remove`: 16-byte set identifier.
  - `14` `set-domain-present`: 16-byte set identifier, then the kind 2
    domain payload.
  - `15` `set-domain-absent`: 16-byte set identifier, then the kind 3
    domain payload.
  - `16` `set-default`: 16-byte set identifier.
  - `17` `schedule-set-put`: the complete kind 8 payload, then a 16-byte set
    identifier.
  - `18` `session-set-start`: the complete kind 6 payload, then a 16-byte
    set identifier.
  - `19` `pause-sets-enabled`: empty payload.

  Writers from release 1.3 author kinds 14, 15, 17, and 18, including for
  the first set, and no longer author kinds 2, 3, 4, 6, and 8. Kinds 4 and
  5 stay valid and reduce as before, but no product state reads the
  application-policy singleton from 1.3 on.
- **Reduction.**
  - **Liveness first.** Set liveness is computed from the complete
    applicable operation set before any domain is reduced. The first set is
    live unless removed and holds one of the 10 slots from the start. A
    UUIDv4 set is live when the set holds a `set-put` for it, no
    `set-remove`, and it is not refused at the cap. Any `set-remove`
    permanently removes its identifier, whatever the order, like
    `schedule-remove`. The greatest total-order `set-put` names a set; the
    first set without a put has no synchronized name.
  - **Set cap.** At most 10 live sets. Puts are processed in total order; a
    put for a new identifier at the cap gets a derived `set-capacity`
    outcome without state change or eviction, recomputed from scratch like
    the schedule cap.
  - **Domains.** Domain operations are keyed by `(set, domain)`; kinds 2
    and 3 use the first set. They are reduced from scratch in total order
    with the existing domain rules, counting only live sets. Operations for
    a set that is not live are left out of the projection and the cap. The
    2,048-domain cap counts unique domains across live sets: a present that
    would add a domain held by no live set at the cap gets
    `domain-capacity`.
  - **Default.** The greatest total-order `set-default` names the default.
    When that set is not live, or no `set-default` exists, the default is
    the first set if live, otherwise the live set whose earliest `set-put`
    is first in total order. With no live set there is no default.
  - **Schedules.** Kinds 8 and 17 are puts in the same register per
    schedule identifier, with the existing removal, cap, and fact rules;
    kind 8 names the first set.
  - **Sessions.** Kinds 6 and 18 are starts grouped by session identifier
    with the existing conflict, end, and expiry rules; kind 6 names the
    first set. Two distinct starts of either kind conflict.
  - **References.** A schedule or session that names a set which is not
    live resolves to no domains and no local applications. The projection
    exposes whether the set is removed, refused at the cap, or unknown.
  - **Kind 19** projects only its presence. Duplicates are harmless; a
    replica authors it at most once per local database.
- **Local retention.** Each replica records, per running part (a session
  identifier or an occurrence key), every domain and local application
  mapping it hands to an enforcement host for that part. An item is
  recorded no later than the request that includes it; recording an item
  that is then not applied is harmless. A host that applies items while
  the app is closed (the iPhone monitor extension) records them itself.
  - A part's restriction is its set's current resolution plus that record,
    so a removal, a set change, or a remote `set-remove` never releases an
    enforced item before the part ends.
  - The current and retained items of all running parts together must fit
    the host's limits (on the Mac the helper's 1,024 domains, on iPhone and
    iPad 50 web domains across all named stores, and each device's
    application limit). An edit that would exceed them applies none of its
    new items, which are shown as not paused yet. A new part applies as
    many of its new items as still fit and shows the rest as not paused
    (`user-confirmed` 2026-09-30, after the iPhone web filter was observed
    to drop entirely past 50 domains). The configuration already applied
    stays in place and is never cleared because of it.
  - Retention only adds items while the part is active and never extends
    it. The record is deleted when the part stops being active for any
    reason under the existing rules, including deletion, refusal, or a
    conflicting start of its schedule or session, an edit that stops the
    occurrence, and workspace removal.
  - Parts running at migration get a record of their set's current
    resolution at that moment (`user-confirmed` 2026-09-30, `SCHEDULE-004`:
    a release 1.2 session's start-time list is not added, accepting that an
    item removed during that session may be released at the upgrade).
  - The record is local like the terminal markers, never synchronized or
    diagnosed.
- **Application choices** stay device-local per set; no operation carries
  them, their count, or their presence.
- **Migration identity.** No migration operation copies data. Existing
  history reduces into the first set, existing schedules and sessions name
  it, and each replica assigns its local application mappings to it. Two
  replicas that migrate concurrently therefore cannot duplicate it. In a
  linked workspace each replica authors one `pause-sets-enabled` when it
  migrates or first links. It never authors `set-put` or `set-default`
  during migration, so a late migration cannot overwrite a name or default
  chosen elsewhere. At first link it authors `set-put` for its sets the
  workspace does not know, and for the first set only while the
  workspace's first set has no name; it authors one `set-default`, after
  those puts, only while the workspace holds no `set-default` at all and
  its own resolved default differs from the workspace's, names a set that
  is not removed or refused in the workspace, and would not be refused at
  the set cap. So a device that renamed
  its first set or chose another default before it linked keeps them
  (`user-confirmed` 2026-10-21, `SYNC-024`; until then the first set's name
  and the default were never authored at first link). Known limits,
  `user-confirmed` 2026-10-21: a replica decides from what has reached it,
  so with two concurrent first links, an iCloud anchor race, or a folder
  that delivers files out of order (`inferred`), both replicas can author a
  name or default and the greater total order (HLC, author, operation)
  wins; and a replica that joins a workspace where nobody named the first
  set or chose a default changes them for every member. Kinds 12 and 16
  are mandatory since 1.3, so a 1.3 or later replica reduces them with no
  new gate. `user-confirmed` known limit (2026-09-30): a replica
  that migrates or links after the first set was removed loses the first
  set's local websites and application choices.
- **Compatibility.** `observed` (release 1.2, `AppleSync.exchangeLegsOrHalt`
  and `AppleMailboxExchange.acceptPage`): an unknown mandatory kind is
  rejected as `invalid-operation` and stops receiving, but a pass still
  publishes local changes before it fetches. Release 1.3 accepts that a
  device still on 1.2 stops receiving once any replica in the workspace
  migrates, until it updates. Its later website edits and session starts
  still arrive and apply to the first set, and are dropped once the first
  set is removed. A 1.2 installation that opens a 1.3 database holding kinds
  12-19 fails closed at snapshot validation; `SCHEDULE-004` decides the
  local-only downgrade, whose database holds none. The optional range is
  not used, because these kinds change restrictions.
- **Security notes.** Workspace-key holders can already author any
  operation (`R-01`); these kinds add no capability beyond domain removal
  and session end, which exist today. Retention bounds what a remote
  removal, set change, or set deletion can release during a running part,
  and its host-limit rule prevents an overflow from clearing enforcement.
  An unknown, refused, or removed set starts nothing new; it cannot widen a
  restriction to items the person did not put in the named set.
- **Threat model.** Asset `A-01` includes set names, identifiers, and the
  default, which stay out of diagnostics; `A-03` mappings are per set.

## SCHEDULE-001 schedule operations amendment

Accepted: `user-confirmed`: accepted by the maintainer on 2026-09-27 (proposed 2026-09-26, `SCHEDULE-001`, delegated night mandate; security
review passed after fixes on 2026-09-27). `SCHEDULE-002` implements it. The product rules are in
[schedule rules](../product/schedules-decisions.md).

- **Supersedes.** "The version-1 operation kinds are closed"
  now reads: kinds 1-11 are mandatory, and kinds 128-255 are optional
  extensions. "Unknown versions and kinds are rejected rather than ignored"
  now applies to unknown versions and to unknown kinds 12-127.
- **New mandatory kinds in format 1.** The payloads are canonical as below:
  - `8` `schedule-put`: 16-byte schedule identifier (UUIDv4); `u16` name
    length and a 1-80 byte name without control characters, normalized to NFC
    by the writer; `u8` weekday mask with bits 0-6 (Monday first), at least
    one set and bit 7 clear; `u16` start minute 0-1439; `u16` end minute
    0-1439, with start different from end and a circular duration
    `(end - start) mod 1440` of at least 15 minutes; `u8` enabled, 0 or 1.
    Decoders validate UTF-8 and control characters, not NFC. Cross-target
    golden vectors pin every invariant.
  - `9` `schedule-remove`: 16-byte schedule identifier.
  - `10` `schedule-skip`: 16-byte schedule identifier; `u16` year 2000-2100;
    `u8` month 1-12; `u8` valid day. Writers reject a date more than 400 days
    after the author's local date.
  - `11` `schedule-occurrence-end`: same payload as kind 10.
- **Reduction.**
  - Any `schedule-remove` permanently removes its identifier, whatever the
    order, like `session-end`.
  - Otherwise the greatest total-order `schedule-put` per identifier is the
    schedule.
  - At most 10 live schedules. A put for a new identifier at the cap gets a
    derived `schedule-capacity` outcome without state change or eviction,
    recomputed from scratch like the domain cap.
  - Kinds 10 and 11 build grow-only sets of `(identifier, date)`. They are
    ignored for a removed identifier and are never undone.
  - Each replica also keeps a local terminal marker per `(identifier, local
    date)` when an occurrence ends or expires, like the session marker. A
    clock rollback or a later edit never recreates an ended occurrence. An
    edit may extend an occurrence that is still running.
- **Optional extension kinds.** Kinds 128-255 are optional:
  - a receiver that does not know one validates the envelope, signature,
    author sequence, and the format-1 byte limits before allocation as usual;
  - it accepts and retains the operation and advances that author's
    sequence;
  - it ignores the operation in projection, and ignores an invalid retained
    optional payload the same way on every replica.

  Optional kinds must never carry restriction or integrity semantics, and
  must never change state owned by a mandatory kind. Kinds 12-127 stay
  mandatory, and an unknown one is rejected as before.
- **Compatibility.** `observed`: a format-1 replica from release 1.1 rejects
  kinds 8-11 as invalid operations, and its mailbox exchange stops with
  action required. Release 1.2 accepts that a device still on 1.1 stops
  syncing once any schedule operation exists, until it updates. A 1.1
  installation that opens a 1.2 database holding kinds 8-11 (a downgrade)
  fails closed at snapshot validation like any unknown kind. The supported
  path is to install 1.2 again. The optional range prevents this for later
  additions.

## Context

ADR 0002 requires one transport-neutral application-encrypted format, signed
immutable operations, deterministic reduction, author sequencing, replay
detection, and preservation of the last valid local state. The accepted Apple
MVP synchronizes exact-domain policy, one semantic application policy, and
active-session intent between one Mac and one iPhone. Apple Account and iCloud
Keychain trust are the complete membership boundary; Posato must not add a QR,
invitation, approval, or independent per-installation revocation ceremony.

The feasibility repository proved that bounded canonical bytes, AES-GCM,
Ed25519, per-author sequences, hybrid logical clocks, deterministic reduction,
and cross-target vectors can satisfy the tested topology. Its format also
included schedules, explicit membership, key agreement, revocation, and key
distribution for a later portable workspace. Its custom wire bytes, split
cryptographic dependencies, deterministic-signature requirement, limits, and
model version are experiment evidence rather than production authority.

This decision freezes only the contract needed by `SYNC-002` and the Apple
bootstrap decision in `SYNC-003`. Database layout, CloudKit records, Keychain
attributes, retry orchestration, UI, and portable-workspace membership or
completeness remain with their named tasks.

## Decision

### One immutable operation per encrypted bundle

Format version 1 stores exactly one operation in each immutable encrypted
bundle. Publishing never edits or re-encrypts an existing bundle. Duplicate
delivery of the same bundle is idempotent. A reused bundle or operation
identifier with different canonical bytes is an integrity failure.

One-operation bundles avoid batching state, partial acceptance, cross-author
bundles, and re-signing. A later format may add batching only after a measured
CloudKit need and a compatibility decision.

The bundle has three byte regions:

1. a fixed 108-byte canonical header used as AES-GCM additional authenticated data;
2. one encrypted canonical operation followed by the 16-byte GCM tag; and
3. one 64-byte Ed25519 signature over a domain-separated concatenation of the
   complete header and ciphertext-with-tag.

The header exposes only:

- four-byte magic and unsigned 16-bit format and algorithm-suite numbers;
- 16-byte bundle, workspace, transport-epoch, and key-epoch identifiers;
- one 32-byte random per-bundle salt; and
- the unsigned 32-bit ciphertext length.

The author identifier, public key, sequence, and operation identifier remain
encrypted. The AES-GCM nonce is implicit and is not transmitted.

The operation identifier stays encrypted. Format 1 requires the bundle and
operation identifiers to be equal UUIDv4 values encoded as 16 raw bytes. This
removes one routing identifier while preserving a stable operation identity.
Workspace, transport-epoch, key-epoch, author, and session identifiers are also
UUIDv4 raw bytes. The all-zero singleton application-policy identifier is the
only exception and is not interpreted as a UUID.
Transport records may expose the bundle identifier and exact byte length, but
must not duplicate protected policy or session fields.

The complete provider-visible format-1 metadata boundary is the format and
suite, per-operation bundle identifier and salt, workspace, transport-epoch,
and key-epoch identifiers, ciphertext size, and transport-level account,
timing, and record-count association. Author identity, public key, sequence,
operation kind, domain, policy, and session data remain encrypted.

### Canonical format

Format 1 uses a small Posato-owned positional binary encoding. It has no maps,
optional fields, reflection, locale-sensitive conversion, platform serializer,
or unknown-field preservation. Integers are unsigned big-endian except signed
epoch milliseconds. Strings are strict UTF-8 with an unsigned 16-bit byte
length. Lists carry an unsigned 16-bit count and are encoded in their specified
canonical order. Boolean values are one byte, `0` or `1`.

Decoding rejects trailing bytes, invalid UTF-8, non-canonical identifiers,
unknown tags or versions, invalid enum values, out-of-order or duplicate list
members, integer overflow, and values outside the limits below. A successful
decoder must re-encode to identical bytes before returning an operation.

The following grammar is normative. `u8`, `u16`, `u32`, and `u64` are unsigned
big-endian integers. `i64` is a big-endian two's-complement signed integer.
`bytes[n]` has exactly `n` bytes. Adjacent fields are concatenated without
padding. Offset zero is the first byte.

| Header offset | Width | Field and required value |
| --- | --- | --- |
| 0 | 4 | ASCII `PSE1` |
| 4 | 2 | envelope format `1` |
| 6 | 2 | algorithm suite `1` |
| 8 | 16 | bundle identifier |
| 24 | 16 | workspace identifier |
| 40 | 16 | transport-epoch identifier |
| 56 | 16 | key-epoch identifier |
| 72 | 32 | random bundle salt |
| 104 | 4 | ciphertext-with-tag length |

The plaintext fields, in exact order, are:

```text
bytes[4]  ASCII "PSO1"
u16       operation format = 1
bytes[16] operation identifier
bytes[16] workspace identifier
bytes[16] transport-epoch identifier
bytes[16] key-epoch identifier
bytes[16] author identifier
bytes[32] Ed25519 public key
u64       author sequence
i64       HLC physical epoch milliseconds
u16       HLC logical counter
u8        operation-kind tag
bytes[n]  kind payload defined below
```

The operation, workspace, and epoch values must equal their header values; the
operation identifier must equal the header bundle identifier.

The AES-GCM additional authenticated data is the complete 108-byte header. The
signature preimage is exactly the UTF-8 bytes of
`app.posato.sync.signature.v1`, then `u32(108)`, the header, then a `u32`
ciphertext-with-tag length and the ciphertext-with-tag. The two length values
must equal the corresponding encoded byte counts.

The version-1 operation kinds are closed:

| Kind | Canonical key and payload | Reduction |
| --- | --- | --- |
| `1` `author-register` | Empty | Registers only the encrypted author and public key at sequence 1. |
| `2` `domain-present` | `u16` domain byte length, then canonical ASCII domain bytes | Marks that exact domain present. |
| `3` `domain-absent` | `u16` domain byte length, then canonical ASCII domain bytes | Marks that exact domain absent. |
| `4` `application-policy-present` | 16-byte singleton policy identifier, `u16` name byte length, then canonical UTF-8 name | Replaces the shared semantic policy; device mappings remain local. |
| `5` `application-policy-absent` | 16-byte singleton policy identifier | Removes the shared semantic policy, never a local mapping. |
| `6` `session-start` | 16-byte session identifier, `i64` start epoch milliseconds, `i64` mandatory-end epoch milliseconds | Proposes one bounded active-session intent. |
| `7` `session-end` | 16-byte session identifier | Permanently ends that session identifier; normal expiry is derived locally. |

The singleton application-policy identifier is the all-zero 16-byte value in
format 1. This encodes the accepted one-policy MVP without inventing a generic
policy collection. Its display name is Unicode NFC, has no control characters,
and is 1 through 80 UTF-8 bytes. `TARGETS-002` owns the user-facing validation
and copy but may not broaden these wire limits without a format decision.

An exact domain is the already accepted lowercase canonical ASCII value from
`ExactDomain`, 3 through 253 bytes. A session starts no earlier than the signed
start instant, ends strictly after it, and lasts at most 24 hours. `SESSION-001`
may select a smaller product maximum. Policy resolution during a session remains
current-state based in format 1: a session operation does not embed or freeze a
policy snapshot. This is the smallest contract consistent with synchronized
active-session intent and avoids a second policy representation.

### Bounds

Implementations reject before allocation beyond these limits:

| Value | Format-1 limit |
| --- | --- |
| Complete bundle | 64 KiB |
| Header | exactly 108 bytes |
| Plaintext operation | 32 KiB |
| Ciphertext and tag | plaintext length plus 16 bytes |
| Operations per bundle | exactly 1 |
| Identifier | exactly 16 bytes |
| Ed25519 public key / signature | RFC 8032 compressed 32-byte public key, not X.509 SubjectPublicKeyInfo / 64 raw signature bytes |
| AES-GCM key / nonce / tag | exactly 32 / 12 / 16 bytes |
| Domain | 3 through 253 ASCII bytes |
| Effective synchronized domain set | at most 2,048 present domains |
| Application-policy name | 1 through 80 UTF-8 bytes after NFC normalization |
| Author sequence | 1 through signed 64-bit maximum |
| HLC physical time and session instants | 0 through 4,102,444,800,000 epoch milliseconds |
| HLC logical counter | 0 through 65,535 |
| Session duration | greater than 0 and at most 24 hours |

The deliberately loose byte ceilings leave room for versioned framing while
remaining far below CloudKit asset-scale data. `SYNC-002` may lower an internal
allocation threshold if every valid format-1 value remains accepted.

### Cryptography and provider ownership

Algorithm suite 1 is HKDF-SHA-256, AES-256-GCM with a 128-bit tag, and
Ed25519:

- each bundle content-encryption key is derived with HKDF-SHA-256 from the
  32-byte workspace key and the header's 32 cryptographically random salt
  bytes. The 32-byte output uses exact info bytes comprising the UTF-8 domain
  `app.posato.sync.bundle-key.v1`, workspace identifier, transport-epoch
  identifier, key-epoch identifier, and bundle identifier in that order;
- the 96-bit nonce is exactly 12 zero bytes and is implicit. Exactly one AES-GCM
  seal is permitted for each derived bundle key. A retry reuses the persisted,
  immutable complete bundle bytes and never seals again. Reusing a bundle
  identifier with different canonical bytes fails closed;
- bundle identifiers are UUIDv4 values and bundle salts are generated with the
  platform CSPRNG before derivation. The salt is authenticated as part of the
  header and is not secret;
- AES-GCM authenticates the complete canonical header as additional data;
- the signature input uses the distinct domain
  `app.posato.sync.signature.v1` and length-prefixes the header and complete
  ciphertext-with-tag;
- signature verification accepts any standards-valid Ed25519 signature and
  never requires byte-identical signature generation; and
- there is no plaintext, unsigned, unauthenticated, or algorithm-fallback path.

Common Kotlin owns HKDF, the bytes, validation order, typed outcomes, and vectors
behind one narrow cryptographic interface. The macOS JVM implementation uses
the standard JCA/JCE `HmacSHA256`, `AES/GCM/NoPadding`, `Ed25519`, and `SecureRandom`
facilities supplied by the selected JDK. The iOS implementation uses CryptoKit
`HKDF`, `AES.GCM`, `Curve25519.Signing`, and system randomness behind a Kotlin/Native or
thin Swift leaf selected by `SYNC-002`. Provider exceptions and native types do
not cross the interface.

No third-party runtime cryptography dependency is accepted by this ADR. Both
selected platform providers expose the required standard algorithms, avoid the
PoC's experimental libsodium wrapper, and remove the false requirement for
deterministic native signatures. If implementation vectors reveal incompatible
standard encodings or provider behavior, `SYNC-002` must stop and propose a
reviewed contract amendment rather than silently add a provider or change wire
bytes.

An Apple authoring Ed25519 private key exists only in process memory for one
local replica-writer incarnation. Posato never persists, synchronizes, exports,
or diagnoses it. `SYNC-002` owns that lifecycle behind the narrow provider
interface; `SYNC-003`, `SYNC-005`, and `SYNC-006` do not create a Keychain item
for it. Managed runtimes and CryptoKit may retain implementation-owned copies;
Posato clears mutable byte arrays it owns where supported and makes no
guaranteed-memory-erasure claim.

### Automatic Apple author registration

Workspace-key possession is the Apple MVP admission proof. Each successful
local replica-writer open starts without an author. Its first local mutation
lazily creates a random author identifier and Ed25519 key pair in memory, then
prepares two immutable bundles: `author-register` at sequence 1 and the
requested business operation at sequence 2. One local transaction accepts and
projects both operations, stores both pending bundles, and advances local HLC
state before either bundle may be published. An open that creates no mutation
creates no author or registration operation.

The active authoring incarnation lives no longer than that writer open. Its
private key and next sequence stay in memory, and the sequence never decreases
from a storage observation during the open. Close, crash, or reopen retires the
incarnation; the next mutation creates a fresh author. Previously committed
pending bundles remain publishable byte-for-byte without the private key.
Transport should publish registration first when possible, but reordered
delivery remains valid and uses the bounded unknown-author staging rule below.

For an unknown author, validation first derives the bundle key, authenticates
and decrypts the bundle, then verifies the signature with the public key in the
canonical plaintext. Complete AEAD authentication proves workspace-key
possession; the signature binds authorship to that encrypted key. A sequence-1
`author-register` registers the author and accepted operation atomically.

A fully authenticated, canonical unknown-author operation with sequence greater
than 1 is deferred, not rejected. The replica retains at most 32 such bundles
per unknown author and 128 total, ordered by sequence and identifier. Staging it
may advance an opaque transport cursor because the exact bytes are durable and
will be reconsidered after registration. A bundle outside those caps produces
a retryable capacity outcome and must not consume transport progress unless the
adapter can request the exact bundle again. An invalid signature, AEAD, context,
key/identifier relation, or canonical operation is rejected and never staged.

For a known author, the stored public key must equal the decrypted key and
verify the signature. A second registration, changed key, changed
identifier, sequence-1 conflict, or author-key mismatch is rejected. Format 1 has no Posato approval,
key-agreement key, membership list, revocation, or key-epoch transition
operation. An Apple-trusted installation with the workspace key can register
authors or decrypt data; that is accepted residual risk `R-01`, not a weakness
that a second ceremony may silently narrow.

### Validation and atomic apply

Remote bytes are untrusted. Validation uses this order and never mutates the
replica before every applicable step succeeds:

1. enforce the complete-bundle limit and parse only the fixed bounded header;
2. reject unknown format or suite and invalid lengths before allocation;
3. confirm expected workspace, transport epoch, and key epoch;
4. reject an already accepted or staged bundle identifier with different bytes;
5. derive the exact bundle content-encryption key from the header context and
   bundle salt;
6. authenticate and decrypt AES-GCM using the header as additional data;
7. strictly decode, validate, and canonicalize the operation and extract its
   author identifier, public key, and sequence;
8. verify all duplicated context, identifier equality, signature, author rule,
   sequence rule, HLC bounds, operation kind, and payload invariants;
9. reduce the complete accepted operation set deterministically; and
10. in one transaction, store the accepted immutable operation, update retained
    author sequence state, project visible state, mark the bundle accepted, and
    only then advance transport progress.

Failure returns one bounded category: `unsupported-version`, `malformed`,
`oversized`, `wrong-context`, `unknown-author`, `invalid-signature`,
`authentication-failed`, `replay-conflict`, `sequence-conflict`,
`invalid-operation`, or `deferred-capacity`. It returns no provider text, protected value, key,
ciphertext, identifier, or plaintext operation. Duplicate identical input is a
successful no-op. Cancellation is propagated and never mapped to a failure.

### Sequence, time, and convergence

Each author allocates a strictly increasing unsigned sequence starting at 1.
Publication retry reuses the same immutable operation, bundle, and sequence. A
sequence already accepted with different bytes is an integrity failure.

`SYNC-002` owns one active authoring incarnation at a time and serializes local
creation and remote acceptance through the same replica state machine. Before a
commit attempt it prepares stable identifiers, canonical bytes, signatures,
and the complete required HLC reservation. The first mutation prepares
registration and business operations together; later mutations prepare one
operation. No prepared bundle is publishable before its local transaction
commits.

Every commit attempt retains its exact prepared identifiers and bytes until the
result is resolved. After an ambiguous result, the store must inspect the exact
transactional footprint. A complete byte-identical operation or first-operation
pair, pending bundles, projection, author state, and HLC state means success. If
complete absence is proven, only the same prepared bytes may be retried.
Partial state, different bytes, or inability to prove presence or absence
returns action-required `local-commit-uncertain`, retires and freezes that
writer, and forbids an automatic retry or replacement incarnation. A
preparation failure after sequence assignment likewise retires the incarnation
unless the exact prepared operation remains available. Crash recovery must
therefore expose either the complete committed transaction or no part of it.

The in-memory sequence never decreases, so storage rollback while the writer
remains open cannot reuse a sequence. Every successful serialized local or
remote transaction atomically advances the writer's in-memory checkpoint to
the exact retained accepted-operation bytes, author sequence state, and durable
HLC/exhaustion state that transaction committed. A greater observed state may
be adopted later only after its exact accepted remote-operation transaction
footprint is verified.

Before preparing any later mutation, the writer compares durable state with
that checkpoint. Any missing, regressed, different, or otherwise unexplained
element returns `local-commit-uncertain`, freezes and retires the writer, and
emits no next-sequence bundle. This prevents a missing unpublished operation
from creating an unfillable local gap and prevents same-open rollback from
lowering HLC or clearing exhaustion without falsely rejecting a legitimate
remote advance. Only a consistent reopen may resume local creation, with a
fresh author.

A restore followed by reopen cannot restore the private identity and uses a
fresh author. The local database still retains accepted per-author sequence
state for replay, equivocation, gap, and reordered-delivery handling; there is
no secure author record or signing-key Keychain item.

Delivery may be duplicated, reordered, or contain gaps. A higher sequence does
not require rejecting the bundle solely because lower sequences are not yet
visible. It is stored as validated but remains unapplied behind the gap; the
last valid projection remains visible. Reduction resumes when missing
operations arrive. This avoids the PoC reducer's fresh-history assumption while
retaining per-author equivocation and gap detection.

Every operation carries a Hybrid Logical Clock value of signed 64-bit epoch
milliseconds plus the bounded logical counter. A fresh replica initializes its
durable HLC state as `active((0, 0))`; afterward the state is either
`active(last)` or terminal `hlc-exhausted(last)`.

The bounded successor of `(physical, logical)` is `(physical, logical + 1)`
when the logical counter is below 65,535, `(physical + 1, 0)` when the logical
counter is 65,535 and physical time is below 4,102,444,800,000, and absent for
the terminal tuple `(4,102,444,800,000, 65,535)`.

A local commit attempt samples the wall clock exactly once before reservation
and validates it. A physical value outside the format-1 range returns
recoverable action-required `clock-out-of-range` without creating an author,
reserving HLC values, sealing bytes, or mutating state. For a valid sample, the
first reserved value is `(wallClock, 0)` when `wallClock > last.physical` and is
the bounded successor of `last` otherwise. Every further value in the same
batch is the bounded successor of the previously reserved value. Preparation,
ambiguous-commit reconciliation, and retry of the same prepared bytes neither
resample the wall clock nor reallocate the batch.

Local creation reserves the complete HLC batch before sealing: two reserved
values for registration plus the first business operation, and one reserved
value for a later operation. If two values remain, the first mutation may use
the penultimate and terminal tuples and commits `hlc-exhausted` atomically with
the two operations. If the complete batch cannot be represented, local creation
commits no operation, records terminal `hlc-exhausted` in an otherwise
state-only transaction, and returns that outcome; it never wraps, clamps,
reuses a tuple, or partially registers an author.

Accepted remote input compares the retained local and remote HLC values in HLC
order and advances to the bounded successor of the greater value; remote
acceptance does not sample the wall clock. If that successor is the terminal
tuple, or no successor exists because the remote or retained value is already
terminal, the same acceptance and projection transaction records
`hlc-exhausted`. Remote operations remain valid, accepted, and projectable
while the local clock is exhausted; only future local operation creation is
refused. Startup treats a retained or accepted terminal HLC as exhausted and
fails closed on inconsistent clock state. Reopen, author rotation, or clock
repair does not clear genuine exhaustion. Recovery requires a separately
accepted workspace-reset or format/epoch migration decision.

Wall-clock skew within the valid range changes conflict winners but cannot
bypass session bounds or extend a mandatory end after it has passed locally.

Each replica stores a terminal local expiry marker keyed by the encrypted
session identifier, without an observed timestamp. When an evaluation first
observes `t >= mandatoryEnd`, it commits that marker before exposing the session
as inactive or clearing enforcement. That session remains inactive after
restart, wall-clock rollback, or discovery of another start for the same
identifier. The marker is local, never synchronized or diagnosed, and is
retained only while its referenced session-start operations remain. `SYNC-002`
exposes the session identity, `SESSION-001` owns the terminal expiry rule, and
`SESSION-002` owns its atomic enforcement integration.

All replicas with the same valid operation set derive the same synchronized
projection. Replicas with that projection, the same evaluation instant `t`,
and the same local terminal-expiry facts derive the same effective state:

- applicable domain operations are reduced from scratch in ascending global
  total order whenever the complete applicable operation set changes. Every
  authenticated operation remains accepted in immutable history; `applied`,
  `no-op`, and `domain-capacity` are derived projection outcomes, not permanent
  validation decisions. A `domain-present` for an existing domain is a no-op;
  for an absent domain below the 2,048-domain cap it adds the domain; at the cap
  it has a `domain-capacity` outcome without state change or eviction. A
  `domain-absent` removes a present domain and is otherwise a no-op. Arrival of
  an earlier-total-order operation may therefore reclassify later outcomes. A
  later removal does not retroactively apply an earlier capacity outcome, while
  a distinct still-later presence may use the freed capacity. The current
  projection exposes every capacity outcome as a truthful conflict requiring
  action;
- the singleton application policy uses the greatest total-order key for its
  singleton identifier;
- session starts are grouped by their encrypted session identifier. Exactly one
  distinct immutable start is eligible; two or more produce a derived
  `session-conflict` outcome for every start in the group and no start for that
  identifier is eligible. This quarantine is recomputed from the complete
  applicable set and is independent of delivery order. Any valid matching
  `session-end`, regardless of relative order, permanently ends that identifier;
- among eligible session starts whose signed start is not after `t`, the greatest
  total-order start is the sole current candidate; future starts do not suppress
  it, while an ended or expired current candidate yields no active session and
  never falls back to an older candidate;
- a current candidate is active only when it has neither a matching end nor a
  terminal marker and `start <= t < mandatoryEnd`; and
- total order is `(HLC physical, HLC logical, author identifier, operation
  identifier)`, comparing raw bytes unsigned where applicable.

Unknown versions and kinds are rejected rather than ignored, so a format-1
replica never projects partial semantics. Format or algorithm changes require a
new suite or format, cross-target vectors, an explicit compatibility decision,
and a migration path. The Apple MVP does not negotiate the highest common
version and does not downgrade.

## Consequences

- `SYNC-002` can implement one small closed model with deterministic JVM/iOS
  golden vectors and tamper, wrong-context, replay, gap, skew, registration,
  and convergence tests.
- CloudKit remains a mailbox. It cannot choose winners, supply identity, or
  replace the local accepted-operation history.
- Apple author registration remains automatic and truthfully no stronger than
  workspace-key possession, while independent author signatures still detect
  forged operations from a transport-only attacker.
- Ephemeral authoring incarnations prevent a restored local store from reusing
  an author sequence without requiring online reconciliation or a persistent
  signing identity. They create more encrypted author registrations over time;
  format 1 adds no speculative author cap, garbage collection, or compaction.
- A terminal HLC preserves inbound convergence but permanently blocks local
  authoring for that workspace until a separately accepted recovery decision.
- Per-operation bundles trade additional record overhead for simpler atomicity,
  signing, retries, deduplication, and review. Batching is deferred until there
  is evidence that it is needed.
- The custom positional codec is intentionally small but security-sensitive;
  it requires cross-target golden vectors and strict decoder tests rather than
  a general serialization framework.
- Portable membership, revocation, key wrapping, rollback completeness, and
  migration require a later format decision and are not hidden extensions of
  format 1.
- The current local-only 1,024-domain implementation limit is not format-1
  product authority. `SYNC-002`, as the first synchronized model consumer, must
  support the 2,048-domain projection without changing that unrelated code in
  this documentation-only task.

## Alternatives considered

### Adopt the PoC format and dependencies

Rejected. They contain portable membership and schedule scope, an experimental
libsodium wrapper, a deterministic-signature test constraint, and prototype
identifiers and limits that the MVP does not need.

### Add a KMP cryptography dependency now

Rejected for format 1. Current cryptography-kotlin exposes the required
algorithms and providers, but the Apple MVP already has two supported platform
providers and no non-Apple runtime consumer. A new runtime dependency and its
provider-selection surface have no present consumer that system APIs cannot
serve. Reconsider only with a concrete target or interoperability failure.

### Use HMAC instead of device signatures

Rejected. A shared MAC authenticates workspace-key possession but cannot bind
an operation to one authoring incarnation, so it cannot satisfy the accepted
signed-author and per-author sequence contract.

### Persist the Apple signing identity or reconcile before every mutation

Rejected. A `ThisDeviceOnly` Keychain item may be restored to the same device
that created its backup, so it cannot distinguish coordinated rollback of the
database and secure record. A mandatory CloudKit reconciliation would make
every writer open depend on service availability and contradict local-first
offline mutation. A Secure Enclave anchor, hardware counter, new wire operation,
or server generation has no minimum format-1 consumer and does not replace the
required atomic local state machine.

### Require an existing Posato installation to approve an author

Rejected by ADR 0002. It would recreate the second membership ceremony removed
from the Apple MVP.

### Batch operations or encode generic extensible maps

Rejected until measured need. Both add partial-acceptance, canonicalization,
unknown-field, and compatibility behavior without improving the two-device MVP
contract.

## Required implementation evidence

Before `SYNC-002` is complete:

- byte-identical canonical header, operation, AAD, and ciphertext vectors pass
  on the macOS JVM and physical iPhone provider implementations;
- the 108-byte header is byte-identical, author metadata is absent from it,
  HKDF vectors bind every specified context field, immutable retry reuses exact
  bytes, and no bundle key is used for more than one seal;
- an idle writer creates no author; its first mutation atomically commits
  registration at sequence 1 and the requested operation at sequence 2; later
  mutations increase the same in-memory sequence, while close, crash, reopen,
  coordinated restore, and same-open storage rollback cannot reuse an accepted
  `(author, sequence)` pair;
- after a committed but unpublished operation, same-open loss or regression of
  its accepted bytes, author sequence, HLC, or exhaustion state freezes the
  writer with no next bundle; consistent reopen uses a fresh author and never
  leaves a newly emitted operation behind an unfillable local sequence gap;
- nonterminal remote acceptance between local mutations atomically advances the
  writer checkpoint and permits the next correct HLC successor; unexplained HLC
  change freezes the writer, while terminal remote acceptance advances the
  checkpoint to exhaustion and refuses later local creation;
- commit-before, commit-after, ambiguous-result, partial-state, preparation
  failure, and crash boundaries either recover the exact immutable prepared
  operation or pair, prove absence and retry those same bytes, or fail closed
  with `local-commit-uncertain` without duplicating the business mutation;
- reordered business-before-registration delivery stages within the existing
  caps and converges after registration, while pending bundles from a retired
  incarnation retry without the private key;
- HLC tests cover the fresh `(0, 0)` baseline; future, equal, and regressed wall
  samples; one wall sample for the registration-plus-business batch; logical
  carry into the next physical millisecond; two, one, and zero remaining
  values; local and remote advancement to the terminal tuple; direct remote
  terminal input; restart and a new author incarnation; inbound projection
  after exhaustion; no wrap or clamp; and recoverable `clock-out-of-range`
  after wall-clock correction;
- the 2,048th and 2,049th domain, an earlier removal arriving after a later
  capacity outcome, a later removal followed by a distinct presence, and
  randomized delivery permutations produce equal state and derived audit;
- expiry commits a marker for the session identifier before exposing
  inactivity; restart, wall-clock rollback, or a competing start cannot
  reactivate it. Competing starts in either delivery order produce the same
  quarantined projection and derived audit;
- signatures verify across both targets, without requiring identical signature
  bytes;
- nonce, key, size, version, unknown-kind, truncation, trailing-byte,
  non-canonical, wrong-context, wrong-key, tamper, signature, author-registration,
  replay, sequence-conflict, gap, duplicate, reordering, and clock-skew cases
  preserve the last valid state;
- randomized delivery orders and duplicate sets converge to equal projected
  state and an equal canonical digest;
- a local mutation and pending immutable bundle commit atomically, and remote
  acceptance, projection, deduplication, and progress commit atomically; and
- dependency and source scans prove there is no plaintext fallback, fixed
  production key or nonce, provider exception leakage, secret logging, or
  unreviewed runtime cryptography dependency.

Physical CloudKit and Keychain bootstrap evidence belongs to `SYNC-003` through
`SYNC-009`; ongoing mailbox publication, consumption, and account-isolation
evidence additionally belongs to `SYNC-010`.

## Evidence and sources

- `observed`: `.research/blocker` revision
  `bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c`, especially
  `docs/experiments/apple-sync-poc-wire-and-crypto.md`, `BundleCodec.kt`,
  `Reducer.kt`, and the cross-target golden vectors, proves only the bounded PoC
  behavior summarized above.
- [Apple CryptoKit](https://developer.apple.com/documentation/cryptokit) exposes
  AES-GCM, Ed25519, and system key generation and recommends the higher-level
  framework over lower-level interfaces.
- [Apple AES.GCM.Nonce](https://developer.apple.com/documentation/cryptokit/aes/gcm/nonce)
  requires nonce uniqueness for encryption calls.
- [Apple Keychain accessibility](https://developer.apple.com/documentation/security/restricting-keychain-item-accessibility)
  states that `ThisDeviceOnly` items may be restored to the same device that
  created the backup, so that attribute is not a coordinated-rollback anchor.
- [Java `Cipher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
  requires AES/GCM support and distinct IVs; [Java `SecureRandom`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html)
  supplies cryptographically strong random bytes.
- [NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-38d.pdf)
  requires IV uniqueness for distinct authenticated-encryption inputs under a
  given key; suite 1 derives a new key for each single-seal bundle.
- [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html) defines HKDF and the
  context-binding `info` input used by suite 1.
- [RFC 8032](https://www.rfc-editor.org/rfc/rfc8032.html) defines Ed25519.
- [cryptography-kotlin 0.6.0](https://github.com/whyoleg/cryptography-kotlin/releases/tag/0.6.0)
  was reviewed as the narrow third-party alternative: Apache-2.0, current at
  review time, and compatible with JDK and CryptoKit providers, but unnecessary
  for the two selected Apple MVP targets.
