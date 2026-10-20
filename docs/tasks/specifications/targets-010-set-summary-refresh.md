# `TARGETS-010`: Pause set summaries refresh at once

- **Review tier:** Standard
- **Tier reason:** The device-local application mapping stores on the Mac
  and iOS start sending the change signal that their readers already
  observe. No persisted field, sync format, privilege, or platform boundary
  changes. Escalate if the Mac's resident process needs a cross-process
  signal.
- **Dependencies:** `ANDROID-001` (merged in #168); release 1.5, wave 4.
  `DOCS-006` and `RELEASE-007` wait for this row.
- **Integration group:** PR-SET-SUMMARY
- **Authority:** [Release roadmap](../release-roadmap.md) revision 23, row
  `TARGETS-010`; idea 37 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md).
- **Record:** [execution record](../executions/targets-010-set-summary-refresh.md)

## Outcome

After a person chooses, removes, or clears a pause set's apps, the Pause
sets list, the Schedules set choices, and a running pause show the new apps
right away, on every platform.

## Boundaries

- `LocalApplicationMappings.invalidations` is the signal. Readers such as
  `PauseSetsViewModel`, the Schedules set source, the schedule hosts' target
  changes, session recompose, and Mac presence already observe it.
  Android and Linux (`CatalogApplicationMappings`) emit after every write.
  The Mac store (`DesktopLocalApplicationMappings`) never emits. iOS
  (`IosLocalApplicationMappings` with the Swift provider) emits only when
  Screen Time authorization changes.
- Emit after every successful choose, remove, clear, and `retainOnly` on
  the Mac and iOS. Never emit for a refused or failed write. Keep the iOS
  authorization signal.
- Check each reader against the new signal, including the iOS schedule
  bindings, which load targets but do not observe it. Check whether Mac
  presence runs in the store's process. If it does not, record the gap and
  its cost rather than add cross-process signalling here.
- Non-goals: the summary's wording, website summaries (they follow policy
  changes already), and Android and Linux stores beyond a check.

## Acceptance

- `AC-01`: Tests written first and failing on `main` show that the Mac and
  iOS stores emit after each successful write and not after a refused one.
- `AC-02`: In a Mac VM, choosing an app and then going back to the Pause sets
  list shows "Apps on this Mac: 1" without a policy change. Removing or
  clearing it shows "none chosen" again.
- `AC-03`: The same on the test iPhone, and a check on the Android phone that
  its list still refreshes.
- `AC-04`: A schedule's set choice and a running pause pick up a changed app
  choice on the Mac without a relaunch.

## Verification

- `desktopApp` tests for `DesktopLocalApplicationMappings`, and iOS tests
  for `IosLocalApplicationMappings` and `ApplicationMappingsStoreTests`.
- `posato-control` runs: `--vm primary`, `-t device` with the application
  picker scenario, and `-t android`, each reading the Pause sets list after
  the change.

## Decisions or blockers

- None. Whether iOS emits from the Swift provider or from the Kotlin wrapper
  is an implementation choice for the plan.
