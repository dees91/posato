# Execution: `IOS-007`

- **Brief:** [ios-007-http-after-pause.md](../specifications/ios-007-http-after-pause.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude Code agent
- **Reviewer:** independent Claude Code agent
- **Branch:** `task/ios-007-http-after-session`
- **Updated:** 2026-10-07

## Plan

1. Build a feedback loop that observes Safari on the test iPhone without the
   UI driver: `xcrun devicectl` opens a page served from the host on the
   local network, and the host sees both the page request and, once Safari
   has parsed the page, a request for the image it embeds. The driver only
   starts and ends the pause.
2. Reproduce on `main`, then cut the steps to the smallest set that still
   hangs, and compare the ways a pause ends.
3. Test whether a different clear in Posato avoids the hang with a temporary
   build switch, then fix it or record the limit for the maintainer to accept.

## Result

- `AC-01`: reproduced on `9c37ef7` (the brief's base) without the driver.
  Steps: start a saved-items pause with `session-start-saved-items.json`, wait
  45 s, end it with `session-early-end.json`, bring Calculator forward with
  `devicectl`, then open the host page in Safari with
  `devicectl device process launch --payload-url`. The first page stayed
  black for 30 s, usually without its request reaching the host; a second
  page loaded in under 1 s. Each run used the same steps.
- Reducing the reproduction showed what matters. A running Safari matters:
  one started fresh loaded at once. The web filter matters: a pause that
  shielded only Calculator did not hang. So does the pause length: about 10 s
  did not hang, 45 s or more did. The scheme does not: `https://example.com`
  hung as the first page too. Neither does where Safari opens: over Posato or
  over Calculator. Waiting 120 s after the end did not help.
- End paths: **End early**, natural expiry of a five-minute pause with Posato
  in front, and expiry of a 25-minute pause with Posato sent to the
  background and killed all hung the first page. In that last run the system
  relaunched Posato in the background before the end, so which process
  cleared the store is not proven; Posato was not in front.
- A temporary build (removed, never committed) changed only the clear. With
  the filter and shields set to nil, with `clearAllSettings()` 5 s later,
  and without `clearAllSettings()` at all, the first page hung each time.
  Without the web filter, it did not.
- `AC-02`: recorded in [iOS enforcement](../../wiki/topics/ios-enforcement.md#safari-after-a-website-pause-ends-ios-007)
  as an `inferred` iOS behavior. The hillclimb conclusions that only plain
  HTTP hangs and that Posato in front avoids it are marked `superseded`.
- `AC-04`: the maintainer accepted the iOS limit on 2026-10-07 and chose to
  end Safari before the scenario opens the page. The
  [limits page](../../product/limits-and-platforms.md#limits) and the
  verification feature map state it. `posato-control` gains the iOS-only
  `terminateApp` action, and `observe-unblocked-ios.json` uses it before
  `openURL`. `AC-03` does not apply.
- `AC-05`: unchanged product code; blocking during a pause and the lifted
  shields after it were observed in the verification runs below.

## Completed-change review

- **Verdict:** approved (independent agent, 2026-10-07)
- **Critical or Required findings:** none
- **Resolution:** both Recommended findings applied: the limits page names
  the trigger (Safari already open), calls the cause apparent, and promises
  only that the next page loads; idea 31 explains its fresh-URL run.
- **Advisory findings:** an optional guard against `terminateApp` naming the
  app under test or SpringBoard was not added.

## Verification

Target: the test iPhone (`-t device`), app and driver built from this branch.
Evidence: ignored `build/verification/ios-007/` (probe scripts, the host
request log, screenshots) and `build/verification/runs/`.

| Check run | Result | Evidence |
| --- | --- | --- |
| Driver-free probe after **End early** (45 s pause or longer), six runs including the HTTPS, over-Posato, and 120 s wait variants | first page hung in each, second loaded | `ios-007/requests.jsonl`, `shots/` |
| Same after natural expiry, Posato in front | first hung, second 0.8 s | run `20261007-130719-b180` |
| Same after a 25-minute pause, Posato in the background | first hung, second 0.8 s | start run `20261007-134141-485f`; shield lifted by 14:06, polled with `ios-007/repro-extension.sh` |
| Old `observe-unblocked-ios.json` after a 60 s pause | fail, `WAIT_TIMEOUT` on `site-loaded` | run `20261007-143901-3cce` |
| New `observe-unblocked-ios.json` after a 60 s pause, three runs in a row | pass | runs `20261007-144051-4d56`, `-144223-dc14`, `-144358-7759` |
| `observe-blocking-ios.json` during the third pause | pass | run `20261007-144342-edff` |
| `./gradlew qualityLint` | pass | |
| `./gradlew quality` | pass on the second run (195 Swift tests, 7 skipped) | the first run failed only because the Simulator test runner hung before connecting, the flake the hillclimb recorded; no Swift test source changed |

## Blockers and accepted risks

- Accepted iOS limit: the first page an already running Safari opens after a
  website pause ends can hang until it is opened again.
- Once during the runs, iOS asked for the passcode to enable UI automation for
  XCTest, and every driver run failed with `DEVICE_AUTOMATION_LOCKED` until
  the maintainer entered it.

## Final

- **Status:** `done`
- **Outcome:** met through `AC-04`: an accepted iOS limit, stated on the
  limits page, with the verification scenario adjusted and passing.
