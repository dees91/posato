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
- Before the repository becomes public, session-link trailers are removed by an
  authorized history rewrite and task-share links are removed from pull-request
  bodies; the maintainer's author e-mail and pull-request attachments stay.

## Verdict and blockers

`observed` (2026-09-14): the readiness verdict is **blocked**. Repository
history and hosted content, licensing, a clean-checkout build, public
documents, disclosures, and the residual-risk recheck pass. The blockers and
their roadmap owners (revision 15) are:

| Blocker | Owner |
| --- | --- |
| Developer ID signing with a secure timestamp, notarization, versioning, bundled notices, release JDK | `MACOS-008` |
| Release iOS configuration with Family Controls distribution, App Store Connect record, encryption declaration | `IOS-003` |
| Production CloudKit schema, quota, and retention | `SYNC-017` |
| Application icons and store assets | `DESIGN-002` |
| Privacy manifests, App Store privacy label, hosted policy and contact | `PRIVACY-001` |
| Showcase README with screenshots and a rendered demo | `DOCS-001` |
| Supported platform matrix, history and link cleanup, final verdict, publication hand-off | `RELEASE-002` |

`open`: automated searches of official trademark databases were refused, so a
manual trademark check remains with the maintainer.

## Evidence limits

- Physical evidence covers one development-signed Mac on macOS 26 with Safari
  and Chrome Stable and one iPhone; previous major versions and distribution
  signing are unverified.
- Apple requirements were read from official documentation on 2026-09-14 and
  must be rechecked when the follow-up rows run.

Details are in the
[execution record](../../tasks/executions/release-001-first-release-readiness.md).
