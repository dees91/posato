# Execution: `RELEASE-005`

- **Brief:** [Verify the 1.3.0 candidates and publish Posato 1.3](../specifications/release-005-release-1-3.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review; pending
- **Branch:** `docs/release-005-release-1-3`
- **Updated:** 2026-10-04

## Plan

Revision 1. The `RELEASE-004` route, with the 1.3 differences marked.

1. **Plan review** before R is built.
2. **Tooling, not in R, with its own Standard review:** `posato-provisioning
   store prepare --subtitle <text>` sets the en-US subtitle in the app's
   editable App Information, changing it only when it differs. Failing
   tests first, as for the existing `store` commands; the first live call is
   step 4b. (Maintainer decision 2026-10-04: extend the tool.)
3. **Release commit R:** `MARKETING_VERSION = 1.3.0` and the signed-feed
   notes `docs/releases/1.3.0-appcast-notes.txt`, one file for both feeds,
   worded to stay true whatever step 5 shows. README, site, store, and
   `PRIVACY.md` text come from `DOCS-004` (#131), not R. R is pinned by its
   full hash.
4. **Candidates from a clean clone of R:**
   - clean-clone `./gradlew quality`;
   - **arm64:** `generateMacOsUpdateFeed`, release channel, build 28,
     Developer ID G2 identity and the sync Developer ID profile; then
     **x86-64:** the same with `-PposatoMacOsArchitecture=x86_64` in the
     same clone, so it finds the arm64 feed beside it and shares build 28.
     One bold Keychain request to the maintainer per prompt. The two
     `SHA256SUMS` are merged into one with both DMG lines.
   - **x86-64 verification build of R:** a development package with
     `-PposatoMacOsArchitecture=x86_64 -PposatoMacOsAllowRosetta=true`
     (`posato-control build -t desktop --arch x86_64 --allow-rosetta`),
     never published.
   - **iOS:** archive, export, and upload as build 6; `VALID` in
     TestFlight; inspect the IPA's signature, entitlements, and privacy
     manifests.
   4b. **iOS store record before the sitting:** `store prepare --version
   1.3.0 --build 6 --whats-new docs/store/en-US/whats-new-1.3.0.txt
   --description docs/store/en-US/description.txt --screenshots
   docs/store/en-US/screenshots --subtitle "Space for what matters."
   --release after-approval` (files from #131), then `store status
   --version 1.3.0` shows everything complete. Nothing is submitted.
5. **Unattended verification on the notarized DMGs** (`vm install --dmg`):
   - macOS 26 `primary`, fresh: unified setup, a second pause set, a
     manual pause with it, a schedule 3 minutes ahead with the window
     closed that starts and ends on its own, back with Command-[,
     `observe`.
   - macOS 15 `legacy`, fresh: the same core flow plus a login launch
     (`vm shutdown`, `vm boot`) with a scheduled start.
   - Upgrade from 1.2.0 on `primary`: the `v1.2.0` DMG set up with the
     helper, two websites, an application, and a schedule; `vm install
     --replace` with the 1.3.0 DMG: one first set holds the websites and
     is the default, the schedule uses it, Start asks for no password and
     blocks, the schedule still starts.
   - macOS 13 `ventura` (arm64 guest): the x86-64 release DMG installs,
     Gatekeeper accepts it, and it shows "This version is for Intel Macs"
     and quits; the x86-64 verification build of R completes setup, a
     manual pause that `observe` reports blocked, and a scheduled start.
   - Linked pair on candidates (`primary` and `peer`, Production
     CloudKit): a set made on one Mac appears on the other and a schedule
     using it starts on both. A CloudKit failure of the test account is
     recorded as a blocker of this check only, with the failing command.
   - Test iPhone (development build of R): pause sets, a manual pause with
     the shield and "Website Not Allowed", a scheduled start with Posato
     closed, and the back gesture.
   - A `primary` clone with 1.2.0 set up (helper, websites, a schedule,
     update consent answered) is kept for the in-app update.
6. **Completed-change review** of R, the candidates, and the evidence.
7. **Publication sitting** (go given 2026-10-04):
   1. Rebase #131 onto `main`, merge it (squash); rebase this branch, set
      `PRIVACY.md`'s effective date to the publication date, merge it.
   2. Tag `v1.3.0` on the final `main` head only when `git diff --name-only
      R HEAD` lists nothing outside `docs/`, `website/`, `video/`,
      `.github/`, `README.md`, `PRIVACY.md`, and `tools/`; `LICENSE`,
      `NOTICE`, `THIRD_PARTY_NOTICES.md`, and `Version.xcconfig` must
      equal R.
   3. Draft release with exactly the five assets, byte comparison with
      step 4, publication as latest with the `DOCS-004` release notes.
   4. `releases/latest/download/appcast.xml` and `appcast-intel.xml`
      resolve and verify. **In-app update** on the prepared clone: About,
      Check for Updates, install, `launch --adopt`, a first set with the
      websites, helper ready, `observe` blocked. It fails when the update is
      not offered, the install errors, data or the helper is lost, or
      `observe` does not block. **Rollback:** `gh release edit v1.2.0
      --latest` so both stable URLs serve 1.2.0 again (1.2.0 has no Intel
      feed, which Intel installs do not exist yet to read), hold iOS, and
      revert the #131 commit on `main`; a fix build then needs build 29.
   5. iOS: `store submit --version 1.3.0`.
   6. Site and README check, Projects Done, milestone `1.3.0` closed.
8. A follow-up PR records the App Review outcome.

**Maintainer steps:** the Keychain prompts in step 4; nothing else unless a
dialog cannot be driven.

## High-risk plan review

Pending.
