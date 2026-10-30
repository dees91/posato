# `SCHEDULE-007`: Take a break in a running scheduled pause

- **Review tier:** `high-risk`
- **Tier reason:** A break adds a synchronized fact to the format-1 operation vocabulary (ADR 0006) and makes every platform release and then re-apply restrictions without a person's action, which touches the automatic Apply path of the macOS helper (ADR 0004, ADR 0009) and the Linux and Windows services.
- **Dependencies:** none (R1.6/W1). `DOCS-007` and `RELEASE-010` depend on this row.
- **Integration group:** `PR-SCHEDULE-BREAK`
- **Authority:** [Release roadmap](../release-roadmap.md) row `SCHEDULE-007`; idea 45 in [open questions](../../wiki/topics/mvp-open-questions.md) (`user-confirmed` 2026-10-20); [schedule rules](../../product/schedules-decisions.md); [DESIGN.md](../../../DESIGN.md). The maintainer named the row on 2026-10-30.

## Outcome

During a running scheduled occurrence, a person can take a break: restrictions are released on every device of the workspace for the chosen number of minutes and return automatically, without a press, until the occurrence ends.

## Boundaries

- Scheduled occurrences only. A manual session keeps **End early**; a break in a manual session is a non-goal unless the maintainer decides otherwise.
- Every platform that runs schedules: Mac, iPhone and iPad, Linux, Android, and Windows. A device that receives the break late releases only the remaining part, and a break that ends while a device is offline is not taken there.
- **End early** and **Skip next session** keep their meaning and stay final; a break never ends or shortens the occurrence.
- Friction sits beside the early-end friction of `SESSION-005` (backlog) without delivering it.
- Older clients: name what a 1.5 device does with a break operation, as `SCHEDULE-003` did for sets.
- Non-goals: breaks in a pause set's own settings, notifications beyond the break's end, history of breaks (`HISTORY-001`).

## Acceptance

- `AC-01` — The product decisions below are recorded in the schedule rules with provenance, and `DESIGN.md` shows where the break is offered and how a running break reads on Session.
- `AC-02` — A proposed ADR 0006 amendment for the break fact, and any ADR 0004 or ADR 0009 delta, pass an independent security review and are accepted by the maintainer before implementation.
- `AC-03` — A break taken on one device releases websites and chosen applications on a Mac in a Tart guest, the test iPhone, Linux, Android, and Windows, and each re-applies them at the break's end with no input.
- `AC-04` — With the limit reached, the break action is unavailable and says why; after the occurrence ends, no break leftover blocks or releases anything.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- A cross-device run: a Mac and a peer in Tart guests, the test iPhone, and Linux, Android, and Windows clones, with blocking observed before, during, and after a break.
- A break whose end falls while the iPhone app is closed, proving the re-apply comes from the Device Activity extension rather than the app.
- The plan states which runs use a shortened time control and which stay on real time (`QUALITY-012` is not available).

## Decisions or blockers

- `user-confirmed` (2026-10-30, questionnaire): one break per occurrence; fixed lengths of 5, 10, and 15 minutes; a running break can be ended early and restrictions return at once, with no friction; starting a break asks the same confirmation as **End early** today, and stronger friction stays with `SESSION-005` for both.
- `user-confirmed` (2026-10-30, questionnaire): with several scheduled occurrences running, all of them break together. One press authors one break per running occurrence with the same start and length; the action is unavailable while any running occurrence has used its break or a manual pause restricts; an occurrence that starts during the break restricts its own items at once.
- iPhone constraint: Device Activity intervals have a 15-minute minimum (`observed`, `SuspendedExpiryActivity.minimumInterval`); a shorter break needs a re-apply mechanism that the plan proves on the device.
