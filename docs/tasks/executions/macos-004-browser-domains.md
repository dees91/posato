# Execution: `MACOS-004`

- **Brief:** [Deny selected exact domains in Safari and Chrome with safe recovery](../specifications/macos-004-browser-domains.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Grok (implementation, 2026-09-04); Claude (physical verification and corrections, 2026-09-05)
- **Reviewer:** independent plan review 2026-09-04; independent completed-change review 2026-09-04; independent review of the 2026-09-05 corrections (see below)
- **Branch:** `feature/macos-004-browser-domains`
- **Worktree:** `~/Projects/Polyglot/posato-macos-004`
- **Updated:** 2026-09-05

## Plan

1. Independent plan review (done 2026-09-04).
2. Pipe operation 11, daemon rejection, client extension, orchestrator (done).
3. Loopback listener with bounded parsing, exact-host policy, relay and
   `CONNECT` tunnel, `/blocked` route, signal, and chain checks (done).
4. Safari and Chrome same-tab adapters with the usage description (done).
5. Swift and JVM tests, redaction test, environment-gated physical harness
   (done; harness corrected 2026-09-05).
6. Physical checklist with the maintainer, categorical evidence,
   `./gradlew quality`, reviews, wiki closeout (done 2026-09-05).

## High-risk plan review

- **Verdict:** `approved after required corrections`
- **Critical or Required findings:** (R1) HTTP/1.1 connection reuse would forward a later selected-host request on an allowed upstream without re-evaluation; (R2) HTTP 200 blocked page omitted `Cache-Control: no-store` / `Connection: close`; (R3) missing parent-helper capability bit for op 11; (R4) listener failure with a cache-busted selected target was unnamed in the harness.
- **Resolution:** one HTTP request per client connection; `no-store` and `Connection: close` on helper-generated responses; capability bit `4` in the required set `1 | 2 | 4`; automated seam plus a named physical row. No second plan review.

## Result

- Implementation complete on 2026-09-04 with no new suppression.
- The committed harness could not run: the signing verifier derived the
  application bundle from the JVM process, and the helper accepts only a
  parent process signed as the application. The 2026-09-04 physical attempts
  used an uncommitted ad-hoc driver and recorded no rows. Corrected on
  2026-09-05: the verifier derives the bundle from the helper path (the
  production default still comes from the running application), the client
  gains a test-only `launchPrefix`, and the harness compiles a minimal parent
  process signed with the maintainer's local development identity and the
  application identifier, gates Apply on `APPLY_GO`, supports `ABORT`, and
  records categorical failure classes. The gate is not a Gradle input, so the
  task runs with `--rerun`.
- Three defects were found by the physical rows and fixed the same day:
  (1) Safari tabs expose no AppleScript `id`, so the Safari adapter never
  presented; it now addresses the tab by index with a numeric guard on the
  captured references. (2) After a failed post-Apply chain check the helper
  echoed the Restore response as the Apply response; the client treated the
  operation mismatch as an unknown outcome, reconciled to `Success`/`Idle`,
  and the orchestrator reported active enforcement with the baseline already
  restored. The helper now answers Apply with an incompatible or
  recovery-required payload, and the orchestrator reports active only for
  `Success` with phase `Applied`. (3) The effective-chain check now polls up
  to 2 s for configd to republish the proxy settings.

## Completed-change review

- **2026-09-04 (implementation):** `changes-required`, resolved. Two Required
  findings: `configureRequest` retained `pendingUnknownRequest` on failure;
  the enforcer `start()` failure path called raw `restore()`, leaving a
  restore-unknown unreconciled. Both fixed with regression tests.
- **2026-09-05 (corrections, Standard tier):** `approved`, no Critical or
  Required findings. One Recommended finding applied: the post-Apply Restore
  now falls back to a 5 s budget when the Apply deadline is exhausted (runs 3
  to 8 predate this one-line change). Optional findings declined as
  test-scope; the tab-index residual is recorded in the wiki topic.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | 2026-09-05 worktree run after the last correction, BUILD SUCCESSFUL |
| JVM `:desktopApp:test` (`app.posato.desktop.macos.*`) | pass | 28 tests, 0 failures, incl. the two orchestrator phase tests |
| `swift test` (macosHelper) | pass | 120 tests, 0 failures, incl. Safari index, numeric guard, and Apply failure-response tests |
| `git diff --check`, suppression scan, private-data scan | pass | no whitespace errors, no `Suppress` token in changed sources, identities only in ignored `local.properties` |
| Physical harness runs 1-8 | see matrix | `build/verification/macos-004/{run.md,rows.md}` (ignored path); macOS 26.5.2, Safari 26.5.2, Chrome Stable 152 |

Physical matrix (categorical):

| Row | Result |
| --- | --- |
| Safari regular HTTP and HTTPS deny with same-tab page | pass (run 3; run 1 denied without presentation, defect 1) |
| Safari Private Browsing HTTP and HTTPS deny with same-tab page | pass (run 9, after lifting the Screen Time passcode that disables Private Browsing) |
| Chrome regular and Incognito HTTP and HTTPS deny with same-tab page | pass (runs 1 and 3) |
| Control, sibling, subdomain, other-suffix hosts reachable | pass (curl and both browsers) |
| Nonstandard ports rejected, never `DIRECT` | pass |
| IP literals | relayed upstream as unselected hosts; `AC-01` amended (`user-confirmed`, 2026-09-05) |
| Presentation failure keeps denial (no active browser) | pass; the Automation-denied path is covered by the adapter unit test only |
| Conflict preflight (enabled manual HTTP proxy) | pass: refused before Apply as `Incompatible`/`Unavailable`, state restored |
| Listener failure with cache-busted selected target | pass: no bytes during the stall, blocked page after resume, never `DIRECT` |
| Forced helper termination (`kill -9`) | pass: baseline restored within 4 s, explicit clear reached `Idle` |
| Sleep and wake (manual, 54 s) | pass: restored on sleep, no reapply after wake, clear reached `Idle` |
| Reboot while active | pass: baseline after boot, daemon `Idle` |
| Privacy canary | pass: absent from unified log, evidence, and JVM output; root-only durable state relies on the protocol tests |
| Proxy baseline byte-identical after every clear | pass (runs 1, 3, 5, 6, 9) |

## Blockers and accepted risks

- Typing a selected site's IP address bypasses exact-domain denial; accepted
  as a non-resistant residual with the `AC-01` amendment (2026-09-05).
- Safari Private Browsing is unavailable on a Mac with a Screen Time
  passcode; those rows needed the passcode lifted for run 9.
- The maintainer's disabled proxy tuples changed from port 7769 to 53407 on
  all services before run 1 by something outside the daemon (the daemon
  writes only the primary service); every run restored its own baseline
  byte-identically.
- The parent-signed harness process uses the maintainer's development identity
  and is test-scope only; it does not weaken the helper's peer requirement.
- The two-major-version release matrix and the exact-version support claim
  stay with `RELEASE-001`.

## Final

- **Status:** done
- **Outcome:** accepted; physical matrix passed on one development-signed Mac
