# `MACOS-029`: The Mac applies restrictions again by itself and asks the person only when it must

- **Review tier:** High-risk
- **Tier reason:** Lets the resident application request grant-authorized
  Apply without a person's action, which
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  "Who may request it" forbids today, and revises a `DESIGN.md` rule. A plan
  review of the authorization precedes implementation.
- **Dependencies:** None.
- **Integration group:** PR-MAC-SELF-RECOVERY
- **Authority:** [Release roadmap](../release-roadmap.md) revision 31, row
  `MACOS-029`; ideas 44 and 55 and the maintainer's request on 2026-10-25.
- **Record:** [execution record](../executions/macos-029-self-recovery.md)

## Outcome

When Posato can restore a pause's restrictions by itself, it does so without
a notice; the red notice appears only when the person must act.

## Boundaries

- Covers a pause received from another device, after sleep, after a login
  launch, and after the helper or its daemon was lost during a pause (idea
  55: Session showed "Restrictions need attention" and **Retry**, and
  **Retry** applied without a prompt through the standing grant).
- Applies by itself only while administrator approval is on (the standing
  grant) and the automatic-start consent is valid, as a schedule does today;
  with bounded retries and no notice in between.
- "Restrictions need attention" and **Resume restrictions** remain only when
  approval is off, the grant is missing or refused, or the retries fail.
- Includes `SESSION-009`: the notice left after an unresumed session from
  another device ends.
- Amends ADR 0004 "Who may request it" and the `DESIGN.md` rule that the
  menu, a login launch, and synchronization never apply by themselves; both
  wordings need the maintainer's acceptance before implementation.
- Keeping the helper alive through a slow moment belongs to `MACOS-031`.
- Non-goals: iPhone, Linux, Android.

## Acceptance

- `AC-01` — With approval on, a pause whose helper is ended during it
  applies again by itself and Session shows no red notice; a test shows the
  notice on `main` first.
- `AC-02` — With approval on, a pause started on the iPhone, a pause across
  sleep and wake, and a pause found at a login launch apply by themselves.
- `AC-03` — With approval off, or when every retry fails, the notice and its
  button appear as today, and pressing it still works.
- `AC-04` — An unresumed session from another device that ends leaves no
  notice and no busy loop (`SESSION-009`), shown failing first.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `AC-01`, `AC-03` in a Tart VM through `posato-control`, ending the helper
  process during a pause.
- `AC-02` with a linked Tart VM and the test iPhone, `vm` sleep and wake,
  and a login launch in the VM.
- `AC-04` through the isolated session-owner regression named in the
  backlog row, then one linked run.

## Decisions or blockers

- The ADR 0004 and `DESIGN.md` amendment wording, for the maintainer.
