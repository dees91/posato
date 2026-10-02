# SCHEDULE-006 execution

- **Status:** complete; ready for review

## Failure modes and why they are tested in isolation

- The largest supported table overflows the bound, so the iPhone keeps
  enforcing an older table.
- A failed write leaves the previous version-2 or version-1 table in force
  for the app and the monitor extension.

Driving 1,024 hosts of maximum length through ten sets, or a forced write
failure, is impractical on the test iPhone, so the Swift tests own both.

## Plan

The maintainer accepted this plan on 2026-10-02:

1. Size `ScheduleMonitorFileStore`'s version-2 table bound for the largest
   supported configuration instead of changing the encoding. That
   configuration is 1,024 websites of 253 characters shared by ten sets,
   64 apps, and ten plans, each listing every stoppable date from yesterday
   to 400 days ahead. With app tokens of 1 KiB it encodes to about 450 KB.
   Screen Time tokens are opaque; that they stay well under 1 KiB is
   `inferred`, not measured. The application mappings store accepts larger
   tokens, and 64 tokens averaging more than about 7 KiB would exceed the
   bound. Such a table is now reported rather than kept silently. The bound
   becomes 1 MiB, the same as the application mappings file.
2. A table that cannot be written removes the version-2 and version-1
   tables. `applySchedule()` then reports `platformFailure`, which the
   shared host shows as a retrying scheduled pause in Session. While Posato
   is closed, the monitor extension starts nothing, and an end callback with
   no table clears the store. There is no Kotlin interface or UI change.
   This departs from the brief, which names Session and Schedules:
   Schedules does not change, and a failed write shows as a retrying pause
   in Session only while an occurrence runs. Outside one, nothing shows the
   failure, and while Posato is closed the next starts are skipped until a
   write succeeds. During a running occurrence, shields already applied
   from the previous table stay until its end or the app's release.
3. Write failing-first Swift tests for `AC-01` and `AC-02`, then run one
   schedule on the test iPhone with Posato closed for `AC-03`.

## Result

- `AC-01`: `testTheLargestSupportedConfigurationFitsTheTable` replaces the
  short-host `testTenSetsSharingTheirWebsitesFitTheTable` with the largest
  supported configuration above. It uses hosts of 253 characters, stronger
  than the 221 the brief names. It failed on the base revision because no
  table was written, and it passes after the change.
- `AC-02`: `testATableThatCannotBeWrittenIsReportedInsteadOfKeepingThePreviousOne`
  publishes a table and then one beyond the bound. On the base revision,
  `applySchedule()` returned `applied` and the extension applied the
  previous table's website. After the change it returns `platformFailure`
  and the extension applies nothing.
- `AC-03`: a schedule with a pause set started and ended on the test
  iPhone with Posato closed (see Checks).
- `posato-control flow schedule` read the editor's time labels without
  scrolling, so it failed on the iPhone 13 mini, where the time buttons sit
  below the fold. It now reveals the button before each read.

## Checks

- `./gradlew iosSwiftTest`: red with the two new tests failing for the
  reasons above, then green with 195 tests and 7 skipped.
- `./gradlew qualityLint`: passed.
- `./gradlew quality` on the branch tip, rebased onto `main` 7e67da6:
  passed.
- `AC-03` ran on 2026-10-02 against revision f8a8315 plus this change, on
  the test iPhone (`-t device`, iPhone 13 mini).
  - Setup: Screen Time was already allowed, and the default set held one
    app and no websites. `flow set` created a set with `example.com`, and
    `flow schedule` added a plan for 13:22-13:37 that uses it.
  - Steps: Posato was terminated before the start. Scenarios with
    `launch.skip: true` then opened `http://example.com/` in Safari: after
    the start it showed Website Not Allowed (run `20261002-132333-ca67`),
    and at 13:37:33, after the end, it showed Example Domain (run
    `20261002-133733-9a7e`), with Posato still closed.
  - Evidence is in those run directories under `build/verification/runs/`.
  - The first check (`20261002-132246-d049`) was hidden behind the
    notification permission prompt that the first saved schedule raises.
    Allowing it in the scenario fixed this.
  - Cleanup removed the plan and the two test sets (run
    `20261002-133805-eb14`). Notifications for Posato stay allowed on the
    test iPhone.

## Review

The independent completed-change review found nothing Critical. Its one
Required finding was this record's missing `AC-03` evidence. From
Recommended, this record now states the token-size assumption, the failure
modes, and the departure from the brief.

The review also suggested a test that seeds only a version-1 table before a
failed write. It is not added: the policy forbids writing a test after the
implementation it covers.

## Open risks

- The monitor extension now decodes tables between 256 KiB and 1 MiB.
  Apple documents a memory cap of about 6 MB for monitor extensions
  (`source-claim`). The device run uses a small table, so how the extension
  behaves with a near-maximum table is `open`.
