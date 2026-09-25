# Execution: `MACOS-011`

- **Brief:** [Accepted macOS update path](../specifications/macos-011-updates.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude; Codex prepared the handoff
- **Reviewer:** independent agents (plan, Stage 1, and each Stage 2 part); maintainer on PR #76
- **Branch:** `feature/macos-011-stage-2` (Stage 1 merged from `feature/macos-011-updates` in PR #76)
- **Updated:** 2026-09-25 (Stage 2 closeout)

## Plan

1. Prove admission, cancellation, and restart safety on notarized candidates
   before integration (Stage 1, merged in PR #76). The mechanism is now the
   ADR 0004 amendment: a persisted gate closed atomically with the
   no-active-session check, a gated enforcement port, confirmed cleanup before
   the first Install reply, an instance lock, and reopening only on exact
   installer-absence, bundle-identity, and service revalidation evidence.
2. Stage 2: consent and settings (D3), copy, request privacy (D1, D2), key
   custody (D4), the release process, the remaining matrix, and closeout.
3. Run every Stage 2 check unattended in Tart clones (step 8, `user-confirmed`
   2026-09-25), after a VM gate that repeated the Stage 1 A-to-B.

## High-risk plan review

- **Verdict:** `approved` for the Stage 1 proof, its mechanism (after five
  Required and three follow-up findings), the Stage 2 plan (after six Required
  findings), and the VM amendment (after four Required findings). Git history
  of this file keeps the full reviewed plans.

## Result

- **Driver.** `vm install --dmg` installs a candidate as a person would:
  quarantine, Finder drag over VNC, Gatekeeper, and one registered bundle.
  Candidates launch through LaunchServices, and `launch --adopt` tracks a
  relaunched build. `observed`: a `ditto` or scripted copy is translocated,
  and a launch owned by the guest agent made macOS deny App Management to the
  agent, after which Sparkle asked for an administrator on every update.
- **Consent and settings.** After first-run setup, a native sheet asks once.
  The answer lives only in Sparkle's defaults. About Posato shows an Updates
  section (toggle and manual check) once the updater started, and the
  application menu keeps its check. The copy comes from `strings.xml`, with a
  distinct second-instance refusal. `DESIGN.md` records the placement. A
  blocking `runModal` prompt starved the startup mappings load, so both alerts
  are sheets.
- **Requests.** Fixed `User-Agent: PosatoUpdater` and `Accept-Language: en`;
  cookie policy Never, with a purge at start and after each cycle.
- **Release process.** Every Developer ID build names a channel. A release
  embeds only the stable feed and the tracked key. A candidate needs its own
  `appcast-test.xml` feed. `generateMacOsUpdateFeed` signs with the Keychain
  key and validates the feed and archive against the app inside the DMG, with
  in-code RFC 8032 contracts. `apple-provisioning.md` documents the
  `RELEASE-003` duties.
- **Key custody (D4).** Maintainer key `posato-release`, encrypted backup;
  embedded in candidate builds 17, 18, 19, 23, 24. The first stable build
  must be **25 or higher**. Other test keys: builds 8–16 (Keychain
  `posato-update-test`) and 21–22 (a throwaway file key, matrix only); build
  20 was never built.
- **Authorities.** ADR 0003/0004 amendments applied, ADR 0008 status updated,
  `TB-08`/`T-13` and provider metadata classified; the public wording stays
  staged for `RELEASE-003` and matches the measurements.

## Evidence

Stage 1 ran on a physical Mac (builds 8–14, loopback feed, throwaway key,
before the VM rule). Stage 2 used notarized candidates in fresh Tart clones,
with evidence in ignored `build/verification/macos-011-stage2/`.

| ADR 0008 row / AC | Evidence | Source |
| --- | --- | --- |
| Consent, opt-out, manual check (AC-02) | 0 requests and 0 GitHub DNS queries before consent (two launches, stale `SULastCheckTime`) and after opt-out; manual checks from About and the menu; plain-text notes | Stage 2 `github-measurement`, `consent-dev` |
| Request fields, cookies, redirects (AC-02) | Every hop to `github.com` and `release-assets.githubusercontent.com` sends no `Cookie`, even the redirect right after `latest/download` sets `_octo`; the app cookie store stays empty; public test prerelease never became latest and was deleted | Stage 2 `github-measurement` |
| Invalid or unavailable update (AC-03) | Unsigned, wrong-key, and tampered feeds; equal and older builds; macOS 99.0; offline; HTTP 404 never close the gate. Missing, wrong, and tampered archive signatures close it before the download; the installer rejects the archive, and the gate reopens after the cycle ends | Stage 2 `matrix` runs 1 and 4 |
| Active session and concurrent intent | Refusal sheet during an enforced session, no download or installer; Start, Resume, sync, retry, and helper-recreation paths are covered by synthetic tests | Stage 2 `matrix` run 5; Stage 1 tests |
| Cleanup failure | Foreign `127.0.0.1` proxy and a booted-out daemon both refuse without touching settings and keep the gate closed until the evidence holds; lost Restore reply and unknown ownership are covered by synthetic tests | Stage 2 `matrix` run 6; Stage 1 tests |
| Cancellation and termination | Cancel and crash during download, Cmd-Q and crash at Ready to Install (window close quits the same way), and another instance; an error after installer launch (Stage 2 archive rows); the system-domain installer reopens only after its job is gone; canceled or retried termination is a limit below | Stage 1 physical Mac; Stage 2 runs 4 and 7 |
| Complete A-to-B (AC-01, AC-04) | VM gate 15→16; enabled service 21→22 with post-update blocking (`observe`) and restoration; final 23→24 via real GitHub keeps websites, app choice, the established iCloud workspace, and the consent answer, and leaves a removed helper disabled | Stage 2 `vm-gate`, run 5, `final-a-to-b` |
| Packaging and release (AC-03, AC-05) | Notarized DMGs; a signed feed and SHA256SUMS produced and validated together on the candidate channel; the release channel refuses candidates and is covered by in-code contracts until its first real run | `generateMacOsUpdateFeed` on builds 18 and 24 |

## Completed-change review

- **Stage 1:** two maintainer Required races (poll during a retried admission,
  stale admission after a newer cycle), fixed with regression tests.
- **Stage 2 parts:** each approved by an independent agent after corrections:

| Part | Required findings | Corrections |
| --- | --- | --- |
| Driver (`ffbd312`) | 1: an empty LaunchServices dump passed the check | fail closed (`ab295c9`) |
| Consent and requests (`2aa04cc`) | none | sheet abort, `available` flag, and UTF-16 copy taken |
| Release feed (`4745403`) | 2: non-numeric previous build; configuration-cache capture | fixed before push |

- **Whole change:** approved after one Required correction (Stage 1 evidence
  provenance) and the recommended AC-04, release-path, and wording fixes.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | after each Stage 2 part and at closeout |
| Stage 2 VM runs (gate, matrix, measurement, final A-to-B) | pass | `build/verification/macos-011-stage2/` |

## Blockers and accepted risks

- The resumable installing stage (a download pending across launches) was not
  reachable, so the Stage 1 `.skip` limit stands. Sparkle's Ready to Install
  window cannot be dismissed.
- Sparkle holds a background-found update until the app is next activated;
  the first iCloud sync in a fresh guest failed once, then succeeded.
- Open observation: "Applications unavailable" (mappings load failure) at 2 of
  about 10 VM launches, recovered on reload, not reproduced on demand.
- Sparkle's canceled or retried termination (an app refusing to quit) was not
  exercised; Posato never blocks termination.
- `RELEASE-003` owns the version bump, the first real release-channel feed
  (pass `-PposatoMacOsPreviousBuildNumber=24` so validation enforces 25 or
  higher), and the public wording.

## Final

- **Status:** `done`
- **Outcome:** met; AC-01 to AC-05 have evidence above, with the listed limits.
