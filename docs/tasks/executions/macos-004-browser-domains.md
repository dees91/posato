# Execution: `MACOS-004`

- **Brief:** [Deny selected exact domains in Safari and Chrome with safe recovery](../specifications/macos-004-browser-domains.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Grok
- **Reviewer:** independent plan review completed 2026-09-04; completed-change review pending
- **Branch:** `feature/macos-004-browser-domains`
- **Worktree:** `~/Projects/Polyglot/posato-macos-004`
- **Updated:** 2026-09-04

## Plan

1. Obtain the independent plan review. The brief's decisions are already
   accepted, including the split proof for the Apply authentication and that
   `commonMain` gains no enforcement contract this wave.
2. Add the helper-only pipe operation that carries the bounded canonical
   domain set (and an optional end time for the page), the daemon-side
   rejection, and the `MacOsHelperClient` extension plus the small
   orchestrator in `desktopApp` (configure, Apply, verified-active, Restore,
   unknown-outcome reconciliation).
3. Implement the loopback listener in the helper: bounded request parsing,
   exact-host policy, HTTP relay and `CONNECT` tunnel over direct upstream
   sockets, the `/blocked` route and page, the host-free signal, and the
   candidate and effective proxy-chain checks around Apply.
4. Implement the Safari and Chrome same-tab adapters over Apple Events with
   URL reduction, membership check, rate limit, and mismatch handling; add
   the usage description to the helper `Info.plist`.
5. Write the Swift and JVM tests from the ADR 0005 evidence list, the
   redaction test, and the environment-gated physical harness.
6. Run the physical checklist with the maintainer (Enable, Apply,
   browser rows, presentation rows, conflict rows, sleep/wake, forced helper
   termination, reboot, canary), record categorical results, then
   `./gradlew quality`, the completed-change review, the wiki topic update,
   and the single wiki-log entry in the closeout commit.

## High-risk plan review

- **Verdict:** `approved after required corrections`
- **Critical or Required findings:** (R1) HTTP/1.1 connection reuse would forward a later selected-host request on an allowed upstream without re-evaluation; (R2) HTTP 200 blocked page omitted `Cache-Control: no-store` / `Connection: close`; (R3) missing parent-helper capability bit for op 11, so a stale helper would fail generically instead of `unavailableOrIncompatible`; (R4) listener-failure with a cache-busted selected target that never falls back `DIRECT` was unnamed in the harness.
- **Resolution:** one HTTP request per client connection with leftover bytes discarded; `no-store` and `Connection: close` on helper-generated responses; capability bit `4` in the required set `1 | 2 | 4`; automated seam plus a named physical harness row. No second plan review.

## Result

- Implementation slices 1–8 complete in the worktree. Slice 9 (automated
  quality) is green after a lint-refactor pass: oversized Swift types were
  split into focused files (`BoundedProxyAuthority`, `BoundedProxyRouter`,
  `BoundedHTTPProxy{,Request,Relay,Lifecycle,Lock}`,
  `BrowserDomainRequestHandler`, `WireDataCursor`), the Kotlin configure codec
  was simplified to named constants with no new suppression, and Swift
  `String(decoding:as:)` uses became failable `String(bytes:encoding:)` so
  malformed input fails closed. Physical rows, wiki closeout, and the PR
  remain pending (maintainer-gated).

## Completed-change review

- **Verdict:** `changes-required`, resolved
- **Critical or Required findings:** two Required findings from the
  independent review (2026-09-04): (1) `configureRequest` retained
  `pendingUnknownRequest` on failure, wedging the client for helper-only op
  11, which `WireReconcilePayload` cannot reconcile; (2) the enforcer
  `start()` failure path called raw `commands.restore()`, leaving a
  restore-unknown unreconciled so the next `clear()` hit the pending
  precondition.
- **Resolution:** (1) dropped the retain line for configure, mirroring the
  `selectApplications` precedent (terminate + return unknown, no pending);
  (2) the failure path now calls `clear()`, which reconciles a
  restore-unknown. Added a regression test (`unknown restore after failed
  apply → reconcile clears the unknown`) and split the fake's
  restore/reconcile queues. Reran `./gradlew quality` green after the last
  correction.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | 2026-09-04 worktree run, BUILD SUCCESSFUL (ktlint, Detekt, `verifyApprovedQualityExceptions`, Swift format/lint/test) |
| JVM `:desktopApp:test --tests 'app.posato.desktop.macos.*'` | pass | 24 tests, 0 failures (`BrowserDomainConfigureProtocolTest` 6, `MacOsBrowserDomainEnforcerTest` 5, `MacOsHelperProtocolTest` 7, redaction/harness/client/selection remainder) |
| `swift test` (macosHelper) | pass | 114 tests, 0 failures, incl. R1 one-request-per-connection and cache-bust rows |
| `git diff --check` | pass | no whitespace errors |
| Suppression scan | pass | no `Suppress` token in new sources; only the two pre-existing allowlisted files carry it |
| Private-data scan | pass | no credentials, personal paths, or keys in added lines; `example.com` appears only in synthetic test fixtures |

## Blockers and accepted risks

- Physical rows need the maintainer at the Mac for System Settings approval,
  administrator authentication, and the Automation prompts; the run mutates
  and must restore the real proxy settings.
- The two-major-version release matrix and the exact-version support claim
  are `RELEASE-001` evidence, not claimed here.

## Final

- **Status:** pending
- **Outcome:** pending
