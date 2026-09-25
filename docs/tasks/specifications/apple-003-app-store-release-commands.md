# `APPLE-003`: Drive the iOS App Store release through `posato-provisioning`

- **Review tier:** `high-risk`
- **Tier reason:** Release operations on account-level App Store Connect resources (versions, builds, screenshots, review submissions) and uploads to URLs the service supplies.
- **Dependencies:** `APPLE-002`, `RELEASE-003` retro
- **Integration group:** retro pull request on `chore/retro-release-003`
- **Authority:** Maintainer directive of 2026-09-25 to address every `RELEASE-003` retro finding in one pull request; `AGENTS.md` (extend the tool rather than scripting around it)

## Outcome

An agent prepares and submits an iOS App Store release with `store status`, `store prepare`, and `store submit`, with no ad hoc App Store Connect script.

## Boundaries

- Reuse the existing client, token source, redaction, envelope, and error conventions; add `PATCH`; retry only `GET`.
- Follow service-supplied URLs only for screenshot upload parts: `PUT`, `https`, a host under `.apple.com`, no redirects, the operation's own headers, the exact file slice, no token, and a bounded deadline.
- Resolve `app.posato.ios` by exact bundle identifier. Stop before any write when the build is not `VALID` or the version is not editable.
- Do not create app records, certificates, or TestFlight groups. Do not touch the macOS release.
- Document the archive, export, and upload commands and the screenshot capture recipe with placeholders only.

## Acceptance

- `AC-01` — `store status` reports versions, builds, the next build number, and one version's build, What's New, and screenshot delivery, without resource identifiers.
- `AC-02` — `store prepare` changes only what differs and replaces screenshot sets with MD5-checked uploads that it waits on until `COMPLETE`.
- `AC-03` — `store submit` refuses an unready version, reuses a draft, resubmits an unresolved submission, and never creates a second submission.
- `AC-04` — The upload policy refuses a non-`PUT` method, plain `http`, and a host outside `apple.com` before sending anything.

## Verification

- Unit tests with recorded App Store Connect responses for request shapes, idempotence, refusals, and the upload policy; `./gradlew :posato-provisioning:check`.
- One live read-only `store status`. No live writes before the next release.

## Decisions or blockers

- Live `store prepare` and `store submit` stay unverified until the next iOS release.
