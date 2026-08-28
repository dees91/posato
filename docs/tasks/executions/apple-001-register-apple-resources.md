# Execution: `APPLE-001`

- **Brief:** [APPLE-001](../specifications/apple-001-register-apple-resources.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Human operator:** Project maintainer
- **Guide:** Codex
- **Plan reviewer:** independent Codex agent
- **Completed-change reviewer:** independent Codex agent
- **Branch:** Manual Gate 7
- **Updated:** 2026-08-26

## Safety boundary

- Record only the public identifiers listed below and pass, blocked, or pending
  results.
- Never record the Apple team ID, account email, credentials, 2FA material,
  signing identities, provisioning profile contents, certificates, or device
  identifiers.
- Do not download or add profiles, certificates, or private account artifacts
  to the repository.
- Stop when account access or a portal decision requires the maintainer.
- Use Apple Developer and CloudKit Console directly, with Xcode limited to team
  visibility. No repository script, parser, wizard, configuration file, or
  automated preflight is part of this task.

## Registered Apple resource matrix

| Resource | Accepted public identifier | Result | Blocker |
| --- | --- | --- | --- |
| iOS application | `app.posato.ios` | pass | none recorded |
| macOS application | `app.posato.macos` | pass | none recorded |
| iOS activity-monitor extension | `app.posato.ios.activitymonitor` | pass | none recorded |
| macOS helper | `app.posato.macos.helper` | pass | none recorded |
| iOS App Group | `group.app.posato.ios.session` | pass | none recorded |
| CloudKit container | `iCloud.app.posato.sync` | pass | none recorded |

The CloudKit container string receives a final manual comparison with this
matrix before registration because Apple does not support renaming or deleting
a container after creation.

## Accepted entitlement value

| Value | Owning targets | Gate 7 verification | Result | Blocker |
| --- | --- | --- | --- | --- |
| `app.posato.sync` | iOS and macOS applications | Confirm the accepted public suffix; configure and verify Keychain Sharing only after the owning application path exists. Do not register it as a separate portal resource or record its resolved team prefix. | pass | The suffix is retained; later configuration is owned by `SYNC-005` and `SYNC-006`. |

## Development capability matrix

| Target | Required Gate 7 portal capabilities | Required association | Result or blocker |
| --- | --- | --- | --- |
| iOS application | Family Controls development, App Groups, iCloud/CloudKit | App Group and CloudKit container | pass |
| iOS activity monitor | Family Controls development, App Groups | App Group | pass |
| macOS application | iCloud/CloudKit | CloudKit container | pass |
| macOS helper | No speculative capability; explicit App ID and signing ownership only | None | pass |

Any target-level push-notification entitlement introduced by the later
CloudKit implementation is not a Posato user-notification feature promise and
is verified with that owning target rather than inferred at Gate 7.

Target entitlements, signing, development profiles, Family Controls
distribution approval, and distribution profiles are outside APPLE-001. Their
absence does not block the credential-free PR #1 skeleton. The task that
configures each real target and capability verifies its profile and signed
entitlements; later `TARGETS-004` or `IOS-001` records the relevant Family
Controls distribution blocker.

`SYNC-005` owns iOS Keychain Sharing target configuration and verification.
`SYNC-006` owns the equivalent macOS external-build entitlement,
provisioning, signing, and verification path.

## Manual sequence

Perform one row at a time and record the result before continuing:

1. Confirm that the intended Apple development team is visible in Apple
   Developer and Xcode. Record only pass or blocked.
2. Register or verify the iOS application identifier.
3. Register or verify the macOS application identifier.
4. Register or verify the activity-monitor extension identifier.
5. Register or verify the macOS helper identifier.
6. Register or verify the iOS App Group.
7. Compare the final CloudKit container string with the matrix, then register
   or verify the container and development access.
8. Enable App Groups on the iOS application and activity-monitor App IDs, and
   assign the registered App Group.
9. Enable iCloud with CloudKit support on the iOS and macOS application App IDs,
   and assign the registered CloudKit container.
10. Enable Family Controls development on the iOS application and
    activity-monitor App IDs.
11. Confirm that the accepted public Keychain access-group suffix remains in
    this record. Do not perform a portal Keychain action, register a separate
    resource, or record the resolved team prefix. Defer implementation and
    verification to `SYNC-005` and `SYNC-006`.
12. Verify the App IDs, App Group, capabilities, and associations in Apple
    Developer, and verify the container in CloudKit Console.
13. Mark every row pass or blocked and name the clearing condition for each
    blocker.

## High-risk plan review

- **Initial verdict:** changes required; no Apple resource was created
- **Critical findings:** none
- **Required findings:**
  - Before target scaffolding, Xcode can confirm account and team access but
    cannot verify the complete identifier and capability matrix. Portal
    resources belong to Certificates, Identifiers & Profiles, with the
    container also inspectable in CloudKit Console. Target entitlement and
    signing verification must wait until each target exists.
  - Three development profiles are the eventual manual-signing count for the
    iOS application, activity-monitor extension, and macOS application, but
    creating them before their targets exist does not verify final entitlements
    and does not unblock the credential-free PR #1 skeleton. Profile ownership
    must be clarified or explicitly retained as a manual Gate 7 choice.
  - `app.posato.sync` is a public Keychain access-group suffix configured in
    target entitlements, not a separately registered portal resource. Its
    resolved team-prefixed value remains private.
  - The manual sequence must register the four App IDs, App Group, and iCloud
    container before enabling and assigning their owning capabilities; any
    retained manual profiles are generated only after capability changes.
- **Corrected-plan verdict:** approved on 2026-08-26
- **Corrected-plan findings:** no Critical, Required, Recommended, or Optional
  findings
- **Resolution:** The maintainer accepted the corrected Gate 7 boundary on
  2026-08-26. The brief, accepted preparation checklist, roadmap, wiki, and
  manual sequence were updated consistently. Independent re-review confirmed
  that every Required finding is resolved and the manual plan may proceed one
  resource at a time.
- **Keychain-boundary amendment verdict:** changes required, narrowly, on
  2026-08-26; the proposed technical boundary was approved
- **Keychain-boundary Required findings:**
  - Clear the stale pending macOS-helper capability row using the already
    passed App ID and ownership evidence.
  - Remove Keychain Sharing from the portal capability rows and manual portal
    step, retain only the accepted suffix, pass the iOS and macOS application
    rows from preserved evidence, and leave functionality, profile
    authorization, and signed-entitlement claims to later owners.
  - Complete the independent completed-change review after the correction.
- **Keychain-boundary resolution:** The maintainer accepted the amendment on
  2026-08-26. The helper and non-Keychain portal rows now pass, and the brief,
  checklist, roadmap, execution record, and wiki route later Keychain work to
  `SYNC-005` and `SYNC-006`. The completed-change review approved the resolved
  boundary.

## Results

- No Xcode target, entitlement, signing, certificate, or profile action has
  been performed in this execution.
- The independent high-risk plan review completed with changes required.
- The maintainer accepted the corrected Gate 7 boundary on 2026-08-26.
- The independent corrected-plan review completed with approval.
- `user-confirmed` (2026-08-26): the intended team is visible and Certificates,
  Identifiers & Profiles is accessible in Apple Developer. No private team or
  account value was recorded.
- `user-confirmed` (2026-08-26): the intended team is visible in Xcode. No
  private team or account value was recorded.
- `user-confirmed` (2026-08-26): the exact iOS application App ID
  `app.posato.ios` passes manual verification. Whether it pre-existed or was
  registered during this task was not recorded.
- `user-confirmed` (2026-08-26): the exact macOS application App ID
  `app.posato.macos` passes manual verification. Whether it pre-existed or was
  registered during this task was not recorded.
- `user-confirmed` (2026-08-26): the exact iOS activity-monitor extension App
  ID `app.posato.ios.activitymonitor` passes manual verification. Whether it
  pre-existed or was registered during this task was not recorded.
- `user-confirmed` (2026-08-26): the exact macOS helper App ID
  `app.posato.macos.helper` passes manual verification. Whether it pre-existed
  or was registered during this task was not recorded.
- `user-confirmed` (2026-08-26): the exact iOS App Group
  `group.app.posato.ios.session` passes manual verification. Whether it
  pre-existed or was registered during this task was not recorded.
- `user-confirmed` (2026-08-26): the exact CloudKit container
  `iCloud.app.posato.sync` passes manual verification after the required final
  string comparison. Whether it pre-existed or was registered during this task
  was not recorded.
- `user-confirmed` (2026-08-26): App Groups is enabled on `app.posato.ios`, and
  `group.app.posato.ios.session` is assigned to that App ID.
- `user-confirmed` (2026-08-26): App Groups is enabled on
  `app.posato.ios.activitymonitor`, and `group.app.posato.ios.session` is
  assigned to that App ID.
- `user-confirmed` (2026-08-26): iCloud with CloudKit support is enabled on
  `app.posato.ios`, and `iCloud.app.posato.sync` is assigned to that App ID.
- `user-confirmed` (2026-08-26): iCloud with CloudKit support is enabled on
  `app.posato.macos`, and `iCloud.app.posato.sync` is assigned to that App ID.
- `user-confirmed` (2026-08-26): Family Controls development is enabled on
  `app.posato.ios`; no distribution request was made.
- `user-confirmed` (2026-08-26): Family Controls development is enabled on
  `app.posato.ios.activitymonitor`; no distribution request was made. The
  extension's complete Gate 7 capability row passes.
- `user-confirmed` (2026-08-26): while inspecting the available capability
  list for the application App ID, the maintainer found no entry whose name
  contains `Keychain`. No substitute capability was selected and no portal
  change was made for this step.
- `source-claim` (2026-08-26): Apple's current Keychain Sharing guidance adds
  the capability to an owning Xcode target, where Xcode updates its
  entitlements and prepends the application-identifier prefix. Apple also
  documents an explicit entitlement and signing path for externally built
  macOS software. The supported-capabilities tables describe provisioning
  profile support.
- `inferred` (2026-08-26): Apple's target, entitlement, signing, and
  provisioning guidance does not establish a separate Keychain Sharing
  resource or required manual App ID association for this pre-target gate.
- `user-confirmed` (2026-08-26): manual portal Keychain checks are removed from
  APPLE-001. The accepted `app.posato.sync` suffix remains public;
  `SYNC-005` owns iOS target configuration and verification, while `SYNC-006`
  owns the equivalent macOS external-build entitlement, provisioning,
  signing, and verification path.
- APPLE-001 is complete. The next handoff is the still-incomplete Ready to open
  PR #1 checkpoint; no production scaffolding is authorized by this result.

## Verification

| Check | Result | Evidence |
| --- | --- | --- |
| Six registered resource rows | pass | All six rows pass manual inspection. |
| Keychain suffix and non-Keychain portal capability rows | pass | The suffix is retained; Family Controls development, App Groups, and iCloud/CloudKit portal rows and associations pass. No later Keychain functionality, profile, or signed-entitlement claim is made. |
| Apple Developer team access | pass | Maintainer confirmed intended-team visibility and Certificates, Identifiers & Profiles access without private values. |
| Xcode access | pass | Maintainer confirmed intended-team visibility without private values. |
| Sensitive-data boundary | pass | This record contains public identifiers only. |
| Gate 7 consistency | pass | The accepted authorities and execution record use the corrected pre-scaffold boundary and name the later Keychain owners. |
| Documentation diff health | pass | `git diff --check` passed after the last correction. |
| Active-handoff scan | pass | No current authority still calls APPLE-001 active, first incomplete, or the next task. |

## Completed-change review

- **Initial verdict:** changes required
- **Critical findings:** none
- **Required finding:** The preparation plan and roadmap still routed work to
  APPLE-001 after Gate 7 had been marked complete.
- **Resolution:** Both handoffs now route to the incomplete Ready to open PR #1
  checkpoint. Fresh documentation consistency and `git diff --check` checks
  passed.
- **Final verdict:** approved on 2026-08-26
- **Final findings:** no Critical, Required, Recommended, or Optional findings

## Blockers

- None.

## Final

- **Status:** `done`
- **Outcome:** Team access, all registered resources, both App Groups
  associations, both CloudKit associations, and iOS application Family
  Controls development passed; the complete extension row also passed. The
  accepted Keychain suffix is retained without a portal action, and later
  configuration and signed-entitlement verification remain with `SYNC-005`
  and `SYNC-006`. The independent completed-change review approved the result.
  The Ready to open PR #1 checkpoint remains incomplete.
