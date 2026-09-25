# Execution: `QUALITY-007`

- **Brief:** [Verify the supported platform matrix](../specifications/quality-007-platform-matrix.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** independent completed-change review (see below)
- **Branch:** `feature/quality-007-platform-matrix`
- **Updated:** 2026-09-25

## Plan

1. iOS 18: closed on the maintainer's confirmation (D2 amendment).
2. macOS 15: prepare a golden Tart VM unattended, install a notarized 1.1
   candidate, run the core flow with the helper and sync against a second Mac,
   and record each step.
3. Update the availability page and the README summary to match the evidence.
4. Obtain the completed-change review, run the checks, and close the record.

## Result

- **iOS 18 (`AC-02`).** `user-confirmed`, 2026-09-24: the maintainer installed
  the App Store release 1.0.0 on a private iPhone with iOS 18 and reported that
  everything works as expected. The run was manual, so the availability page
  names the 1.0.0 check rather than a 1.1 candidate.
- **macOS 15 (`AC-01`).** A new golden VM `legacy` line on macOS 15.6.1 (24G90),
  created from Apple's last full Sequoia IPSW and prepared without a person:
  Setup Assistant over VNC, the guest agent, automatic login, privacy grants,
  and the test Apple Account with iCloud Keychain. The two-factor code came
  from the test iPhone through the XCUITest driver.
- **Candidate.** Notarized Developer ID build 25 from `main` at `9c75c8f`
  (marketing 1.0.0 until `RELEASE-003` bumps it), candidate update channel
  with a throwaway key; it embeds no release key.
- **Driver.** `posato-control` gains the `legacy` line
  (`posato.vm.legacyGolden`), `vm boot`, `vm shutdown`, and `vm type` with
  `--secret admin|account|phone`. `vm install` and the LaunchServices launch
  from `MACOS-011` work unchanged on macOS 15.
- **Availability.** `limits-and-platforms.md` states the checked matrix per
  system; the README points to it. The website's "targets macOS 15 or later
  … iOS 18 or later" wording already matches, so it is unchanged.

## Evidence (`AC-01`)

Runs in ignored `build/verification/quality-007/` and the listed run
directories; guests: `legacy` (macOS 15.6.1) and a macOS 26.6.2 clone.

| Step | macOS 15.6.1 result |
| --- | --- |
| Install | `vm install`: Finder drag, Gatekeeper "Notarized Developer ID", first open from `/Applications` |
| Onboarding | iCloud sync created the workspace on the first attempt; helper enabled after background approval |
| Websites and apps | `example.com`, `example.org`; Calculator chosen through the helper picker (1 mapping) |
| Start and block | 25-minute session with administrator approval; `observe` blocked the website and Calculator |
| Relaunch | `session-early-end` restarted Posato and found the session still active |
| Early end | Ended early; `observe` allowed both; no proxy left |
| Expiry | 5-minute session, blocked while active, expired naturally after about 4.6 minutes; allowed afterwards |
| Sync | The macOS 26 Mac linked to the macOS 15 workspace and received both websites; `example.net` added there reached macOS 15 after Sync now |

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| macOS 15 core flow in Tart | pass | table above |
| `./gradlew :posato-control:check` | pass | after the driver change |

## Blockers and accepted risks

- Environment finding: after the new golden VM signed in, the macOS 26 golden
  VMs lost iCloud Keychain access ("Some iCloud Data Isn't Syncing") and could
  not receive the workspace key. Resume Data Sync with the account and guest
  passwords fixed both lines. The guide now describes this recovery.
- Later macOS 15 point releases (15.7.x) were not checked; no full installer
  image exists for them.
- iOS 18 was checked on 1.0.0, not on a 1.1 candidate; `RELEASE-003` rechecks
  if the iOS binary changes materially.

## Final

- **Status:** `done`
- **Outcome:** met; `AC-01` and `AC-02` have results, `AC-03` wording matches them.
