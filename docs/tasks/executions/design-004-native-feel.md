# Execution: `DESIGN-004`

- **Brief:** [design-004-native-feel.md](../specifications/design-004-native-feel.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude Code
- **Reviewer:** pending
- **Branch:** `spike/ios-navigation-feel`
- **Updated:** 2026-10-05

## Plan

1. Start from the spike commits on PR #137, rebased on `main`.
2. Run a native `impeccable audit` of iOS and the Mac; the maintainer
   chooses the polish scope from its findings and the known gaps.
3. Run one polish round on that scope.
4. Adapt `posato-control` and `verify-posato` to the new interface and run
   the existing scenarios and flows.
5. Rewrite the affected `DESIGN.md` sections and route the wiki to them.
6. Verify the final head on the Simulators, the Mac in a Tart VM, and the
   test iPhone; request the independent review; describe the pull request.

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- None.

## Final

- **Status:** active
- **Outcome:** pending
