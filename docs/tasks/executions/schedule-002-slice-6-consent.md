# Execution: `SCHEDULE-002` slice 6

- **Brief:** [Consent to automatic starts on a Mac](../specifications/schedule-002-slice-6-consent.md)
- **Status:** `ready-for-review`
- **Review tier:** `high-risk`
- **Implementer:** Claude, under the maintainer's delegated goal (2026-09-27)
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `feature/schedule-002-consent`, stacked on #99
- **Updated:** 2026-09-27

## Plan

1. **Port.** `MacHelperPort` gains `automaticStartConsent(): Boolean` (default false) and `recordAutomaticStartConsent(given: Boolean)` (default no-op). The desktop implementation reads and writes `automaticStartConsent` through the same native user-defaults bridge as `setupOfferDismissed` (as a `MacAutomaticStartConsent` sub-object, like the login item and the grant); `iOS` keeps the defaults.
2. **State.** `MacHelperSetupUiState` keeps `consent` in Compose state, loaded from the port:
   - `setUp()` records it before the run starts;
   - `allowSchedules()` records it only while setup is complete, without calling the helper;
   - every grant read (quiet read, check, enable, setup outcome, grant switch) that yields anything but `ON` clears it, and so do `remove()` and turning the grant switch off;
   - `MacSetupPresentation` gains `readyForSchedules = setupComplete && consent`.
3. **Schedules.** `ScheduleDeviceReadiness.macSetUp` becomes ready for schedules. When setup is complete but the consent is missing, the card reads "Schedules can't start on this Mac yet." with **Allow schedules to start on this Mac** and a caption that it needs no password; otherwise the existing **Set up this Mac** card stays.
4. **Copy (contract 5).**
   - `onboarding_permission_control`: "Nothing is paused until you start a pause or a schedule you set begins."
   - `mac_standing_grant_supporting`: "An administrator approves this once. Restrictions apply only during a pause you start or a schedule you set."
   - `mac_unified_overview_schedules`: "Once this Mac is set up, your schedules start on their own, including schedules added on your other devices. Setup itself creates no schedule."
5. **Tests first:** consent tests in `MacHelperSetupUiStateTest` style with the existing fakes; a readiness mapping test for the Schedules card.
6. **E2E:** the Tart flow in `AC-02`, with screenshots.

## High-risk plan review

- **Verdict:** `changes-required`, folded here before the code.
- **R1 The port is the source of truth.** The Mac host of slice 4 runs without a window, so it cannot read Compose state. `MacHelperPort.automaticStartConsent` is a `StateFlow<Boolean>` backed by user defaults; the UI state observes it, and one Compose-free function, `macReadyForSchedules(setupComplete, grant, consent)`, serves the Schedules card and the host. The host does its own status and grant read and clears the consent by the same rule.
- **R2 No stale read clears a fresh consent.** A quiet read checks its guard again after the grant read, before it touches the grant or the consent; a test suspends the grant read across a setup run.
- **R3 Clear only on a real answer.** The consent is cleared by a grant read or change that answers `OFF`, `UNKNOWN` or `UNSUPPORTED`, by `NOT_ENABLED` after a status or removal, by the switch turned off, by Remove, and by a setup run that ends unfinished (deferred, cancelled, failed). A grant that was not read (the helper `UNCERTAIN` or waiting for approval) keeps it stored; it does not count, because ready for schedules needs the grant confirmed `ON`.
- **R4 The offer names what it records.** The compact Session offer runs the same setup, so its body now says that schedules then start on their own, including schedules added on other devices.
- **Recommended, taken.** Ready for schedules checks `grant == ON` directly. Before the first read, Schedules on a Mac shows neither a setup card nor Add schedule, and opening Schedules starts a quiet read. The setup caption keeps the sign-in and wake sentence. The stored key names the wording's version (`automaticStartConsentV1`), so a new wording asks again; the native bridge stores booleans, and the product rule now says the same.
- **Deferred to slice 4.** A "Setup required on this Mac" marker on rows while the Mac is not ready for schedules; the card covers it until the host exists.

## Result

- **Delivered.** A Mac keeps the consent to automatic starts as its own value. **Set up Posato** records it wherever the action appears with its wording (onboarding, Session, Schedules, the offer, and This Mac, which now shows the same caption); the Schedules card records it without a password on a Mac whose grant was turned on only through This Mac. Schedules offers **Add schedule** only on a Mac ready for schedules, shows nothing before the first read, and the copy names schedules as well as manual pauses.
- **Deviations.** The consent is a `MacAutomaticStartConsent` sub-object of the port and a `MacConsentKeeper` inside the setup state, to keep both classes within the function limit. The stored form is a boolean under a versioned key rather than an integer; the product rule was amended to say so.
- **Completed-change review:** `changes-required` with two Required findings, both fixed: This Mac's **Set up Posato** now shows the automatic-start caption, and a run cancelled with the window withdraws the consent (a test cancels the scope mid-run). Recommended, taken: the grant switch invalidates an in-flight quiet read; a test for clearing on `NOT_ENABLED`; the storage wording. Optional, kept: the dismissed-offer test as a regression guard.
- **Note for slice 4.** The host must not read the grant while another account has the console: the daemon then reports the grant as absent, which would clear the consent. The window's reads happen only while this account is active.

## Checks

- `./gradlew quality` passed after the last correction (local properties moved aside for the packaging check).
- Mutations killed: the stale-read guard, clearing on an unread grant, the unfinished-run withdrawal (normal and cancelled), the unguarded card action, Remove, the switch turned off, and Set up recording the consent.
- Mac, Tart VM (primary line): onboarding with setup deferred; Schedules showed **Set up this Mac**; setup from Schedules (the driver answered the Login Items toggle and one password dialog) and **Add schedule** appeared directly; the grant switch turned off and on in This Mac (one password dialog, as before) made Schedules show "Schedules can't start on this Mac yet."; **Allow schedules to start on this Mac** restored **Add schedule** with no dialog. The toggle and card steps passed again on the final build.
