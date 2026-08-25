# Execution: `APPLE-001`

- **Brief:** [APPLE-001](../specifications/apple-001-register-apple-resources.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Human operator:** Project maintainer
- **Guide:** Codex
- **Plan reviewer:** pending
- **Completed-change reviewer:** pending
- **Branch:** Manual Gate 7
- **Updated:** 2026-08-25

## Safety boundary

- Record only the public identifiers listed below and pass, blocked, or pending
  results.
- Never record the Apple team ID, account email, credentials, 2FA material,
  signing identities, provisioning profile contents, certificates, or device
  identifiers.
- Do not download or add profiles, certificates, or private account artifacts
  to the repository.
- Stop when account access or a portal decision requires the maintainer.
- Use Apple Developer and Xcode directly. No repository script, parser, wizard,
  configuration file, or automated preflight is part of this task.

## Public identifier matrix

| Resource | Accepted public identifier | Result | Blocker |
| --- | --- | --- | --- |
| iOS application | `app.posato.ios` | pending | none recorded |
| macOS application | `app.posato.macos` | pending | none recorded |
| iOS activity-monitor extension | `app.posato.ios.activitymonitor` | pending | none recorded |
| macOS helper | `app.posato.macos.helper` | pending | none recorded |
| iOS App Group | `group.app.posato.ios.session` | pending | none recorded |
| Keychain access-group suffix | `app.posato.sync` | pending | none recorded |
| CloudKit container | `iCloud.app.posato.sync` | pending | none recorded |

## Capability and development-profile matrix

| Target | Required development capabilities | Development profile | Result or blocker |
| --- | --- | --- | --- |
| iOS application | Family Controls, App Groups, iCloud/CloudKit, Keychain Sharing | Required | pending |
| iOS activity monitor | Family Controls, App Groups | Required | pending |
| macOS application | iCloud/CloudKit, Keychain Sharing | Required | pending |
| macOS helper | No speculative capability; explicit identifier and signing ownership only | Deferred until the helper task selects its packaging path | pending |

If Apple requires push-notification provisioning for the selected CloudKit
development path, record that requirement when observed. It is not a Posato
notification feature promise.

Family Controls distribution approval and distribution profiles are outside
APPLE-001. Their absence does not block the PR #1 skeleton; later
`TARGETS-004` or `IOS-001` records the relevant distribution blocker.

## Manual sequence

Perform one row at a time and record the result before continuing:

1. Confirm that the intended Apple development team is visible in Apple
   Developer and Xcode. Record only pass or blocked.
2. Register or verify the iOS application identifier.
3. Register or verify the macOS application identifier.
4. Register or verify the activity-monitor extension identifier.
5. Register or verify the macOS helper identifier.
6. Register or verify the iOS App Group and its memberships.
7. Register or verify the Keychain access-group capability and suffix.
8. Register or verify the CloudKit container and development access.
9. Enable only the capabilities in the matrix on their owning identifiers.
10. Create or refresh the three required development profiles.
11. Confirm in Xcode that the team, identifiers, capabilities, and profiles are
    visible without copying private values into this record.
12. Mark every row pass or blocked and name the clearing condition for each
    blocker.

## High-risk plan review

- **Verdict:** pending
- **Critical or Required findings:** none recorded
- **Resolution:** Obtain a concise review of the identifier, capability,
  privacy, and manual sequence before the first external resource is created.

## Results

- No Apple Developer resource has been created or changed in this execution.
- No Xcode account verification has been performed in this execution.
- The next action is the plan review, followed by maintainer confirmation that
  the intended team is visible.

## Verification

| Check | Result | Evidence |
| --- | --- | --- |
| Seven public identifier rows | pending | Manual Apple Developer inspection. |
| Capability and profile rows | pending | Manual Apple Developer inspection. |
| Xcode access | pending | Maintainer confirms visibility without private values. |
| Sensitive-data boundary | pass | This record contains public identifiers only. |
| Gate 7 consistency | pending | Update after all required Gate 7 resources pass. |

## Completed-change review

- **Verdict:** pending
- **Critical or Required findings:** pending
- **Resolution:** Review after all manual results are recorded.

## Blockers

- Human access to the intended Apple development team is required.
- Any unavailable required development capability, container, or profile blocks
  Gate 7 until Apple access changes or the maintainer changes the accepted
  target boundary.

## Final

- **Status:** `active`
- **Outcome:** Pending manual Apple Developer and Xcode work.
