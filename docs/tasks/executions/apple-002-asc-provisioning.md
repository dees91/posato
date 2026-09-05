# Execution: `APPLE-002`

- **Brief:** [Provision development profiles and certificates through the App Store Connect API](../specifications/apple-002-asc-provisioning.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** pending
- **Reviewer:** pending until assigned
- **Branch:** `feature/apple-002-asc-provisioning`
- **Worktree:** `~/Projects/Polyglot/posato-apple-002`
- **Updated:** 2026-09-05

## Plan

1. Confirm the module name and command shape with the maintainer.
2. Module skeleton, configuration keys, JWT signing, bounded HTTP client with
   redacted errors, unit tests.
3. App IDs, devices, certificates, and profiles resources with idempotent
   `ensure` logic and profile installation.
4. `doctor`, the developer guide, `./gradlew quality`.
5. Maintainer creates the team key; manual run for both `AC-02` profiles;
   completed-change review; record and wiki closeout; pull request.

## Result

- Not started.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- Maintainer action: App Store Connect API team key (Admin role) created and
  configured as the brief states; implementation proceeds meanwhile.

## Final

- **Status:** `active`
- **Outcome:** pending
