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
2. Pipe operation 11, daemon rejection, client extension, orchestrator,
   loopback listener, chain checks, and both same-tab adapters (done).
3. Swift and JVM tests and the gated physical harness (done; harness
   corrected 2026-09-05).
4. Physical checklist, categorical evidence, `./gradlew quality`, reviews,
   and wiki closeout (done 2026-09-05).

## High-risk plan review

- **Verdict:** `approved after required corrections`
- **Required findings, all resolved:** (R1) HTTP/1.1 connection reuse could forward a later selected-host request, now one request per connection; (R2) the blocked page lacked `no-store` / `Connection: close`; (R3) capability bit `4` for op 11; (R4) the listener-failure row with a cache-busted target was unnamed. No second plan review.

## Result

- Implementation complete on 2026-09-04 with no new suppression.
- The committed harness could not run: the signing verifier derived the
  application bundle from the JVM process, and the helper accepts only a
  parent signed as the application; the 2026-09-04 attempts used an ad-hoc
  driver and recorded no rows. Corrected 2026-09-05: the verifier derives the
  bundle from the helper path (production still starts from the running
  application), the client gains a test-only `launchPrefix`, and the harness
  compiles a minimal parent process signed with the maintainer's development
  identity, gates Apply on `APPLY_GO`, supports `ABORT`, and records failure
  classes. The gate is not a Gradle input, so the task runs with `--rerun`.
- Three defects were found by the physical rows and fixed the same day:
  (1) Safari tabs expose no AppleScript `id`, so the adapter never presented;
  it now addresses the tab by index with a numeric guard. (2) After a failed
  post-Apply chain check the helper echoed the Restore response as the Apply
  response, the client reconciled the mismatch to `Success`/`Idle`, and the
  orchestrator reported active enforcement with the baseline restored; the
  helper now answers Apply with an incompatible or recovery-required payload
  and the orchestrator requires `Success` with phase `Applied`. (3) The
  effective-chain check polls up to 2 s for configd to republish settings.

## Completed-change review

- **2026-09-04 (implementation):** two Required findings (`configureRequest`
  retained `pendingUnknownRequest` on failure; the enforcer failure path left
  a restore-unknown unreconciled), both fixed with regression tests.
- **2026-09-05 (corrections, Standard tier):** `approved`; one Recommended
  finding applied (5 s fallback Restore budget), Optional findings declined
  as test-scope, tab-index residual recorded in the wiki topic.
- **2026-09-05 (maintainer-review corrections, Standard tier):** two Required
  findings, both resolved: presentation now runs outside the lock that
  `stop()` takes, and run 12 reverified the helper after the last change.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | 2026-09-05 worktree run after the last correction, BUILD SUCCESSFUL |
| JVM `:desktopApp:test` (`app.posato.desktop.macos.*`) | pass | 28 tests, 0 failures, incl. the two orchestrator phase tests |
| `swift test` (macosHelper) | pass | 124 tests, 0 failures, incl. Safari index, Apply failure-response, configure and Apply refusal, tunnel relay, and header-deadline tests |
| `git diff --check`, suppression scan, private-data scan | pass | no whitespace errors, no `Suppress` token in changed sources, identities only in ignored `local.properties` |
| Physical harness runs 1-12 | see matrix | `build/verification/macos-004/{run.md,rows.md}` (ignored path); macOS 26.5.2, Safari 26.5.2, Chrome Stable 152; run 12 followed the last code change |

Physical matrix (categorical):

| Row | Result |
| --- | --- |
| Safari regular HTTP and HTTPS deny with same-tab page | pass (runs 3 and 11; run 1 denied without presentation, defect 1) |
| Safari Private Browsing HTTP and HTTPS deny with same-tab page | pass (run 9, after lifting the Screen Time passcode that disables Private Browsing) |
| Chrome regular and Incognito HTTP and HTTPS deny with same-tab page | pass (runs 1, 3, and 11) |
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
| iCloud Private Relay, VPN, relay, or filter | not staged: the reader refuses `utun`/`ipsec`/`ppp` primary interfaces and unsatisfied paths; Private Relay has no public detection (open limit below) |
| Proxy baseline byte-identical after every clear | pass (runs 1, 3, 5, 6, 9, 11); run 10 timed out at the prompt and left no mutation |

## Maintainer review of 5347daf (2026-09-05)

| Class | Findings | Decision |
| --- | --- | --- |
| P1 fail-safe ordering | configure while an Apply is owned stopped the live listener before the replacement could start | accepted: configure is refused as `invalidInput` while an Apply is owned, and a replacement starts before the previous session stops |
| P2 misordered Apply | Apply without a configured session authenticated and mutated first | accepted: refused before authorization |
| P2 presentation on the proxy queue | Apple Events and the TCC prompt stalled relayed traffic and `stop()` | accepted: presentation runs on its own serial queue and outside the lock that `stop()` takes |
| P2 duplication, dead code | second cursor type, unused `expectsContinue` | accepted: one `WireDataCursor`, dead code removed |
| P2 Private Relay | `AC-03` names Private Relay, nothing detects it | accepted as a recorded limit (below), no detection claim |
| P2 tests, record | no tunnel or header-deadline test; run range mismatch | accepted: tunnel relay and header-deadline tests added, record corrected |

## Blockers and accepted risks

- `open`: iCloud Private Relay has no public detection API, so activation is
  not refused while it is on; ADR 0005 keeps undetectable overrides as a
  non-resistant residual and `RELEASE-001` owns the disclosure.

- Typing a selected site's IP address bypasses exact-domain denial; accepted
  as a non-resistant residual with the `AC-01` amendment (2026-09-05).
- Safari Private Browsing is unavailable on a Mac with a Screen Time
  passcode; those rows needed the passcode lifted for run 9.
- The maintainer's disabled proxy tuples changed from port 7769 to 53407 on
  all services before run 1 outside the daemon; every run restored its own baseline.
- The parent-signed harness process is test-scope only and does not weaken
  the helper's peer requirement; the release matrix stays with `RELEASE-001`.

## Final

- **Status:** done
- **Outcome:** accepted; physical matrix passed on one development-signed Mac
