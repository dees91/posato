# `TARGETS-013`: Open a website briefly every N minutes during a pause

- **Review tier:** `high-risk`
- **Tier reason:** The rule enters the synchronized model as a new format-1 operation (ADR 0006), and each platform must open and close a website on a timer without a person's action, which revises the exact-domain presentation of ADR 0005 and the helper behavior bound by ADR 0004 and ADR 0010.
- **Dependencies:** none (R1.6/W1). `DOCS-007` and `RELEASE-010` depend on this row.
- **Integration group:** `PR-TIMED-WEBSITE`
- **Authority:** [Release roadmap](../release-roadmap.md) row `TARGETS-013`; idea 46 in [open questions](../../wiki/topics/mvp-open-questions.md) (`user-confirmed` 2026-10-20); [pause sets](../../product/pause-sets.md); [DESIGN.md](../../../DESIGN.md). The maintainer named the row on 2026-10-30.

## Outcome

A website in a pause set can carry a window rule, for example open for 5 minutes once an hour, and during a pause that uses the set the website opens and closes on that rhythm on every device of the workspace, while the pause page says when it opens next.

## Boundaries

- Decide first, then deliver: the product decision, the `DESIGN.md` screens and copy, and the ADR amendments are accepted before any product code, as the roadmap row requires.
- Websites only. Applications keep their current behavior; a window rule for applications is a non-goal.
- Every platform with website enforcement: Mac, iPhone and iPad, Linux, Android, and Windows. A device that is offline or asleep follows the rule from its own clock.
- The exact-domain contract of ADR 0005 stays in force; the rule applies to one saved website and its covered `www` form (`TARGETS-006`), not to a pattern.
- Older clients: name what a 1.5 device does with the rule.
- Non-goals: a per-website daily budget, usage counting, browsing history (forbidden by `PRIVACY.md`).

## Acceptance

- `AC-01` — The decisions below are recorded in the pause-set product page with provenance, and `DESIGN.md` shows how the rule is set and how the pause page and Session show an open window.
- `AC-02` — A proposed ADR 0006 amendment, and the ADR 0005 and ADR 0010 deltas, pass an independent security review and are accepted by the maintainer before implementation.
- `AC-03` — During a pause, a website with a rule loads in a browser only inside its window on a Mac in a Tart guest, the test iPhone, Linux, Android, and Windows, and is blocked again after it, with no input.
- `AC-04` — The pause page shows the next opening time; a website without a rule behaves exactly as before.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Per target, a browser load observed blocked, open inside the window, and blocked after it, with the app closed where the platform allows.
- A rule edited on one device during a running pause, observed on a peer.
- The plan states which runs use a shortened rhythm and which stay on real time.

## Decisions or blockers

- `user-confirmed` (2026-10-30, questionnaire): the rule belongs to a website inside a set, so the same website in another set may have none; windows sit at fixed clock times aligned to the hour (every 30 minutes, 1 hour, or 2 hours, starting at :00), not counted from the pause start; the window lasts 5, 10, or 15 minutes, fixed choices only; the website opens by itself in its window, and the pause page says when it opens next.
- iPhone constraint: Device Activity intervals have a 15-minute minimum (`observed`, `SuspendedExpiryActivity.minimumInterval`), and the shield cannot run a timer of its own; the decision must name what the iPhone can honor.
