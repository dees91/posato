# `APPLE-001`: Register required Apple resources

- **Review tier:** `high-risk`
- **Tier reason:** Manual account-level identifiers, capabilities, entitlements, and signing resources can block every Apple target.
- **Dependencies:** none
- **Integration group:** Manual Gate 7
- **Authority:** [MVP roadmap revision 2](../mvp-roadmap.md)

## Outcome

The maintainer verifies that the accepted public Apple identifiers and
development resources exist and are accessible before PR #1 starts.

## Boundaries

- Record only public stable identifiers, capability availability, results, and blockers.
- Guide the maintainer through Apple Developer and Xcode one resource at a time.
- Keep credentials, team IDs, profile files, signing identities, device IDs, and account details outside Git.
- Do not add scripts, parsers, wizards, local configuration contracts, or automated preflight.
- Do not scaffold applications or request Family Controls distribution approval.

## Acceptance

- `AC-01` — The seven accepted public identifiers have a truthful pass or blocked result.
- `AC-02` — Required development capabilities and profiles have a truthful pass or blocked result and clearing condition.
- `AC-03` — The intended Apple team and resources are visible in Xcode without recording private account data.
- `AC-04` — Gate 7 stays incomplete while any required Gate 7 resource is blocked.

## Verification

- Manual inspection in Apple Developer and Xcode.
- Documentation consistency and scoped sensitive-data scan.

## Decisions or blockers

- Family Controls distribution approval belongs to the later iOS target and enforcement work.
- Human Apple account access is required; when it blocks progress, stop and ask for that one action.
