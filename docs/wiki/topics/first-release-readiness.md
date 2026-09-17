# First-Release Readiness

## Decisions

`user-confirmed` (2026-09-14), recorded by `RELEASE-001`:

- Posato's first release is a public open-source repository with official
  builds: macOS through a Developer ID signed and notarized download, iOS
  through the App Store.
- The repository is licensed under Apache-2.0 with copyright "Piotr Krawczyk
  and Posato contributors"; third-party runtime components and required
  attributions are listed in [`THIRD_PARTY_NOTICES.md`](../../../THIRD_PARTY_NOTICES.md).
- Security reports use GitHub private vulnerability reporting, bugs and support
  use GitHub Issues, and external pull requests are not accepted for now.
- Release verification stays local; hosted CI remains disabled.
- The privacy policy text in [`PRIVACY.md`](../../../PRIVACY.md) is accepted for
  `posato.app` with a `privacy@posato.app` contact.
  `user-confirmed` (2026-09-16, `WEB-001`): it is in effect from 2026-09-16 and
  published at `https://posato.app/privacy/`, with support at
  `https://posato.app/support/` and `support@posato.app` until GitHub Issues is
  public.
- Before the repository becomes public, session-link trailers are removed by an
  authorized history rewrite and task-share links are removed from pull-request
  bodies; the maintainer's author e-mail and pull-request attachments stay.
  `superseded` in part (2026-09-17, `RELEASE-002`): the links were in review-bot
  comments and three pull-request body edit histories, so the comments were
  deleted and reposted without links and the maintainer deleted the revisions.

## Verdict and blockers

`observed` (2026-09-14): the readiness verdict is **blocked**. Repository
history and hosted content, licensing, a clean-checkout build, public
documents, disclosures, and the residual-risk recheck pass. The blockers and
their roadmap owners (revision 15) are:

| Blocker | Owner |
| --- | --- |
| Developer ID signing with a secure timestamp, notarization, versioning, bundled notices, release JDK | `MACOS-008` |
| Supported in-app removal on macOS (added in revision 18) | `MACOS-009` |
| Release iOS configuration with Family Controls distribution, App Store Connect record, encryption declaration | `IOS-003` |
| Production CloudKit schema, quota, and retention | `SYNC-017` |
| Application icons and store assets | `DESIGN-002` |
| Styled macOS pause page (added in revision 17) | `DESIGN-003` |
| Privacy manifests and App Store privacy label | `PRIVACY-001` |
| Showcase README with screenshots and a rendered demo | `DOCS-001` |
| `posato.app` site with the hosted privacy policy, its contact, and support routes (moved from `PRIVACY-001` in revision 17) | `WEB-001` |
| Supported platform matrix, history and link cleanup, final verdict, publication hand-off | `RELEASE-002` |

`observed` (2026-09-16, `DOCS-001`): the README row is implemented on its pull
request: hero GIF, walkthrough attachment link, quick start routed to the
development guide, and the user-facing limits, availability table, and
supported platforms relocated verbatim to
[`docs/product/limits-and-platforms.md`](../../product/limits-and-platforms.md).
`user-confirmed`: Intel Macs, Android, Linux desktop, and Windows desktop are
planned for later releases with no dates. The walkthrough attachment answers
404 to signed-out visitors while the repository is private; `RELEASE-002`
rechecks it at publication.

`observed` (2026-09-15, `IOS-003`): the iOS row is cleared for internal
TestFlight. A distribution-signed Release build with Family Controls, the
shared version, and an encryption declaration is processed for record Posato,
installs from TestFlight on a physical iPhone, and starts sessions with
restrictions active. `user-confirmed`: the maintainer saw it block and clear a
website and an application. It adds release inputs: iPad stays supported with
all orientations, so iPad copy and store screenshots are needed, and EU trader
status must be declared before App Store submission.

`observed` (2026-09-16, `SYNC-017`): the production CloudKit row is cleared.
The deployed production schema equals the audited development schema and what
the code writes: `PosatoWorkspaceV1` and `PosatoEncryptedBundleV1` with four
`BYTES` fields and no index. A Developer ID Mac package and a TestFlight iPhone
link, converge both directions, share a session, remove, and re-link against
it. Quota and retention are disclosed rather than limited: a record carries
about 350 bytes of payload against the 65,536-byte cap, a deletion costs a
record like an addition, and a heavy year is about 2.3 MB (`inferred`). A full
iCloud account keeps local saves and reports a generic retryable status.

`observed` (2026-09-15, `PRIVACY-001`): the iOS app and its `ActivityMonitor`
extension bundle privacy manifests with tracking off and no collected data. A
scan of the Release binaries found only `stat` and `fstat`, both from Skiko
inside Compose Multiplatform, in the app, and no listed API in the extension.
`user-confirmed`: the app declares the file timestamp reason `0A2A.1` following
JetBrains' guidance, although Apple's text reserves that reason for third-party
SDKs; the extension declares none; the macOS bundles get no manifest; and the
App Store label answer is "Data Not Collected". `observed` (2026-09-16): the
row is cleared. TestFlight build 1.0.0 (2) with both manifests processed with no
errors or warnings, the policy URL `https://posato.app/privacy/` is set through
the App Store Connect API, and the maintainer published the label.

`user-confirmed` (2026-09-17): the maintainer's manual search of UPRP, EUIPO
TMview, WIPO Global Brand Database, and USPTO found no "Posato" mark; this is
not a legal opinion.

## Release verdict

`user-confirmed` (2026-09-17, `RELEASE-002`): the verdict is **ready** for
revision `84d0c47`, Developer ID DMG 1.0.0 (7), and TestFlight 1.0.0 (3), for
publication through GitHub Releases; App Store submission waits for Apple's
approval of EU trader status.

- The device matrix was not repeated: the maintainer relied on earlier
  release-build acceptance (`MACOS-009` notarized build 6, `SYNC-017`,
  `PRIVACY-001` TestFlight build 2). macOS 15 and iOS 18 remain unverified.
- `observed`: a clean clone passed `quality`; both candidates passed signature,
  notarization or processing, and entitlement checks.
- History cleanup was best effort. A plumbing rewrite removed the trailers from
  222 `main` commits with identical trees (`main` moved from `65443bd` to
  `84d0c47`); `git filter-repo` was unsuitable because it strips GitHub
  merge-commit signatures and would have changed 347 commits. 51 merge commits
  lost their verified signature, and the six old trailer commits stay reachable
  through pull-request refs, which only GitHub Support could purge.
- Exposure scans must include edit histories of pull-request bodies and
  comments, which GitHub publishes; only the web UI can delete a revision.
- Accepted limits: iPad shows iPhone-only copy; suspended expiry and reinstall
  behavior were not rerun on distribution builds; the Mac app has no automatic
  updater, so security fixes need a manual download.

## Evidence limits

- Physical evidence covers one Mac on macOS 26 with Safari and Chrome Stable
  and one iPhone, on development and earlier release builds; previous major
  versions and the final candidates on devices are unverified.
- Apple requirements were read from official documentation on 2026-09-14 and
  must be rechecked when the follow-up rows run.

Details are in the
[`RELEASE-001` record](../../tasks/executions/release-001-first-release-readiness.md)
and the [`RELEASE-002` record](../../tasks/executions/release-002-release-candidate.md).
