# `MACOS-027`: Mac helper readiness that agrees with what the helper does

- **Review tier:** Standard
- **Tier reason:** A defect in how the Mac application reads helper
  readiness. The fix is expected to stay in the application's readiness check
  and its recovery. Escalate to High-risk, with a plan review, before
  implementation if the fix changes the helper signature verification, the
  XPC peer requirement, the daemon registration, or another
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  contract.
- **Dependencies:** None; release 1.4, wave 1. `DOCS-005` and `RELEASE-006`
  wait for this row unless it returns to the backlog.
- **Integration group:** PR-MAC-HELPER-READINESS
- **Authority:** [Release roadmap](../release-roadmap.md) revision 20, row
  `MACOS-027`; the maintainer named the row on 2026-10-09.
- **Record:** [execution record](../executions/macos-027-helper-readiness.md)

## Outcome

On a Mac where the helper is allowed and blocking works, Session, setup, This
Mac, and schedules report the helper as ready. A helper that really cannot
block is still reported as unavailable.

## Boundaries

- Start from idea 29 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md): on 1.3.0 (28)
  Session said "Setup incomplete", a schedule "couldn't start here", **Finish
  setup** ended in "Blocking could not be turned on yet", and This Mac
  reported `MacHelperReadiness.UNAVAILABLE`. At the same time System Settings
  allowed the helper and blocking worked. Clean Tart installs and the
  `RELEASE-005` update from 1.2.0 reported the helper ready.
- **Timebox:** one work session to reproduce the defect in a Tart VM. If it
  does not reproduce, the execution record lists every attempt and its
  evidence, the hypotheses stay `open` in idea 29, and the maintainer moves the
  row back to the backlog in a roadmap revision. Then `DOCS-005` and
  `RELEASE-006` no longer wait for it. No fix is made without a reproduction.
- Find out whether the schedule really did not start or whether only the
  report was wrong.
- Never run, install, or reset Posato on the maintainer's Mac. Every
  reproduction runs in Tart. A read-only check of the maintainer's install is
  allowed only if the maintainer decides it below.
- Non-goals: the helper protocol, proxy apply and restore, the standing grant,
  the update feed, iOS, and any weakening of the signature checks.
- Tart runs at most two macOS guests at once on this host, and the `SYNC-021`
  and `TARGETS-009` recheck and `MACOS-026` share them. Agree on VM use before
  each run.

## Reproduction plan

Hypotheses ranked from the code, all `hypothesis` until a run proves them:

1. **The readiness check verifies the signature again on every call; the
   running helper does not.** `DesktopMacHelperState.readiness` calls
   `MacOsHelperSigningVerifier.verify` before every Status, Enable, and
   Recheck. That check runs `codesign --verify --deep --strict` on the whole
   bundle, including the Java runtime, with a 5 s timeout. Any failure maps to
   `UNAVAILABLE`. `MacOsHelperClient` verifies only when it starts the helper
   process and then reuses that process, so enforcement keeps working
   (`inferred` from the code). Two variants:
   - (a) The check takes longer than 5 s on a busy, long-running Mac with a
     cold disk cache.
   - (b) The bundle no longer passes `--strict` verification after it was
     installed, for example because of Finder information, extended
     attributes, or files left inside it.
2. **Leftover background item records.** Earlier Posato copies in other
   locations or with other signing identities (development packages, older
   releases) leave records. The current bundle's `SMAppService` status then
   reads `.notRegistered` or `.requiresApproval` and the request fails, which
   maps to `UNAVAILABLE`. Meanwhile System Settings shows another record as
   allowed.
3. **An older daemon is still running after an in-app update.** The XPC
   connection then fails (`NSXPCConnectionInvalid` → `PipeFailure.unavailable`).
   This is less likely, because blocking works.

Runs, cheapest first, on `main` builds and published DMGs:

- **Baseline:** time `codesign --verify --deep --strict` on the installed
  `/Applications/Posato.app` in a guest, both cold and warm.
- **H1a:** saturate the guest's CPU and disk and purge the disk cache, then
  open This Mac and use **Check again**.
- **H1b:** add Finder information, an extended attribute, and a stray file to
  the bundle, then open This Mac.
- **The maintainer's update path:** `v1.0.0` set up with the helper, a
  website, and a schedule. Then `vm install --replace` to `v1.1.0`. Then the
  in-app update or `vm install --replace` through `v1.2.0` to `v1.3.0`. After
  each step, read Session and This Mac, run `observe` during a pause, and
  watch a scheduled start.
- **H2:** register a second, differently signed or differently located copy,
  remove it, then read the readiness of the remaining copy.

## Acceptance

- `AC-01` — A Tart reproduction on `main` that shows the false
  `UNAVAILABLE` while `observe` reports blocking, with its steps, revision,
  and evidence directory in the execution record. Or, if there is no
  reproduction, the attempts list described in the timebox.
- `AC-02` — The cause is recorded with provenance labels on the
  [macOS enforcement](../../wiki/topics/macos-enforcement.md) wiki page, along with whether the schedule did not start or only
  its report was wrong.
- `AC-03` — With the fix, the `AC-01` steps report ready in Session, setup,
  This Mac, and the schedule, and the schedule starts on its own.
- `AC-04` — A helper that cannot block is still reported as unavailable, and
  a tampered helper is still refused. Shown in Tart by moving the helper out
  of the bundle and by breaking its signature.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Tart runs of the `AC-01` steps before and after the change, followed by a
  manual pause and a scheduled start that `observe` reports as blocked, and
  the `AC-04` refusal runs.
- An isolated test only if a credible failure cannot be reached end to end,
  written failing first under the testing policy.

## Decisions or blockers

- Whether the agent may run read-only checks of the maintainer's real
  install, never Posato itself: `codesign --verify --deep --strict` with its
  duration, the extended attributes on the bundle, and the list of Posato
  background items.
- Which versions and copies the maintainer's Mac has run. This includes
  development packages installed before 2026-09-24 and any copy outside
  `/Applications`.
