# `APPLE-001`: Register required Apple resources

- **Review tier:** `high-risk`
- **Tier reason:** Manual account-level identifiers, capabilities, entitlements, and signing resources can block every Apple target.
- **Dependencies:** none
- **Integration group:** Manual Gate 7
- **Authority:** [MVP roadmap revision 2](../mvp-roadmap.md)

## Outcome

The maintainer verifies that the six registered Apple resources exist, the
accepted public Keychain access-group suffix is retained, and the required
non-Keychain portal capabilities are available and associated before PR #1
starts.

## Boundaries

- Record only public stable identifiers, capability availability, results, and blockers.
- Guide the maintainer through Apple Developer, CloudKit Console, and Xcode
  team access one resource at a time.
- Keep credentials, team IDs, profile files, signing identities, device IDs, and account details outside Git.
- Do not add scripts, parsers, wizards, local configuration contracts, or automated preflight.
- Do not scaffold applications or request Family Controls distribution approval.
- Defer target entitlement, signing, and development-profile verification to
  the task that configures each real target and capability.
- Defer iOS Keychain Sharing target configuration and verification to
  `SYNC-005`. Defer the equivalent macOS external-build entitlement,
  provisioning, signing, and verification path to `SYNC-006`; do not assume a
  macOS Xcode target.

## Acceptance

- `AC-01` — The four explicit App IDs, App Group, and CloudKit container have a truthful pass or blocked result.
- `AC-02` — The public Keychain suffix and required non-Keychain portal capability associations have a truthful pass or blocked result and clearing condition.
- `AC-03` — Apple Developer and CloudKit Console show the required resources, and Xcode shows the intended team, without recording private account data.
- `AC-04` — Gate 7 stays incomplete while any required Gate 7 resource is blocked.

## Verification

- Manual inspection in Apple Developer and CloudKit Console, plus Xcode team
  visibility.
- Documentation consistency and scoped sensitive-data scan.

## Decisions or blockers

- Family Controls distribution approval belongs to the later iOS target and enforcement work.
- Development profiles and signed-target entitlement checks belong to the
  tasks that configure their owning targets and capabilities.
- `SYNC-005` and `SYNC-006` own Keychain Sharing configuration, provisioning
  authorization, and signed-entitlement verification for their respective
  application packaging paths.
- Human Apple account access is required; when it blocks progress, stop and ask for that one action.
